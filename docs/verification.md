# Verification — Wuu preview and live read-only integration

## Single-process session renewal — 2026-10-29

- Selected-source Vitest gate **133 tests / 19 files PASS**, coverage **92.79%
  lines / 85.56% branches / 95.11% functions**. The newly instrumented renewal
  utility has **95.2% lines / 84.05% branches / 100% functions**. No whole-UI or
  whole-backend coverage claim (`/tmp/opencode/renewal-coverage-final.log`).
- Typecheck, production build, boundaries and scoped formatting PASS. Full
  `pnpm format:check` reports pre-existing `app/layouts/default.vue`; the slice
  leaves that unrelated tracked file untouched (`renewal-format-check-final.log`).
- Full contract auth browser suite **32 PASS** (`renewal-auth-final.log`),
  including credential-alias concurrency, SSR replacement cookies, short-TTL
  recovery, timer/gateway expiry, logout, unrelated-user recovery and Origin
  denials. Unit tests cover ambiguous-response rejection, in-flight cancellation,
  bounded capacity/outage recovery and fail-closed process restart.
- RED checkpoints `dbc6d63` and `70ea423`; GREEN `7a85cbe`. A read-only security
  review found late-replay, short-TTL and capacity issues; regressions and follow-up
  review resolved them without weakening Java's strict reuse revocation. The
  reviewer did not run gates; final restart hardening was separately regression
  tested. See `session-renewal.md` for exact limits.
- Backend additive refresh-lifetime contract is published at `255be2c` on main.
  Its full vendor gate is **729 backend / 91 device PASS**, zero failures/errors/
  skips (`/tmp/opencode/session-refresh-full-gate.log`), backend JAR SHA256
  `4f4a24c329537252e06f6f5aab97347054172046a2e32196fa9c260030767454`.
- Final actual-Java production-Nitro browser acceptance **1/1 PASS** with that JAR,
  a disposable two-minute access TTL to force real refresh rotation, authorized
  reads and account-wide revocation. Receipt `/tmp/opencode/renewal-java-final2.log`;
  private browser `/tmp/opencode/wuu-java-50ejEH/browser.log`. Owned cleanup passed
  and the container audit found no fixture leftovers. The earlier default-TTL
  run exercised the endpoint but did not force rotation; this run supersedes it.

Only one BFF process is supported. Restart requires sign-in; distributed durable
rotation coordination, production ingress/TLS and final release approval remain
open. This is not `RELEASE_READY` or deployment authorization.

## Subscription owner/access continuation — 2026-10-29

- Frontend selected-source unit gate **122 PASS** under Vitest 4.1.12, coverage
  **92.38% lines / 85.69% branches / 94.63% functions**; the earlier higher
  percentages used Vitest 3 and are not directly comparable. Typecheck, build,
  boundaries and formatting passed (`role-ui-coverage.log`,
  `role-ui-build2.log`).
- Auth browser suite **25 PASS**, including the read-only tenant role catalog,
  mounted snapshot refresh and failed-cancellation retry
  (`role-ui-auth-full.log`); preview suite 31 PASS and focused mobile subset 12
  PASS before these follow-ups. Role fixture tests verify exact paging,
  `roles.read` visibility, ignored `orgId`, denied nested paths/writes and axe
  cleanliness (`role-ui-auth2.log`).
- Actual Java final subscription/access acceptance passed on isolated backend
  artifact SHA256 `0866662f…4d99e` with real owner enrollment/cancel,
  same-tenant different-user denial, cross-tenant denial, effective access,
  tenant role reads with ignored override, bearer secrecy and revocation.
  Receipt `/tmp/opencode/role-catalog-java-final.log`; private browser
  `/tmp/opencode/wuu-java-ht67wt/browser.log`.
- Backend full gate **729 / 91 PASS** including the tenant role-catalog slice;
  focused role HTTP 10/10 and real PostgreSQL role regression 1/1
  (`tracker-role-catalog-full-gate.log`).
- Independent review found no confirmed critical/high regression. Both warnings
  (stale mounted snapshot, failed cancellation dismissing confirmation) are fixed
  with regressions. Dependency overrides and Vitest 4 remove the prior
  high/critical advisories; one low `deepmerge` advisory remains because its
  advertised fixed version is unpublished. Audit is **NOT_CLEAN**, but no
  critical/high/moderate remains (`frontend-audit-vitest4.json`).

Receipts are under `/tmp/opencode/` unless an explicit path is given. These results
do not establish production readiness; `IN_PROGRESS` remains set.

## Actual-Java fixture lifecycle — 2026-10-29

Final post-review rerun **PASS**: `/tmp/opencode/tracker-reviewed-browser-gate.log`,
private logs `/tmp/opencode/wuu-java-uWqSb5/`, rebuilt JAR SHA256
`a452439bf220fa78a7cbcaaf975348531fb9ded8fbdbad2c6c9da3b2049e6ecb`.
This run uses the final cleanup helper; production build, Java dependency health
and browser assertions passed, followed by empty fixture-container/process audits.
The helper separately passed four Node process tests with 100% helper coverage.
Earlier receipts below retain their historical artifact hashes.

`node tools/verify-java-integration.mjs` passed production build, real dependency health and deployed browser acceptance after cleanup/port lifecycle hardening. Receipt: `/tmp/opencode/tracker-deployed-lifecycle-v3.log`; private logs: `/tmp/opencode/wuu-java-76xX5K/`. No owned fixture containers or Java/Nitro/Playwright descendants remained. The artifact hash and bounded assertions are recorded in `deployed-java-acceptance.md`; this does not verify that artifact against newer subscription source or certify release readiness. Existing typecheck/coverage/boundary receipts are retained there without claiming a fresh rerun.

## Historical verification

Updated 2026-10-25. Includes a limited HTTP smoke test against the existing local Java JAR, in addition to the earlier contract-server and preview checks. This does **not** certify production readiness. Older evidence below retains its original dates, assumptions, and artifact names.

