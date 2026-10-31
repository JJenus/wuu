# Progress and next slice

## INT-00 baseline reconciliation — 2026-10-31

Owner/startup coordination now follows workspace DELIVERY_PLAN.md and backend
PARALLEL_EXECUTION.md: independent top-level task claims, at most two bounded
helpers within one task, isolated worktrees and serialized final integration.
INT-00 uses int00-tracker-frontend/backend worktrees, preserving original dirty
app/ and sidie/ checkouts.

Reserved direct-detail and fail-closed list changes reconciled as8a89cd3, on the
existing b00c92c alert UI. Independent review found false-empty linked geofence
vehicles after incomplete/403/503 fleet reads. Browser RED3/3 reproduced
(47c350a); fix34d3740 preserves assignments from the geofence and independent
loading/error/retry state. Current **144 units /40 auth browsers/typecheck/build
PASS**; selected coverage94.74%line/86.05%branch/95.15%function. Review correction
has no remaining scoped medium/high finding. Full-stack and preview verification
for this combined clean baseline remain pending; do not infer production readiness.
Durable current logs: workspace .coordination/evidence/INT-00/.

Live alerts now acknowledge/resolve using the authenticated backend actor, with
notes and persisted attribution displayed in detail. Historical disabled-UI and
missing-independent-device-harness statements below are superseded by the
implementation, not by whole-product readiness. INT-00 still must accept the
combined source/artifact tuple before opening dependent UI tasks.

### Latest B0 execution evidence — 2026-10-30

Docker restored; actual-Java alert acceptance **1/1 PASS** after production
snake_case mapping correction (`cbdd54e`, RED `2ff125e`). Receipt
`/tmp/opencode/plan-b0-java2.log`, private `/tmp/opencode/wuu-java-AORcij/browser.log`.
Real acknowledge/resolve actor/notes persistence and tenant/Origin denials passed.
Follow-up136 units/33 auth browsers/typecheck/boundaries pass; selected
coverage94.62%line/85.71%branch/95.15%function. UI remains disabled pending B1.
Unified full-start/restart including independent device is still B0 work, not
established by the existing Java/Nitro harness. Earlier Docker-blocked statements
below are historical. Follow the pinned roadmap rather than rebuilding old slices.

## PINNED ROADMAP — 2026-10-30

Follow [delivery-plan.md](delivery-plan.md), which points to the canonical current
detailed production plan and existing backend inventory/queue. Every feature
addition/update must pass complete-stack FULL_START and its actual workflow,
not just tests/build. Immediate batch B0: local Docker is now restored; pending
Java alert acceptance and complete app cold-start/restart. Next B1: usable alerts,
then vehicle/tracker, geofences/rules, team, tracking/device, subscription and
notification completion, production operations and final independent audit.
Historical slice updates below are evidence, not a replacement execution order.

## Alert gateway boundary — 2026-10-30

Backend `77518dd` fixes authenticated alert audit actors. Frontend `ebe1e80`
allows only individual POST acknowledge/resolve with strict bounded bodies,
no query/actor/tenant selection, same-origin checks and existing sealed sessions.
UI remains disabled and the legacy adapter remains unchanged. **136 units /33
auth browser tests PASS**, selected coverage94.61%line/85.71%branch/95.15%function;
typecheck/build/boundaries/scoped formatting pass. Bounded security review found
no concrete medium/high defect. Actual-Java alert acceptance is **BLOCKED**:
Docker socket missing after restart. Added acceptance assertions have not run
against Java. See `alert-transitions.md` for RED/GREEN, receipts and resume command.
Preserve pre-existing detail/list edits. STATUS=IN_PROGRESS, not RELEASE_READY.

Post-publication preview regression: **31 browser tests PASS**, receipt
`/tmp/opencode/renewal-preview-final.log`. The next live alert-transition slice
first needs backend-authoritative audit actor binding; it must not trust actor
names supplied by the UI. Existing frontend detail/list work remains separate.

## Single-process session renewal — 2026-10-29

The BFF now retains encrypted HttpOnly refresh credentials using Java's additive
`refresh_expires_in` contract (backend `255be2c`). Timer, gateway and SSR renewal
coordinate by authenticated refresh credential; SSR forwards replacement cookies.
Lost BFF responses recover the same generation for 30 seconds, including short
access lifetimes. Ambiguous upstream failures fail closed. Bounded rejection
tracking can require old clients to reauthenticate under outage pressure, without
locking fresh logins out of renewal after recovery. Logout still revokes all Java
sessions and clears local state. Multi-worker deployment remains unsupported.

