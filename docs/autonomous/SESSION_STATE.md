# Recoverable execution state

## Active continuation — subscription UI and effective access

- Backend changes are isolated at `/tmp/opencode/tracker-effective-access`, branch
  `feat/effective-access`: `e28fdea` RED and `8f8307b` GREEN. Original checkout's
  README/start-backend.sh remain untouched. Do not drop that uncommitted work.
- Read-only `/api/v1/users/me/access` exposes current database-derived permissions,
  active tenant and access expiry, no tokens/session ID. No role elevation or new
  permission policy. Full backend718/device91 gate PASS; see effective-access-slice.md.
- Independent frontend repo has subscription catalog/enrollment/cancel UI with
  sealed-session, exact-origin and bounded-payload gateway, retry-key recovery,
  owner isolation and explicit non-payment copy. Effective-access settings panel
  uses the new contract; platform concept routes remain blocked. Changes are not
  published yet; preserve all pre-existing detail/list edits.
- Actual-Java final fixture **PASS** against isolated built artifact SHA256
  `3ad87f9a7bce65cbdfade542dd9b92666800a0ee5a91d6bb0d6c8d300849b8e6`:
  `/tmp/opencode/effective-access-java-final.log`, private browser receipt
  `/tmp/opencode/wuu-java-vfPHdZ/browser.log`. Owned cleanup completed.
- Current next steps: final focused mobile/format checks, publish scoped
  frontend/backend commits, then continue the next
  actionable capability. Paid-provider/recipient policy remains blocked; do not
  mark RELEASE_READY. Entries below are historical publication checkpoints.

## Current state — durable grant/usage slice

- RED/GREEN commits `697d880` and `9293dc8` plus evidence were pushed normally to
  `origin/main`; remote verified at `1c740c08c1abb9f8e2e15659e9675c2379669141`.
  This entry is a follow-up publication receipt, not another pending feature gate.
- Authenticated tenant-scoped system-administrator non-paid grant/usage routes,
  V6 migrations, row-locked balance/receipt transaction and validated domain state
  reconstruction implemented. See subscription-ledger-slice.md for exact limits.
- Focused instrumented **61/61** passed (18 HTTP cases); PostgreSQL usage race/
  replay and V2→V6 upgrade **2/2** passed. Package coverage93.47%line/91.01%branch,
  domain100%; not whole-backend coverage.
- Full vendor gate **PASSED backend714/device91**, zero failures/errors/skips,
  both executable JARs built: `/tmp/opencode/tracker-subscription-ledger-full-gate.log`.
  Do not repeat completed gates without relevant source changes.
- Publication complete for this slice. Subscription UI and actual feature-meter
  enforcement remain next work;
  payment/provider and recipient decisions remain unresolved. STATUS=IN_PROGRESS.
- Preserve unrelated backend README/start-backend.sh and all existing frontend
  edits. No frontend source/device source change belongs to this new slice.

## Previous checkpoint (historical)