Post-verification cleanup: frontend commits were pushed to `origin/frontend-app`; all backend source was pushed to `origin/backend-updates` (`a12eaff`, parent original `main` `551db79`). Remote refs were checked before deleting the temporary `../backend-accounts` directory. Its Java process was terminated first, so the local backend is no longer running. The active `sidie` checkout and protected snapshot were not changed; the backend branch is not merged into main. The test results below remain historical executed evidence, not a claim that the removed runtime still exists.

## Password-based local accounts and seeds (2026-10-25)

Acceptance: an opt-in local backend seeds a regular user and business administrator without duplicating or altering existing accounts; the sign-in page discovers accounts from Java and selecting one still requires its password. New enabled/unlocked accounts appear without frontend roster edits. The active `sidie` checkout and snapshots stay untouched.

### Isolation, security and regression evidence

- Backend implementation is in the independent `../backend-accounts` repository, branch `local-account-picker`, snapshotted from the current uncommitted backend tree. It was not rebuilt from stale backend HEAD and was not merged back into `sidie`. Device/shared code was not edited.
- Backend RED: focused seed/policy tests could not compile because `LocalAccountSeeder` did not exist (`46c7df5`). Initial GREEN: five policy/seed/login/directory checks passed (`7629ae7`).
- A further executed regression exposed case-sensitive seed duplication: expected 2 users, got 3 after uppercasing an existing email (`f63e58e`). Case-insensitive existence checks fixed it; the full 494-test package passed (`144bd05`). Other checks cover concurrent empty-database seeds, repeated seeds, preservation of hashes/profiles/disabled flags, dynamic directory rows, locked-user exclusion, and remote-peer denial.
- Frontend RED: `pnpm test tests/unit/local-accounts.test.ts` could not resolve the unimplemented helper (`e5de421`). Initial GREEN: 3 unit tests, types and boundary checks (`20d105e`). Expanded selected-source coverage includes the directory helper/domain/client adapter, not the entire Vue UI.
- Real-Java browser smoke initially failed: immediately after valid login, the alerts repository's unbound `:sortBy` caused an exception; error dispatch surfaced it as 401 and Wuu correctly required reauthentication. An executed MockMvc regression reproduced the exception (`390d2fa`). Removed the unbound CASE ordering; the existing service-supplied Pageable owns sorting. This narrow backend fix is included only in the isolated implementation. Error-dispatch status masking in general is not redesigned here.
- The full backend run first exposed missing policy wiring in a manually assembled security test context, then a strict registration mock that did not model the new role existence check. Both test configurations were corrected rather than making production checks lenient.
- The local directory is off by default, requires explicit local opt-in and backend profile/binding/peer checks, and returns only email/display name. Wuu independently checks its flag plus loopback Host/socket/upstream. No credentials or tokens are returned, auto-filled or embedded in HTML. Account clicks clear/focus the password field; standard login/session/Origin guards and backend permissions remain authoritative.
- Generated runtime files are ignored and private: `.data/local-accounts/credentials.json`, isolated backend `.env`, and frontend `.env.live.local`. Do not publish them. Java's real-browser suite has trace/video/screenshots disabled and leaves one named additional local verification account in the isolated database.

### Final executed checks

| Check                                                     | Outcome                                                                                                                                                                                                                                       |
| --------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `./mvnw -B package` in `../backend-accounts`              | **495 tests PASS**, zero failures/errors/skips; executable JAR produced                                                                                                                                                                       |
| `pnpm test:coverage`                                      | **82 tests / 14 files PASS**; selected-source **96.17% lines/statements, 84.48% branches, 95.74% functions**                                                                                                                                  |
| `pnpm typecheck`                                          | PASS on rerun; an earlier concurrent build/typecheck attempt exceeded its 120-second tool timeout                                                                                                                                             |
| `pnpm check:boundaries`                                   | PASS                                                                                                                                                                                                                                          |
| `pnpm test:e2e:auth`                                      | Build PASS; **15/15 Chromium tests PASS** against the test-only backend, including dynamic picker, required password, manual-login fallback, nonlocal Host denial and mobile/dark accessibility                                               |
| `PLAYWRIGHT_BASE_URL=http://127.0.0.1:3002 pnpm test:e2e` | **30/30 Chromium preview tests PASS** against the same production output; temporary preview server stopped                                                                                                                                    |
| `pnpm test:e2e:java`                                      | **1/1 PASS** against the freshly built isolated Java JAR and live Wuu: both seeds log in, `/session` verifies VIEWER/TENANT_ADMIN, logout works, a new user is created/reused via authenticated user API, and its picker/password login works |

`pnpm format:check` and `git diff --check` in both repositories: PASS. The first full formatting check found one unit-test file; it was formatted and the full check rerun successfully. Final listener check confirmed Wuu on 3001, Java on 8080, and no listener on 3000.

Default-off frontend smoke on the preview runtime returned exactly `{enabled:false,accounts:[]}`. Wuu is configured on loopback port 3001; no Wuu listener was started on 3000 for this verification. Port 3000 was stopped at the user's request. Backend auth/read smoke uses file-backed H2, disposable Redis on 16379, and disabled JMS listeners. Artemis health/full event flows, PostgreSQL concurrency, non-Chromium devices, whole-UI coverage, broad tenant-isolation/permission certification, and production deployment remain unverified.

## Local Java URL discovery and HTTP smoke (2026-10-25)

