# Verification report

## INT-00 final runtime acceptance — 2026-10-31

Frontend3c8eee9/backend-device451a083: FULL_START PASS at16:36:43.324Z, all eight
stages, clean stable fingerprints. Backend646/device91; frontend144 units,
40 auth browsers,31 preview browsers; types/build/boundaries/scoped format PASS.
Real Java cold/restart browsers each PASS, including alert actor persistence,
tenant/permission denial and persisted geofence membership/error recovery.
Source-only independent scoped reviews complete. Raw logs retained under workspace
.coordination/evidence/INT-00, with full-start2-private copied out of /tmp.
See handoffs/INT-00.md for commands, artifact hashes, RED/GREEN and scope limits.
This default Maven verify does not run all opt-in *IT; no whole-backend coverage
or production-readiness claim.

## Session refresh lifetime contract — 2026-10-29

- Final frontend at `5b7278c`: **133 unit / 32 auth browser tests PASS**;
  selected coverage92.79%line/85.56%branch/95.11%function, not whole-UI coverage.
  Actual-Java final acceptance forces real rotation with a disposable two-minute
  access TTL: **PASS**, `/tmp/opencode/renewal-java-final2.log`, private browser
  `/tmp/opencode/wuu-java-50ejEH/browser.log`. Owned cleanup confirmed. Earlier
  default-TTL route acceptance below did not itself force rotation.
  One-process renewal only; restart requires reauthentication and distributed
  durable coordination remains open. See independent frontend
  `docs/session-renewal.md` for RED/GREEN and bounded security-review evidence.

- Focused `AuthServiceTest`: **PASS**, asserting seven-day refresh lifetime on
  login/registration and session-bound rotation lifetime (`mvn -Dtest=AuthServiceTest`).
- Full `TMPDIR=/tmp/opencode sh tools/verify-release.sh`: **backend 729**,
  **device 91**, zero failures/errors/skips, both executable JARs built
  (`/tmp/opencode/session-refresh-full-gate.log`). Backend JAR SHA256
  `4f4a24c329537252e06f6f5aab97347054172046a2e32196fa9c260030767454`;
  device JAR SHA256
  `8329c651be98d0dc00d9f8eb926e552a0d5a99f3c0baabff52edeeab1fa0dfcf`.
- Actual-Java production-Nitro acceptance **PASS** with this JAR, including a real
  BFF refresh rotation and an authorized vehicle read
  (`/tmp/opencode/refresh-java-acceptance.log`; private browser receipt
  `/tmp/opencode/wuu-java-guAgb5/browser.log`). Owned fixture cleanup completed.
  No whole-backend coverage, distributed refresh coordination or production
  release/security certification follows.

## Tenant role catalog — 2026-10-29

- Focused `RoleControllerIntegrationTest`: **10 tests, 0 failures/errors/skips**,
  `BUILD SUCCESS` (`/tmp/opencode/role-catalog-green-v2.log`). Covers paged
  current-tenant reads, ignored tenant override, forged claim, anonymous,
  tenantless, four invalid-pagination cases and direct use-case denial.
- `PostgresDeploymentIT#roleCatalog_authenticatedTenantAdmin_pagesOnlyCurrentTenantWithoutGlobalRoles`:
  **PASS**, `BUILD SUCCESS` (`/tmp/opencode/role-catalog-postgres-v4.log`).
- Full `TMPDIR=/tmp/opencode sh tools/verify-release.sh`: **backend 729**,
  **device 91**, zero failures/errors/skips, both executable JARs built
  (`/tmp/opencode/tracker-role-catalog-full-gate.log`). Backend JAR SHA256
  `0866662ffa28b86072a262fa3c6236af12759048b94d264c6180cf9f80d4d99e`;
  device JAR SHA256
  `fb8302d55f8ed4d43c952a9f5ba27d189c4b4642f56637bda5c18e7c7ff96cb2`.
- This proves the backend catalog contract. The independent frontend now consumes
  the exact collection in a read-only settings panel; no role
  write/assignment/admin UI, whole-backend coverage claim or release certification
  follows. The frontend role panel is verified by its contract browser suite;
  actual-Java acceptance verifies the tenant catalog API, not the panel itself.

