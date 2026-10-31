# Recoverable execution state

## Current accepted baseline — INT-00, 2026-10-31

INT-00 reconciliation accepted at frontend3c8eee9/backend-device451a083.
FULL_START PASS2026-10-31T16:36:43.324Z with stable clean revisions and all eight
stages, including browser/restart/persistence/cleanup. Backend646/device91,
frontend144 units/40 auth/31 preview PASS; scoped reviews complete. See
[handoffs/INT-00.md](handoffs/INT-00.md) for hashes, limits, raw evidence and
RED/GREEN. Earlier disabled-alert-UI/missing-device-harness statements below are
historical. Coordinator remains tracker-int00-20261031; live shared board decides
task availability. Production NOT_READY; no deployment authority. FLEET-01 is
independently owned and not integrated. SESS-01 dependency INT-00 is now satisfied.

## Owner update — independent agents, 2026-10-31

- Implementation paused to prepare parallel independent-session dispatch, not
  subagents. Read PARALLEL_EXECUTION.md before selecting work; TASK_QUEUE routes
  to its task board. Live atomic claims/progress are in workspace .coordination/.
- Separate worktrees/domain ownership, shared-file locks, two heavyweight local
  build slots, independent-session reviews and one integration coordinator prevent
  duplicate work and acceptance races. No implementation tasks preclaimed during
  this documentation bootstrap. Owner starts the sessions.
- Old /tmp/opencode backend checkout/raw logs lost. Published backend main was
  recovered into durable tracker-worktrees/coordination-plan, without modifying
  active sidie or frontend work. Frontend local commits through b00c92c survive.
- Retained frontend .verification/last-startup.json is PASS, timestamp
  2026-10-30T13:38:43Z, frontend b00c92c + dirty hash18baeef0…a7f,
  backend/device e0193dd with clean hash; all8 stages including restart/persistence/
  cleanup. Extra final test-fixture reviewer hit usage limit, not approval.
  INT-00 owns baseline/evidence and reserved dirty-source reconciliation before
  declaring B1 fully accepted. Production readiness remains IN_PROGRESS.

## Current batch — B1 usable alerts after unified startup PASS

- FULL_START implementation published frontend8e211f9/device bind dfa883f.
  First cold+restart complete stack PASS, including independent device real
  Artemis publication, actual Java/browser workflows, DB persistence and owned
  cleanup. `/tmp/opencode/full-start-first.log`, private wuu-java-XdYYyz. First
  receipt captured tested precommit source fingerprints; frontend docs/full-start.md
  records hashes/limits. Device91 and frontend141 units/types/boundaries pass.
- Two startup review findings fixed before gate: guard against creating resources
  after signal; reject surviving old process groups before restart acceptance.
- B1 underway: new adapter regression RED e4a61db (session-derived identity,
  canonical positive Long IDs, notes10–1000). Implement shared permission-aware
  list/detail response component and preserve pre-existing detail/list changes.
  Actual next feature must rerun FULL_START; current UI remains disabled until
  implementation/acceptance. STATUS=IN_PROGRESS.

## Latest B0 checkpoint — 2026-10-30

- Pinned roadmap published backend `f8d0aeb` (initial plan `9c892d4`), frontend
  pointer `b618681`; workspace DELIVERY_PLAN.md and AGENTS.md route future work
  to canonical PRODUCTION_DELIVERY_PLAN.md. Independent plan review accepted
  all six corrections. Current batch B0, next B1; do not repeat completed planning.
- Restored Docker and ran actual-Java alerts. First run found production
  SNAKE_CASE mismatch; frontend RED `2ff125e`, correction `cbdd54e`. Corrected
  actual-Java **1/1 PASS**, production build/Java dependency health pass, real
  acknowledge/resolve actor/notes persisted, tenant/Origin/forged-body denials pass.
  Receipt `/tmp/opencode/plan-b0-java2.log`, private
  `/tmp/opencode/wuu-java-AORcij/browser.log`. Owned cleanup completed.
- Follow-up frontend **136 units /33 auth browsers PASS**, selected
  coverage94.62%line/85.71%branch/95.15%function; types/boundaries pass. Scoped
  independent naming-correction review approves; no whole-product certification.
- Backend artifact SHA256
  `e207fcc526d7585bf3985729f70cb591d87bff9ebf6c2b1416e7d89bc180d9af`.
  Existing Java/Nitro harness omits device startup/restart: FULL_START remains
  NOT_VERIFIED. Next executable B0 task: unified owned final-revision startup/
  restart gate including independent device, fail-closed durable summary and
  reproducible dev launch. Then integrate B1 UI and production JSON adapter.
- UI actions still disabled; pre-existing detail/list edits preserved. Docker
  missing-socket statements below are historical. STATUS=IN_PROGRESS.

