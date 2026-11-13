# Backend-to-gateway command channel — 2026-11-13

Outcome: a tenant-authorized backend use-case can pass already encoded bytes through
an authenticated private HTTP channel to an exact live TCP connection in the
independently deployed device gateway. No command syntax or device acknowledgement
is invented. Both ends are disabled by default. Nothing was deployed.

## Boundaries and flow

```text
trusted protocol encoder (command-specific integration remains future work)
  -> TrackerCommandDispatcher.dispatch(trackerId, attemptId, expiresAt, encodedBytes)
     -> current tenant + devices.command + scoped tracker lookup
     -> Redis opaque live connection token
     -> CommandWriteChannel / HttpCommandWriteChannel
        -> POST /internal/command-writes on the configured device gateway
           -> credential, size, expiry and correlation checks
           -> persistent CommandWriteJournal intent before socket write
           -> ReactiveTcpServer.writeCommand(exact connection token, exact bytes)
           -> typed CommandWriteResult
```

The shared contract is in `shared/.../commands/`; no business-module dependency was
added to `device-comm`. `CommandWrite` accepts 1–4096 bytes, copies arrays defensively
and carries an attempt UUID, device ID, exact connection ID and expiry. Internal
JSON uses camelCase and Base64 bytes regardless of the business API's snake_case
mapper. The channel adds no `#`, newline, text conversion or other framing.

`TrackerCommandDispatcher` checks the current user/tenant and exact
`devices.command` authority, looks up the tracker within that tenant and derives
the device/connection server-side. Missing/foreign trackers return the same 404;
missing scope/permission returns 403. It is an internal bean, **not a new public
raw-command endpoint**. Background jobs cannot borrow an HTTP user's ThreadLocal
context; any later job integration needs its own explicit authorization boundary.

Only the opaque Redis reverse-lookup token is needed by the backend. Gateway
metadata is not deserialized through the business API's differently named fields.
The gateway verifies that the exact connection exists, is still the current route
and is pinned to the requested device. Connection IDs are UUIDs, not counters that
repeat after restart. Once identified, a TCP connection cannot switch device IDs.
Redis cleanup atomically removes a reverse route only if the closing connection
still owns it, so an old socket cannot erase its replacement's route.
This is connection fencing, **not physical device authentication**; the existing
device ingress trust limitations remain.

## Results, duplicates and uncertainty

- `SENT`: the gateway's local socket write completed. It is **not** proof of
  hardware receipt, execution or an acknowledgement.
- `NOT_CONNECTED` / `STALE_CONNECTION`: the exact local route was unavailable or
  no longer matched. There is no fallback to another connection or another device.
- `EXPIRED`: this request's admission window has ended. It does not prove an
  earlier attempt with the same ID never wrote bytes.
- `REJECTED`: invalid/configuration/authentication/capacity rejection.
- `ID_CONFLICT`: an existing attempt ID was supplied with a different envelope.
- `UNKNOWN`: network timeout, malformed/mismatched response, interrupted write,
  corrupt receipt or another ambiguous result. **Do not automatically resend.**
- `DISABLED`: the backend channel is not enabled.

Callers must persist and reuse the attempt ID, exact expiry and encoded bytes for
one logical attempt, and never reuse an ID for another command. The current
transport does not add a database command scheduler, command-record state machine
or an outbox. The existing `/commands` create API still creates records only; it
does not automatically call this channel. The legacy `DeviceCommandTransport`
fuel-cut path stays disabled and its backend builder list stays empty. Public
sent/delivered/failure transition endpoints remain denied. UI controls are unchanged.

The gateway fsyncs an `UNKNOWN` receipt **before** attempting the irreversible
write. Identical requests return the saved outcome without resending; changed
payload/target/expiry under that ID conflicts. A crash between intent and result
can lose a command, but cannot cause this gateway to repeat the socket attempt.
That is a deliberate at-most-once-attempt/possible-loss tradeoff, not exactly-once
hardware execution. Journal restart and interruption cases are tested.

The journal has one process owner, private directory permissions and a configured
receipt count limit. A full or damaged journal fails closed. Receipts contain a
fingerprint/outcome, not command bytes or credentials. There is no automatic
receipt eviction: retain it across gateway restarts, monitor capacity and arrange
an operator-reviewed archive/rotation procedure before exhaustion. Do not delete
receipts while either endpoint can still accept requests against them. Journal
maintenance and production operations require separate authorization.

