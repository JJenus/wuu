# Production delivery plan — pinned execution order

Updated: 2026-10-30. STATUS=IN_PROGRESS. Authority:
workspace `AUTONOMOUS_DIRECTIVE.md`, especially §§7,13–19, plus owner scope
clarification: only ride-hailing is deferred; configurable token/monthly/yearly
subscriptions are required. Production deployment and real financial operations
still require explicit authorization.

## Read this first

This is the **current detailed execution plan**, not a reset of completed work.
`MASTER_PLAN.md` is the short roadmap; this file defines batch exit criteria.
`TASK_QUEUE.md` selects the current task; `SESSION_STATE.md` records recovery.
`FEATURE_MATRIX.md` and `requirements-inventory.md` retain the exhaustive feature
inventory, including requirements not individually named in the batches below.
`TEST_REPORT.md`, frontend `docs/verification.md` and slice reports hold evidence.
Old dated statements such as “no frontend” or “no subscription UI” are historical,
not reasons to rebuild delivered capabilities or exclude unfinished ones.

Backend: independent Java business JAR and device JAR. Frontend: independent
Nuxt repository at workspace `app/`. Preserve existing detail/list changes and
the active backend checkout's README/start script. Backend code changes use the
isolated worktree. Maximum two active agents; direct work by default. Default
model for bounded implementation, strongest available reasoning for ambiguous
security/data decisions and independent review; do not claim model independence
without evidence. Never run concurrent Maven builds against one target directory.

## Current baseline, not product acceptance

- Backend main `8af0e37`: authenticated tenant APIs, current database access,
  subscriptions pending enrollment/cancel and explicit non-paid grant/usage
  ledger, role catalog, authenticated alert actor binding. Much broader API
  surface exists but UI integration is incomplete.
- Frontend frontend-app `5aee8bc`: preview, authenticated fleet/detail reads,
  effective-access and role inspection, pending subscription enrollment/cancel,
  single-process refresh, strict alert transition BFF (UI still disabled).
- Latest frontend contract gate136 units/33 auth browsers passed. Historical
  preview31 and actual-Java auth/subscription/refresh acceptance passed. New alert
  actual-Java attempt stopped before startup: Docker socket absent. Historical
  backend735/device91 passed before restart; recovered actor fix32 focused passed.
  These numbers are provenance, not proof of a currently running application.
- Existing unknowns: notification recipient/provider policy; paid charging and
  automatic entitlement/metering policy; command/ACK and physical hardware;
  session replica coordination; hosted CI, production ingress and final audit.
  No external requirement disappears because it is blocked.

## Non-negotiable feature-update gate: FULL_START

After **every coherent feature addition/update**, before accepting that batch:

1. Build affected frontend/backend/device artifacts from recorded commits. Run
   relevant unit, contract, database and regression tests. Do not substitute
   mocked HTTP fixtures for the integration being accepted.
2. Start the complete controlled local stack: PostgreSQL with migrations and
   Hibernate validation, Redis, Artemis, business backend, **independent** device
   gateway, and production-built Nuxt/Nitro frontend. Use owned disposable data,
   isolated ports and private generated test secrets; record versions/digests.
3. Wait for actual dependency/application readiness, not merely open ports or
   process existence. Verify backend health, frontend sign-in render, device TCP
   listener and connected broker/cache/database. Fail if a process exits early.
4. Browser: sign in, open fleet/detail and the changed feature, perform its real
   authorized action, reload and confirm persisted result. Verify denied
   permission, cross-tenant access, validation/failure UI, console/network errors,
   cookie secrecy and no preview fallback. Exercise two independent tenants.
5. Restart the changed service, restore required connections and recheck health,
   persisted state and representative workflow. Before shared-session work,
   a BFF restart legitimately requires sign-in; it must not silently reuse lost
   single-use-token receipts. Test clean stop/SIGTERM and owned-resource cleanup.