- Backend location is `../sidie` relative to this frontend. No rename, source edits, or backend build were performed.
- `application.yaml` has no server-port/context-path override: the local origin is `http://127.0.0.1:8080`, login is `/api/v1/auth/login`, and identity is `/api/v1/users/me`. The frontend origin setting must not include `/api/v1`; some resource paths use other prefixes.
- Started the existing `target/tracking-engine-1.0.0-SNAPSHOT.jar` (dated October 23) with Java 21, loopback HTTP binding, a separate file-backed H2 database and disposable loopback Redis on port 16379. JMS listeners were disabled for this limited test. Configuration and all three migration resources match the checkout; compiled Java freshness was not established.
- Created a generated local test account in that isolated database, then restarted with registration disabled. Credentials and session/JWT secrets were generated locally without printing them. Frontend settings are in ignored, mode-0600 `.env.live.local`; runtime state is under private `/tmp/opencode/wuu-java-i_8i0_yc`.
- Using Python HTTP requests through the production Wuu server on port 3001: anonymous vehicle gateway **401**; login **200**, no bearer token in response JSON and an HttpOnly `wuu_session` cookie; session identity **200**; vehicles **200**, trips **200**, alerts **401**, geofences **200**; logout **204**; subsequent anonymous vehicle gateway **401**. These are status-level HTTP checks, not browser rendering, domain-schema, tenant-isolation, per-role, expiry, or account-wide revocation verification.
- `/actuator/health` returned **503** with a JMS/Artemis connection failure. Alerts' authenticated **401** remains unresolved. Do not treat successful reads as evidence of complete permission enforcement or a fully healthy backend.
- Only local configuration/documentation changed. Prior package/browser/build outcomes below were not rerun for this discovery slice; Markdown formatting was checked separately.

## Authenticated live sessions — latest slice (2026-10-23)

Acceptance: connect the hardened backend auth contract without exposing bearer tokens to JavaScript, keep preview public and isolated, require authenticated live reads, and preserve errors for denied/expired requests.

### Contract inspection and boundaries

- Read-only current backend inspection: HEAD `551db79`, with an uncommitted refactor/hardening under `src/main/java/com/surf/tracker/`. Inspected `SECURITY.md`, auth/user DTOs/controllers, `AuthService`, `PermissionService`, and `SecurityConfig`. No backend files were changed or built.
- Backend uses snake_case JSON; the data transport now normalizes nested keys before validation and rejects ambiguous duplicate spellings.
- Login validates bounded JSON and exact Origin/Host, obtains a token, verifies `/users/me`, and returns only validated identity. Access tokens and expiry are AES-256-GCM sealed in HttpOnly SameSite=Strict cookies, Secure outside loopback. Deployment supplies at least 32 characters of random secret material. Legacy plaintext-token cookies and shared bearer-token fallbacks no longer grant access.
- Backend logout revokes all account sessions. The local cookie clears even on upstream failure; the UI reports failed revocation instead of claiming success. No refresh token is stored: sessions expire with the access token and require sign-in. This avoids unsafe parallel rotation of single-use refresh tokens; coordinated refresh remains future work.
- Live route guards and authenticated GET-only gateway are implemented. Live admin stays blocked without an effective-capabilities contract, regardless of role names. Protected live HTML is private/no-store. Auth identity initializes before query composition, and login/logout hard navigation resets old caches.

### TDD evidence

| Guarantee                                                                                                                                                            | RED                                                                                                                                                | GREEN                                                                                        |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Credentials/identity validation, local return paths, sealed/tamper-proof/expired sessions, same-origin policy, sanitized upstream failures, snake_case compatibility | `pnpm test tests/unit/auth.test.ts` failed because the referenced auth implementation did not exist; checkpoint `fb8492c`                          | Same seven tests passed; implementation checkpoint `dd50da3`                                 |
| Permitted fleet remains visible when trips return 403                                                                                                                | `pnpm exec playwright test --config playwright.auth.config.ts --grep 'denied trip'` failed: expected live vehicle was hidden; checkpoint `54ff2af` | Per-domain query-state isolation; all nine auth browser scenarios pass; checkpoint `0bb9fbf` |

### Verification outcomes

- `pnpm typecheck`: PASS.
- `pnpm test:coverage`: **77/77 tests, 13 files PASS**. Selected-source coverage **96% lines/statements, 83.78% branches, 95.62% functions**. This includes pure auth/session/backend helpers, not whole-UI or complete HTTP-handler coverage.
- `pnpm check:boundaries`: PASS. `pnpm format:check`: PASS. Production build: PASS.
- `pnpm test:e2e:auth`: **11/11 PASS in 33.5 seconds** after a fresh production build. Covers anonymous redirects/401, rejected legacy cookies, cross-origin denial, login/identity, safe return paths, no tokens in login JSON/HTML/cookie plaintext/document cookies/localStorage, SSR reload, live admin denial despite admin role names, blocked writes, invalid credentials/rate limits, independent domain permissions, denied reads without fixtures, backend revocation, identity-service failure, failed-revocation warning/local sign-out, tampered cookies, invalid/oversized bodies, active-read expiry, private HTML headers, mobile/dark layouts, and automated accessibility.
- Final `pnpm test:e2e`: **30/30 PASS in 3.9 minutes**. First run was **27/30**: two map tests depended on unavailable external tiles, and a geofence test's generic alert selector collided with the tile-error alert. Tile-engine tests now use explicitly labeled deterministic images; provider checks remain available with `WUU_TEST_LIVE_TILES=1`. A later run exposed the same ambiguous selector in another geofence assertion (**29/30**); both assertions now target the form-error summary. This is not evidence of current public-provider availability.
- One earlier auth run failed because a test tried reading a response after hard navigation; the test now captures it before navigation. Another test entered SSR form fields before hydration; auth helpers now wait on `data-workspace-ready`. No app retry was added to hide these test issues.

### Not verified / follow-up

No Java server was listening and no deployment credentials were supplied. The auth browser server is a clearly test-only fixture on port 18080; these results are not an authenticated Java smoke test. Real deployment must verify current source/build compatibility, permissions/tenant isolation, cookie/TLS/Host handling, shared ingress rate limits, revocation, and secret management. Automatic refresh, public registration/recovery, effective-permissions UI, live writes, and telemetry remain outside this slice.

## Live read-only integration and fail-closed runtime — latest slice (2026-10-22)

Acceptance: add a typed live path without rewriting the UI, keep preview isolated, keep the UI HTTP-free, and ensure failed live requests never become sample data.

### Implementation evidence

