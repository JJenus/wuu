# Persistent task queue

## CURRENT DISPATCH — 2026-10-31

Use [PARALLEL_EXECUTION.md](PARALLEL_EXECUTION.md) before choosing work. Its task
board replaces sequential-only dispatch below. Separate owner-started sessions
atomically claim one eligible task plus its resources in the shared workspace
`.coordination/`; claims show owner, current step, completed work, next action and
blockers. Maximum two active bounded subagents per task owner for subtasks or
independent verification, never other whole tasks. Only the coordinator accepts/pushes
delivery branches. Shared-file edits, browser fixtures and final acceptance are
serialized; isolated domain implementation is parallel.

Initial READY choices: **INT-00, FLEET-01, GEO-01, TEAM-01, DEVICE-01, OPS-01,
SUB-01, NOTIFY-01**. Read each task's exact write scope, blockers and acceptance.
They are initially unclaimed; always inspect live claims rather than assuming
this list represents current availability. Dependent UI/session/tracking tasks
open only after their stated dependencies are accepted. The B0–B9 table below
retains batch scope/history, not current task ownership.

INT-00 reconciles retained B0/B1 startup PASS and local frontend commits through
b00c92c with reserved dirty detail/list work. Raw temporary logs and the old
backend worktree were lost; a local all-stage PASS receipt survives. See the
parallel contract's baseline note; never invent an approved missing review.

## PINNED EXECUTION QUEUE — 2026-10-30

Follow [PRODUCTION_DELIVERY_PLAN.md](PRODUCTION_DELIVERY_PLAN.md), the current
detailed version of MASTER_PLAN.md. Older dated lanes below are historical;
they do not override this queue. Authority: workspace AUTONOMOUS_DIRECTIVE.md.

| Order | Batch | Status | User-visible acceptance / next work |
|---|---|---|---|
| NOW | B0 Full startup/recovery | TESTING | Docker restored; real-Java alert gateway PASS after JSON correction; implement/prove unified cold start/restart including independent device and frontend |
| NEXT | B1 Usable alerts | PARTIAL | Protected backend/BFF exist; wire permission-aware UI/notes/cache updates and prove real persistence |
| 2 | B2 Vehicle/tracker setup | PARTIAL | Enable safe create/provision/assignment and verify duplicate/tenant protection end-to-end |
| 3 | B3 Geofences/rules | PARTIAL | Repair lossless geometry/assignment contract, wire editors/rules, verify actual evaluation |
| 4 | B4 Team/tenant administration | PARTIAL | Current APIs to usable member/role workflows, no fabricated invitations |
| 5 | B5 Tracking/trips/device commands | PARTIAL | Independent-process telemetry/history/reconnect and safe command transport/ACK |
| 6 | B6 Complete subscriptions | PARTIAL | Preserve catalog/pending/ledger; deliver approved usage/entitlement and genuine paid lifecycle; external payment policy/credentials BLOCKED |
| 7 | B7 Notifications/durability | PARTIAL | Inbox/preferences, approved recipients/providers, initial dispatch/replay/restart; external policy/credentials BLOCKED |
| 8 | B8 Production operations | PARTIAL | Durable shared sessions, CI/config/TLS, migration/restore/observability/load/security |
| FINAL | B9 Independent readiness acceptance | NOT_STARTED | Every required inventory feature verified or explicitly excluded by owner, no release blockers, final full start and fresh audit |

Every feature update must pass **FULL_START**, not just component tests. Exit
criteria/dependencies/rollback are in the detailed plan. Do not repeatedly rebuild
completed product slices; reverify when code/config changes or evidence is stale.
Do not let external provider/payment/hardware blockers stall safe core workflows.

Historical delegation budget is superseded by owner-started independent sessions
and the claim/resource protocol above. No spawning or automatic agent respawn.

## Superseding scope/work — 2026-10-29

- Tenant-scoped read-only role catalog `GET /api/v1/roles` is implemented with
  `roles.read`, ignored client tenant overrides and SQL paging. Focused HTTP
  **10/10**, PostgreSQL **1/1**, full backend **729**/device **91** pass. No role
  writes/assignment or frontend consumer; see role-catalog-slice.md.
- Effective-access contract added in isolated worktree with current database
  permissions/tenant/expiry, no tokens or new grants. Full backend718/device91
  and actual-Java frontend subscription/access acceptance PASS. Subscription
  owner catalog/enrollment/cancel UI now implemented; paid billing, automatic
  product metering and admin catalog/grant UI remain open. See effective-access-slice.md.

- Current slice: V6 durable explicit trial/complimentary grants and trusted-admin
  usage recording implemented with shared row locks, atomic receipt/balance and
  domain state restoration. Focused61 and PostgreSQL2 pass; final full gate
  **backend714/device91 PASS** (`tracker-subscription-ledger-full-gate.log`). No paid activation,
  automatic product metering/renewal or subscription UI. See subscription-ledger-slice.md.

- Final checkpoint: vendor **backend701/device91 PASS** and rebuilt-artifact actual-Java browser **PASS** with no fixture container/process leftovers. Review regressions resolved. Next independent engineering work: durable authenticated grant/usage integration and subscription frontend; notification/payment/provider decisions and production/command acceptance remain open. See final-slice-review.md. Historical pending statuses below are superseded only within this bounded scope.

