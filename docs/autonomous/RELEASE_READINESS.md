# Release readiness

**NOT RELEASE_READY — IN_PROGRESS**

Latest: 2026-10-28. Receipts and RED/GREEN history: TEST_REPORT.md. No final readiness inferred from local tests.

| Gate | Current evidence |
|---|---|
| Feature inventory accepted | Source/design inventory recorded; owner scope decisions unresolved |
| Business/device build | Clean backend653/device37 verify PASSED; both executable JARs built; current executable socket pipeline smoke passed |
| Unit/regression tests | Full653/device37 passed, zero failures/errors/skips; real notification retry/DLQ and ambiguous core send/redelivery tested; recipient functionality remains blocked |
| PostgreSQL migrations/integration | Real health, migrations, authenticated tenant CRUD/conflicts/races, trips/CSV passed focused tests; query/filter/ordering/404 follow-up8 passed |
| Real telemetry/command/notification workflows | Synthetic TCP packet through independent executable gateway/backend→Artemis→tracker-linked PostgreSQL location→rule→alert→scheduled outbox publication/HTTP tenant isolation PASSED. Actual notification delivery and remote command/ACK NOT_VERIFIED; nondurable topic/downstream idempotency gaps OPEN |
| Frontend/browser essential workflows | No product frontend present; approved UX/scope remains unresolved |
| Independent security/architecture review | Audits plus bounded outbox and rule-cache/fetch reviews completed; reviewer-requested concurrent relay/multi-vehicle tests passed. Later earlier-query/error mapping changes only directly reviewed; no final whole-release audit |
| External provider callbacks/delivery | NOT_VERIFIED; SECURITY.md documents denied callbacks |
| Deployment instructions/package smoke | Device executable lifecycle passed; actual backend PostgreSQL-profile startup/V1–V4 migration/dependency health/security-denial/SIGTERM smoke passed. Production deployment NOT_VERIFIED |
| Fresh independent final audit | Not started |

Production credentials, live device security, provider verification and deployments must not be fabricated or inferred from local mocks.

Measured raw whole-bundle coverage: backend68.85%line/53.84%branch; device/shared37.11%line/19.34%branch. BELOW_TARGET. Missing notification recipient policy fails explicitly, with original event retained after actual broker retries; this is safety, not delivered notification capability. Core retry identity is verified, not full DB/JMS commit atomicity. A passing local test gate alone cannot close notification policy, device security, durable messaging, command transport, or product scope gates. Delegation: maximum two active subagents, direct work by default (D007). User authorized accumulated source commit/push, not deployment.
