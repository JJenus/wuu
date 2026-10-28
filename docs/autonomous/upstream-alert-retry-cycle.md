# Upstream alert publication/retry cycle — 2026-10-28

## RED receipts
- AlertingEngineTest10/1failure: publication failure was swallowed; expected exception absent. `/tmp/opencode/tracker-rule-publish-red.log`.
- Actual Redis lease test1/1failure: an expired holder released another holder's replacement lease. `/tmp/opencode/tracker-cooldown-lease-red.log`.
- Source-aware engine entrypoint test compile RED: missing overload prevented preserving source identity through evaluation; `/tmp/opencode/tracker-derived-event-red.log`. This is a compile-time missing-capability receipt, not a runtime redelivery claim.

## Fix and guarantees
- Separate per-rule evaluation-error handling from publication. Publication failures release the acquired lease and escape to the actual JMS listener, allowing broker redelivery; existing evaluation-error/continue semantics remain.
- Each cooldown acquisition has its own random owner token. Release uses atomic Redis Lua compare/delete with the configured value serializer. Token is per attempt, **not** stable source-event ID: even same-event reacquisition must not let the stale holder release its successor.
- JMS evaluation receives the complete source update. Derived alert ID is SHA-256 over versioned, length-framed organization/sourceID/vehicle/rule components. Derived timestamps follow original source occurredOn. Repeating an update does not generate a fresh source identity merely because wall-clock detection reruns. The compatibility two-argument engine path remains; it has no upstream identity guarantee.
- Geofence publication failure does not advance persisted geofence state. Missing/mismatched source organization is rejected before rule access. Stable identity prevents duplicate persistence when the same derived event reaches the downstream outbox repeatedly; it does not make all upstream operations transactional.

## GREEN and review
- Focused real Redis/engine/listener/scheduler contracts passed. Vendor batch31passed, `/tmp/opencode/tracker-publish-retry-vendor.log`, including actual Artemis listener failure→redelivery and one persisted alert/intent.
- Scoped read-only reviewer `ses_e7f0c6685ffeY1LMZHn5zTNPpg`: no critical/high issue. Suggested independent tenant/rule identity variation and geofence failure-state tests; both added and passed in follow-up. One active reviewer at most; now zero.
- Clean instrumented full gate `/tmp/opencode/tracker-upstream-retry-full-gate.log`: **root633/device37, zero failures/errors/skips**, both JARs built. Reports generated from fresh `/tmp/opencode/tracker-upstream-retry-coverage.exec`: backend4977/7089lines (**70.21%**),968/1767branches (**54.78%**); device/shared37.19%line/19.34%branch. Targets unmet.
- Targeted Redis container removed only after verifying its exact session owner label. Unrelated containers untouched.

## Limits / next work
- Existing dedup fail-open behavior during Redis outages remains; zero-cooldown bypasses Redis. No exactly-once claim.
- A Redis outage while releasing a failed attempt can leave its cooldown until TTL; a new process/core telemetry replay can still create a different upstream update identity. Nondurable topics and initial core transaction/publication atomicity remain open.
- Disconnection scheduler still uses its compatibility boolean acquire path; failure/repeated-disconnect identity semantics are separate work, not silently covered.
- Rule repository errors are still swallowed as empty results in VehicleRuleCacheService; next actionable cycle must distinguish healthy empty data from failed lookup.
- No recipient policy, real provider, device credentials, command/ACK or deployment decisions invented. No commits/pushes/deployments.
