# Live fleet snapshots — 2026-11-12

Durable intake update: the gateway now retains parsed GPS fixes on disk, replays
them through persistent work queues and uses transactional fix receipts/outbox
intents. Native acceptance includes gateway crash, broker/backend/database outage,
duplicate replay, late history and automatic map refresh. See backend
`docs/telemetry-ordering.md` for storage/broker requirements and the physical-device
boundary. This supersedes the failed-packet/non-durable transport limits below;
recorded GPS replay UI and actual hardware resend behavior remain separate work.

The live `/map` now renders saved backend coordinates without simulated movement,
invented paths or trails. A map-local Vue Query polls the existing authenticated
fleet adapter every ten seconds, pauses in hidden tabs/on demand, refreshes on
return/resume, and stops its timer on unmount. Permanent read errors stop interval
retries; explicit refresh can recover. Errors hide cached positions rather than
presenting stale or revoked data as current. Preview simulation is unchanged.

GPS timestamps and snapshot refresh time are separately disclosed. Missing GPS,
unknown time, clock skew and locations older than five minutes have explicit labels.
This is a latest-snapshot reader, not a telemetry stream or a history/replay contract.

Verification: the browser regression was RED for absent stale-location labeling;
after implementation, three live-map browser tests passed (exact coordinates,
polling, pause/resume, hidden/visible tabs, navigation cleanup, permission failure,
manual recovery and stale/missing GPS). Six preview/map/replay regressions passed.
All 208 units passed; configured pure-code line coverage was 94.73%, not whole-UI
coverage. Production build, typecheck and boundary checks passed.

Subsequent transport acceptance also passed: valid packets sent over the standalone
device gateway crossed real authenticated Artemis into PostgreSQL and appeared on
the automatically refreshed live map, including after an owned broker outage and
fresh TCP connection. Malformed/unregistered devices did not create locations;
foreign tracker updates stayed tenant-isolated. See backend
`tools/verify-telemetry.py` and `docs/telemetry-ordering.md` for the executable check,
failure/replay limits and prerequisites. This supersedes the transport-unverified
limit of the original direct-database snapshot fixture below.

One real Java/PostgreSQL browser case passed: two committed database GPS fixtures
were read through Java and the sealed-session gateway, automatically refreshed and
retained after reload. That fixture seeds PostgreSQL directly; it does **not** prove
device TCP, broker intake, ordering, alerts or notification delivery. Docker was
unavailable in this environment. Run it with `playwright.tracking.config.ts` and
the guarded `TRACKING_TEST_*` variables against an owned loopback
`tracking_map_test` database. No production data or services were changed.
