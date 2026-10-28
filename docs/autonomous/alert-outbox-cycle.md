# FLOW-05 — durable alert creation/publication cycle

## Intended guarantee
Broker publication failure or concurrent redelivery of one AlertDetectedEvent must not produce multiple committed alerts. Alert creation and durable publication intent commit/rollback together. Retrying transport reuses the original persisted alert/event identity and payload.

## RED evidence
- `./mvnw -B -Dtest=AlertEventDeliveryIntegrationTest test`
- `/tmp/opencode/tracker-alert-replay-red.log`: **1 executed test, 1 assertion failure, 0 errors/skips**; expected one committed alert, actually two. Actual migrated H2 persistence/transactions, with only the outgoing broker transport mocked unavailable.
- This is a confirmed application defect, not unavailable Docker or broken test setup.

## Implementation under verification
- New V4 migration in both vendor histories adds `alert_event_outbox`. Earlier migrations untouched. Source-event primary key and retained owner/vehicle identity support replay safety; serialized historical payload and original alert ID survive retries.
- Intake transaction locks the authoritative vehicle row, sets/restores tenant scope, rejects cross-owner source reuse, and writes both alert and intent. Per-vehicle serialization prevents check-then-insert races; database uniqueness is the final guard.
- Scheduled bounded relay selects due intents with row locking/SKIP LOCKED, publishes original JSON, and records success or exponential retry (up to256 seconds). Stable event identity is preserved across attempts.
- No new recipient policy, device transport or business dependency inside device-comm. No real provider credentials or financial operations.

## Focused GREEN receipts
- `./mvnw -B -Dtest=AlertEventDeliveryIntegrationTest,AlertCreationEventHandlerTest,MigrationParityTest test`
- `/tmp/opencode/tracker-alert-outbox-green.log`: **12 tests, zero failures/errors/skips; BUILD SUCCESS**.
- Covers original duplicate-alert reproducer, rollback removing alert+intent, failed relay/backoff then successful retry with identical ID/time/payload, original persisted ID mapping, tenant rejection/context restoration, and exact V4 vendor-history parity.
- Two additional tests for concurrent H2 redelivery and cross-owner source reuse were added after that run compiled. Their outcomes are pending, not included in12.
- Extended run `/tmp/opencode/tracker-alert-outbox-vendor.log` includes those tests and real PostgreSQL concurrent replay→Artemis publication plus V2→V4 upgrade verification. Outcome pending.
- Extended run completed **41 cases, 0 failures, 27 errors, 0 skips**: all14 H2/handler/parity cases passed, including concurrent intake and cross-owner identity collision. All27 PostgreSQL errors originated in disposable Redis port-wait timeout before application context startup; no vendor assertion ran. This is not a green batch or an application assertion RED.
- Rerun `/tmp/opencode/tracker-alert-outbox-vendor-v2.log` includes the additional reviewer-requested two-relay test. Pending; no readiness claim.
- Rerun completed **42 tests, zero failures/errors/skips; BUILD SUCCESS**: handler7, migrated-H2 replay/rollback/retry/concurrency/collision5, migration parity2, PostgreSQL28. Actual Artemis acceptance preserved original stored ID/time/payload; two explicit PostgreSQL relay transactions proved SKIP LOCKED ownership while one publisher was blocked. V2→V4 upgrade and earlier vendor regressions passed.
- Clean full release gate with fresh coverage data is running: `/tmp/opencode/tracker-outbox-full-gate.log`, `/tmp/opencode/tracker-outbox-coverage.exec`. The42-case focused result is accepted, not a substitute for that gate.
- That full gate completed backend619/1failure/0errors/skips: only stale FlywayMigrationIT migration count3vs4 failed. Exact count updated to4 with a direct table-existence/empty-state assertion; Hibernate validation preserved. Device phase not reached. Clean rerun `/tmp/opencode/tracker-outbox-full-gate-v2.log` with fresh coverage data is pending.
- Clean rerun **PASSED backend619/device37**, zero failures/errors/skips. Reports inspected: backend69.68%line/53.65%branch, device/shared37.19%line/19.34%branch. Outbox36/39lines and8/10branches. This verifies the bounded fix under local regressions, not recipient/provider decisions, nondurable-topic delivery, downstream idempotency or release readiness.

## Review and cost discipline
One bounded independent code review requested (`ses_e7f4df836ffeXFKVLZWOwAt6Vh`), justified by new schema/transaction risk. It owns no implementation or tests. One active subagent at dispatch, below owner's two-agent cap; all other work direct. No actual monetary telemetry or savings claim.

Review completed: no confirmed critical/high defect within scoped transaction/schema implementation. Reviewer requested a PostgreSQL two-relay/blocked-transport ownership regression. Added a bounded test with two actual relay transactions; scheduled polling is delayed in this fixture so ownership is deterministic. This test uses a spy only for one blocked publication; the separate acceptance test uses actual Artemis. New test not included in earlier12-case receipt. Zero active subagents after review returned.

## Migration/deployment safety
- Additive V4 creates only a new table/index; it does not rewrite existing business tables or backfill historical alerts. Fresh/upgrade vendor checks are pending; no production-sized dataset or production migration run is claimed.
- Forward-only Flyway history: retain V4 after deployment. Before any downgrade/corrective migration, quiesce intake/relay, preserve/back up pending intents and source identities, and review how they will be drained/reconciled. Dropping the table discards both unsent work and deduplication history and is not a safe automatic rollback.
- Dedup guarantees apply to events first accepted by the new intake; historical alerts created by older versions have no source-event ledger. A rolling mixed-version/historical-redelivery cutover requires an explicit plan, not an assumed retroactive guarantee.

## Deliberate limits and remaining gates
- At-least-once, **not globally exactly-once**: broker success followed by DB mark/commit failure may resend the same event. Downstream consumers still require deduplication.
- Existing nondurable topics can lose messages while subscribers are absent; persistent sends/outbox alone do not repair that topology.
- Tombstones/payloads are retained; no automatic retention rule is invented. Historical event facts can outlive deleted alert rows. Storage/retention/operator workflow remains to specify.
- Wider telemetry→broker atomicity, initial notification dispatch/authorized recipient policy, command/ACK transport and secure device identity remain separate open gates.
- Last fully green local root612/device37 and coverage percentages predate this cycle's source changes. Re-run full acceptance/instrumentation after this implementation stabilizes; do not reuse them as current outbox acceptance.

## Checkpoint self-evaluation
| Axis | Score | Evidence / improvement |
|---|---|---|
| Accuracy | 4 | Counts/logs distinguish initial12 green from later unrun cases; full outbox acceptance still pending. |
| Completeness | 3 | Mission still lacks full notification/command/device-security gates and coverage target; continue open queue rather than infer release readiness. |
| Clarity | 4 | Resume state and source guarantee explicit; historical progress duplicates in older documents need consolidation. |
| Actionability | 4 | Exact commands/tests/schema and next gate recorded; operator retention/cutover remain decisions, not runnable release steps. |
| Conciseness | 3 | Repeated interim counts/docs updates add noise; keep current snapshot concise and detailed history here. |

Overall3.6/5. Highest-impact improvements: complete vendor/relay concurrency verification, close full-gate/coverage evidence for changed source, then resolve remaining runtime acceptance. Would the user agree? Likely on cost discipline (one bounded review; no fan-out), but delivery is still incomplete and should remain IN_PROGRESS.
