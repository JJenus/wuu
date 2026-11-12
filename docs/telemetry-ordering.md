# TCP telemetry order and recovery — 2026-11-12

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

## Real transport and failure recovery

The next regression reproduced a failed publication leaving the socket open. The
gateway now propagates infrastructure failures to Reactor/Netty, closing that
connection without updating successful routing/activity. Malformed GPS remains an
isolated rejected frame; checked parse errors retain their protocol classification.
A fresh connection can deliver after recovery. All 93 device/shared tests and
standalone packaging pass, including the formerly failing socket case.

`tools/verify-telemetry.py` passed using independently launched backend/device JARs,
authenticated Artemis 2.40.0, PostgreSQL 18 and Redis, all on owned loopback ports.
It stopped/restarted only its broker, verified failed intake closed TCP and saved
no location, waited for the backend's actual subscription to return, then passed
the real browser flow: fragmented TCP packets → parser → Artemis → tenant-resolved
location persistence → automatic map refresh/reload. Malformed/unregistered device
packets did not persist; foreign-device updates remained tenant-isolated.
No broker, parser, persistence handler or publisher was replaced in this check.

To reproduce, build the backend, independent device JAR and frontend output, then
run `python3 tools/verify-telemetry.py --artemis-home /path/to/apache-artemis-2.40.0`.
Use the official Maven Central binary distribution and verify its `.sha512` before
running it. The runner needs Java 21, pnpm, a Playwright Chromium, Redis and
PostgreSQL binaries (`--pg-bin` overrides `/usr/lib/postgresql/18/bin`). It owns a
new `tracking_map_test` cluster and never connects to application databases. Logs
remain in its printed `/tmp/opencode/telemetry-native-*` directory; services stop
on success, exceptions and normal termination. Docker is not required.

Limits: receive order is per TCP connection, not timestamp ordering across
reconnects/devices. Failed packets have no durable local spool or automatic replay;
actual hardware retransmission/ACK behavior is not proven. Multicast intake uses
non-durable subscriptions, so backend-offline capture also remains unproven. This
test does not certify notification delivery or production readiness. No production
service was started or deployed.