## Effective access and subscription owner UI — 2026-10-29

Isolated backend full gate **718 backend /91 device tests PASS**, zero failures/
errors/skips, both executable JARs built (`tracker-effective-access-full.log`).
Actual-Java production-Nitro browser acceptance **PASS** with new subscription
catalog/pending enrollment/cancel and current-tenant effective-access panel, plus
fleet isolation/revocation/credential secrecy (`effective-access-java-final.log`).
Artifact SHA256 `3ad87f9a7bce65cbdfade542dd9b92666800a0ee5a91d6bb0d6c8d300849b8e6`.
No new whole-backend coverage or independent release/security certification.
See [effective-access-slice.md](effective-access-slice.md) and frontend
`docs/subscriptions.md` for scope and remaining requirements.

## Durable non-paid grant/usage continuation — 2026-10-29

Focused **61 tests passed**, including 18 actual HTTP cases; real PostgreSQL
final-unit contention/replay and V2→V6 upgrade **2 tests passed**. Explicit
package-filtered JaCoCo: subscription372/398 lines (93.47%),162/178 branches
(91.01%); domain123/123 lines and98/98 branches (100%). See
[subscription-ledger-slice.md](subscription-ledger-slice.md) for RED, the corrected
administrator fixture, transactional rollback, exact commands and limits.
The full post-V6 vendor gate **PASSED backend714/device91**, zero failures/errors/
skips, both executable JARs built (`tracker-subscription-ledger-full-gate.log`).
No whole-backend coverage or new browser-artifact claim. The backend701/device91
and browser receipts below predate V6; they must not be described as current-source
full verification of grant/usage.

Final rebuilt-artifact actual-Java browser run **PASS**:
`/tmp/opencode/tracker-reviewed-browser-gate.log`, private logs
`/tmp/opencode/wuu-java-uWqSb5/`, JAR SHA256
`a452439bf220fa78a7cbcaaf975348531fb9ded8fbdbad2c6c9da3b2049e6ecb`.
Production Nuxt build, dependency health and deployed acceptance passed after the
final cleanup fix; no fixture containers or Java/Nitro/Playwright processes remained.
This supersedes pending-browser statements below, not product/release limitations.

## Latest full gate after review fixes — 2026-10-29

`TMPDIR=/tmp/opencode sh tools/verify-release.sh` **PASSED backend701/device91**,
zero failures/errors/skips, both executable JARs built. Receipt:
`/tmp/opencode/tracker-reviewed-vendor-gate.log`. This includes the final two
subscription storage-bound validation regressions (HTTP suite now nine cases).
Four frontend process-lifecycle tests also passed with 100% helper coverage;
browser acceptance of the rebuilt artifact is pending separately. These counts
supersede older pending full-gate statements below; no fresh whole-backend
coverage measurement or independent release certification is implied.

## 2026-10-29 subscription enrollment / device wiretap / frontend lifecycle

- Authenticated subscription catalog + enrollment: `SubscriptionControllerIntegrationTest` **7 tests, zero failures/errors/skips** via real random-port Spring HTTP, JWT filter, Flyway H2 PostgreSQL mode and Hibernate validation. Covers admin-only catalog, token/monthly/yearly models, exact 18-decimal price, duplicates, precision loss, auth, tenant/user scoping, receipt replay, key-conflict precedence and six concurrent same-key inserts. Full default backend suite: **612 tests, zero failures/errors/skips**, `/tmp/opencode/tracker-subscription-backend-test.log`. `FlywayMigrationIT` **1/1** applies five migrations on both histories and validates all entities; disposable PostgreSQL V2→V5 targeted case **1/1**, `/tmp/opencode/tracker-postgres-v5-upgrade.log`. Details: [subscription-enrollment-slice.md](subscription-enrollment-slice.md). Pending enrollment grants no access; grants/consumption/payment/renewal remain absent.
- Device: `./mvnw -B -f device-comm/pom.xml test` **91 tests, zero failures/errors/skips**, `/tmp/opencode/tracker-device-wiretap-test.log`. Raw frame wiretap is now opt-in and disabled by default; deterministic post-success/malformed/publication-failure tracking coverage is included. See [autoseeker-sanitation-cycle.md](autoseeker-sanitation-cycle.md). This is a standalone unit/socket gate, not physical hardware or command/ACK acceptance.
- Frontend: actual-Java disposable fixture rerun after lifecycle hardening **PASS**, `/tmp/opencode/tracker-deployed-lifecycle-v3.log`; production build, real PostgreSQL/Redis/Artemis health, two-tenant browser/API acceptance, revocation, forged-cookie denial, genuine `system.admin` 403/trips 200, label-checked cleanup and no remaining Java/Nitro/Playwright descendants. Frontend docs: `../app/docs/deployed-java-acceptance.md`.
- No fresh whole-bundle coverage claim. The last measured backend was 68.85% line / 53.84% branch and device/shared 37.11% / 19.34%, below target. Latest focused runs do not replace those measurements. Full serialized vendor gate, independent review and commit/push are pending. STATUS remains `IN_PROGRESS`, not `RELEASE_READY`.