## PINNED CURRENT EXECUTION PLAN — 2026-10-30

- Owner requested clear production-readiness plan, existing detail pointers and
  complete startup after every feature addition/update. Authority remains workspace
  AUTONOMOUS_DIRECTIVE.md. Entry point workspace DELIVERY_PLAN.md; canonical
  detailed version `PRODUCTION_DELIVERY_PLAN.md`; short MASTER_PLAN/active
  TASK_QUEUE/FEATURE_MATRIX overlay and RELEASE_READINESS now point to it.
- B0 full startup/recovery is current; B1 alerts UI is next. Batches B2..B9 cover
  vehicle/tracker, geofences/rules, team, live tracking/device, subscriptions,
  notifications, production operations/shared sessions and final independent audit.
- Independent plan review identified six gaps and confirmed all addressed:
  inventory crosswalk/policy edges, full subscription obligations, final revision
  FULL_START reruns after review fixes, device threat model, common screen UX and
  authorization scope. This is plan acceptance, not runtime acceptance.
- `sudo -n systemctl start docker` initially exceeded60s client timeout, but
  subsequent bounded systemctl status confirms docker/socket/containerd RUNNING;
  daemon initialization finished2026-10-30 12:34:56 local time. Do not keep
  classifying missing socket as an active blocker. No shared containers removed.
- Pending actual-Java alert runner resumed; receipt `/tmp/opencode/plan-b0-java.log`.
  Do not mark PASS until completion. Existing Java harness omits independent device
  startup, so alone cannot satisfy the new FULL_START gate. Next B0 implementation
  joins existing owned harnesses with durable final-revision PASS/FAIL summary,
  full process readiness, browser workflow and changed-service restart.
- Preserve all unrelated frontend detail/list edits and active sidie checkout.
  STATUS=IN_PROGRESS; production deployment remains unauthorized.

## Active continuation — alert gateway accepted locally, Java blocked

- Backend actor fix published on main `77518dd`; frontend narrow gateway and
  evidence published on frontend-app `5aee8bc` (GREEN `ebe1e80`, RED `1e7227f`).
- Only exact individual alert acknowledge/resolve POSTs are admitted with strict
  bounded bodies, no query/actor/tenant selection and existing Origin/sealed auth.
  UI and legacy adapter remain disabled/unchanged; pre-existing detail/list work
  is preserved. Contract gates **136 units /33 auth browser tests PASS**, selected
  coverage94.61%line/85.71%branch/95.15%function, types/build/boundaries/scoped
  formatting pass. Read-only security review found no introduced medium/high.
- Actual-Java runner blocked before services start: `/var/run/docker.sock` absent
  after restart; `/tmp/opencode/alert-bff-java.log`. New acceptance checks provision
  tenant alerts, assert real actor attribution/persistence and cross-tenant/Origin
  denial but have not run against Java. No actual mutation acceptance claim.
- Rebuilt backend JAR SHA256
  `e207fcc526d7585bf3985729f70cb591d87bff9ebf6c2b1416e7d89bc180d9af`.
  Once Docker is restored, from independent app run
  `WUU_JAVA_JAR=/tmp/opencode/tracker-alert-actor/target/tracking-engine-1.0.0-SNAPSHOT.jar TMPDIR=/tmp/opencode node tools/verify-java-integration.mjs`.
  See frontend `docs/alert-transitions.md`. Then UI workflow remains next.
  STATUS=IN_PROGRESS; external infrastructure/provider/payment/hardware and
  production release gates remain open. No routine restart of completed slices.

## Active continuation — alert actor fix recovered after restart (2026-10-30)

- Original isolated checkout and temporary logs were erased by environment
  restart. Committed RED `b6be4ef` survived. Reconstructed identical three-file
  fix is GREEN `9c98b31` on `fix/alert-actor-recovery`, isolated checkout
  `/tmp/opencode/tracker-alert-actor`; keep original active `sidie` unchanged.
- Single/bulk alert acknowledge/resolve bind the actor to the authenticated
  database user ID. Client-supplied actor fields cannot forge audit identity.
  Existing tenant/permission/note-validation boundaries remain unchanged.
- Historical pre-restart full gate **735 backend / 91 device PASS**, independent
  Java review approves. Logs/artifacts from that run were erased; do not claim
  they remain available. Fresh reconstructed-tree focused gate **32 PASS** on
  2026-10-30, `/tmp/opencode/alert-actor-recovery-focused.log`. Full gate was not
  repeated. See `alert-actor-identity-slice.md`.
- Frontend renewal and preview documentation published at `6f1aac1`; preview
  browser suite31 passed pre-restart. Preserve unrelated detail/list edits.