Final local gates: **133 units PASS**, selected coverage **92.79% lines / 85.56%
branches / 95.11% functions**, **32 auth browser tests PASS**, typecheck/build PASS.
Read-only security-review findings were fixed with regressions. See
`session-renewal.md` for RED/GREEN receipts and deployment limits. Preserve the
separate pre-existing detail/list changes. Paid billing, automatic product
metering, notification policy/provider configuration, physical hardware acceptance
and production release gates remain open; `STATUS=IN_PROGRESS`.

Actual-Java final acceptance **1/1 PASS** against backend `255be2c` artifact
`4f4a24c3…767454`, forcing real rotation with a disposable two-minute access TTL.
Receipt `/tmp/opencode/renewal-java-final2.log`, private browser
`/tmp/opencode/wuu-java-50ejEH/browser.log`; owned cleanup verified. The next slice
is verified live workflows, not production deployment or simulated paid access.

## Subscription owner integration — 2026-10-29

`/subscriptions` now reads the live configured token/monthly/yearly catalog and
owner enrollments. Narrow same-origin authenticated pending-enrollment and
confirmed owner-cancellation workflows are implemented; grants/catalog writes
remain blocked at the BFF. Preview explicitly offers no simulated billing.
Reload-safe operation IDs are scoped to owner/pinned version, with no tokens in
browser storage. See `subscriptions.md` for contracts and current gate receipts.
Actual Java acceptance passed for these workflows and the effective-access
settings panel; full backend729/device91 passed including the role-catalog slice,
and the final same-tenant Java acceptance passed on the rebuilt artifact. Independent
review found no confirmed critical/high regression; its refresh and
failed-cancellation warnings are fixed, and own-property gateway hardening is
green. Settings now also consumes the verified tenant role catalog read-only,
with `roles.read` gating, bounded pagination, exact collection routing and no
write/assignment path. Selected unit gate **122 PASS** (coverage
92.38/85.69/94.63) and auth browser suite **25 PASS**; no production deployment
certification claimed. The pre-existing detail/list changes remain separate and
preserved.

## Actual-Java read acceptance — 2026-10-29

The disposable production-Nitro/Java browser fixture passed after lifecycle hardening. It verifies two-tenant fleet/detail isolation, real password sign-in, sealed-cookie/bearer secrecy, account-wide revocation, forged-cookie denial and a genuine system-admin-only denial while permitted trips/fleet remain accessible. Cleanup left no owned containers or Java/Nitro/Playwright processes. See `deployed-java-acceptance.md` for exact artifact hash, receipts and limits. These dated results supersede older non-verification statements only for the exercised assertions; expiry/refresh, production TLS, writes and billing remain unverified.

Pre-existing detail-read/list-completeness changes remain separate uncommitted work. The next product slice is still verified live workflows, not a claim of production readiness.

## Push and requested cleanup — 2026-10-25

Frontend work was pushed to `origin/frontend-app`. All backend source was preserved on `origin/backend-updates` at `a12eaff`, with original `main` (`551db79`) as its parent, rather than pushing an unrelated snapshot root. This source commit includes the existing backend refactor and verified local-account/query changes; generated secrets and artifacts were excluded.

The user-requested `../backend-accounts` deletion is complete; the Java process running its JAR was stopped first. The account credentials/H2 state under frontend `.data/local-accounts/` remain private and ignored. The canonical `../sidie` checkout was not changed or renamed, and the named snapshot remains because the current repository instructions explicitly protect both. Backend source is pushed but **not merged into main or the canonical local checkout**. Live login/read verification cannot be rerun until that integration/runtime is restored. Historical paths and runtime outcomes below describe the completed verification before cleanup, not a second backend directory to recreate.

## Local integration checkpoint — 2026-10-25

Implemented the requested local-only user/admin seeds and backend-driven password-based account picker. Backend changes live in the isolated `../backend-accounts` repository (`local-account-picker`), preserving the active `../sidie` checkout. Seeds create VIEWER and TENANT_ADMIN accounts only when their case-insensitive email is absent, using transaction-scoped locking and existing constraints. Reruns never reset existing accounts or privileges. The opted-in directory queries all available backend users, not a hardcoded seed list. Selecting an account still requires its password; refresh occurs on mount/refocus/manual request. Both sides fail closed for unsupported/nonlocal access and default to disabled.

Current results: isolated backend **495 tests/package PASS**; frontend **82 unit tests PASS**, selected coverage **96.17% lines/statements, 84.48% branches, 95.74% functions**; typecheck/boundaries PASS; production build and **15 auth browser tests PASS**; **30 preview browser tests PASS** on port 3002; **one real-Java browser smoke PASS** covering both seed logins and discovery/login of a newly added account. Full formatting/diff results are recorded in `verification.md`.