- Added domain service interfaces and separate preview/live adapters for fleet, trips, alerts, geofences, and preferences.
- Added a shared Zod gateway client with timeout/cancellation handling, HTTP mapping, contract validation, request IDs, and corrected `204` handling. An incompatible `204` is now a contract error rather than a server error.
- Added runtime origin/token resolution and an allowlisted Nitro proxy at `server/api/_wuu/[...path].ts`.
- Extracted the gateway path/origin policy to `server/utils/wuu-gateway.ts` so traversal, encoded separators, unsafe origins, and method/path policy are unit-tested.
- Added preview/live service composition, Vue Query keys including data mode/context, preview cache seeding, live client-only queries, and aggregate async/error/retry states.
- Moved pure API-error helpers to `core/domain/errors.ts`; UI-bound error display no longer imports an API-adapter module. `pnpm check:boundaries` passes.
- Added a live team fail-closed state, live-safe identity/footer/date copy, UTC deterministic timestamps, and explicit disabled-action messaging.
- Fixed the vehicle-create field error slot so validation is visible and accessible; that browser case passes.

### Unit and static checks

- `pnpm test:coverage`: **70/70 PASS across 12 files**. Selected-source coverage: **95.62% lines/statements, 82.24% branches, 95.16% functions**. The configured scope includes pure domain/policy/adapters, live contracts/mappers, the gateway, service composition, the BFF policy, map/search, tracking, CSV, and preview state; it does not measure Vue templates or the entire UI.
- New focused tests cover:
  - gateway cancellation, timeout, abort, network failure, HTTP mapping, invalid JSON/contracts, paged data, and `204` handling;
  - BFF allowlisting, traversal/encoded-separator rejection, HTTPS/local-origin policy, credentials/query/fragment rejection, and malformed paths;
  - live vehicle/trip/alert/geofence mapping, null/missing-field handling, paging, detail 404s, and guarded unsupported mutations;
  - preview isolation, service composition, defensive copies, and duplicate/field validation;
  - deterministic UTC display and data-mode/context defaults.
- `pnpm typecheck`: PASS.
- `pnpm check:boundaries`: PASS.
- `pnpm format:check`: PASS.
- `pnpm build`: PASS.

### Browser and runtime checks

- Final `pnpm test:e2e` against a fresh production build: **30/30 Chromium tests PASS in 2.8 minutes**. The default Playwright command now builds and owns a production server; `PLAYWRIGHT_BASE_URL` can still target an already-running compatible server. The suite includes branding, fleet/filter/search, creation validation, alert transitions, trip/downloads, settings, detail/reload/missing routes, geofences/editor, real maps/clustering/tile recovery, tracking/replay, mobile/dark layouts, and automated light/dark accessibility checks.
- Dev-server attempts exposed Vite's on-demand dependency reload rather than an application failure: the first full run reached 28/30 (two readiness checks hit the 5-second default during a reload), and a 15-second readiness retry reached 26/30 as Nuxt UI's transitive dependencies triggered further full reloads. Prebundling the application's four heavy dependencies reduced but did not eliminate this dev-only behavior. Rather than mask the reload, `pnpm test:e2e` now builds and runs the production artifact, where dependency optimization does not occur. The 30/30 production result above is the release-relevant evidence.
- Live configuration test: rebuilt production server on port **3001** with `NUXT_PUBLIC_DATA_MODE=live` and **no** `WUU_API_ORIGIN`.
  - `GET /api/_wuu/vehicles` → **503**, `{"message":"The live API is not configured on this server.","requestId":"…"}`.
  - Home displayed “We couldn’t load your workspace” with that error; no `Transit 01`, `Transit 02`, or `Alex Carter` text appeared.
  - Map displayed “Map data unavailable”; no preview vehicle appeared.
  - Team hid all sample members and displayed the explicit live contract gap.
  - Sidebar/footer used live-mode copy, not preview identity or “connected” claims.
  - Screenshots: `/tmp/opencode/wuu-live-home.png`, `/tmp/opencode/wuu-live-map.png`, `/tmp/opencode/wuu-live-team.png` (local verification artifacts, not committed).

### Limits

- No authenticated request was sent to the real backend. The live path is verified fail-closed and through adapter/gateway unit tests, not through a running upstream.
- No login, session issuance, CSRF, effective-permission resolution, tenant isolation, or route protection is implemented. `/admin` remains an unprotected concept page.
- Live mutations are not enabled: vehicle creation, alert transitions, geofence create/edit, team administration, and preferences.
- Map tiles and interactions are Chromium-tested; no real-device, non-Chromium, screen-reader, large-fleet, or provider-capacity test was performed.
- The BFF tests cover policy helpers; no live upstream proxy/redirect/header integration test was run.
- CSP, production secrets management, dependency audit, and deployment hardening remain open.

Self-assessment: accuracy **4/5** (actual fail-closed paths and tests pass; real-backend behavior unproven), completeness **3/5** (read-only integration and safety gates delivered; authentication/mutations remain blocked), clarity **4/5** (mode/action limits are visible and documented), actionability **4/5** (runnable preview and configurable live start; provider/session work remains), conciseness **4/5** (one service composition and gateway; docs are longer because release gates are explicit). Overall **3.8/5**. Highest-impact next step: an approved backend session/authorization slice, not more preview screens.

## Commands and results

### Wuu rename and supplied logo — previous slice (2026-10-22)