## Latest completed core telemetry retry acceptance
**Clean backend653/device37 PASSED**, zero failures/errors/skips, both JARs built: `/tmp/opencode/tracker-core-retry-final-v2-full-gate.log`. Current executable socket pipeline passed: `/tmp/opencode/tracker-core-retry-final-v2-socket.log`. Publication/service IllegalArgumentException/IllegalStateException no longer masquerade as skipped packets; same-source/same-assignment retries preserve update identity/time. Actual independent ambiguous send plus input redelivery completes both downstream evaluations with one location/alert/outbox. RED/fixture-failure/review details: [core-telemetry-retry-cycle.md](core-telemetry-retry-cycle.md).

Fresh whole-bundle coverage backend **68.85% line / 53.84% branch** (4883/7092;954/1772), device/shared **37.11% / 19.34%** (475/1280;70/362), BELOW_TARGET. Earlier complete source snapshot pushed as `4f0da1a`; follow-up core fix being committed/pushed under the same explicit authorization. Not RELEASE_READY.

## Latest completed notification safety acceptance
**Clean backend644/device37 PASSED**, zero failures/errors/skips, both executable JARs built: `/tmp/opencode/tracker-notification-final-full-gate.log`. Current executable TCP-to-alert smoke passed: `/tmp/opencode/tracker-notification-final-socket.log`. Fabricated admin/email/phone and external destination fallbacks removed; missing routing fails before persistence and is not swallowed by the alert listener. Actual Artemis retries and retains the original unroutable event in DLQ. Scoped review and RED/GREEN details: [notification-recipient-safety-cycle.md](notification-recipient-safety-cycle.md).

Fresh measured backend coverage **68.85% line / 53.61% branch** (4879/7086;950/1772); device/shared **37.19% / 19.34%**. Targets remain unmet. Alert notification functionality is policy-blocked, not completed by the safety refusal. User authorized complete source snapshot commit/push to origin/main; no release/deployment authorization.

## Latest completed rule-database retry acceptance
- Database-rule lookup RED:18 tests/2failures/0errors/skips, `/tmp/opencode/tracker-rule-database-red.log`. Repository failures were converted to an empty rule list and listener success. Separated Redis read exceptions from database loading; database errors now propagate once rather than masquerade as cache misses or trigger duplicate fallback queries. Missing tenant validation and successful Redis/database fallback remain.
- Focused engine/cache/listener regressions passed, `/tmp/opencode/tracker-rule-database-green.log`.
- Repository fault-injected vendor listener case first failed before invocation because an interface spy cannot call an abstract real method (`tracker-rule-database-vendor.log`); fixture corrected to delegate through its configured Spring Data proxy. Rerun passed (`tracker-rule-database-vendor-v2.log`): actual Artemis redelivery retries the real PostgreSQL repository and creates one alert/intent. This is one injected repository failure, not a stopped physical DB server.
- Clean instrumented full gate **backend636/device37 passed**, zero failures/errors/skips, both JARs built: `/tmp/opencode/tracker-rule-db-retry-full-gate.log`. Fresh reports from `/tmp/opencode/tracker-rule-db-retry-coverage.exec`: backend4981/7090lines (**70.25%**),968/1767branches (**54.78%**); device/shared37.19%line/19.34%branch. Targets remain unmet.
- Latest executable two-process TCP→alert/outbox/tenant-HTTP smoke passed again after upstream retry and database-error fixes: `/tmp/opencode/tracker-socket-pipeline-latest.log`. These remain synthetic packets/local owned services, not secure hardware/provider/command/deployment acceptance.