- Date: 2026-10-29
- Publication verified: `origin/main` reached `34ee76136e300267cb777dc908dfdd464d52601a` and `origin/frontend-app` reached `c541e91ecfc011bf240832acc8f242551a237925` via normal pushes; remote refs checked. Feature code and evidence are published. This receipt is a follow-up documentation commit. Remaining backend README/start-backend.sh and pre-existing frontend detail/list changes were deliberately not staged or discarded. Next task is durable authenticated grant/usage integration; do not repeat the completed vendor/browser gates unless source changes warrant it.
- Final superseding checkpoint: post-review vendor **backend701/device91 PASS**, rebuilt-artifact browser **PASS**, no fixture container/process leftovers. Browser receipt `tracker-reviewed-browser-gate.log`, artifact SHA256 `a452439bf220fa78a7cbcaaf975348531fb9ded8fbdbad2c6c9da3b2049e6ecb`. Remaining work is durable grant/usage/entitlement integration, subscription UI, payment/provider and notification recipient decisions, command/ACK and production acceptance. Preserve unrelated frontend edits and backend README/start-backend.sh. Current slices are being published; STATUS remains IN_PROGRESS. Entries below are historical recovery snapshots, not new rerun requests.
- Latest superseding result: final post-review vendor gate **PASSED backend701/device91**, no failures/errors/skips, both JARs built (`tracker-reviewed-vendor-gate.log`). Backend local commits now include `01bd54f` device sanitation; final browser acceptance is running in background (`tracker-reviewed-browser-gate.log`). Await it without restarting Maven; then audit cleanup, update receipts and publish verified commits. All publication is still pending. Entries below preserve earlier recovery context.
- Recovery superseding checkpoint: full vendor gate passed **backend699/device91**, both JARs built (`tracker-final-vendor-gate.log`). Two review regressions then reproduced oversized meter409 and extreme price exponent401; both fixed, subscription HTTP now **9/9** (`tracker-subscription-review-{red,green}.log`). Local backend commits `8805ba9`/`ebcac8c` are not pushed. Frontend process-group escalation fixed with **4 Node tests / 100% helper coverage**, local commits `31c0ebf`/`fb7af6d`, not pushed. Final vendor rerun currently in background (`tracker-reviewed-vendor-gate.log`); after it completes, rerun frontend fixture against the newly built JAR, update docs, commit remaining device/docs changes, push both repos normally. Do not rerun a gate already in progress. See final-slice-review.md.
- Phase: verified integration slices and release-gate reconciliation
- Current task: Final serialized backend/device/vendor gates and publication after authenticated subscription enrollment, device wiretap hardening, and frontend fixture lifecycle fixes
- Last completed task: Authenticated V5 plan catalog + tenant/user-scoped pending enrollment (7 HTTP tests); device default-payload-wiretap disabled (91 tests); frontend actual-Java fixture lifecycle hardened and rerun PASS
- Published source checkpoint: a50fb33 (origin/main synchronized at recovery); current slice not yet committed
- Git worktree: device sanitation/source, subscription domain + V5 enrollment, autonomous evidence, and independent frontend fixture/docs remain uncommitted. `start-backend.sh` and its README section are pre-existing/unverified work; preserve them and do not infer acceptance.
- Latest verification: full backend default suite **612 tests, zero failures/errors/skips** `/tmp/opencode/tracker-subscription-backend-test.log`; subscription HTTP **7/7**; `FlywayMigrationIT` **1/1** (five migrations); disposable-PostgreSQL V2→V5 upgrade **1/1** `/tmp/opencode/tracker-postgres-v5-upgrade.log`. Device `./mvnw -B -f device-comm/pom.xml test` **91/91** `/tmp/opencode/tracker-device-wiretap-test.log`. Frontend actual-Java fixture post-lifecycle **PASS** `/tmp/opencode/tracker-deployed-lifecycle-v3.log`, zero owned containers/descendants. Coverage unchanged and below target; no new whole-bundle coverage claim.
- Latest device-only verification: 91 tests, zero failures/errors/skips, including configured-parser identity, socket framing/reconnect/post-success routing and deterministic success/malformed/publication-failure tracking. Raw frame wiretap is now opt-in (`tracking.tcp.server.wiretap-enabled`, default false). See autoseeker-sanitation-cycle.md.
- Known failures: none in the latest backend/device/frontend focused runs. Full disposable vendor gate (`sh tools/verify-release.sh`) and independent review of the final combined slice remain pending. Coverage target, real notification delivery, payment/entitlement, command/ACK, secure physical-device provisioning, production frontend auth/TLS and deployment remain open. STATUS is IN_PROGRESS, not RELEASE_READY.
- Scope correction: only ride-hailing deferred. Subscriptions approved: configurable token usage and monthly/yearly periodic billing. Catalog + pending enrollment are durable; grants/consumption/payment/renewal are not implemented. Existing frontend `../app/` must be assessed/delivered. APIs require auth; Autoseeker TCP requires sanitation, not a new credential scheme.
- Next executable task: run the fresh serialized root `verify`, device `verify`/vendor release harness, review the combined diff, update evidence with exact receipts, then commit/push verified source. No production deployment/release authorization.
- Decisions: see DECISION_LOG.md; preserve user migration, serialize root Maven, no invented recipients/providers/financial policy
- Owner delegation constraint: maximum TWO active subagents; direct work by default. No subagents are active. Previous frontend/subscription agents hit usage limits; their partial work was resumed and verified directly rather than auto-respawned.

## Resume
From `/home/jjenus/workspace/apps/tracker/sidie`:
1. Read AGENTS.md and docs/autonomous/{STATUS,TASK_QUEUE,FEATURE_MATRIX,DEFECT_REGISTER,TEST_REPORT}.
2. `git status --short`; reconcile changes without restoring old modules or discarding pre-existing `start-backend.sh`.
3. `./mvnw -B test`; device: `./mvnw -B -f device-comm/pom.xml test`.
4. Subscription HTTP: `./mvnw -B -Dtest=SubscriptionControllerIntegrationTest test`.
5. Docker-dependent suite: `TMPDIR=/tmp/opencode sh tools/verify-release.sh` (owns disposable services and selects `*IT`).
6. Consult `/tmp/opencode/tracker-baseline-test.log` and `/tmp/opencode/tracker-device-baseline.log` if still available.

## Resource ownership
- Docker daemon is running locally; do not stop it while other local work may use it.
- Owned Redis container `tracker-autonomous-redis-ses-e8735256`, port 32768; remove only this container after tests if still present.
- Testcontainers owns its own integration containers and cleanup; the frontend fixture removes only `wuu.fixture`-labelled containers.
- `tools/verify-release.sh` owns its own random-port services and includes all *IT explicitly; run only when no other Maven lane is compiling.