- User request: rename the app to Wuu and edit/resize/convert/use the logo in `public/`. Inspection found only the supplied blue mark in `photo_2026-10-21_20-47-14.jpg`, not a PNG. The original remains unchanged; SHA-256 before and after: `0c1436e1529fff27793d5193bf88f2d80a984dded4e25f33a3261f354782078d`.
- `tools/prepare-brand.py` uses installed Pillow 10.2.0 to remove the white JPEG matte, suppress fringe noise, crop/pad without changing aspect ratio, and generate transparent PNG/lossless WebP plus platform icons. No runtime dependency or backend change. Sidebar logo: 44px display, 96/192px responsive sources. WebP payloads: **6,608 bytes / 17,468 bytes**. Master is 512px; ICO contains 16/32/48px, PNG favicon is 32px, Apple icon is 180px, manifest icons are 192/512px.
- Updated UI wordmark/footer/platform copy, document titles, application metadata, package name, preview depot label, README/design/scope/progress docs, and CSV filename. Existing teal interface styling and internal `--wayline-*` token identifiers are intentionally retained; these are not user-visible branding. Historical screenshot filenames below are not renamed.
- RED `3d51c78`: both new branding unit tests failed for missing assets and the old package name. GREEN asset checkpoint `969644c`: generated assets/config/package checks pass. These focused commits do not discard the existing uncommitted feature work.
- `pnpm test:coverage`: **22/22 PASS**, including two asset/metadata tests. Existing configured pure-file coverage remains **99.04% lines/statements, 95.56% branches, 96.77% functions**; it does not measure image-processing code or the entire UI.
- `pnpm typecheck`, `pnpm build`, `pnpm check:boundaries`, `pnpm format:check`, and `git diff --check`: **PASS**.
- `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e`: **23/23 PASS in 2.2 minutes**. New browser target: Wuu page titles, loaded supplied logo, business/admin navigation, dark theme, 375px navigation without overflow, manifest identity and icon HTTP responses. Existing download test verifies `wuu-preview-trips.csv`.
- Visually inspected `test-results/wuu-desktop-dark.png` and `test-results/wuu-mobile-logo.png`: readable wordmark and clean transparent logo on dark surfaces. Existing full suite also passed light-theme and automated accessibility checks. No committed visual-regression baseline or native iOS/home-screen/favicon-cache validation.
- Ports 3000/3001 were checked and active listeners stopped before edits. Preview is restarted only for testing/delivery. The supplied JPEG is left untouched; generated outputs are separate files. No SVG was fabricated from the raster; a real vector source would be needed for a true scalable master. No service worker/offline support is claimed.

Self-assessment: accuracy **4/5** (asset dimensions, headers, browser loading and original hash verified; cleanup remains JPEG-derived), completeness **4/5** (UI/metadata/download identity and web/platform formats covered; native launcher appearance untested), clarity **4/5** (original source and regeneration documented; legacy internal CSS names remain), actionability **4/5** (working app and generator; regeneration requires Pillow), conciseness **4/5** (single generator and existing layout; no extra branding framework). Overall **4.0/5**. Follow-ups: supplied vector/higher-resolution source, native-device icon checks, then visual-regression baselines.

### Simulated tracking and trip replay — previous slice

User journeys/acceptance: `detail-pages-plan.md`, tracking movement and trip replay. Frontend-only, no new dependencies; active backend inspected read-only. Ports 3000/3001 were checked and active preview listeners stopped before each edit batch, per user instruction.

- RED checkpoint `c530b40`: new unit target failed because `features/tracking/domain/playback` did not exist. GREEN core checkpoint `e435358`: the same target passed **5 tests**. Checkpoints are on `frontend-app`; unrelated prior geofence work was not discarded or included in those focused commits.
- `pnpm test:coverage`: **20/20 unit tests PASS**; **99.04% lines/statements, 95.56% branches, 96.77% functions** in the configured pure-file scope. New tracking domain/preview-track modules each have 100% coverage. Vue/composable lifecycle code remains outside unit coverage.
- `pnpm typecheck`, `pnpm build`, `pnpm check:boundaries`: PASS on final source. `pnpm format:check` and `git diff --check` passed during verification.
- Final rebuilt production run: `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e` — **22/22 PASS in 1.8 minutes**.

| Guarantee                                                                                                                    | Target                        | Evidence                        |
| ---------------------------------------------------------------------------------------------------------------------------- | ----------------------------- | ------------------------------- |
| Interpolation, endpoint clamping, detached trails, bounded/looping clock math and unknown-trip fail-closed behavior          | `tests/unit/playback.test.ts` | 5 tests PASS                    |
| Only moving fixtures move; pause is stable; selected camera follow, speed/filter/reset controls work                         | `tests/e2e/tracking.spec.ts`  | Tracking browser case PASS      |
| Replay plays/pauses/seeks, stops at end, restarts/reloads paused, and pauses on visibility event                             | `tests/e2e/tracking.spec.ts`  | Replay browser case PASS        |
| Reduced-motion preference gets no auto-play; 375px layouts and automated mobile/light plus desktop/dark accessibility checks | `tests/e2e/tracking.spec.ts`  | Accessibility browser case PASS |
| Existing geofence, alert, fleet, export, search, details and theme workflows remain working                                  | Full browser suite            | 22/22 PASS                      |

Early failures were corrected rather than skipped: SSR/client CSS serialization made raw style-string comparisons unsuitable, so position assertions now compare normalized coordinates. A new map detail link required the existing vehicle-detail test to scope its link to the vehicle panel. Mobile screenshot review caught a status span inheriting decorative badge-dot styling; it now has its own wrapping-safe status style. The full suite passed again afterward.

Visually inspected artifacts: `test-results/tracking-mobile.png`, `test-results/replay-mobile.png`, `test-results/trip-replay-dark.png`. No committed visual baseline; these are current-state checks, not automated visual-regression comparisons. A targeted scan found no raw HTML rendering, debug logs, or obvious credential patterns in the new feature; no dependency audit was performed.

Limits: this is **not live tracking or recorded GPS history**. Dedicated sample tracks use illustrated-canvas percentages and invented speeds. They do not write to workspace records or exported aggregates. Hidden-tab pausing is browser-tested via a dispatched visibility event, not OS-level backgrounding. Manual screen-reader testing, real touch devices, browser-engine diversity, production stream reconnection/staleness/gaps, GPS accuracy and backend authorization remain unverified.