## Latest completed upstream retry acceptance
Clean gate **root633/device37 passed**, zero failures/errors/skips, both executable JARs built: `/tmp/opencode/tracker-upstream-retry-full-gate.log`. Real Artemis publication failure→listener redelivery retains original derived identity/time/payload and one persisted alert/outbox row. Atomic Redis lease release protects newer holders, even same-event reacquisition. Review requested identity scope/geofence state tests passed. Details/RED receipts: upstream-alert-retry-cycle.md. Fresh backend70.21%line/54.78%branch; device/shared37.19%line/19.34%branch. Targets remain unmet. Next actionable investigation: DB lookup errors currently converted into empty rules; status remains IN_PROGRESS, not a request to pause.

## Latest completed acceptance (2026-10-28, cache fallback + socket pipeline)
- Redis-cache outage RED: RuleRedisTenantCacheTest16/3failures/0errors/skips, `/tmp/opencode/tracker-rule-cache-outage-red.log`. Redis/index failures skipped persisted rules; missing scope was swallowed. Scope validation now precedes fallback, and Redis read/index errors use explicit organization-scoped DB reads. Cache-write failure remains best-effort, not a reason to discard DB rules.
- First broader batch52 had one strict mock-signature error; preserved existing key-generator call, then **52/0failures/errors/skips**, `/tmp/opencode/tracker-rule-cache-outage-green-v2.log`.
- Actual disconnected Redis client + PostgreSQL rules/initialized assignments/foreign-tenant exclusion and real telemetry journey: **2 passed**, `/tmp/opencode/tracker-rule-cache-vendor.log`.
- Bounded independent read-only review `ses_e7f2867b3ffeePelaeiRbahdz5` approved scope, suggested multi-vehicle fetch assertion. Added it; explicit vendor case **passed**, `/tmp/opencode/tracker-multivehicle-fetch.log`. No production edits after full gate.
- Clean instrumented full gate `/tmp/opencode/tracker-cache-fallback-full-gate.log`: **backend626/device37, zero failures/errors/skips**, both executable JARs built. PostgreSQL30cases. Fresh JaCoCo reports generated from `/tmp/opencode/tracker-cache-fallback-coverage.exec`: backend lines4939/7055 (**70.01%**), branches946/1739 (**54.40%**); device/shared476/1280 (**37.19%**) lines,70/362 (**19.34%**) branches. Whole-bundle targets remain unmet.
- Current packaged backend smoke passed again: `/tmp/opencode/tracker-backend-process-smoke-final.log`.
- **Actual two-process TCP pipeline PASS**: `TRACKER_SMOKE_TELEMETRY=1 python3 /tmp/opencode/tracker-backend-process-smoke.py`, `/tmp/opencode/tracker-socket-pipeline-result.log`. Fresh owned loopback PostgreSQL/Redis/Artemis, real backend and independent device executable JARs. Synthetic Autoseeker V1 packet sent over a real socket; asserted one PostgreSQL location linked to physical tracker, HTTP-created speed rule produced one owner-visible alert, foreign tenant saw none, scheduled outbox marked publication complete. Both process shutdown receipts recorded. Logs `/tmp/opencode/tracker-process-0718fce386{,.device}.log`.
- Disposable backend fixture enabled registration only to provision two synthetic tenants; defaults and production settings unchanged. External providers disabled. Gateway uses an ephemeral test port but its existing listener binds all interfaces; this test does not certify a secure internet-facing gateway. No device credentials/provisioning, physical hardware, command/ACK, actual notification recipients/provider delivery, downstream idempotency, nondurable subscription recovery, or production deployment accepted.
- Remaining DB/repository-failure catch-and-empty and rule cooldown/transport failure semantics were outside this bounded Redis-read fallback review. Do not expand its guarantee into all outage safety.

