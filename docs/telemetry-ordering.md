# Durable telemetry capture and replay — 2026-11-12

## Contract

The independent device gateway parses supported GPS frames into a versioned,
concrete location envelope. It writes a private temporary file, forces its contents
to disk, atomically renames it and forces the directory before capture succeeds.
Accepted envelopes retain their original source identity/time across restart.
Broker delivery is asynchronous: a broker outage alone does not reject a fix that
was safely captured. The file is removed only after a persistent JMS transaction
commits. This is at-least-once delivery, not a fabricated device ACK.

The work path is now:

1. Gateway disk spool → persistent `tracking.telemetry.locations` queue.
2. Backend transaction → location history, fix receipt and downstream update intent.
3. Database outbox → persistent `tracking.work.vehicleupdatedevent` queue.
4. Evaluation → persistent `tracking.work.alertdetectedevent` queue → existing
   transactional alert/outbox handling.

Legacy location, vehicle-update and detected-alert topics remain accepted for
compatibility; new traffic must use the durable path. Notification delivery beyond
the existing alert outbox remains a separate provider/release concern.

The receipt fingerprint includes tenant, vehicle, device, protocol, GPS time,
coordinates and speed. Identical parsed fixes are duplicates even if the device
retransmits them under newly generated gateway UUIDs. Receipt, location and update
intent commit together. An input acknowledgment failure cannot lose that intent;
an ambiguous output acknowledgment can replay the same derived event identity.
Concurrent duplicate processing is serialized by the trusted tracker/vehicle lock.

Older fixes remain in history but cannot rewind the current position. By default,
fixes older than five minutes do not create live alert intents. Fixes more than five
minutes ahead of the server remain historical and cannot poison current position
or alerts. Configure `tracking.telemetry.max-alert-age-seconds` and
`tracking.telemetry.max-future-skew-seconds` deliberately; device timestamp-zone and
server clock configuration still matter.
The time window is rechecked at evaluation too, so a vehicle update delayed in its
work queue cannot recreate stale live effects. Already-detected alerts retain their
original event times; notification-delivery policy remains separate.

## Bounds and failure behavior

- `TELEMETRY_SPOOL_DIRECTORY` defaults to `./data/telemetry-spool`. Use a dedicated
  persistent Linux/POSIX volume and an absolute configured production path, with
  working file locks, atomic rename and fsync.
  One gateway owns a directory at a time; replicas require separate directories.
  Newly created directories/files use owner-only permissions. Encrypt and back up
  the volume because it contains GPS data; an ephemeral container layer is unsafe.
- Defaults: 10,000 pending files, 64 MiB serialized payload bytes, 32 KiB per record;
  configure `TELEMETRY_SPOOL_MAX_FILES` / `TELEMETRY_SPOOL_MAX_BYTES` for the expected
  outage duration and reporting rate. Filesystem block/inode overhead is additional.
  The relay retries every second by default (`TELEMETRY_SPOOL_POLL_DELAY_MS`).
- A complete frame backlog is bounded to 256 per connection
  (`TCP_MAX_PENDING_FRAMES`, allowed 1–4096). Disconnect and framing/overflow errors
  do not interrupt an already-started capture; overflow closes the connection after
  that capture, rather than growing an unbounded queue.
- Full or failed storage rejects further capture and closes the connection;
  retained records are never evicted to make room. Invalid GPS is rejected without
  manufacturing coordinates or routing success. Structurally invalid, incomplete,
  unknown-version or oversized retained files stop replay/startup for inspection.
- Preserve the volume before operator recovery. Never delete pending `.json` files
  to free space or blindly rename/delete `.tmp` evidence. Temporary files were not
  confirmed captures; recover/quarantine them only after inspection with the gateway
  stopped. Corrupt accepted files need restoration/recovery, not invented data.
- Monitor retained-file counts, disk/inode headroom, queue depth, relay failures and
  pending database intents. The latter can be counted without exposing coordinates:
  `SELECT count(*) FROM telemetry_receipts WHERE update_payload IS NOT NULL AND published_at IS NULL`.
  Do not prune unpublished intents or receipt keys while packets can be replayed.