## Configuration and deployment limits

Backend:

| Variable | Default | Purpose |
| --- | --- | --- |
| `COMMAND_CHANNEL_ENABLED` | false | Enable the private channel client |
| `COMMAND_GATEWAY_ORIGIN` | empty | Bare gateway origin; HTTPS required except literal loopback/localhost HTTP |
| `COMMAND_CHANNEL_SECRET` | empty | Shared 32–256 printable non-space ASCII characters; generate random secret material |
| `COMMAND_CHANNEL_TIMEOUT_SECONDS` | 10 | Total request bound, 1–20 seconds; no application retry loop |

Device gateway:

| Variable | Default | Purpose |
| --- | --- | --- |
| `COMMAND_GATEWAY_ENABLED` | false | Start the private HTTP listener |
| `COMMAND_GATEWAY_HOST` | 127.0.0.1 | Bind address, separate from device TCP ingress |
| `COMMAND_GATEWAY_PORT` | 8890 | Private service port |
| `COMMAND_CHANNEL_SECRET` | empty | Same secret as the backend |
| `COMMAND_WRITE_JOURNAL_DIRECTORY` | ./data/command-writes | Dedicated private persistent directory |
| `COMMAND_WRITE_MAX_RECEIPTS` | 10000 | Fail-closed receipt capacity |

Enabled clients reject missing secrets, credentials in origins, non-base paths,
queries, fragments and non-loopback plaintext origins. Redirects are never followed.
The gateway checks its credential before reading the body, caps JSON at 8192 bytes,
rejects unknown fields and compressed bodies, and accepts deadlines at most 30
seconds ahead. Socket writes have a five-second bound; client responses are capped
at 1024 bytes. No command payload, response payload or shared secret is logged.

The listener itself is plain HTTP for private/proxied service use. Remote deployments
require TLS termination, service-network access controls and secret management;
do not expose the port publicly or send credentials over a plaintext network.
Coordinate clocks. The fixed configured origin must reach the gateway owning the
connection; multi-gateway discovery/load balancing is **not implemented**. Wrong-node
requests fail closed. Sharing one journal between gateway processes is prohibited.

Outbound payloads are protocol-neutral, including binary. Inbound framing is still
the existing ASCII `#` path; new binary protocols will need their own inbound
framing/decoding integration before they can identify a live connection. The parser,
battery/status normalization and plug-in structure analysis remains the next
separately scoped step, not a completed refactor here.

## Checks and evidence

Journeys: an authorized caller can write exact bytes to its identified tracker;
unauthorized/foreign/offline calls cannot reach a socket; stale/duplicate/ambiguous
attempts cannot silently send again. RED checkpoints compiled tests against missing
channel/journal, gateway/client and authorized dispatcher implementations. GREEN:

- `./mvnw -B verify`: **757 backend/shared tests passed**, packaging passed.
- `./mvnw -B -f device-comm/pom.xml verify`: **112 device/shared tests passed**,
  standalone packaging passed.
- Focused tests cover authenticated bounded HTTP, mismatched/oversized responses,
  rejected redirects, current tenant/permission, offline routing, exact binary TCP
  bytes, pinned device identity, stale connection tokens, duplicate/conflicting
  attempts, journal restart, full journal and interrupted-write uncertainty.
- `python3 tools/verify-command-channel.py`: **passed** with private Redis and the
  actual packaged gateway in a separate process. `CommandChannelNativeIT` invokes
  the actual backend dispatcher/client and verifies bytes at a real TCP peer,
  duplicate suppression, conflicts, expiry and replacement-connection routing. A
  native RED reproduced an old socket deleting the newer route; atomic
  compare-and-delete made that same reconnect test GREEN. Backend tracker ownership is mocked
  in this transport probe; authorization/scoped lookup cases are covered separately.
  It is not a new public HTTP command workflow or physical tracker/ACK acceptance.
- The native runner stops its owned processes and preserves evidence under
  `/tmp/opencode/command-channel-native-84oko0ut/`. Full build logs:
  `/tmp/opencode/command-channel-{backend,device,native}-final.log`.

No frontend source changed, so frontend builds/browser suites were not rerun for
this transport-only step. No Java coverage percentage, production deployment,
hardware acknowledgement or completed command-specific integration is claimed.
