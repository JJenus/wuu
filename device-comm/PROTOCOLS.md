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

## Not yet published

`DeviceHealth` (battery, signal, alarms) is decoded for `PositionReport` and `StatusReport`, but only the position is
published: `DurableLocationPublisher` deliberately accepts no dynamic metadata. Publishing health needs a typed event
and a consumer in core.