6. Obtain independent bounded review, resolve confirmed findings, record command,
   commits, artifact hashes, outcomes and retained log location. Update matrix,
   queue, defect register and session state; commit only scoped changes. Existing
   owner authorization permits scoped source pushes to the two repository branches;
   it does not authorize remote infrastructure changes, deployment or payments.

Any review correction or subsequent code/config change invalidates the affected
receipt. Rerun affected tests **and FULL_START on the final backend/device/frontend
revision tuple** before accepting the batch. B0 must supply one fail-closed gate
invocation that writes a durable sanitized PASS/FAIL summary keyed to that tuple;
batch acceptance must reference it, not a receipt for pre-review artifacts.

For each changed screen, verify responsive/mobile layout, keyboard/focus behavior,
appropriate accessibility checks, navigation/deep links and meaningful loading,
empty, pending and error states. At batch completion run the wider affected
journeys; a working POST is not enough to accept its page.

If any service cannot start, prioritize its repair before dependent features.
Mark the feature IMPLEMENTED_UNVERIFIED/FAILED/BLOCKED as appropriate; **never
VERIFIED** on unit tests alone. Continue independent work when an external gate
cannot be resolved. A successful gate tears down only its owned test resources;
it does not imply a running demo remains available. Record a separate development
launch command and status when leaving a usable stack running.

Existing commands (run in their owning repositories):

- Backend `./mvnw -B verify`; device `./mvnw -B -f device-comm/pom.xml verify`.
- Disposable backend vendor gate `sh tools/verify-release.sh` (Docker required).
- Frontend `pnpm typecheck`, `pnpm test:coverage`, `pnpm check:boundaries`,
  scoped formatting plus full-format debt reporting, `pnpm build`, auth/preview
  Playwright suites using their repository configurations.
- Real Java BFF browser harness: `WUU_JAVA_JAR=<absolute-current-jar> TMPDIR=/tmp/opencode node tools/verify-java-integration.mjs`.
  This harness alone currently does **not** satisfy independent device startup;
  extend existing owned lifecycle tooling rather than invent parallel launchers.
- Device lifecycle/socket tests under `device-comm/src/test` and historical
  process receipts: locate retained runnable tooling before reusing it. Backend
  `tools/` currently contains `verify-release.sh`, not a retained unified
  device/telemetry launcher. B0 must add the missing orchestration; do not cite
  erased temporary smoke scripts as executable commands.

## Ordered delivery batches

### Inventory-to-batch ownership (reconcile during B0, not final audit)

Every inventory ID remains assigned below. The owner deferred only ride-hailing;
that does not turn unspecified design hypotheses into invented business rules.
Unspecified required intent stays BLOCKED on a named decision, never excluded or
silently accepted. B0 reconciles each row's current source/evidence and records
policy questions in DEFECT_REGISTER/DECISION_LOG; implementation continues on
independent approved work. New inventory IDs need an owner batch when discovered.