## Latest: telemetry→evaluation→alert acceptance
- Extra outbox failure-boundary tests passed: migrated-H2 class7/0failures/errors/skips, `/tmp/opencode/tracker-outbox-ambiguity-test.log`. Broker success followed by DB rollback resends the same identity (explicit at-least-once behavior); corrupt stored JSON retains its intent/backoff while healthy work continues.
- Real journey RED: `./mvnw -B '-Dtest=PostgresDeploymentIT#telemetry_httpConfiguredRule_reachesPersistedAlertAndOriginalOutboxPublication' test`, `/tmp/opencode/tracker-telemetry-alert-acceptance.log`: one executed case timed out awaiting the missing intent. Logs establish `AlertRule.vehicleIds` LazyInitializationException during Redis serialization and domain evaluation; not an infrastructure startup failure.
- Fix: fetch vehicle assignments explicitly with an EntityGraph on the active-rules-for-vehicle list query, preserving organization/vehicle/enabled predicates and ordering; no global eager association or open-session-in-view workaround. Identical journey GREEN in `/tmp/opencode/tracker-telemetry-alert-green.log`.
- Clean instrumented full gate `/tmp/opencode/tracker-rule-fetch-full-gate.log` **PASSED root622/device37**, zero failures/errors/skips, both executable JARs built. PostgreSQL29 cases include actual broker telemetry→evaluation→persisted alert→outbox→Artemis event with persisted identity and foreign-tenant HTTP invisibility. Input begins at JMS, not a physical TCP packet; no recipient/provider completion claim.
- Fresh execution data `/tmp/opencode/tracker-rule-fetch-coverage.exec`; both report goals passed. Backend lines4932/7055 (**69.91%**), branches946/1739 (**54.40%**); device/shared unchanged37.19%line/19.34%branch. Whole-bundle targets remain unmet. Report XML is in each target/site/jacoco directory.
- No additional subagents for this bounded fetch correction; direct source review confirmed only the required list query fetch plan changed. Earlier independent outbox review does not constitute an independent review of this later annotation.

## Current outbox iteration
**Latest clean full gate PASSED:** `/tmp/opencode/tracker-outbox-full-gate-v2.log`: backend619/device37, zero failures/errors/skips, both executable JARs built. Root/device JaCoCo0.8.14 report generation passed using fresh `/tmp/opencode/tracker-outbox-coverage-v2.exec`; root analyzed242 classes without mismatch warnings. Raw backend lines4916/7055 (**69.68%**), branches933/1739 (**53.65%**); device/shared lines476/1280 (**37.19%**), branches70/362 (**19.34%**). Targets remain unmet. Outbox itself:36/39 lines,8/10 branches; intake49/53 lines,16/22 branches. Report XML remains in each project's `target/site/jacoco/`.

Actual packaged backend smoke is running through `/tmp/opencode/tracker-backend-process-smoke.py`, receipt `/tmp/opencode/tracker-backend-process-smoke-result.log`. Own labelled loopback-only PostgreSQL/Redis/Artemis, fresh DB/secrets, notification providers disabled. No production services or credentials used. Outcome pending.

Packaged backend smoke completed **PASS**: executable PostgreSQL-profile boot, fresh V1–V4 migrations, dependency health200/UP, protected vehicles401, nonexistent-user login401, SIGTERM pool shutdown. Receipt `/tmp/opencode/tracker-backend-process-smoke-result.log`, process log `/tmp/opencode/tracker-process-0afe3fe053.log`. Label-filtered Docker inspection confirms no smoke-owned containers remain. Not authenticated product workflow or real-provider/device acceptance.

Further test-only hardening: two outbox cases cover broker success followed by DB rollback (same identity must resend) and corrupt stored JSON retained/backed off while a healthy intent is sent. Focused run pending `/tmp/opencode/tracker-outbox-ambiguity-test.log`. Added separate real JMS location→HTTP-configured overspeed evaluation→persisted alert→outbox→Artemis/tenant-HTTP acceptance test; NOT_RUN until its explicit vendor invocation.

Full outbox gate completed backend **619 tests, 1 failure, 0 errors/skips**: `FlywayMigrationIT` still expected3 migrations instead of4 after V4. All other618 tests passed; device phase not reached. Updated exact migration count and added a direct empty-outbox-table assertion, preserving Hibernate schema validation. Clean instrumented rerun now at `/tmp/opencode/tracker-outbox-full-gate-v2.log`, fresh execution data `/tmp/opencode/tracker-outbox-coverage-v2.exec`. This rerun is pending, not accepted.