Self-assessment: accuracy **4/5** (passing checks; intentionally approximate/invented paths), completeness **4/5** (preview movement/replay controls delivered; real GPS integration remains gated), clarity **4/5** (visible simulation labels; no geographic accuracy), actionability **4/5** (working map and trip-detail controls; opt-in playback required), conciseness **4/5** (shared map/clock/domain core; component controls could be further consolidated). Overall **4.0/5**. Highest-value follow-ups: real-device/assistive-technology checks, dedicated composable lifecycle tests, then separately authorized telemetry/history contracts. This assessment describes a sample-data preview, not a live tracking release.

### Geofence creation and editing — previous slice

- RED checkpoint `17a55a1`: geometry/editor acceptance tests added first; unit target failed because the geofence domain module did not exist.
- `pnpm typecheck`, `pnpm check:boundaries`, and `pnpm build`: PASS.
- `pnpm format:check` and `git diff --check`: PASS. Targeted source scan found no raw HTML rendering, debug logs, or obvious credential patterns in the geofence feature; this is not a dependency/security audit.
- `pnpm test:coverage`: **15 unit tests PASS**; configured pure domain/policy/adapter/CSV scope: **98.62% lines/statements, 94.3% branches, 96% functions**. Vue templates and composables are outside this measurement.
- `PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e`: **19 tests PASS in 1.5 minutes** against the rebuilt production bundle. All original workflows remain green.
- Restarted the primary preview at `http://localhost:3000`; editor suite rerun there: **4/4 PASS in 19.8 seconds**.
- New browser coverage: create/save/reload reset, edit/save, polygon drawing and undo, pointer and keyboard vertex movement, pan without geometry mutation, zoom/fit, duplicate/radius validation, cancel/keep/discard, internal-navigation guard, missing edit IDs, 375px/light and 1440px/dark editor layout/accessibility checks.
- Browser failures corrected before the final full run: exact accessible name for the required name field; exact-match vertex locator ("Move" also matched "Remove"); mobile global-search button lacked a name when its text was hidden.
- Visually inspected desktop/mobile screenshots: `test-results/geofence-editor-inspect-{desktop,mobile}.png`; final automated screenshots: `test-results/geofence-editor-{375,1440}.png`.
- Self-review: saved state remains adapter-owned, drafts are copied, geometry validation runs again inside the adapter, map viewport changes never write geometry, and text uses Vue interpolation. No HTTP integration, backend changes, or new dependencies. The unrelated untracked photograph remains untouched.
- Limits: Chromium only; real touch-device gestures, native beforeunload dialogs, manual screen-reader use, injected adapter failures, and real GIS precision were not verified. No server persistence, actual monitoring, or notification delivery is claimed.
- Delivery self-assessment: accuracy **4/5** (build and tests verified; projection is approximate); completeness **4/5** (requested preview workflows delivered; touch-device behavior remains unverified); clarity **4/5** (session-only/map limitations are visible; coordinate editing is still technical); actionability **4/5** (working create/edit routes; changes intentionally reset); conciseness **4/5** (shared map and workflow modules; numeric field markup could be consolidated later). Overall **4.0/5**. Highest-value improvements: real-device gesture/assistive-technology checks, injected save-failure coverage, then separately scoped live-contract integration. These limits should match the user's experience of a polished preview, not production GIS software.

### Detail routes and scrollbars — previous slice

Source plan: `detail-pages-plan.md`, slice 1. No backend changes or new dependencies.

- RED checkpoint `9f40321`: new unit target failed because the shared trip CSV module did not exist. The first attempted browser RED timed out navigating to a missing route on the older preview; it is not counted as valid behavior-failure evidence.
- GREEN: `pnpm test:coverage` passed **10 unit tests**, with 100% lines/statements/functions/branches across the explicitly configured domain/policy/adapter/CSV scope. UI/composable coverage is not included in that percentage.
- `pnpm typecheck` passed (an initial run timed out during type preparation; a longer rerun and the final rerun passed).
- `pnpm check:boundaries`, `pnpm format:check`, and `pnpm build` passed. The build emitted plugin-timing warnings, not compilation errors.
- `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e`: **15 tests passed in 1.4 minutes** against the rebuilt production preview, including all nine original tests.
- The new detail suite checks list-to-detail navigation, alert actions and vehicle links, trip-to-vehicle-to-trip navigation, direct reload, single-record download content, geofence membership disclosure/links, missing-record recovery, and keyboard horizontal scrolling.
- Responsive checks cover three new routes × light/dark × 375/768/1440px = **18 combinations**, with no document-width overflow. Automated WCAG A/AA scans passed for the three routes in both themes at desktop size.
- A real pointer-interception failure in the first browser run exposed an uncontained geofence illustration pseudo-element. Its positioning/overflow styles were made shared before the unchanged tests passed. Mobile detail headers and the three-stat trip grid were also refined after screenshot inspection.
- Additional Chromium inspection with hidden-scrollbar defaults disabled confirmed a 12px rail, 999px thumb radius, distinct light/dark thumb colors, system-color forced-colors fallback, and no browser page errors. Artifacts: `test-results/scrollbars-light.png`, `test-results/scrollbars-dark.png`, and `test-results/details-{alerts,trips,geofences}-{light,dark}-{375,768,1440}.png`.
- Firefox and OS-specific overlay scrollbar behavior were not browser-tested. Native scrolling remains intact; browser/OS settings may hide rails until scrolling.

Code review: new routes use the existing workspace composable and stable IDs; no HTTP or adapter imports in UI, no raw HTML rendering, no fabricated route history, and no unsupported geofence mutations. CSV escaping and spreadsheet-formula neutralization are unit-tested. Export failures show a toast; alert-update failures show an inline error, but those injected failure paths were not browser-tested.

Self-review: accuracy 4/5 (actual checks above), completeness 4/5 (three detail types delivered; remaining types explicitly planned), clarity 4/5 (preview labels and recovery routes), actionability 4/5 (working links/actions and current server), conciseness 4/5 (shared CSV workflow; focused plan). Remaining gaps are real integration, cross-browser checks, and manual assistive-technology testing.