| Inventory IDs | Owning batch / acceptance obligation |
|---|---|
| AUTH-01..09 | B4 account/organization/role/profile/password workflows; B8 auth/session/security operations; reconcile each route and required UI |
| AUTH-10 | B4 retains verified role catalog; test regression rather than rebuild |
| AUTH-X01..02 | B4 provider sign-in/linking and token retention; BLOCKED until provider/confirmation/API-scope policy is approved |
| AUTH-X03..04 | B8 security anomaly/audit retention, B7 authorized delivery; BLOCKED on privacy/geo/retention rules |
| AUTH-X05 | B4 role management/account recovery/verification/MFA scope decision; implement approved proof/rate-limit flows, no inferred email/MFA success |
| TRACK-01..02 | B2 vehicle/tracker creation, queries and assignments |
| TRACK-03..06 | B5 tenant telemetry, state/trips/history/export; include real CSV download correctness and permissions |
| CMD-01..02, DEV-01..04 | B5 command persistence/transport/safety, protocols, connections and independent deployment; B8 device threat model |
| ALERT-01..05 | B3 rule/geofence configuration and evaluation; B5 real ingestion/reconnect/disconnect |
| ALERT-06..08 | B1 alert lifecycle/list/stats; B3/B7 cache/event/dedup/delivery reliability |
| NOTIFY-01..08 | B7 fan-out, inbox/preferences/templates, providers/retries/socket/callbacks; explicit recipient/provider blockers |
| SUB-01..12 | B6 full subscription design obligations, detailed below; no feature disappears behind “billing later” |
| OPS-01..04 | B0 startup and B8 migration/config/metrics/correlation/recovery; B9 final acceptance |
| FUT-01..02 | B5 owns ML anomaly/predictive-maintenance intent decisions; BLOCKED on agreed inputs/privacy/outcome criteria, not an invented model |
| FUT-03..04 | B5 fuel analytics/driver scoring; BLOCKED on real sensors/driver identity/calibration/scoring/privacy rules |
| FUT-05 | B8 configuration/scale requirements; architecture alternatives remain proposals until supported, not mandatory technology rewrites |
| RIDE-01..16 | Explicit owner-deferred ride-hailing family; retain inventory and decision provenance, do not implement or count as delivered |
| Other retained design-only IDs/families | B0 must enumerate and assign them from requirements-inventory before closing scope reconciliation; no default exclusion, B9 checks full coverage |

For SUB-01..12, B6 explicitly owns payment-method/customer tokenized ownership;
plan active/deprecated/archived lifecycle; immutable price grandfathering and cohort
migration; entitlement overrides/metering; trial/paid activation; renewal/dunning;
period-end cancellation/transition audit; immutable invoice numbering/history and
line-item totals; upgrades/proration/downgrade schedules; payment callback/replay/
reconciliation; coupons/redemption concurrency; external tax/address handling.
Provider, jurisdiction, rounding, cancellation timing, overage, coupons and price
migration rules without approved requirements are named owner-policy blocking
edges. Do not choose financial rules merely to make a test green.

Each batch is a coherent user outcome, not a promise of one agent run. Break into
small commits only where useful; do not report an intermediate endpoint as a
finished workflow. Dependencies are explicit. Rollback means reverting only our
scoped commits/config in a controlled branch; never discard user changes, rewrite
applied migrations or remove shared data. Database rollback uses compatible
forward fixes/validated backup procedures, not editing Flyway history.

### B0 — Reproducible full startup and recovery (NOW)

Context: runtime was healthy historically but Docker disappeared after restart;
separate component receipts are not a current full-stack launch guarantee.

- Diagnose/restore local Docker/container runtime using available legitimate
  privileges; inspect failed startup rather than repeatedly retrying it.
- Reuse and join existing owned Java/Nitro and device lifecycle harnesses into
  one documented full-start gate. Add bounded readiness/timeouts and failure
  logs, not only a success-path shell script. Provide reproducible dev start/stop
  instructions with clear configuration prerequisites and no committed secrets.
- Run current alert actual-Java assertions; correct defects they reveal. Preserve
  already completed auth/subscription tests and unrelated edits.
- **Exit:** cold start and service restart of all six service classes pass
  FULL_START, actual browser sign-in/read plus alert API persistence works, clean
  teardown is proven; instructions actually launch the app. Keep physical-device
  acceptance separate. Rollback: scoped harness changes only.

### B1 — Usable alerts from list/detail (depends B0)

- Reconcile pre-existing detail/list work without overwriting it. Remove legacy
  adapter actor selection; use the strict BFF. Permission-aware acknowledge and
  resolve, notes validation/confirmation, pending/error/duplicate-submit handling.
- Refetch visible list/detail/overview counts after success; do not optimistically
  fake success after timeout. Denied users cannot mutate, even via direct calls.