The real browser smoke exposed the earlier alerts 401 as an unbound `:sortBy` repository parameter. A RED MockMvc regression and the narrow Pageable-based sorting fix are included in the isolated backend; seeded accounts now stay signed in. This does not fix arbitrary backend error-dispatch status masking or certify full tenant/production behavior. Artemis remains unavailable; JMS listeners are disabled only in this local auth/read runtime. Wuu runs on **http://127.0.0.1:3001/sign-in**; generated credentials are in ignored `.data/local-accounts/credentials.json`. The active backend/snapshots remain untouched and no commits were pushed.

### Earlier URL-discovery checkpoint

Confirmed `../sidie` defaults to `http://127.0.0.1:8080` (no context prefix). Ignored `.env.live.local` configures that origin, live mode, port 3001, and a generated session secret. No backend rename, source changes, or build were performed.

A limited HTTP smoke against the existing October 23 executable JAR and isolated H2/Redis verified Wuu login/session/logout and 200 responses for vehicles, trips, and geofences. Alerts returned 401; backend health returned 503 because Artemis was unavailable. Compiled-source freshness, browser rendering, tenant/role isolation, expiry, and account-wide revocation remain unverified. See `verification.md` for exact scope. The earlier full frontend gate below remains dated October 23, not rerun evidence for this slice.

## Previous full frontend checkpoint — 2026-10-23

Wuu has authenticated live read-only fleet/trip/alert/geofence services and an isolated public preview. Live sign-in validates backend credentials and `/users/me`, issues a sealed HttpOnly access cookie, guards routes, scopes caches to verified identity, and signs out through account-wide backend revocation. Access expiry requires reauthentication; refresh tokens are discarded, not raced. All live resource writes remain blocked at the gateway. Failed live requests never become fixtures, and per-domain permission failures cannot hide permitted content in another domain.

Verified on the final source:

- `pnpm typecheck`: PASS.
- `pnpm test:coverage`: **77 unit tests / 13 files PASS**; selected-source coverage **96% lines/statements, 83.78% branches, 95.62% functions**. This is not whole-UI coverage.
- `pnpm check:boundaries`: PASS.
- `pnpm format:check`: PASS.
- `pnpm build`: PASS.
- `pnpm test:e2e`: **30/30 Chromium tests PASS in 3.9 minutes** against a fresh production build. Geographic tile-engine tests now use labeled deterministic images by default; public provider availability is a separate opt-in smoke check.
- `pnpm test:e2e:auth`: **11/11 PASS**, including verified identity, bearer secrecy, sealed cookies, Origin checks, revocation/failure, identity-service failure, expiry, denied reads, independent permissions, and mobile/dark automated accessibility. Uses a test-only snake_case contract server, not Java.
- Anonymous live routes now redirect to sign-in; anonymous/legacy-cookie gateway calls return **401**. Live admin routes fail closed even for descriptive administrator role names. No actual backend URL/credentials or running Java service were available for real-deployment verification.

See `verification.md` for commands, evidence, screenshots, and limitations.

Wuu frontend (previously named Wayline) is built on the independent orphan branch `frontend-app` at `/home/jjenus/workspace/apps/tracker/app`.

Before alterations, `/home/jjenus/workspace/apps/tracker/sidie` was copied to `/home/jjenus/workspace/apps/tracker/sidie-snapshot-20261021-141524`. All 952 regular files were byte-verified at snapshot time. The frontend repository was cloned with independent Git metadata from that snapshot without checkout, then switched to an orphan branch. The active backend checkout was not modified.

Current backend HEAD observed: `551db79`; active code has an uncommitted refactor under `src/main/java/com/surf/tracker/`. `SECURITY.md` and read-only source inspection confirm active-session and current-database-permission enforcement, snake_case JSON, 15-minute default access tokens, single-use refresh rotation, and account-wide logout. Recheck the working tree, not just HEAD, before further integration. No backend files were modified or built here.

## Implemented

### Product and UI