### Initial preview evidence

Restart recovery: restored the production preview with `pnpm preview --port 3000`. The first browser run began before the listener was ready (one connection-refused failure; eight passed). After startup, the full unchanged suite passed: **9/9 in 44.7 seconds**, using `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e`.

| Check                            | Actual command                                            | Result                                                                                |
| -------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| TypeScript / Vue templates       | `pnpm typecheck`                                          | PASS                                                                                  |
| Domain, policy, preview adapter  | `pnpm test:coverage`                                      | 8 tests PASS; 100% lines, statements, functions, and branches in the configured scope |
| UI/API separation                | `pnpm check:boundaries`                                   | PASS; no HTTP calls/direct API imports in Vue UI, no forbidden surface classes        |
| Production bundle                | `pnpm build`                                              | PASS; Nuxt 4.6.2, Vue 3.5.44                                                          |
| Browser journeys + accessibility | `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e` | 9 tests PASS against the production build; 39.9 seconds                               |

Browser: existing cached Chromium `chromium-1243`, discovered without a successful browser download. Playwright's exact-version browser download was initially unavailable; configuration now supports existing system/cached builds and an explicit executable override. No OS/browser packages were installed globally.

Final hygiene checks: `pnpm format:check` and `git diff --check` both passed. Read-only backend status showed ongoing changes beyond the initial snapshot; those changes were left untouched, not reverted or treated as frontend work.

## User-facing guarantees covered

| Guarantee                                                                                                                          | Test target                    | Evidence                                           |
| ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ | -------------------------------------------------- |
| Search/filter preserves original fleet; counts derive from records                                                                 | `tests/unit/workspace.test.ts` | Unit PASS                                          |
| Required fields, registration shape/length, name length, duplicate plates validated                                                | Same                           | Unit PASS                                          |
| Unknown roles deny access; organization templates lack platform access; platform template contains seeded organization permissions | Same                           | Unit PASS; descriptive UI policy only              |
| Preview sessions are isolated; returned data cannot mutate adapter internals                                                       | Same                           | Unit PASS                                          |
| Alert states update; unknown alert IDs reject                                                                                      | Same                           | Unit PASS                                          |
| Overview filters, marker selection, zoom/reset, legend visibility work                                                             | `tests/e2e/workspace.spec.ts`  | Browser PASS                                       |
| Add vehicle validates and resets after reload                                                                                      | Same                           | Browser PASS                                       |
| Acknowledgment/resolution update alert views and overview counts                                                                   | Same                           | Browser PASS                                       |
| Global search navigates; unknown vehicles show a helpful state                                                                     | Same                           | Browser PASS                                       |
| Trip CSV contains only filtered preview records                                                                                    | Same                           | Browser PASS; actual downloaded contents inspected |
| Preferences save in the preview; routes render                                                                                     | Same                           | Browser PASS                                       |
| Mobile menu, filters, dark theme, modal contrast, Escape/focus return work                                                         | Same                           | Browser PASS                                       |
| Automated WCAG A/AA checks across 11 key routes, dark modal, mobile navigation                                                     | Same                           | No automated violations detected in tested states  |

An additional Chromium sweep checked **36 route/viewport combinations**: 12 routes at 375px, 768px, and 1440px. No document-width overflow, non-200 page response, or browser page errors were detected. Tables scroll inside their containers instead of widening the page.

## Visual artifacts

Locally generated, ignored files:

- `test-results/wayline-1440.png` — default desktop overview.
- `test-results/wayline-768.png` — tablet overview.
- `test-results/wayline-375.png` — small-screen overview.
- `test-results/overview-dark.png` — dark overview.
- `test-results/add-vehicle-dark.png` — dark modal.

Desktop/mobile/dark screenshots were visually inspected during implementation. There is no committed visual baseline: **automated visual regression comparison is INCONCLUSIVE**, not PASS. These are current-state screenshots, not a historical comparison.

## RED → GREEN evidence

Initial acceptance tests failed because the fleet domain, access policy, and preview adapter did not exist. Root checkpoint: `15bf8a9`. Implementation made those tests pass. A later regression case exposed incomplete SUPER_ADMIN presentation permissions (`users.read` missing); the explicit template was corrected, and all 8 unit tests passed.

Browser REDs also found early-interaction/hydration races and a mobile overflow from an absolutely positioned table label. Fixes: deterministic preview cache seeding, an explicit hydration-ready boundary, a positioned table scroll container, and readiness-aware browser tests. A notification icon selector and map legend behavior were corrected during UI review. Formatter-induced multi-statement template parsing errors were fixed by moving actions into named handlers; production build and Vue typecheck passed afterward.

## Security and coverage limits

- All data is fictional. No secrets, tokens, API credentials, real fleet information, or backend mutations are involved.
- No login, production RBAC, tenant boundary, session revocation, or safe remote-command implementation is claimed.
- User-entered vehicle names are rendered with Vue text interpolation, not raw HTML. Registration/name validation is local preview validation, not a server trust boundary.
- Basic response headers deny framing, disable MIME sniffing, set referrer policy, and disable camera/microphone/geolocation. Production CSP/session/CSRF controls remain part of the authenticated integration slice.
- Unit coverage excludes Vue templates, framework internals, and runtime API integrations; the reported 100% is **not whole-application coverage**.
- Automated accessibility checks do not replace manual screen-reader, full keyboard, browser/OS, or usability review. Only Chromium was exercised.
- Core Web Vitals, load/performance benchmarks, dependency vulnerability audit, and real backend readiness were not measured.

## Delivery assessment

Preview UX is verified; the production integration remains a separate workstream.

| Axis          | Score | Evidence / improvement                                                                                                           |
| ------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------- |
| Accuracy      | 4/5   | Passing build/tests and explicit limits; live contracts are inspected, not integration-tested                                    |
| Completeness  | 3/5   | Broad preview UI delivered; authenticated backend workflows remain unfinished and require the separately gated integration slice |
| Clarity       | 4/5   | Preview versus real capability is visible; future docs should stay short as contracts mature                                     |
| Actionability | 4/5   | App runs immediately with pnpm and sample data; real usage still depends on backend security fixes                               |
| Conciseness   | 4/5   | Agent entrypoint is brief with scoped document pointers; canonical docs intentionally retain integration evidence                |