- **Exit:** real browser performs acknowledge/resolve, sees accurate status after
  reload, correct actor is persisted; errors and tenant/permission denials pass;
  FULL_START passes. Rollback: hide UI actions; retain protected backend contract.

### B2 — Vehicle and tracker setup (depends B0; serialize UI ownership with B1)

- Verify insert-only vehicle/tracker creation and assignment contracts, duplicate
  identity rules and current permissions. Deliver forms and safe assignment/
  unassignment with actionable errors. No invented device credential scheme.
- **Exit:** create vehicle, provision/assign owned tracker, reload/details/filter
  reflect persisted state; duplicate and foreign-tenant attempts leave original
  data unchanged; FULL_START passes. No claim of physical telemetry from a form.
  Rollback: disable new controls; preserve migrated/persisted tenant records.

### B3 — Geofences and alert-rule management (depends B2/B0)

- Repair backend geometry/assignment update-loss contract before enabling UI.
  Preserve circle/polygon shape and vehicle bindings; add compatible migrations
  only if needed. Expose authorized CRUD and rule configuration where inventory
  requires it; do not allow silent partial updates.
- **Exit:** create/edit/reload/assign real circle and polygon, invalid geometry
  rejected without data loss; authorized rule config persists and actual
  telemetry exercises the rule; isolation and FULL_START pass. Rollback: disable
  editing; use forward schema fix if applicable.

### B4 — Team and tenant administration (depends B0; independent of B3 domain)

- Reuse current user/role APIs; deliver member CRUD and authorized role assignment
  with last-admin safeguards, anti-escalation and disabled-account behavior.
  Implement intended invitations only with a real approved transport/policy;
  never label a database placeholder as “invitation sent.” Retain inventory of
  platform/organization/profile/password workflows; wire required surfaces safely.
- **Exit:** actual two-user browser administration, immediate server permission
  revocation and tenant denial pass; invitations are either genuinely verified
  or explicitly BLOCKED (not silently excluded); FULL_START passes.
  Rollback: scoped controls/routes, preserve memberships and audit history.

### B5 — Real tracking, trips and independent device reliability (depends B2/B3)

- Close framed input/connection replacement/backpressure, GT06/Autoseeker parsing
  and ownership sanitation gaps from the matrix. Preserve separate deployment.
- B5/B8 must establish a deployment threat model covering device identity,
  spoofing/replay and transport exposure. Owner clarification forbids inventing
  an Autoseeker credential scheme; sanitation is not authenticated hardware.
  Production requires verified controls or an explicitly approved constrained
  deployment boundary. Missing hardware/transport evidence remains a release gate.
- Connect approved tenant-scoped live updates/history and trips to UI; clearly
  separate recorded GPS from illustrative paths. Complete command transport/ACK
  through separate processes with safe offline/failure states and fuel-cut safety.
- **Exit:** synthetic TCP→gateway→broker→database→trip/rule/alert→browser chain,
  reconnect/restart/error/tenant tests and FULL_START pass. Physical device and
  dangerous-command acceptance remain separately gated; no simulated ACK claim.
  Rollback: disable commands/live stream; retain safe read-only history.

### B6 — Complete configurable subscriptions (depends B0/B4; policy gates explicit)

- Preserve token/monthly/yearly catalog, pending enrollment/cancel and explicit
  non-paid grant/usage ledger. Complete authorized catalog/admin controls and
  approved consumption/entitlement integration with atomic replay-safe accounting.
- Implement paid provider sandbox checkout/callback/reconciliation/renewal/
  cancellation/refund/dunning/tax behavior as required by approved provider and
  business rules. Missing provider/credentials/policy is a recorded blocking edge,
  not permission to invent payment success or grant paid entitlement.
- **Exit:** required models verified over real database/browser; duplicate usage,
  callback replay, expiry and recovery pass. Real/sandbox evidence is labeled;
  paid production readiness remains BLOCKED without required live evidence.
  FULL_START passes with honest disabled-provider behavior. Rollback: disable new
  charging entry points; never reset ledgers or conceal reconciliation debt.

