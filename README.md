# Wuu

**Your fleet, in focus.** A responsive, map-led fleet workspace built with Nuxt 4, Vue 3, TypeScript, and Nuxt UI v4.

Wuu runs in two explicit data modes:

- **Preview** (default): isolated sample data, no backend or credentials required.
- **Live**: typed read-only adapters call an allowlisted server-side gateway. Failed live requests stay visible and are never replaced with sample data.

Preview remains a frontend demonstration. Live mode is an integration seam, not a claim of production readiness: authentication, effective permissions, tenant isolation, and several backend mutation contracts are still release gates.

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
WUU_API_BEARER_TOKEN='server-only-token' \
PORT=3000 node .output/server/index.mjs
```

| Variable                     | Purpose                                                                                                                        |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `NUXT_PUBLIC_DATA_MODE=live` | Switches the client and server to live mode. Any other value remains preview.                                                  |
| `WUU_API_ORIGIN`             | Server-only upstream origin. `NUXT_API_ORIGIN` is also supported. HTTPS is required except for loopback.                       |
| `WUU_API_BEARER_TOKEN`       | Server-only token forwarded to the upstream. `NUXT_API_BEARER_TOKEN` is also supported.                                        |
| `wuu_access_token` cookie    | Optional per-session bearer token. No login or token-issuance flow is implemented in this app.                                 |
| `NUXT_PUBLIC_MAP_TILES_*`    | Optional URL/attribution/zoom override for the configured map tile provider. See [maps integration](docs/maps-integration.md). |

Never put a real token in a `NUXT_PUBLIC_*` variable, client code, a URL query, or the preview fixtures. The browser talks only to `/api/_wuu/*`; the Nitro gateway resolves the private origin/token at runtime and forwards only `vehicles`, `trips`, `alerts`, `geofences`, and `notifications`. Unknown resources, encoded path separators, traversal segments, unsafe origins, and unsupported methods fail closed.

Live mode currently reads fleet vehicles, trips, alerts, and geofences. Vehicle creation, alert transitions, geofence create/edit, team administration, and preferences remain disabled or unavailable until their identity, geometry, and server-authoritative contracts are verified. Live geofence and alert screens are read-only. Simulated tracking and trip replay remain clearly labeled preview simulations; they are never live GPS or recorded history.

## What works

- Responsive overview, fleet search/filter, vehicle detail, alerts, trips, geofences, team, and settings routes.
- Typed service layer with Zod contracts, pagination, normalized errors, retries, and Vue Query caching.
- Failure-isolated preview and live services composed from one container. A live error never becomes fixture data.
- Real Leaflet/markercluster maps with geographic coordinates, tiles and attribution, clustering, selection, search, layers, fit/reset, provider-error recovery, and mobile gestures.
- Explicit preview workflows: validated session-only vehicle creation, alert acknowledgment/resolution, geofence drawing/editing, trip replay, tracking simulation, and notification preferences.
- Live read-only tables and detail routes backed by the allowlisted gateway, with unsupported actions disabled.
- Filtered client-side trip CSV downloads (`wuu-preview-trips.csv` in preview, `wuu-trips.csv` in live).
- Dark/light mode, keyboard navigation, responsive tables, automated accessibility coverage, and a hydration-ready boundary for browser tests.
- Platform administration remains a clearly labeled **concept preview**; it is not connected to production access control.

## Safety boundary

- Preview and live state are separate. Preview fixtures are seeded only in preview mode and are never a fallback.
- Server authorization and tenant scoping are **not implemented by this frontend**. The BFF is not a substitute for upstream authorization, and `/admin` is not protected.
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
pnpm build
```

`pnpm test:e2e` builds a fresh production output and starts it on port 3000, so the browser suite verifies the release artifact rather than Vite's on-demand dev transform. Playwright uses a system or cached Chromium. To target an already-running compatible server instead:

```sh
PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e
```

Screenshots and failing traces go to ignored `test-results/`. See [verification](docs/verification.md) for actual results and limits.

## Logo assets

The supplied blue logo is used in navigation with transparent 96/192px PNG and lossless WebP variants, plus a 512px master, 16/32/48px ICO, 32/48/180/192/512px PNG icons, and manifest icons. The manifest does not add offline support.

Regenerate with `python3 tools/prepare-brand.py` (Pillow required). The supplied `public/photo_2026-10-21_20-47-14.jpg` is preserved unchanged. No source PNG or vector was present, so these are cleaned raster derivatives, not vector artwork. See [design system](docs/design-system.md).

## Canonical documents

Read [scope](docs/scope.md), [architecture](docs/architecture.md), [API contracts](docs/api-contracts.md), [access matrix](docs/access-matrix.md), [design system](docs/design-system.md), and [progress](docs/progress.md) before starting another slice.

Do not infer production access from client role templates or hidden navigation. Do not connect device commands, telemetry sockets, or administrative mutations until the backend contracts, effective permissions, and tenant boundaries are verified in a separately approved workstream.
