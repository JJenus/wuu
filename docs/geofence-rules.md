# Geofence rule configuration — 2026-11-12

Outcome: live geofence details support typed rule creation, listing and enable/disable,
with permission refresh, draft retention and honest uncertain-save errors. Backend
validation rejects foreign/unlinked vehicles and inactive boundaries; enabling
revalidates references. Existing five-minute rule/vehicle cooldown is disclosed.

Checks: backend `./mvnw -B verify` passed 675 tests; explicit
`GeofenceRuleEvaluationIT` passed seven cases using disposable PostgreSQL/Redis
(circle/polygon ENTRY/EXIT/BOTH, cooldown, persisted alerts, tenant denials and
inactive boundaries). Frontend build, typecheck and boundary check passed; 206
unit tests passed, with 94.68% line coverage of the configured pure-code scope,
not the whole UI. Geofence browser suite passed 14 cases; fleet regression passed
four; alert transition/UI regressions passed five. Real Java/PostgreSQL browser
creation, reload and disable passed one case.

Limits: native engine tests inject locations into the real engine and substitute
broker publication with the real persistence handler. They do not prove device,
JMS or notification delivery. Generic rule editing/deletion/bulk setup remains
unavailable. Next separately scoped work: real telemetry/live tracking transport.
