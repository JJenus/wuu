# Adding a device protocol

Every protocol is one stateless class that turns a frame into typed messages. Everything else is shared.

```
TCP bytes ─► DelimitedFrameDecoder ─► Frame ─► IngestPipeline ─► LocationDataEvent
 (ReactiveTcpServer,                          │  detect once per connection (ProtocolRegistry)
  framing from the registry)                  │  DeviceProtocol.decode(frame) -> List<DeviceMessage>
                                              │  verify one device id, pin it to the connection
                                              └─ route: PositionReport → publish; StatusReport/LoginReport → no event
```

## Steps

1. Create `protocol/<name>/<Name>Protocol.java` implementing `DeviceProtocol`.
2. Register it: add a `@Bean` in `config/ProtocolConfig` (or annotate the class `@Component`). No registry or server edit.
3. Declare `framing()`. All protocols on one listener must share the same framing (checked at startup); a protocol
   with different framing needs its own listener port.
4. Pick a `detectionPriority()`. Lower is asked first. Protocols whose frames overlap must be ordered deliberately.
5. Decode into `PositionReport` (valid GPS fix), `StatusReport` (alive, no fix) or `LoginReport`. Reuse
   `protocol/common` (`StatusBitMap`, `BatteryCurve`) and, for `*XX,id,type,...#` text frames, `protocol/hq`.
6. Provide `commands()`; `HqCommandCodec` covers template-style commands.
7. Add `<Name>ProtocolContractTest extends DeviceProtocolContractTest` with real sample frames, then protocol-specific tests.

## Rules the contract test enforces

- Stateless and thread-safe: one instance serves every connection.
- `recognizes` never throws. `decode` throws only `ProtocolException`, never an unchecked leak.
- Never invent a value: unparseable coordinates, dates, or an invalid fix reject the frame.
- Exception messages never echo device payload (they are logged).
- Command encoding rejects device ids that could inject fields or delimiters.

## Health and alarms

`DeviceHealth` (battery, GSM/GPS signal, alarms) travels on the position as a typed field of `LocationDataEvent`
(`shared.domain.DeviceHealth`, `DeviceAlarm`), through the durable spool (envelope v2, `-1` = not reported) and into
core. Core applies battery and signal to the tracker; `DeviceAlarmProcessor` (alerting) raises alerts for SOS, tamper,
battery removed, power cut and low battery, with Redis cooldown and a deterministic id so a redelivery cannot alert twice.
Alarms with no mapping (e.g. VIBRATION) are counted as `device.alarm.unmapped`. To add a new device alarm, add it to
`DeviceAlarm`, decode it in the protocol, and map it in `DeviceAlarmProcessor`.

Status-only frames (GT06 heartbeat/V3/V5) carry health but no position, so they are still not published: there is no fix
to attach them to. A dedicated status event would be the next step.

## Phone / browser adapter (live demos)

Setup, HTTPS and local-dev testing are in [PHONE-TRACKING.md](PHONE-TRACKING.md).

A phone is just another protocol on a second transport. `DeviceProtocol.transport()` selects the listener
(`TCP` or `WEBSOCKET`); `ProtocolConfig` builds one registry and one `IngestPipeline` per transport, so protocols on a
listener always share framing and the same decode, identity, publish flow applies. `PhoneProtocol` uses
`FramingSpec.Preframed` (one WebSocket message = one frame).

Identity is never taken from the payload. `PhoneIngestServer` (off unless `tracking.phone.enabled=true`) requires the
first WebSocket message to be `{"token": "<pairing token>"}`, verifies it, then stamps every later frame with the
token's device id (`Frame.authenticatedDeviceId`). `PhoneProtocol.recognizes` is false without it, so raw TCP can never
reach it.

Demo flow:
1. Register a tracker with protocol `PHONE` and a 10-15 digit `device_id`, assigned to a vehicle (`POST /trackers`).
2. `POST /trackers/{id}/pairing-token` returns a signed token and, with `tracking.phone.public-url` set, a `pairing_url`.
3. Open `pairing_url` on the phone (served over https by your proxy) and tap Start tracking.
4. Movement, speed and geofence/speed alerts appear in the platform like any other tracker.

Config (both services need the same `PHONE_PAIRING_SECRET`, 32+ bytes): `PHONE_INGEST_ENABLED`, `PHONE_INGEST_PORT`,
`PHONE_MAX_ACCURACY_METERS` (device-comm); `PHONE_PAIRING_TTL_MINUTES`, `PHONE_PUBLIC_URL` (core). Tokens are
stateless: they cannot be revoked individually, only by expiry or by rotating the secret.

Browsers report battery only where the Battery API exists (not Safari); speed is derived on the page when the
browser omits it, and the server rejects a frame with no speed rather than inventing one.
