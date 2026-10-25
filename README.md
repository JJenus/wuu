# Wuu

**Your fleet, in focus.** A responsive, map-led fleet workspace built with Nuxt 4, Vue 3, TypeScript, and Nuxt UI v4.

Wuu runs in two explicit data modes:

- **Preview** (default): isolated sample data, no backend or credentials required.
- **Live**: sign in against the configured backend; typed read-only adapters call an authenticated server-side gateway. Failed live requests stay visible and are never replaced with sample data.

Preview remains a frontend demonstration. Live sign-in/session integration is implemented and contract-tested, not production-certified. Real-deployment authorization/tenant checks and several mutation contracts still need verification.

## Run the preview

Node 24 and pnpm 11 are used in this workspace.

```sh
pnpm install
pnpm dev
```

Open http://localhost:3000. No backend service, API key, or account is needed.

## Enable live reads

Build once, then start the server with runtime configuration:

```sh
pnpm build
NUXT_PUBLIC_DATA_MODE=live \
WUU_API_ORIGIN=https://fleet-api.example.com \
WUU_SESSION_PASSWORD='<cryptographically-random-secret-at-least-32-characters>' \
PORT=3000 node .output/server/index.mjs
```

| Variable                     | Purpose                                                                                                                                                                 |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `NUXT_PUBLIC_DATA_MODE=live` | Switches the client and server to live mode. Any other value remains preview.                                                                                           |
| `WUU_API_ORIGIN`             | Server-only upstream origin. `NUXT_API_ORIGIN` is also supported. HTTPS is required except for loopback.                                                                |
| `WUU_SESSION_PASSWORD`       | Server-only cookie-encryption secret, at least 32 random characters. `NUXT_SESSION_PASSWORD` is also supported. Share across app replicas; rotation signs everyone out. |
| `wuu_session` cookie         | Encrypted, HttpOnly, SameSite=Strict access session issued after login and `/users/me` verification; Secure except on loopback development hosts.                       |
| `NUXT_PUBLIC_MAP_TILES_*`    | Optional URL/attribution/zoom override for the configured map tile provider. See [maps integration](docs/maps-integration.md).                                          |

Never put a real token in a `NUXT_PUBLIC_*` variable, client code, a URL query, or the preview fixtures. The browser uses same-origin `/api/auth/*` and `/api/_wuu/*`. Login/logout require an exact matching Origin; tokens never enter browser-readable state or localStorage. The gateway requires a valid sealed session, forwards only allowlisted GETs, and never uses shared bearer-token environment fallbacks or the legacy `wuu_access_token` cookie. Configure HTTPS and trusted Host handling at the ingress, shared authentication rate limits, and request-body limits. Login bodies are bounded to 4 KiB.

Access sessions expire with the backend access token (15 minutes by backend default). Refresh tokens are discarded: automatic refresh is deliberately not implemented because concurrent rotations revoke the backend session. Expiry requires sign-in again. Sign-out revokes **all backend sessions** for the account; if revocation fails, the local cookie is still cleared and a warning is shown. There is no public registration or password-reset flow in this slice.

Live mode currently reads fleet vehicles, trips, alerts, and geofences. Vehicle creation, alert transitions, geofence create/edit, team administration, and preferences remain disabled or unavailable until their identity, geometry, and server-authoritative contracts are verified. Live geofence and alert screens are read-only. Simulated tracking and trip replay remain clearly labeled preview simulations; they are never live GPS or recorded history.

### Local account picker and seeds

Backend source, including the existing refactor and verified account changes, is pushed to `origin/backend-updates`, based on the original `main` history. The temporary `../backend-accounts` directory was deleted at the user's request. The active `../sidie` checkout remains unchanged; these changes are **not merged into main or the canonical local backend**. Do not recreate a second backend checkout as the normal workflow.

The backend opts in with `SPRING_PROFILES_ACTIVE=local`, `SERVER_ADDRESS=127.0.0.1`, and `LOCAL_ACCOUNTS_ENABLED=true`. Supply strong `LOCAL_USER_PASSWORD` and `LOCAL_ADMIN_PASSWORD` values through private environment configuration; there are no default passwords. Startup creates `user@wuu.local` (VIEWER) and `admin@wuu.local` (TENANT_ADMIN) in one local workspace. It checks emails case-insensitively, serializes seed transactions with a database row lock, and never resets existing passwords, profiles, flags, roles or memberships. A business administrator is not a platform administrator.

`WUU_LOCAL_ACCOUNT_PICKER=true` enables Wuu's pre-login picker only for loopback host/peer/upstream. It selects the email and focuses an empty password field; **it never logs in without a password**. New enabled/unlocked backend users appear on page load, window refocus, or **Refresh accounts**. The list is a fresh, paginated backend query, not a seed roster. Directory errors leave manual sign-in available. Both backend and frontend settings are off by default; never enable this account-directory feature on a public deployment or point it at a real-user database.

For this workspace, generated credentials are in ignored `.data/local-accounts/credentials.json` (mode 0600). Read only the two seeded credentials locally:

```sh
python3 -c 'import json; a=json.load(open(".data/local-accounts/credentials.json")); [print(a[k]["email"], a[k]["password"]) for k in ("user", "admin")]'
```

