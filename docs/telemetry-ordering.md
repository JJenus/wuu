# Per-connection TCP telemetry order — 2026-11-12

Outcome: the TCP receive pipeline now awaits each packet's parse, publication and
routing/activity update before starting the next frame on that connection. Blocking
work still runs off the Netty event loop, and unrelated device connections remain
independent. The gateway stays independently packaged without business dependencies.

The socket regression was RED: a second GPS publication overtook a blocked first
publication. It now passes using real sockets/framing/parser/processor and mocked
external publication/Redis boundaries. It also proves a second connection can
publish while the first is blocked. Existing fragmented/coalesced, malformed-frame,
non-ASCII and failed-publication routing checks remain green.

Checks: focused socket/routing tests passed; `./mvnw -B -f device-comm/pom.xml verify`
passed all 92 device/shared tests and produced the standalone executable JAR.
No coverage percentage is claimed; this build does not instrument JaCoCo.

Limits: order is receive order within one TCP connection, not timestamp ordering
across reconnects/devices. This does not add durable intake, protocol ACK/retry,
global ordering, or real broker/device-to-map acceptance. Failed publications keep
the existing logging/no-routing behavior; durable recovery remains separate work.
Real Artemis/PostgreSQL/Redis end-to-end transport remains unverified here because
Docker is unavailable. No production service was started or deployed.