Latest completed result: **42 focused tests passed, zero failures/errors/skips**, `/tmp/opencode/tracker-alert-outbox-vendor-v2.log`. Includes28 PostgreSQL cases with actual Artemis outgoing-event identity, concurrent intake, concurrent relay ownership and V2→V4 upgrade. Clean current-source full gate with fresh JaCoCo data now running at `/tmp/opencode/tracker-outbox-full-gate.log` (execution data `/tmp/opencode/tracker-outbox-coverage.exec`). No updated full-gate count or coverage percentage claimed yet.

The root612/device37 full gate below predates V4/outbox changes. Earlier extended outbox verification: **41 tests, 0 failures, 27 errors, 0 skips**; all14 H2/handler/migration cases passed, PostgreSQL could not start because its disposable Redis port wait timed out. See `/tmp/opencode/tracker-alert-outbox-vendor.log`; the successful rerun above supersedes this infrastructure failure. One bounded independent review completed with no critical/high finding; its relay-coverage request is now fulfilled. Zero active subagents. Details: alert-outbox-cycle.md. STATUS remains IN_PROGRESS.

## 2026-10-28 direct continuation
- Delegation limited by owner to two active subagents; zero spawned this continuation. Last two failed agents' results were not accepted. D007 records cost-aware selection; no actual spending telemetry available.
- Added missing `Clock` bean to manually assembled security fixture; kept production expiry enforcement and all security assertions.
- Focused command `./mvnw -B -Dtest=SecurityBoundaryIntegrationTest,AlertCreationEventHandlerTest,AlertRuleControllerIT,AlertRuleServiceTest,PostgresDeploymentIT test`: **92 tests, 0 failures, 19 errors, 0 skips**. All 19 errors are PostgreSQL context startup from Docker inactive after restart; no PostgreSQL assertion executed.
- Fresh passing classes: alert handler7 (including rejected owner and context restoration), rule service32, rule API21, security boundary13: **73 passing cases**. This is not an all-green batch.
- Restored local Docker with `sudo -n systemctl start docker`; isolated `PostgresDeploymentIT` rerun now in progress, `/tmp/opencode/tracker-postgres-repair.log`.
- Earlier Oct27 temporary logs are absent after restart; durable records and current Surefire output remain. Do not describe historical receipts as a fresh rerun.