The temporary backend's `.env` and built JAR were deleted with that directory, and its Java process was stopped. Generated account credentials and the isolated H2 database remain under ignored `.data/local-accounts/` in this frontend. Restore a backend runtime only after integrating `backend-updates` into the canonical backend while preserving its uncommitted work; supply the existing seed passwords from that private file and a fresh private JWT secret. Spring does **not** automatically load `.env` files.

The verified local configuration used Redis on `127.0.0.1:16379` and disabled JMS listeners for auth/read verification. Artemis-dependent health/functions were not verified by that setup. Do not delete/regenerate the credential file to reset an existing account: seed reruns intentionally preserve its password hash.

Frontend startup remains `node --env-file=.env.live.local .output/server/index.mjs` after `pnpm build`, at **http://127.0.0.1:3001/sign-in**. The ignored file includes live mode, local API origin, picker flag, session secret and port 3001. **Login/live reads require a running compatible backend; the deleted temporary backend is no longer running.** No generated credential file is required in production.

## What works

- Responsive overview, fleet search/filter, vehicle detail, alerts, trips, geofences, team, and settings routes.
- Typed service layer with Zod contracts, pagination, normalized errors, retries, and Vue Query caching.
- Failure-isolated preview and live services composed from one container. A live error never becomes fixture data.
- Real Leaflet/markercluster maps with geographic coordinates, tiles and attribution, clustering, selection, search, layers, fit/reset, provider-error recovery, and mobile gestures.
- Explicit preview workflows: validated session-only vehicle creation, alert acknowledgment/resolution, geofence drawing/editing, trip replay, tracking simulation, and notification preferences.
- Live read-only tables and detail routes backed by the allowlisted gateway, with unsupported actions disabled.
- Filtered client-side trip CSV downloads (`wuu-preview-trips.csv` in preview, `wuu-trips.csv` in live).
- Dark/light mode, keyboard navigation, responsive tables, automated accessibility coverage, and a hydration-ready boundary for browser tests.
- Platform administration remains a clearly labeled **concept preview** in preview mode. Live `/admin` routes fail closed, including for users with descriptive admin role names.

## Safety boundary

- Preview and live state are separate. Preview fixtures are seeded only in preview mode and are never a fallback.
- Server authorization and tenant scoping are enforced by the backend, not inferred by this frontend. The BFF and route guards do not replace those controls. Live identity comes from `/users/me`; no client role template grants permissions. Query caches are scoped to that identity and reset via a full navigation on sign-in/out.
- The gateway rejects non-HTTPS remote origins, limits forwarding to known resource prefixes, disables redirects, applies a 15-second timeout, and marks responses `no-store`.
- API timestamps display deterministically in UTC so server and browser rendering agree.
- Map tiles use the configured public OpenStreetMap endpoint by default. That endpoint has no SLA; approve a production provider, licensing, capacity, privacy review, and CSP before launch.
- The active `../sidie/` checkout and `../sidie-snapshot-*` were not modified. Backend fixes require separately approved isolation.

## Verify

```sh
pnpm typecheck
pnpm test:coverage
pnpm check:boundaries
pnpm format:check
pnpm test:e2e
pnpm test:e2e:auth
pnpm build
```

`pnpm test:e2e` builds a fresh production output and starts it on port 3000, so the browser suite verifies the release artifact rather than Vite's on-demand dev transform. Playwright uses a system or cached Chromium. To target an already-running compatible server instead:

```sh
PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e
```

Screenshots and failing traces go to ignored `test-results/`. See [verification](docs/verification.md) for actual results and limits.

`pnpm test:e2e:auth` builds and tests live mode on port 3001 against the test-only snake_case contract server on 18080; it does not contact Java or use real credentials. Geographic tile tests normally use a labeled deterministic tile image. Set `WUU_TEST_LIVE_TILES=1` to run those checks against the configured public provider separately.

`pnpm test:e2e:java` is a separate explicit local smoke against already-running Java on 8080 and Wuu on 3001. It reads the private local credentials above, tests both seed logins, and creates/reuses `additional@wuu.local` through the permission-checked user API to verify dynamic discovery. It leaves that local verification account in the isolated database. Traces/videos/screenshots are disabled for this suite to avoid recording credentials. It is not a whole-deployment security certification. To keep port 3000 free during preview verification, start a preview production server on 3002 and run `PLAYWRIGHT_BASE_URL=http://127.0.0.1:3002 pnpm test:e2e`.

## Logo assets

The supplied blue logo is used in navigation with transparent 96/192px PNG and lossless WebP variants, plus a 512px master, 16/32/48px ICO, 32/48/180/192/512px PNG icons, and manifest icons. The manifest does not add offline support.

Regenerate with `python3 tools/prepare-brand.py` (Pillow required). The supplied `public/photo_2026-10-21_20-47-14.jpg` is preserved unchanged. No source PNG or vector was present, so these are cleaned raster derivatives, not vector artwork. See [design system](docs/design-system.md).

## Canonical documents

Read [scope](docs/scope.md), [architecture](docs/architecture.md), [API contracts](docs/api-contracts.md), [access matrix](docs/access-matrix.md), [design system](docs/design-system.md), and [progress](docs/progress.md) before starting another slice.

Do not infer production access from client role templates or hidden navigation. Do not connect device commands, telemetry sockets, or administrative mutations until the backend contracts, effective permissions, and tenant boundaries are verified in a separately approved workstream.
