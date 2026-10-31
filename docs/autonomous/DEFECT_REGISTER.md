# Defect register

## INT-00 superseding disposition — 2026-10-31

- OPS-FULL-START: RESOLVED for baseline451a083/3c8eee9, all8 stages PASS including
  independent device startup/restart and owned cleanup. Future updates require a
  new final-runtime-tuple gate. No production/hardware implication.
- GEO-MEMBERSHIP-EMPTY (Medium): incomplete/403/503 linked fleet reads displayed
  false0/no assignments. Browser RED3/3; fix34d3740 retains known membership and
  independent pending/error/retry. Auth40 and real Java recovery PASS.
- GEO-READ-404 (Medium): missing/foreign scoped GET raised IllegalArgumentException
  and returned400. Real Java RED plus unit RED7270707; fix451a083 uses existing
  not-found domain factory. Backend646 and real Java foreign API/page404 PASS.
  Mutation/geometry/assignment rules remain GEO-01 scope.
- Independent bounded reviews accepted both corrections by source inspection;
  runtime evidence/limits in handoffs/INT-00.md. Production blockers remain open.

## Current startup acceptance findings — 2026-10-30

- **OPS-FULL-START (HIGH, OPEN):** no retained single invocation currently proves
  database/cache/broker/backend/independent-device/frontend startup and restart
  against one final revision tuple. Existing Java/Nitro harness omits the device
  process. Owner requires this after every feature update; B0 owns implementation
  and actual acceptance. See PRODUCTION_DELIVERY_PLAN.md FULL_START.
- **ALERT-JSON (MEDIUM, RESOLVED at gateway):** after restoring Docker, actual-Java acceptance
  reached healthy backend/PostgreSQL/Redis/Artemis then alert fixture POST returned
  400: production Jackson uses SNAKE_CASE, unlike the contract fixture. Production
  mapping and fixture correction under fresh unit/Java verification; do not accept
  UI or whole alert workflow from earlier mocked success. Receipts
  `/tmp/opencode/plan-b0-java.log`, private `/tmp/opencode/wuu-java-KrmppI/browser.log`.
  RED `2ff125e`, correction `cbdd54e`; corrected actual-Java1/1 PASS
  (`/tmp/opencode/plan-b0-java2.log`), unit136/auth33/types pass. Legacy UI adapter
  mapping remains B1 work; unified FULL_START still open under OPS-FULL-START.
- **OPS-DOCKER-RESTART (RESOLVED locally):** Docker socket absence after restart
  repaired by starting local docker.service; daemon/socket/containerd active.
  This establishes runtime availability, not complete application acceptance.

## Latest superseding disposition (2026-10-29)
- SUB-LEDGER: V6 durable explicit non-paid grants and administrator usage recording now pass focused61/PostgreSQL2 and full backend714/device91. Receipt/balance commit atomically under a shared enrollment row lock; reconstruction/future/expiry and rollback-after-flush tested. This supersedes no-durable-grant/usage statements below only; paid activation, real business metering and subscription UI remain OPEN.
- SUB-ENROLL: authenticated V5 catalog/enrollment is durable and HTTP-verified (7 cases), with owner/key uniqueness in the database and exact-price storage guards. It creates only `PENDING` rows with zero units; grants/consumption, entitlement checks, payment, renewal and dunning remain OPEN. Do not describe enrollment as a subscription sale or access grant. See subscription-enrollment-slice.md.
- API-03: unsupported HTTP methods previously fell through servlet error dispatch and became 401. `GlobalExceptionHandler` now returns explicit 405 without permitting `/error`; exercised by the subscription catalog immutability test.
- FLOW-02 follow-up: device raw-frame wiretap is opt-in/default-off and the latest standalone device suite is 91/91; physical hardware/commands remain OPEN.
- FLOW-03 safety portion: fabricated administrator/contact routing removed; unsupported external destination mappings fail before persistence. Notification listener rethrows runtime failures. Actual Artemis redelivery and terminal DLQ retention tested. Authorized recipients/tenant worker validation/initial dispatch remain OPEN/policy-blocked; no delivered-notification claim.
- FLOW-07 follow-up: database rule lookup errors propagate rather than become empty rule sets; actual broker retry reaches real PostgreSQL and creates one alert/intent. Upstream publication failure releases only its attempt-owned cooldown lease and propagates; derived identity/time stable across redelivery. See TEST_REPORT.md and upstream-alert-retry-cycle.md.
- Latest focused gates: backend612/device91, zero failures/errors/skips; PostgreSQL V2→V5 upgrade passed; frontend actual-Java lifecycle rerun passed. Coverage below target; full vendor gate and combined review pending. Historical pending states below are retained as investigation history, not current failing-gate claims.

