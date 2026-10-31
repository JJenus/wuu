# Release readiness

Updated2026-10-30. **STATUS=IN_PROGRESS — NOT RELEASE_READY.**

INT-00 update2026-10-31: local final-revision full startup/restart baseline now
PASS (frontend3c8eee9/backend-device451a083). Alert lifecycle UI and actor/notes
persistence accepted; backend646/device91 and frontend144/40auth/31preview pass.
See handoffs/INT-00.md. Historical pending startup/disabled-UI rows below are
superseded only for this baseline. Broad feature and production gates remain open.
Authority: workspace AUTONOMOUS_DIRECTIVE.md§17. Pinned detailed plan:
[PRODUCTION_DELIVERY_PLAN.md](PRODUCTION_DELIVERY_PLAN.md). No deployment authority.

| Required gate | Current state / closing evidence required |
|---|---|
| Complete application starts after each feature update | NOT_VERIFIED for current complete stack; local Docker now running. FULL_START requires PostgreSQL, Redis, Artemis, backend, independent device gateway and production frontend plus browser/restart checks |
| Required feature inventory | Retained in FEATURE_MATRIX/requirements-inventory; only ride-hailing deferred. Broad live CRUD/admin/tracking/payment/notification requirements are unfinished, not excluded |
| Core usable workflows | Read-only fleet/details/auth/access/roles and pending subscriptions were Java-tested; alert gateway contract-tested, UI disabled; write workflows remain PARTIAL |
| Build and regression | Historical backend735/device91 full gate passed; reconstructed backend actor fix32 focused passed. Current frontend136 units/33 auth browsers/types/build passed. Logs lost at restart are labeled in session/slice records; no aggregate count establishes release acceptance |
| Database clean/upgrade migrations and restore | Historical vendor integration passed; final-release clean/upgrade/backup-restore evidence required; both histories preserved |
| Security/tenant boundaries | Scoped audits and database-authoritative permissions exist; final whole-product audit, Host/TLS/rate-limit/CSP/secret configuration and unresolved findings still require closure |
| Sessions and production topology | One BFF process only; restart requires sign-in. Durable multi-worker coordination/recovery is not implemented |
| Device/telemetry/commands | Independent packaging and synthetic chain historically verified; framing/connection/backpressure/command ACK and physical hardware acceptance remain open |
| Notifications/providers | Approved recipients/opt-in/provider credentials and real initial dispatch/durable delivery/callback evidence BLOCKED or unfinished; no invented recipients or successful sends |
| Configurable subscriptions | Catalog/pending/cancel/non-paid grant/usage ledger implemented; automatic approved metering/entitlement and paid lifecycle/provider evidence incomplete; no simulated paid success |
| CI, operations and reliability | Hosted release gates, approved map service, observability/load/restart/restore/rollback and validated deployment configuration pending |
| Every essential page/control | Actual browser inventory and workflows must pass; disabled/decorative required actions cannot count as complete |
| Independent final audit | NOT_STARTED; must reconstruct original requirements and find no confirmed unresolved release blocker |

Local acceptance and production verification are separate. Missing credentials,
provider decisions or hardware acceptance remain blockers, not implicit scope
exclusions. Prepare deployable artifacts/configuration autonomously, but deploy,
send real external messages or move real money only with explicit authorization.

Release decision requires final recorded artifacts, actual-process FULL_START and
critical end-to-end evidence, passing relevant tests/migrations, no critical/high
confirmed security findings or release defects, accurate docs and an independent
adversarial sign-off. Only then set RELEASE_READY; actual deployment is separate.