- Wuu identity with supplied blue logo, responsive transparent PNG/WebP exports, multi-size ICO/favicon, Apple icon and manifest icons. Original source JPEG is preserved; no true vector source was supplied.
- Responsive semantic-token UI, light/dark mode, locally bundled font/icons, mobile navigation, contained table scrolling, reduced-motion handling, empty/error/retry states, and application error page.
- Business routes: overview, map, vehicles/detail, trips/detail, alerts/detail, geofences/detail/create/edit, team, settings.
- Platform concept routes: overview, organizations, roles/permissions, with explicit sample/security labels.
- Real Leaflet/markercluster maps: geographic coordinates, tiles/attribution, clustering, selection, search, layers, fit/reset, provider-error recovery, touch/keyboard navigation, polygons/circles in meters, and runtime-configurable tiles.
- Preview-only workflows: search/filter, vehicle creation, alert transitions, geofence drawing/editing, notification preferences, simulated tracking, and trip replay.
- Clear preview/live copy. Live identity is not invented; static preview identity is shown only in preview. Live team fails closed instead of listing sample people.
- Sign-in, safe local return paths, backend-verified account identity, HttpOnly encrypted cookies, same-origin login/logout, expired-session recovery, and protected live admin routes. Tokens never enter browser-readable state. Protected HTML is private/no-store.

### Typed data layer

- `DataMode` with explicit preview/live contexts; no fallback from live failures.
- Typed `ApiError`, HTTP/error mapping, retry policy, cancellation/timeout handling, and deterministic errors for malformed contracts.
- Zod contracts for vehicles, trips, alerts, geofences, preferences, and paged responses. `204` responses use the normal contract path.
- Shared `GatewayClient` with a 15-second timeout, combined abort signals, query handling, JSON parsing, request IDs, and contract errors.
- Feature service interfaces composed by `createServices`; preview adapters use isolated in-memory fixtures, live adapters use the gateway.
- Vue Query owns server state; cache keys include mode/organization/user context. Preview seeds caches; live queries run client-side only. Mutations are guarded by service capability checks as well as UI availability.
- Nitro `/api/_wuu/*` gateway: runtime private origin/token, allowlisted resource bases, HTTPS policy, path-segment validation, unsupported-method rejection, redirect blocking, timeout, safe JSON errors, and `no-store`.
- UTC timestamp formatting with a fixed locale prevents SSR/client hydration mismatches.

### Verification and drift control

- Unit coverage includes gateway behavior, error mapping, contracts, mapping, guarded live transitions, preview isolation, service composition, BFF allowlisting/origin policy, map geometry, geofence rules, tracking playback, and CSV safety.
- Browser coverage includes 30 tests across fleet, alerts, trips, details, settings, geofences, maps, tracking, themes, mobile behavior, downloads, and automated accessibility.
- `pnpm check:boundaries` enforces UI HTTP/adapter separation and semantic surface classes.
- Documentation records backend capability gaps, data-source behavior, security posture, map-provider limits, and the distinction between preview and production readiness.

## Not done

- Coordinated automatic refresh, registration/password recovery, and an effective-permissions/active-tenant endpoint integrated into UI capabilities. Initial sign-in/session/Origin guards/logout are implemented.
- Broad authenticated browser/tenant/role validation beyond the October 25 seed/picker Java smoke. The isolated backend fixes the alerts query blocker; Artemis/full event health remains unverified.
- Actual-deployment tenant/permission validation, role catalog/assignment integration, and team/invitation workflows. Live `/admin` is blocked rather than exposed as a working administration UI.
- Live alert transitions, vehicle creation, geofence create/edit, and preferences.
- Tenant-scoped telemetry, recorded GPS history, live trip replay, device commands, and notification streaming.
- Server-side export, alert rules, trackers, billing, ride-hailing, and payouts.
- Production map-provider approval, licensing/privacy/capacity review, CSP, non-Chromium/real-device verification, and whole-app performance/security audits.

## Next slice

Production-readiness work takes priority over adding more concept screens:

1. Configure the Java deployment URL and a random shared session-encryption secret, then verify actual login, reads, per-role denial, tenant isolation, expiry, and account-wide logout.
2. Verify an effective-permissions/active-tenant contract and design coordinated refresh before expanding the session lifecycle.
3. Then verify alert actor/review-note and geofence geometry/assignment contracts before enabling any live mutation.
4. Approve/configure the production tile provider and CSP.
5. Only then add telemetry/history, team administration, or additional domains.

Before implementing real authentication, verify token/session lifecycle, DTOs, pagination, error envelopes, and server-side resource scoping. Keep preview isolated; do not silently connect the present UI to insecure operations. Any backend changes require separate authorization and isolation from the active checkout.

## Drift protocol

1. Read canonical scope, API, access, architecture, design, and progress docs.
2. Pick one acceptance-sized vertical slice; verify contracts against current backend changes.
3. Add regression tests first, capture RED, then implement and capture GREEN.
4. Run typecheck, boundary check, unit tests, relevant browser tests, and a production build.
5. Record what is verified, what is merely illustrative, and the next blocker.
6. Do not mark a screen integrated because its preview works. Do not add a second state/UI/HTTP architecture.