### Live-service follow-up
- `./mvnw -B -Dtest=PostgresDeploymentIT test`: **19 tests, 12 failures, 0 errors/skips**, `/tmp/opencode/tracker-postgres-repair.log`. Real services started and migration/health/broker checks executed. Failures exposed API errors being replaced by401 during servlet error dispatch, and telemetry writing a null required tracker because vehicle/device identities differ.
- Added direct JSON handling for intentional ResponseStatusException and malformed-body errors, preserving original4xx without relaxing security on `/error`. Location persistence now receives the incoming device identity and checks its actual tenant-owned vehicle association before saving.
- Rerun `./mvnw -B -Dtest=PostgresDeploymentIT,VehicleEventHandlerTest,SecurityBoundaryIntegrationTest test`: **35 tests, 1 failure, 0 errors/skips**; `/tmp/opencode/tracker-postgres-green.log`. All create/takeover races, tenant CRUD, trips/CSV, migration, broker, real two-tenant telemetry and security-boundary cases passed. Single remaining failure: PostgreSQL null search term inferred as bytea inside LOWER(CONCAT(...)) during rule listing.
- Explicit string cast added to rule search query; full disposable gate now rerunning with `/tmp/opencode/tracker-release-oct28.log`. Query fix not yet accepted.
- Fresh device `./mvnw -B -f device-comm/pom.xml verify`: **37 tests, 0 failures/errors/skips**, executable Boot JAR BUILD SUCCESS, `/tmp/opencode/tracker-device-oct28.log`.
- Actual packaged-process smoke: `python3 /tmp/opencode/tracker-device-jar-smoke.py` passed executable startup, ephemeral TCP bind, SIGTERM shutdown and stopped-listener log. No device packet or command sent; Redis/Artemis intentionally pointed at unused local port1. This proves packaging/lifecycle, not dependency health or command delivery. Initial hardcoded Java-path attempt failed before launch; corrected to installed PATH Java and reran successfully.
- Full `TMPDIR=/tmp/opencode sh tools/verify-release.sh` completed backend phase: **605 tests, 1 failure, 0 errors/skips**, `/tmp/opencode/tracker-release-oct28.log`; device phase not reached. PostgreSQL search cast passed. Sole failing rule-lifecycle test accumulated four missing/foreign-rule responses400 instead of404. Cause: domain exception mapping strips only the first prefix, so `ALERT_RULE_NOT_FOUND` is misclassified. Corrected recognized not-found code endings without changing authorization; focused vendor verification running.
- Searched sibling query implementations before accepting the PostgreSQL fix. Geofence/alert search use the same null CONCAT shape, so added six vendor-backed absent/present-search empty-tenant regressions. They are under focused RED verification, not assumed broken/passed from similarity alone.
- Focused sibling-query run: **7 tests, 3 failures, 0 errors/skips**, `/tmp/opencode/tracker-postgres-search-red.log`. Rule lifecycle now passes (including foreign/missing404). Actual failures: both alert queries reference an undeclared `:sortBy`; absent geofence search triggers LOWER(bytea). Removed alert query's unbound sort expressions (existing Pageable supplies ordering) and added typed string casts to both remaining search predicates.
- Next full gate includes an explicit installed JaCoCo0.8.14 JVM agent via JAVA_TOOL_OPTIONS so in-process root Surefire is actually instrumented; shared execution data `/tmp/opencode/tracker-oct28-coverage.exec`. Instrumentation/report generation and coverage percentages are NOT_ACCEPTED until execution completes and reports are inspected. Log `/tmp/opencode/tracker-release-coverage.log`.
- Instrumented gate's first attempt stopped **before Maven/tests**, because the wrapper assumes Java version is the first stderr line; JAVA_TOOL_OPTIONS prints a notice first. This is a tooling failure, not test failure. Wrapper now selects the actual `version` line. `JAVA_TOOL_OPTIONS='-Dtracker.smoke=1' ./mvnw -version` passed with installed Java21. Full instrumented gate relaunched to `/tmp/opencode/tracker-release-coverage-v2.log`; no coverage percentage claimed yet.
- Instrumented backend gate completed: **611 tests, 3 failures, 0 errors/skips**. Two actual alert-query failures now exposed untyped optional timestamp parameter (`could not determine data type of parameter $14`); one fixture failure exhausted the unchanged30/min HTTP auth limiter due to many test-setup logins. Device phase not reached. Coverage data exists, but no inspected report/percentage yet.
- Alert search now uses existing Spring Data Specifications, emitting only supplied predicates (explicit fail-closed organization scope), with Pageable sorting. This avoids untyped absent parameters rather than continually adding vendor casts. Added nonempty two-tenant regression for case-insensitive search, all filters, sort order and independent start/end bounds.
- Fleet token setup now invokes the real AuthService/session/JWT path directly; dedicated login/logout/refresh HTTP tests remain unchanged. Production rate limit remains enabled and unchanged; no trusted IP spoofing or test-disable switch added.
- Bounded eight-case vendor query/lifecycle verification is running before another full gate, `/tmp/opencode/tracker-postgres-search-green.log`.
- That bounded run completed **8 tests, 0 failures/errors/skips; BUILD SUCCESS** (1m15s). Includes nonempty case-insensitive search/all filters/sort/time bounds, tenant isolation, six optional-search vendor cases and complete rule lifecycle with404.
- Current-source full instrumented gate now running to `/tmp/opencode/tracker-release-final-gate.log`; fresh separate execution data `/tmp/opencode/tracker-oct28-final-coverage.exec` avoids mixing previous source-class versions. Do not treat it as passed until completion.