### B7 — Notifications and durable delivery (depends B3/B4/B5; external policy)

- Obtain approved tenant recipient/contact/opt-in policy and provider credentials;
  keep unavailable channels honest. Complete initial dispatch, durable subscribers,
  downstream idempotency, retry/DLQ and provider-specific signed callbacks.
- Deliver inbox/preferences and authenticated browser transport where intended;
  do not assume HTTP tenant context crosses workers or browser bearer headers
  work for native WebSocket handshakes.
- **Exit:** actual alert reaches the authorized recipient/inbox, preferences and
  channel failures behave correctly; replay/restart does not duplicate/loss-hide
  delivery. Provider/hardware evidence stays explicit; FULL_START passes.
  Rollback: disable outbound provider dispatch; retain retryable audit records.

### B8 — Production operations and shared sessions (B0 first; integrate continuously)

- Replace process-local BFF coordination with durable multi-worker-safe rotation
  and receipt semantics without weakening backend reuse revocation; test parallel
  workers, lost responses and rolling restarts. Review rate limits and Host/TLS.
- Establish hosted CI for both repositories/builds, migration clean/upgrade tests,
  secret/config validation, TLS/ingress/CORS/CSP, approved map provider, secure
  health/metrics/logging, capacity tests, backups/restore and rollback procedures.
  Prepare and test repository CI/ingress definitions locally; changing remote
  runners, secrets, DNS, ingress or live infrastructure needs explicit authority.
- Resolve dependency findings and material coverage gaps with risk-based tests;
  never inflate coverage with trivial mocks. Record unresolved low advisories.
- **Exit:** release-like topology starts and restarts; CI and load/recovery gates
  pass; critical/high findings resolved, observability/restore demonstrated and
  FULL_START passes. Local preparation is not authorization to deploy production.
  Rollback: tested prior release/config plus forward-compatible data strategy.

### B9 — Production-readiness acceptance (depends all required batches)

- Fresh independent reviewers reconstruct requirements from original directive,
  inventory and real rendered UI—not just the implementer's done list. Trace every
  essential page/control to persisted/observed effects; reopen confirmed gaps.
- Execute full regression, actual-process journeys, migration/upgrade, restart,
  backup/restore, security and relevant capacity/device/provider acceptance against
  final recorded artifacts. Confirm reproducible start instructions from clean
  supported configuration. Consolidate evidence in RELEASE_READINESS.md.
- **Exit:** all directive§17 conditions met, every required inventory row VERIFIED
  or an explicitly approved exclusion, no release blockers and fresh independent
  audit accepted. Only then STATUS=RELEASE_READY. Missing production-only evidence
  stays BLOCKED/NOT_VERIFIED; never rename local success “production verified.”
  Deployment is a separate explicitly authorized operation with rollback plan.

## Execution and progress policy

Critical path: B0→B1/B2→B3/B5→B9; B4/B6/B7 and B8 enter where dependencies allow.
At most two independent lanes; never overlap file ownership or Maven targets.
Do not let blocked charging/recipient/hardware decisions stall basic usable CRUD.
Do not bypass final readiness gates by treating those blocked features as optional.

Before each batch: read session/queue/matrix/defects and inspect Git. Define the
user-visible exit criteria, tests and acceptance data. After each feature update:
FULL_START, review, scoped commit and evidence update. If startup fails, stop
accepting dependent features and fix it. Reassess backlog and continue without
routine approval prompts. No run-count/ETA promise; report batch and blockers,
not micro-commit counts. Plan changes require a reason, affected dependencies and
updated acceptance in DECISION_LOG.md; completed evidence remains historical.

Retain concise verification outcomes in Git so `/tmp` loss does not erase state.
Raw logs may be private/ephemeral: record their retention status honestly; never
commit secrets or treat lost logs as currently inspectable receipts.
