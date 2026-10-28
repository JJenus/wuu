# Core telemetry retry — 2026-10-28

## Source publication checkpoint
Accumulated consolidation/security/outbox/notification-safety source snapshot committed and pushed to `origin/main` as `4f0da1aa0007c023bee7b969c913dc0f605ade5c`. Remote `ls-remote` matched; worktree was clean. User explicitly authorized inclusion of the pre-existing consolidation. No deployment performed.

## RED → GREEN
- `VehicleEventHandlerTest`:5executed/2failures/0errors/skips, `/tmp/opencode/tracker-core-publish-red.log`. Publication `IllegalStateException` was swallowed as a skipped packet; retrying the same source produced a new update identity.
- Invalid/unknown device assignment now returns before mutation. Service/publication failures propagate, rather than a broad IllegalArgumentException/IllegalStateException catch committing a skipped result. Tenant context still restores on every exit.
- Derived update identity is SHA-256 over versioned length-framed sourceID/orgID/deviceID/vehicleID. `occurredOn` follows the original source event, not attempt time. Different source IDs stay distinct. This identity guarantee assumes the same authoritative persisted assignment; reassignment between attempts is not resolved here.
- Focused handler/JMS/engine tests passed in `/tmp/opencode/tracker-core-publish-green.log`; initial vendor retry fixture passed after correcting its guessed SQL table name to the actual migrated `tracker_location_data`.
- Bounded independent review: no critical/high issue. Added suggested tests for both service exception classes, null/zero/negative owner IDs, distinct source identities, and downstream completion before duplicate-count assertions.
- First full gate:653tests/1failure/1error, `/tmp/opencode/tracker-core-retry-final-full-gate.log`. Fixtures incorrectly assumed (1) a publication in the listener's local JMS transaction survives its rollback, and (2) the relay only emits the current test's intent. These were test assumptions, not evidence of a new production failure. A follow-up13case run passed the stronger ambiguity test but still failed the separate telemetry consumer's unfiltered unrelated-event assertion; corrected that consumer too.
- True ambiguous-send fixture now publishes the first attempt from a separate thread outside the listener's JMS transaction, then throws. Real input redelivery creates the second outgoing update with the same identity/time/payload; both complete actual downstream evaluation. Final assertions require one location row, one alert, one outbox intent. Test consumers filter unrelated vehicles while still rejecting duplicate events for their own vehicle.
- Final clean instrumented gate **backend653/device37, zero failures/errors/skips**, both executable JARs built: `/tmp/opencode/tracker-core-retry-final-v2-full-gate.log`.
- Fresh JaCoCo: backend4883/7092lines (**68.85%**),954/1772branches (**53.84%**); device/shared475/1280lines (**37.11%**),70/362branches (**19.34%**). Raw whole-bundle targets remain unmet. Execution data `/tmp/opencode/tracker-core-retry-final-v2-coverage.exec`.
- Current executable two-process TCP→alert/outbox/tenant-isolation smoke passed again: `/tmp/opencode/tracker-core-retry-final-v2-socket.log`.

## Deliberate limits
No telemetry outbox or global exactly-once claim. DB commit/JMS commit atomicity, duplicate location history after later redelivery, durable subscriber topology, reassignment replay, Redis outage semantics, recipient policy, trusted hardware provisioning and physical commands/ACK remain open. User authorization to commit/push source is not release/deployment approval.