| ID | Severity | Evidence | Remediation / verification | State |
|---|---|---|---|---|
| DOC-01 | Medium | README/AGENTS reactor commands conflict with current root JAR POM and deleted modules | Verify current commands; update navigation/instructions without altering user migration | OPEN |
| ENV-01 | Release verification blocker | Docker daemon connection fails | Obtain disposable runtime services; existing PostgresDeploymentIT must actually run | OPEN |

Security and architecture findings are recorded below and in ARCHITECTURE_AUDIT.md. Unavailable infrastructure is not an application defect or a passed test.

## Independent reconnaissance/security findings (source evidence; reproduction pending)

| ID | Severity | Evidence | Acceptance / owner | State |
|---|---|---|---|---|
| SEC-01 | High | VehicleController POST → VehicleService.saveVehicle → assigned-ID JPA merge can replace another tenant's vehicle | Insert-only create; real DB duplicate-ID tests preserving original owner/state; create-isolation agent | IN_PROGRESS |
| SEC-02 | High | TrackerController POST → TrackerService.saveTracker merges an existing tracker onto caller's vehicle | Insert-only create; duplicate tracker/association tests in H2 and PostgreSQL | IN_PROGRESS |
| SEC-03 | High, untrusted TCP deployment | ReactiveTcpServer binds extracted numeric device ID before successful parsing; connection tracker accepts replacement claims | Sanitize frames before connection binding; malformed/duplicate/identity-switch/race tests. Owner explicitly does not require a new Autoseeker device credential scheme; API authentication remains mandatory. | OPEN |
| FLOW-01 | High | JMS telemetry has no HTTP TenantContext; tracking lookup/evaluation depends on it | Authoritative persisted ownership resolution and scoped cleanup; telemetry agent | IN_PROGRESS |
| FLOW-02 | High | Two alert consumers evaluate same update; AlertRaisedEvent uses rule key instead of persisted alert ID | One evaluation path with persisted identity and replay-safe semantics | IN_PROGRESS |
| FLOW-03 | High | NotificationOrchestrator hardcodes admin, fabricated email/phone; queued tenant not applied; pending deliveries not dispatched | Define approved recipient policy; enforce tenant/member identity; persist and dispatch after commit; real broker tests | OPEN |
| FLOW-04 | High | Unsupported SMS/push adapters simulate success | Permanent unsupported-provider failure, never success/sent metrics; provider agent | IN_PROGRESS |
| BUILD-03 | High | Device POM repackage has no main launcher | Runnable independent JAR, context and startup evidence; device agent | IN_PROGRESS |
| TEST-01 | Medium | Opt-in AlertRuleControllerIT uses stale URL and @Mock; repository IT lacks migrated config | Repair to current contracts without disabling authorization; test agent | IN_PROGRESS |

No cascading data-loss behavior is claimed reproduced until database tests establish it. Provider credentials and recipient policy must not be invented.