- Latest post-review gate: **backend701/device91 PASS**, both packaged, no failures/errors/skips (`tracker-reviewed-vendor-gate.log`). Subscription storage-bound validation and process-group escalation review findings are resolved with regressions; final rebuilt-artifact browser acceptance is in progress. Publication pending, `STATUS=IN_PROGRESS`.

- Owner decision: only ride-hailing is deferred. Implement configurable token-usage and monthly/yearly subscriptions; API authentication is required, no new Autoseeker credential scheme. Resolve reversible product choices from existing source/directive without routine owner questions.
- Frontend exists at workspace `app/` (independent Git repository); historical NOT_STARTED/no-frontend assessments below are incorrect. Preserve its uncommitted detail-read/list-completeness work. Fresh coverage/typecheck passed; independent actual-Java browser fixture/test lane active.
- Device sanitation/socket slice: standalone **91-test** gate passed; default raw-frame wiretap removed (now opt-in, default false). Follow-up review and full/vendor gate pending. See autoseeker-sanitation-cycle.md. Keep connection replacement/backpressure/GT06/commands open.
- Subscription **partial integration**: reviewed domain kernel plus V5 durable catalog and authenticated tenant/user-scoped `PENDING` enrollment/cancel. Seven real HTTP tests pass, including cross-instance operation-key uniqueness. Grants/consumption, payment, renewal and entitlement remain unimplemented; domain tests alone cannot complete billing. See subscription-enrollment-slice.md.
- Latest published backend checkpoint a50fb33. Commit/push authorization remains; production deployment/release certification is not authorized.

| Priority | ID | Task | Status |
|---|---|---|---|
| P0 | AUDIT | Reconcile all current source/modules/design docs and scope | Inventory and runtime audit recorded; design-only scope decisions remain unresolved |
| P0 | BASELINE | Fresh root and device test runs | Completed current clean root653/device37, zero failures/errors/skips; backend68.85%line/53.84%branch, device37.11%line/19.34%branch, below target |
| P0 | SECURITY | Independent security boundary audit | Findings recorded; scoped create/auth fix review completed; final audit pending |
| P0 | ARCH | Independent runtime/deployment/message-flow audit | Recorded in ARCHITECTURE_AUDIT.md; runtime gaps remain open |
| P0 | INFRA | Establish disposable PostgreSQL/Redis/Artemis verification infrastructure | Docker restored locally; full gate starts owned disposable services |
| P1 | FIX | Reproduce and fix highest confirmed release blockers | FLOW-05 focused42 passed including PostgreSQL replay/relay races and Artemis identity; scoped review complete. Clean full gate/coverage running; downstream idempotency/topology still open |
| P1 | SUB | Durable plan catalog + authenticated enrollment/cancel | PARTIAL: V5 both histories, admin catalog and tenant/user-scoped pending enrollment, 7 real HTTP tests; no grant/consume/payment/renewal/UI |
| P1 | E2E | Real-process cross-layer telemetry and command verification | Actual synthetic TCP→independent gateway/backend→PostgreSQL→rule→alert/outbox/publication plus HTTP tenant isolation PASSED. Hardware identity/provisioning, command transport/ACK and real recipient/provider delivery unresolved |
| P2 | DOCS | Correct current build/deployment instructions | README/AGENTS updated; reconcile final evidence before acceptance |
| P2 | FINAL | Fresh independent release audit | Pending implementation/integration |

## Active implementation lanes
- Current superseding state: no active subagents. Subscription catalog/enrollment is now durable and HTTP-verified but remains pending-only; device sanitation/wiretap changes pass 91 standalone tests; frontend fixture lifecycle hardening passed the actual-Java browser run. Fresh serialized vendor/full gate and combined-diff review remain. Earlier lanes below are historical and completed only to the scope recorded in TEST_REPORT.md.
- SEC-01/02: insert-only fleet creation; independent real PostgreSQL HTTP regression lane.
- FLOW-01/02: telemetry tenant propagation and persisted alert event identity.
- FLOW-04: unsupported SMS/push providers fail honestly.
- BUILD-03: independent device launcher/package.
- TEST-01: restore dormant alert rule API/repository integration tests.
- DOCS: full inventory expansion includes all design-only domains without claiming approved exclusions.
- Infrastructure now actionable: Docker 29.8.1 reachable; PostgresDeploymentIT running in full gate. Do not assume historical Redis port 32768 remains owned/live after restart.
- Fresh recovery receipts: [recovery-cycle-2026-10-27.md](recovery-cycle-2026-10-27.md).
- Oct28 direct continuation: tenant-safe PostgreSQL concurrent creates and broker→DB telemetry now pass focused tests; full gate rerunning after rule-query null typing fix. Device37 + packaged-process lifecycle smoke passed. Next actionable runtime priority is FLOW-05 dedup/outbox; do not misclassify it as policy-blocked.
- Targeted8 vendor query/lifecycle tests now pass after replacing absent alert predicates with tenant-scoped Criteria, fixing not-found mapping and geofence string typing. Final instrumented gate uses fresh execution data; await completed result/report before acceptance.

Consult cycle-specific evidence files before regression/integration acceptance. Do not run clean builds while implementation agents are compiling against shared target directories; run final clean verification after all lanes return.