Overall: 3.8/5. Highest-impact follow-up: verified effective-permission/tenant contracts, then secure sessions and a read-only fleet adapter. That gap cannot safely be closed by changing this frontend alone. A production user should not mistake this preview assessment for release approval.

# Real geographic rendering — 2026-10-22

Supersedes the illustrated-renderer limitation recorded in the earlier map slice below. User requirement: mock fleet data is acceptable; actual map rendering, production-quality interactions and marker highlighting are mandatory.

## TDD evidence and guarantees

| Guarantee                                                                           | Test/evidence                                             | Actual result                                                                  |
| ----------------------------------------------------------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Coordinates are geographic, nullable and validated; tile config is HTTPS/attributed | `tests/unit/geographic-map.test.ts`                       | RED missing module (`80b9cc0`); GREEN contract (`36ead5c`)                     |
| Boundaries work outside San Francisco; area is measured in square meters            | Same suite                                                | RED London rejected (`084be62`); GREEN spherical/global validation (`7b74482`) |
| Missing vehicle positions stay missing                                              | `tests/unit/map-search-provider.test.ts`, preview adapter | PASS; newly added vehicles have no invented position                           |
| Geographic tiles actually load and provider attribution is visible                  | `tests/e2e/geographic-map.spec.ts`                        | PASS against real OpenStreetMap tiles, not intercepted success fixtures        |
| Tile failure is visible and retry restores real tiles                               | Same suite                                                | PASS with blocked provider requests followed by actual provider recovery       |
| Selected markers/list/details agree; low-zoom clusters expand                       | Same suite                                                | PASS on desktop and mobile                                                     |
| Map gestures, search, follow release, layers and geofence viewport isolation        | `tests/e2e/maps.spec.ts`                                  | PASS, including Chromium native touch-event pinch                              |
| Existing editing, trip replay and workspace flows remain operational                | Full browser suite                                        | PASS                                                                           |

## Final outcomes

- `pnpm test:coverage`: **30 tests passed**, 8 files. Selected-file coverage **99.30% lines/statements, 96.07% branches, 97.56% functions**. Four obsolete illustrated-camera tests were removed with that implementation; geographic, global-boundary and missing-position coverage replaced it. This is not whole-UI/engine coverage.
- `pnpm typecheck`, `pnpm check:boundaries`, `pnpm format:check`, `git diff --check`: passed.
- `pnpm build`: passed. The harness restart cancelled one final verification attempt and left an empty generated server entry; rebuilding restored the artifact successfully. No implementation work was lost.
- Final `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e`: **30/30 Chromium tests passed in 2.2 minutes** against the rebuilt production server.
- Inspected `test-results/geographic-map-desktop.png` (fully loaded tiles) and `test-results/geographic-map-mobile.png` (real tiles, clustered assets, selection/details, attribution and responsive controls). Existing light/dark automated accessibility suites passed.
- Early browser runs caught a circle-fit lifecycle error and overlapping small-screen hit targets. Geographic circle fitting and native marker clustering corrected them. Compact-map tests now expand groups before selecting children instead of assuming unclustered marker DOM.

## Release limits

The renderer is real Leaflet/markercluster, not a geographic illustration. Mock positions and journeys remain explicitly fictional; no backend file or snapshot was modified. Runtime provider settings, public-tile no-SLA terms, privacy/attribution requirements, supported geographic limits and authenticated API gates are documented in `maps-integration.md`. Global address geocoding, live fleet transport, server-authoritative persistence/authorization and whole-application production release remain unimplemented. Real-device/manual screen-reader, non-Chromium, large-fleet performance and provider-specific deployment checks remain unverified.

## Delivery self-evaluation

Accuracy 4/5: real tile loading and interaction tests pass; non-Chromium/device behavior remains unverified. Completeness 3/5: the real-renderer requirement is delivered, but the broader production-ready-app objective still has explicit authentication, API and provider deployment gates. Clarity 4/5: provider/data-source distinctions and runtime settings are documented; historical preview notes remain below as history. Actionability 4/5: runnable app and deployment variables are supplied, but production provider approval is still required. Conciseness 4/5: operational limits are explicit, although integration documentation is longer than a quick-start. Overall **3.8/5**. Highest-impact follow-ups: verified server authorization/session boundary, approved provider capacity/configuration, then cross-browser/device and fleet-scale measurement. User-agreement check: do not call the entire application production-ready merely because the maps and mock-data workflows pass.

# Earlier maneuverable illustrated-map slice — 2026-10-22 (superseded renderer)

- RED: new camera/search suite failed because the viewport/provider modules did not exist (`4226392`). GREEN: seven camera/provider/workflow tests passed (`838f893`).
- Final `pnpm typecheck`, `pnpm build`, `pnpm check:boundaries`, and `pnpm format:check`: passed.
- `pnpm test:coverage`: 29 tests passed; configured coverage 99.34% lines/statements, 95.93% branches, 97.82% functions. Coverage includes selected domain/policy/adapter files and the map-search workflow, not the whole UI or gesture composable.
- `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e`: all 27 Chromium tests passed in 2.1 minutes, including four map tests, touch-event pinch/pan, keyboard/wheel/drag/search/layers, follow release, geofence search isolation, mobile layout and automated light/dark accessibility checks.
- Inspected `test-results/maps-mobile.png`. Chromium touch emulation is not real-device verification; manual screen-reader, non-Chromium and actual touch-device checks remain unverified.
- Read-only backend DTO recheck and provider limitations: `maps-integration.md`. No backend edits, new dependencies, external map traffic or live API connections. Illustrated positions are not GPS. Geographic rendering, global geocoding, secure transport and subscription handling remain unimplemented.
