# Progress and next slice

## Current checkpoint — 2026-10-23

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
- Authenticated end-to-end validation against the real Java backend; live mode has only been exercised fail-closed with no upstream configured plus unit-tested adapters.
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
