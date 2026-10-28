# Interrupted-lane recovery — 2026-10-27

## Reconciliation
- Read workspace AUTONOMOUS_DIRECTIVE.md and all required recovery records.
- HEAD remains `551db79`; extensive user migration remains uncommitted and preserved.
- Several specialist sessions stopped on usage limits. Their partial source/test changes were reviewed and rerun, not accepted from their status messages.
- Prior `/tmp/opencode` logs were absent after the environment restart. Historical summaries remain historical; new receipts below are fresh.
- Docker was inactive again; local `sudo -n systemctl start docker` succeeded. Only disposable services are used.

## Reproductions and completed focused evidence

| Guarantee | RED | Focused GREEN |
|---|---|---|
| Long-lived identity retains/enforces access expiry | AuthIdentityServiceTest: 2 executed assertion failures; expired/missing-expiry claims accepted (`tracker-resume-compile.log`) | 4 tests passed, including equality-at-expiry and valid active identity (`tracker-foundations-green.log`) |
| Vehicle/tracker create is insert-only, no assigned-ID takeover | After correcting interrupted test setup to use actual Flyway H2 history, 8 tests produced 2 failures + 4 persistence errors: graph overwrite, destructive cascade attempts, unmapped collisions (`tracker-create-jms-red.log`) | 8 tests passed on actual migrated H2 tables; same/foreign tenant duplicate IDs/devices leave full vehicle/tracker/location/trip snapshots unchanged; authorized updates retain associations |
| Actual concrete event JSON round trips | 7 JMS contract cases: queue mode assertion failed; 5 concrete event deserializations failed (`tracker-create-jms-red.log`) | All 7 passed; preserve event ID/time/payload for LocationData, VehicleUpdated, AlertDetected, AlertRaised, NotificationSent and FuelCutRequested; queue/topic domains survive Boot defaults |
| Unsupported SMS/push never report SENT | Earlier provider lane recorded RED; fresh normal lifecycle rerun included services + dispatcher | 14 passed in normal Maven lifecycle, not just surefire:test |
| Independent device gateway starts and packages | Historical missing-main finding; interrupted launcher recovered | `./mvnw -B -f device-comm/pom.xml verify`: 37 tests, no failures/errors/skips, Boot repackage BUILD SUCCESS (`tracker-device-recovery.log`); startup/shutdown TCP context test passed |
| Dormant rule repository suite | Interrupted test wiring corrected by specialist | 11 opt-in repository tests passed |

Focused batch total: **72 tests, 1 failure, 0 errors, 0 skips**. Its single failure is a newly identified production validation defect, not an accepted green suite: UpdateAlertRuleRequest defaults priority to -1 while @PositiveOrZero rejects it. A valid name-only update returns 400. Other completed scoped counts above are extracted from that run, not a claim that the entire batch passed.

## Next RED/GREEN cycle
- `tracker-alert-flow-red.log`: 25 tests, 5 failures, 0 errors/skips.
- Three failing alert-handler tests prove duplicate update consumers, rule key substituted for persisted alert ID, and swallowed persistence failure.
- Two rule-update failures prove omitted priority rejection and inability to apply valid zero priority.
- Fixes now under wider verification: nullable optional priority, actual saved alert response/ID, one evaluation consumer, authoritative vehicle ownership scope/restoration, propagated persistence failure.
- Wider command: `TMPDIR=/tmp/opencode sh tools/verify-release.sh`; result pending. This is not a release acceptance until completed.

## Completed full-gate failure and follow-up
- First full gate **FAILED: 598 tests, 11 failures, 14 errors, 0 skips**, backend phase, 8m41s. Device phase was not reached. Log: `/tmp/opencode/tracker-release-recovery.log`.
- Eleven failures include camelCase HTTP fixture bodies/assertions conflicting with production snake_case JSON and failed broker health. Corrected fixtures to production naming without changing production API naming or weakening assertions.
- Artemis connection errors diagnosed independently with an owned disposable container. Generated `artemis.profile` contains `-Xms512M -Xmx256M`; JVM exits with `Initial heap size set to a larger value than the maximum heap size`. Both fixtures now request maximum512M and wait for actual broker-live log, not merely a mapped port. Fix awaiting rerun.
- SecurityBoundaryIntegrationTest's 13 context errors delegated for focused diagnosis; no acceptance claimed before results.
- Tests added after the first full gate compiled: two-tenant real broker telemetry→database/update publication and two concurrent cross-tenant create races. These are **NOT_RUN** until the next invocation; the 598 total does not include them.
- Alert handler rejection/context-restoration coverage expanded after the first gate compiled; new cases NOT_RUN.
- Independent alert-change review `ses_e8372b57dffejSd3TXtw5IIJa0`: targeted ID/priority/single-consumer logic had no additional confirmed defect, but publish-failure redelivery can insert duplicate alerts because persistence commits before publication. Unique source-event identity plus outbox remains an actionable release defect, not an owner-policy blocker.

## Independent review
Code-reviewer session `ses_e837a8388ffeRkco5UFGCu41nX` found no confirmed blocker in the scoped create-isolation/access-expiry/event-constructor/JMS-domain changes. It did not rerun tests and did not approve the whole release.

Review follow-ups retained:
- Real authenticated PostgreSQL HTTP and concurrent create races remain necessary.
- H2 standalone MockMvc is not evidence of production security/JSON wiring.
- Mocked JMS transport round trips are not Artemis listener evidence or old persisted-message compatibility proof.
- Broadened integrity failures now use generic conflict messages rather than asserting every constraint error means a duplicate identity.

## Remaining limitations
No coverage measurement, live provider result, authenticated device identity, remote command ACK, notification recipient policy, browser frontend, durable topic/outbox recovery, or production readiness is claimed. Device package success does not establish these workflows.