## Broker and rollout requirements

Merge [telemetry-broker-address-settings.xml](telemetry-broker-address-settings.xml)
into the owned broker's existing settings; do not replace unrelated policies. These
queues must be persistent, non-expiring, non-auto-deleting and backed by a durable
journal. The supplied policies use bounded exponential redelivery delays with no
attempt-count discard, so database/cache outages do not exhaust ten fast retries.
A poison message can therefore block its work queue: alert operators, inspect and
park it deliberately rather than silently deleting it.

Deploy the additive V9 migration and compatible backend consumers before switching
gateways. Keep private spool volumes across gateway replacement. No existing
migration was rewritten. For rollback, stop new intake and keep compatibility
consumers until retained work is drained; leave the additive receipt ledger intact.
Never drop a populated ledger/outbox as an expedient rollback. Device reassignment
remains unsupported; drain/quarantine old work before changing ownership mappings.
This acceptance covers one backend consumer per work queue, not multi-consumer
ordering certification. Production rollout still requires separate authorization.
The default in-memory H2 profile is development-only: use the PostgreSQL profile
and durable database storage for restart guarantees.

## Verification

RED regressions reproduced missing retention, duplicate fixes, live-position rewind,
historical/future alert effects, broker-dependent location persistence, and disconnect
or overflow interrupting a capture. Focused checks are green. Backend
`./mvnw -B verify` passed **693 tests**; independent device
`./mvnw -B -f device-comm/pom.xml verify` passed **107 tests** and executable packaging.
No Java coverage percentage is claimed: these builds do not instrument JaCoCo.

`tools/verify-telemetry.py` passed against real loopback Artemis 2.40.0, PostgreSQL 18,
Redis, independently launched backend/device JARs and the browser. It verified:
capture while broker/backend were offline; abrupt gateway crash and unchanged
envelope recovery; durable queue retention without consumers; broker journal restart;
duplicate-safe location/alert effects; late history; online database outage/recovery;
peer disconnect; automatic map refresh/reload and tenant isolation. No transport,
parser, publisher, persistence handler or database was replaced in this check.

Build both JARs and frontend output, then run
`python3 tools/verify-telemetry.py --artemis-home /path/to/apache-artemis-2.40.0`.
Use the official Maven Central distribution and verify its `.sha512` first. Java 21,
pnpm, Playwright Chromium, Redis and PostgreSQL binaries are required (`--pg-bin`
overrides `/usr/lib/postgresql/18/bin`). The runner owns a new loopback
`tracking_map_test` cluster and broker; it never connects to application data.
It prints its private evidence directory, stops owned services on exit and needs
no Docker. No production service was changed.

## Physical tracker retransmission remains unknown

Server-side tests do not prove what a physical Autoseeker or GT06 firmware does.
To investigate on a staging endpoint with an authorized test device:

1. Record packet arrival times and the device's original GPS timestamps privately
   using a staging packet capture (for example Wireshark/tcpdump), not map timestamps alone.
2. Interrupt **device-to-gateway connectivity**, not merely the broker; this
   distinguishes device buffering from the server spool. Test both a closed TCP
   connection and a stalled link, without changing production connectivity.
3. Restore connectivity. Multiple distinct fixes timestamped during the outage,
   arriving only afterward, are evidence of store-and-forward. One repeated last
   point alone is not proof of a complete buffer.
4. Repeat at several outage lengths and with a controlled device reboot. Record
   gaps, buffer limits, reconnect delay and whether buffered points survive reboot.

Ask for firmware-specific buffer capacity, resend/ACK rules, sequence fields,
timestamp zone and power-loss behavior, but keep behavior **unknown** if the vendor
will not confirm it. GT06 variants cannot be inferred from the family name.
Do not send undocumented acknowledgments or commands. Current socket acceptance
uses Autoseeker V1 fixtures, not a physical-device or binary-GT06 certification.
No server implementation can reconstruct bytes never received, recover a destroyed
persistent volume, or guarantee lossless operation after all storage is exhausted.