### Completed current-source local gate (2026-10-28)
- `tools/verify-release.sh` instrumented run **PASSED both clean verify phases**: backend **612 tests**, device/shared **37 tests**, zero failures/errors/skips, both executable JARs built. PostgreSQL deployment class: **26 passing cases**. Counts include shared cases in both builds; do not sum as unique tests.
- Root/device `generate-sources org.jacoco:jacoco-maven-plugin:0.8.14:report -Djacoco.dataFile=/tmp/opencode/tracker-oct28-final-coverage.exec` both passed. Logs show analyzed239 root classes and45 device/shared classes; no class-mismatch warnings. Execution data combines both test processes; shared-code coverage can include exercises from either build.
- Measured backend: lines **4732/7011 (67.49%)**, branches **898/1723 (52.12%)**, methods1663/2586 (64.31%). Device/shared: lines **476/1280 (37.19%)**, branches **70/362 (19.34%)**, methods138/302 (45.70%). XML reports in each project's `target/site/jacoco/jacoco.xml`. These are raw whole-bundle values, without DTO/config exclusions. Coverage target is NOT_MET.
- `docker ps -a --filter label=tracker.autonomous.run`: no owned harness containers remain. Actual hosted CI, signal-cancellation cleanup and production deployment remain NOT_VERIFIED.
- This is a verified local build/test baseline, **not RELEASE_READY**. FLOW-05 dedup/outbox, remote command/ACK, secure device provisioning, notification policy/full flow, coverage and final independent acceptance remain open.

## Latest iteration: 2026-10-27
See [recovery-cycle-2026-10-27.md](recovery-cycle-2026-10-27.md) for fresh RED/GREEN counts, exact limitations, independent review and interrupted-lane reconciliation. First clean full gate FAILED: **598 tests, 11 failures, 14 errors, 0 skips**; device phase not reached. Fixture corrections and additional telemetry/concurrency cases await rerun; STATUS remains IN_PROGRESS.

## 2026-10-26 initial baseline
- `opencode --version`: v2.0.17.
- `opencode mcp list`: no MCP servers configured.
- `opencode plugin list`: local ECC plugin.
- `docker info --format '{{.ServerVersion}}'`: FAILED, cannot connect to unix:///var/run/docker.sock.
- Root `./mvnw -B test`: running; log `/tmp/opencode/tracker-baseline-test.log`.
- Device `../mvnw -B test` from device-comm: running; log `/tmp/opencode/tracker-device-baseline.log`.

Prior SECURITY.md test counts are historical evidence only, not rerun results. Coverage is NOT_MEASURED. Browser, live providers and production deployment are NOT_VERIFIED.

## Fresh completed baseline
- Root `./mvnw -B test`: **489 tests, 0 failures, 0 errors, 0 skips; BUILD SUCCESS**, 2:07 minutes, `/tmp/opencode/tracker-baseline-test.log`.
- Device `../mvnw -B test`: **36 tests, 0 failures, 0 errors, 0 skips; BUILD SUCCESS**, `/tmp/opencode/tracker-device-baseline.log`. Includes 15 shared tests; do not count these as distinct device tests.
- Docker daemon was inactive, started locally via `sudo -n systemctl start docker`; `docker info` now reports 29.8.1. No production infrastructure touched.
- Disposable Redis owned by this session: `tracker-autonomous-redis-ses-e8735256`, loopback port 32768. Must remove after verification; never flush application Redis.
- `PostgresDeploymentIT` rerun in progress, `/tmp/opencode/tracker-postgres-baseline.log`.
- `sh -n tools/verify-release.sh`: passed syntax check. Full execution pending repair of discovered dormant suites. CI configuration added but hosted GitHub execution is NOT_VERIFIED.

## Failures under investigation
- PostgreSQL baseline `./mvnw -B -Dtest=PostgresDeploymentIT test`: **6 tests, 6 context errors**, BUILD FAILURE. Root cause: Artemis container port readiness timeout (`/tmp/opencode/tracker-postgres-baseline.log`, lines 89–166). Application assertions were not reached. Fixture/infrastructure diagnosis assigned to real-HTTP test lane.
- Access-expiry reproducer attempt `./mvnw -B -Dtest=AuthIdentityServiceTest test`: blocked by simultaneous telemetry lane's intentionally compile-RED event/repository API tests. This is **not valid RED evidence for access expiry**, so no auth production change made on its basis. Rerun after shared testCompile stabilizes.
