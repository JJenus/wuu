# Decision log

## D008 — Outcome batches and mandatory full startup after updates (2026-10-30)

Owner explicitly requested a pinned clear plan targeting production readiness,
following AUTONOMOUS_DIRECTIVE.md, and complete startup after feature changes.
Adopt PRODUCTION_DELIVERY_PLAN.md as canonical detailed plan, MASTER_PLAN as short
map, TASK_QUEUE as active selector and workspace DELIVERY_PLAN.md/frontend
docs/delivery-plan.md as pointers. Preserve exhaustive inventory and old evidence;
do not restart implementation or silently reduce scope to delivered read-only UI.

Accept each coherent feature batch only after FULL_START against its final
backend/device/frontend/config revision tuple. Include independent device startup,
actual browser mutation/persistence/denials, restart and owned cleanup. Review
corrections invalidate affected evidence. B0 must supply one fail-closed gate and
durable sanitized receipt; until then component gates are partial evidence only.
Only ride-hailing is deferred. Unspecified financial/provider/privacy/device rules
stay explicit blocking edges; continue independent work without fabricated success.
Source push authorization does not authorize deployment or remote infrastructure.

## 2026-10-28 source publication authorization
Owner explicitly requested commit/push, then confirmed **Commit all source work** after being told the verified fixes depend on the pre-existing consolidation. Include that coherent consolidation plus accumulated implementation, tests and documentation; exclude generated artifacts and secrets; push normally to origin/main. This supersedes earlier no-commit/no-push constraints only. It does not authorize deployment, release certification, live provider sends, device provisioning assumptions or financial activity. Preserve all existing source work; do not rewrite remote history. Publication checks include full gate backend644/device37, executable socket smoke, staged-file/credential-pattern review and whitespace checks. Full release status remains IN_PROGRESS.

## D001 — Preserve existing migration (2026-10-26)
Git status shows extensive user-owned deletions, moves, new security tests and deployment changes. Work against the current layout; do not restore old modules, reset, or commit unrelated work.

## D002 — Reuse tools and specialists
Use existing Maven/JUnit/Mockito/AssertJ/Testcontainers and built-in browser tooling. Reuse installed specialized agents rather than spending delivery time adding agent configuration. No harness configuration modified.

## D003 — No assumed readiness
Historic 514/602 test claims are not current-run evidence. Record unavailable Docker explicitly; keep STATUS IN_PROGRESS until work is either verified or genuinely blocked.

## D004 — Restore local test infrastructure, not production
Docker was installed but inactive. Starting its local service succeeded; use only disposable Testcontainers services and an owned loopback Redis container. No live database credentials, production deploys or outbound provider sends authorized.

## D005 — No fabricated notification recipients/providers
Hardcoded admin/example email/phone and simulated provider success are not deliverable functionality. Unsupported provider success can be corrected safely. Recipient/fan-out policy, SMS/push provider choice, device credentials, and design-only billing/ride-hailing rules require explicit supported configuration/specification; retain them visibly in the inventory rather than invent business-critical policy.

## D006 — Checkpoint commits deferred
The active app and many tests are entirely pre-existing untracked files. Committing a minimal edit to them would necessarily absorb unrelated user migration. Preserve RED/GREEN logs in docs instead of staging that work; no Git commit created.

## D007 — Cost-aware delegation, maximum two active subagents (2026-10-28)
Owner explicitly limits concurrent subagents to **two**. Default to direct work and parallel independent tool calls, not agent fan-out. Delegate only a bounded independent task where expected time saved or independent review value justifies startup/context cost. Batch related review questions into one request; never duplicate delegated work or poll agents. Count background agents as active until completion/error; do not respawn failed agents automatically.

The last fixture-repair and outbox-design agents both stopped on usage limits with no accepted result. Inspect partial files before continuing directly. No monetary balance or token-cost telemetry was obtained; cost effectiveness here is a scheduling policy, not a measured savings claim. Keep independent review for high-risk changes when capacity permits; lack of capacity is an explicit verification gap, not approval.

Follow-up: implementation/testing remained direct; only two bounded read-only reviews were dispatched, sequentially (outbox transaction/schema risk; rule-fetch/tenant fallback risk). Both completed, with requested concurrency/multi-vehicle regressions added and passed. Peak active reviewers: one; current active: zero. No actual monetary balance or quantitative savings claim.

Further continuation: one additional sequential bounded review covered upstream broker retry/stable source identity/atomic cooldown lease ownership. Suggestions were implemented and tested; three completed bounded reviews total, peak one active, zero now. Owner explicitly requires continued actionable execution while IN_PROGRESS; do not end merely at a green gate or phase boundary. Record genuine policy/infrastructure blockers and keep advancing independent work; respect tool/runtime limits without claiming unlimited operation.
