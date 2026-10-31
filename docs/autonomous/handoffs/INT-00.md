# INT-00 — accepted baseline, 2026-10-31

Coordinator: tracker-int00-20261031. Scope: B0/B1 baseline recovery, reserved
frontend detail/list reconciliation, and defects exposed by its acceptance.
Original dirty `app/` and `sidie/` checkouts were preserved.

## Accepted runtime tuple

- Frontend: `3c8eee90dcbe9b0dcb9d3305f5cbe956cf1070c5`.
- Backend/device source: `451a08396f55863fb25f93fed95a8e85fe543aa0`.
- Backend JAR SHA256: `152601242a3bc970f05e2be629341e58b3e7eae2c2921d3aa432729b84fa3074`.
- Device JAR SHA256: `717e5e96ed58f3f7c3c4a097693be98a1b40859702d9846416e29e63ef89806f`.
- All source dirty fingerprints: empty SHA256. Receipt reports stable revisions.
- FULL_START PASS: `2026-10-31T16:36:43.324Z`, all eight stages including
  restart, persistence and owned cleanup. No usable demo remains running.

Sanitized receipt: workspace `.coordination/evidence/INT-00/startup-receipt/last-startup.json`.
Raw private logs were copied into `.coordination/evidence/INT-00/full-start2-private/`
from the receipt's temporary log path. Failed attempt retained separately in
`failed-full-start/`. Do not publish private fixtures or logs.

## Changes and verification

- Reserved transfer patch SHA256:
  `34b8dd5742d6c77b60a54a477dbf21dc6a9f6097b722aff8879544b49c289118`.
  Reconciled source `8a89cd3` builds on local alert UI through `b00c92c`.
- Direct detail queries no longer depend on complete collection reads; bounded
  list pagination fails closed rather than presenting a truncated collection.
- Membership regression RED `47c350a`: incomplete/403/503 fleet reads rendered
  false zero assignments. Fix `34d3740` keeps geofence assignment count and
  independent fleet loading/error/retry; unresolved details remain explicit.
- Real Java exposed foreign/missing geofence GET400. Backend RED `7270707`
  failed two of nine service tests; correction `451a083` uses the existing
  not-found domain error, preserving tenant cache filtering/scoped lookup.
  No mutation/geometry/assignment policy was changed.
- `./mvnw -B verify`: backend **646 PASS**, zero failures/errors/skips. Device
  `./mvnw -B -f device-comm/pom.xml verify`: **91 PASS**. Default discovery
  excludes `*IT`; this is not the exhaustive vendor integration gate.
- Frontend typecheck/build/boundaries/scoped formatting PASS; units **144 PASS**,
  selected coverage94.74%lines/86.05%branches/95.15%functions (not whole UI).
  Auth browser **40 PASS**, preview browser **31 PASS** including automated
  accessibility/mobile checks. No separate configured lint script.
- Actual Java cold and post-restart browsers each passed: tenant reads, alert
  acknowledge/resolve with authenticated actor/notes, persisted geofence details,
  foreign404, injected fleet403 recovery, pending enrollment/cancellation,
  cookie secrecy, revocation and permission/Origin denials. The injected403 is
  fault recovery evidence, not a real geofence permission-denial claim.
- Bounded independent reviews: `ses_e6d471147ffebdCjydUX4moZki` membership
  correction; `ses_e6d3338d7ffeNDA9ricoieT4LD` Java404 correction. Source-only,
  no reviewer-run commands/tests; no remaining scoped medium/high findings.

## Limits, rollback and next work

INT-00 accepts this baseline, not all B0 inventory obligations or production
readiness. Physical hardware, command/ACK, notification policy/providers, paid
billing/metering, shared sessions and production operations remain open.
Synthetic device publication does not prove persisted owner telemetry.

Rollback uses reviewed scoped forward/revert commits on an isolated branch;
never reset dirty owner checkouts. No schema changes in INT-00.
Dependent session work is now eligible; UI tasks still require their domain
contract acceptance. FLEET-01 retains its independent ownership/review and its
cross-table device-identity migration request; no policy is inferred here.
Subsequent documentation-only acceptance commits do not replace the runtime tuple.