- Next: publish scoped backend fix, then live alert transition workflow and
  actual-Java mutation acceptance. STATUS=IN_PROGRESS; no deployment authority.

## Active continuation — refresh lifetime contract and frontend renewal

- Publication/evidence update: backend lifetime contract is on `origin/main`
  `255be2c`; independent frontend renewal/tests/docs are on `origin/frontend-app`
  `5b7278c` (GREEN `7a85cbe`). Final frontend gate **133 units / 32 auth browser
  tests PASS**; selected coverage92.79%line/85.56%branch/95.11%function.
- Final actual-Java acceptance **PASS** with a disposable two-minute access TTL
  that forces real BFF rotation; `/tmp/opencode/renewal-java-final2.log`, private
  browser `/tmp/opencode/wuu-java-50ejEH/browser.log`. Same backend JAR hash below;
  owned cleanup and empty fixture-container audit verified. Earlier default-TTL
  refresh-route acceptance did not prove rotation and is superseded by this run.
- Credential-alias coordination, SSR cookie forwarding, late replay rejection,
  short-TTL recovery, in-flight logout and bounded outage recovery are tested.
  A random process epoch forces sign-in after restart. Single-process operation
  only: durable distributed coordination remains a release gate. All concrete
  medium findings from the bounded read-only security review were fixed.
- Continue with verified live workflows/device sanitation where contracts permit;
  preserve pre-existing frontend detail/list edits and active sidie README/script.
  Provider/recipient/payment/production/hardware approvals remain external gates.
  STATUS remains IN_PROGRESS, not RELEASE_READY.

- Additive `refresh_expires_in` is implemented on branch
  `feat/session-refresh-expiry` for login, registration and refresh responses; it
  reports the existing session refresh lifetime without changing rotation or
  revocation policy. See session-refresh-expiry-slice.md.
- Fresh full vendor gate **PASS**: backend **729**, device **91**, zero
  failures/errors/skips; both executable JARs built. Receipt
  `/tmp/opencode/session-refresh-full-gate.log`. Backend JAR SHA256
  `4f4a24c329537252e06f6f5aab97347054172046a2e32196fa9c260030767454`;
  device JAR SHA256
  `8329c651be98d0dc00d9f8eb926e552a0d5a99f3c0baabff52edeeab1fa0dfcf`.
- Actual-Java production-Nitro acceptance **PASS** against that JAR, now including
  a real BFF refresh rotation followed by an authorized read. Receipt
  `/tmp/opencode/refresh-java-acceptance.log`; private browser log
  `/tmp/opencode/wuu-java-guAgb5/browser.log`. Owned cleanup completed.
- The independent frontend now persists the sealed refresh credential in the BFF
  cookie, coalesces concurrent rotation in-process, recovers a lost refresh receipt
  for 30 seconds, auto-refreshes before access expiry and keeps refresh tokens out
  of browser-readable state. Actual-Java browser **1/1 PASS**; full frontend auth
  browser suite **26 PASS**, selected unit **123 PASS** at that intermediate
  checkpoint. The publication/evidence update above supersedes those counts.
- The cache is deliberately process-local and bounded; distributed multi-instance
  refresh coordination is not claimed. Paid provider/recipient policy, physical
  device acceptance and production deployment remain blocked. STATUS stays
  IN_PROGRESS.

## Historical continuation — role catalog and publication

- Tenant-scoped read-only `GET /api/v1/roles` is implemented on branch
  `feat/tenant-role-catalog`: RED `46e25e2`, GREEN `a2db590`. It requires the
  database-derived `roles.read`, ignores client tenant overrides, pages only
  current-tenant roles and never assigns authority. See role-catalog-slice.md.
- Fresh full vendor gate **PASS**: backend **729**, device **91**, zero
  failures/errors/skips; both executable JARs built. Receipt
  `/tmp/opencode/tracker-role-catalog-full-gate.log`. Backend JAR SHA256
  `0866662ffa28b86072a262fa3c6236af12759048b94d264c6180cf9f80d4d99e`.
- Focused role HTTP **10/10** and real PostgreSQL role-catalog regression **1/1**
  pass. The PostgreSQL case builds two tenants, proves paging/order/isolation and
  ignores `orgId=999999`.
- Independent frontend remains at `5e3892a` plus dependency patch `ed285d1`;
  documentation commits and actual-Java same-tenant rerun remain. Preserve all
  pre-existing detail/list edits.
- Next: push backend role slice/docs, run final actual-Java acceptance against the
  new JAR, finish frontend docs/test commits, then continue refresh/write
  workflows and feature metering. Paid provider, recipient policy and production
  deployment remain blocked. STATUS stays IN_PROGRESS.

## Historical continuation — subscription UI and effective access

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