## 2026-10-27 recovery disposition
- ENV-01: local Docker restored; full PostgreSQL integration result pending, no longer blocked on daemon availability.
- DOC-01: README/AGENTS corrected to two independent Maven builds; final documentation acceptance pending verified runtime commands.
- SEC-01/02: **focused H2 regression VERIFIED**, 8 actual migrated-database cases preserve full graph; production insert-only fix independently reviewed. Authenticated PostgreSQL HTTP/concurrency acceptance remains open.
- AUTH-01 (High): expired/missing-expiry claims accepted by identity service and long-lived connections could outlive access expiry. RED reproduced; clock-based expiry retained in AuthPrincipal and rechecked by isActive. **Focused regression VERIFIED**, 4 cases; wider gate pending.
- JMS-01 (High): five concrete event types failed configured converter round trip; queue factory inherited global topic setting. RED reproduced; explicit wire constructors/domain overrides added. **Focused contract VERIFIED**, 7 cases; actual listener delivery and historical wire compatibility remain NOT_VERIFIED.
- FLOW-04: **focused regression VERIFIED**, 14 fresh service/dispatcher cases; unsupported adapters fail permanently and never record SENT. Real providers remain unavailable.
- BUILD-03: **build/context VERIFIED**, 37 tests and executable JAR produced; packaged-process startup smoke and business-command connection remain NOT_VERIFIED.
- TEST-01: repository11 passed; controller suite exposed a genuine omitted-priority validation bug rather than stale test wiring. Fix under broader verification.
- RULE-01 (Medium): omitted priority incorrectly validated as -1, zero priority silently ignored. Both reproduced; nullable priority/zero-bound update fix under full gate.
- FLOW-02: duplicate consumer, wrong persisted ID and swallowed persistence failure reproduced in 3 failing tests; fix under full gate and independent review. Idempotency/outbox/durable subscription gaps remain OPEN.
- FLOW-05 (High, independent review): alert persistence commits before event publication. Publish failure now propagates, but redelivery inserts another alert for the same source event. Add unique source-event identity plus transactional outbox, and failure/redelivery regression. OPEN and actionable, not blocked by recipient policy.
- ENV-02: full gate's Artemis JVM exited because generated initial512M exceeds requested maximum256M. Confirmed via owned container logs/profile. Both fixture maximums corrected to512M with live-broker wait; rerun pending.
- 2026-10-28: RULE-01 and TEST-01 controller wiring **focused VERIFIED**: rule API21/service32 passed; security fixture13 passed after adding Clock. FLOW-02 targeted handler guarantees7 passed; broader broker/idempotency acceptance remains open.
- API-01 (Medium): intentional404/409 and malformed400 were replaced by401 on servlet error dispatch. Real HTTP RED reproduced; direct safe JSON handlers restore intended statuses without opening `/error`. **Focused real PostgreSQL HTTP VERIFIED** for fleet conflicts/not-found and invalid enum.
- FLOW-01 follow-up: incoming tracker resolved correctly but location persistence queried Vehicle.deviceId instead of the physical incoming device, violating required tracker_id. Real broker/PostgreSQL RED reproduced; explicit device/tenant/vehicle association persisted. **Focused two-tenant broker→DB/update-publication VERIFIED**; full TCP→alert→notification and replay/durability still open.
- SEC-01/02: **authenticated PostgreSQL focused VERIFIED**, including simultaneous cross-tenant assigned-ID creates (exactly one201/one409), original graph preserved and winner/loser visibility. Full gate pending.
- RULE-02 (Medium): absent search term binds as bytea inside LOWER on PostgreSQL; rule listing fails. Runtime RED confirmed, explicit string casts added, full-gate verification pending.
- BUILD-03: fresh device37 + actual packaged-process TCP startup/SIGTERM shutdown smoke passed Oct28. External infrastructure delivery and secure physical-device provisioning remain open.
- API-02: domain `ALERT_RULE_NOT_FOUND` was misclassified400 by prefix stripping. Real rule lifecycle now passes after recognizing not-found endings; no authorization relaxation.
- QUERY-01: real PostgreSQL optional-search regressions exposed undeclared `:sortBy` in alert search (both search modes fail) and null-search LOWER(bytea) in geofences. Removed unbound sort expressions in favor of existing Pageable ordering; typed search parameters. Full gate pending.
- FLOW-05 cycle: real H2 RED confirmed duplicate count2vs1. V4 transactional outbox/intake source dedup added; first12focused tests passed. Extended H2/PostgreSQL races, actual relay publication and independent review pending. Durable subscription and downstream idempotency limitations explicitly remain OPEN; see alert-outbox-cycle.md.
- FLOW-05 latest: **focused42 VERIFIED**, including PostgreSQL concurrent intake, relay SKIP LOCKED ownership, original-event publication via Artemis and V2→V4 migration. Scoped independent review completed; its requested relay regression now passes. Full current-source gate running. This closes the focused duplicate-alert/atomic-intent defect only, not nondurable topic loss or downstream exactly-once guarantees.
- FLOW-06 (High): actual JMS telemetry→rule evaluation failed on detached lazy AlertRule.vehicleIds, losing the alert despite passing isolated tests. Runtime RED reproduced. Explicit active-rule query fetch graph fixes the boundary; identical full journey and clean root622/device37 gate passed. JMS→evaluation→alert/outbox/Artemis/tenant visibility VERIFIED; physical TCP and notification completion still open.
- FLOW-07 (High): Redis rule/index read outages silently became no-rules, skipping DB-backed evaluation. Three RED regressions; explicit scoped DB fallback/missing-tenant rejection repaired. Focused52, real disconnected-Redis/PostgreSQL2, clean root626/device37 passed. Independent scoped review approved; complete multi-vehicle assignment fetch regression passed afterward. Repository failure and cooldown/transport semantics are not closed by this fix.
- Runtime acceptance follow-up: actual synthetic TCP→independent executable gateway/backend→Artemis→PostgreSQL tracker location→speed evaluation→persisted alert→scheduled outbox publication and owner/foreign HTTP visibility passed. Device credential security, commands/ACK, notification recipients/dispatch, downstream dedup and nondurable subscriptions remain OPEN.
