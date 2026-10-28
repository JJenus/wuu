# Persistent task queue

Delegation budget: at most **two active subagents**, direct work by default; see D007. Last fixture/outbox agents hit usage limits; coordinator resumes directly, without automatic respawn.

| Priority | ID | Task | Status |
|---|---|---|---|
| P0 | AUDIT | Reconcile all current source/modules/design docs and scope | Inventory and runtime audit recorded; design-only scope decisions remain unresolved |
| P0 | BASELINE | Fresh root and device test runs | Completed current clean root644/device37, zero failures/errors/skips; backend68.85%line/53.61%branch, coverage below target |
| P0 | SECURITY | Independent security boundary audit | Findings recorded; scoped create/auth fix review completed; final audit pending |
| P0 | ARCH | Independent runtime/deployment/message-flow audit | Recorded in ARCHITECTURE_AUDIT.md; runtime gaps remain open |
| P0 | INFRA | Establish disposable PostgreSQL/Redis/Artemis verification infrastructure | Docker restored locally; full gate starts owned disposable services |
| P1 | FIX | Reproduce and fix highest confirmed release blockers | FLOW-05 focused42 passed including PostgreSQL replay/relay races and Artemis identity; scoped review complete. Clean full gate/coverage running; downstream idempotency/topology still open |
| P1 | E2E | Real-process cross-layer telemetry and command verification | Actual synthetic TCP→independent gateway/backend→PostgreSQL→rule→alert/outbox/publication plus HTTP tenant isolation PASSED. Hardware identity/provisioning, command transport/ACK and real recipient/provider delivery unresolved |
| P2 | DOCS | Correct current build/deployment instructions | README/AGENTS updated; reconcile final evidence before acceptance |
| P2 | FINAL | Fresh independent release audit | Pending implementation/integration |

## Active implementation lanes
- Current superseding state: no active subagents. Notification recipient safety + actual broker retry/DLQ retention passed; remaining recipient policy is blocked. Source commit/push authorized, including consolidation; publication checks in progress. Earlier lanes below are historical and completed only to the scope recorded in TEST_REPORT.md.
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
