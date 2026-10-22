# Progress and next slice

## Current checkpoint — 2026-10-22

Wuu now has two explicit data modes. **Preview** remains an isolated sample workspace. **Live** adds typed read-only fleet, trip, alert, and geofence services behind an allowlisted Nitro gateway that resolves the upstream origin/token at runtime. Failed live requests stay errors; there is no fixture fallback. Unsupported live mutations fail closed and are hidden or disabled.

Verified on the final source:

- `pnpm typecheck`: PASS.
- `pnpm test:coverage`: **70 unit tests / 12 files PASS**; selected-source coverage **95.62% lines/statements, 82.24% branches, 95.16% functions**. This is not whole-UI coverage.
- `pnpm check:boundaries`: PASS.
- `pnpm format:check`: PASS.
- `pnpm build`: PASS.
- `pnpm test:e2e`: **30/30 Chromium tests PASS in 2.8 minutes** against a fresh production build; the default command owns a production server rather than a Vite dev server.
- Live fail-closed runtime check: BFF returned **503** with `{"message":"The live API is not configured on this server."}`; home/map showed live errors and no preview vehicles or fake identity; team hid sample members and showed the explicit live gap.

See `verification.md` for commands, evidence, screenshots, and limitations.

Wuu frontend (previously named Wayline) is built on the independent orphan branch `frontend-app` at `/home/jjenus/workspace/apps/tracker/app`.

Before alterations, `/home/jjenus/workspace/apps/tracker/sidie` was copied to `/home/jjenus/workspace/apps/tracker/sidie-snapshot-20261021-141524`. All 952 regular files were byte-verified at snapshot time. The frontend repository was cloned with independent Git metadata from that snapshot without checkout, then switched to an orphan branch. The active backend checkout was not modified.

Backend source commit observed during this work: `822f2a2b7314cd2f0df7184c6762e28028b76e6d`. The source also has uncommitted ongoing work; recheck contracts and diffs before subsequent integration. The independent frontend remote contains only this application; it is never a path for changing the backend or snapshot.

## Implemented

### Product and UI

- Wuu identity with supplied blue logo, responsive transparent PNG/WebP exports, multi-size ICO/favicon, Apple icon and manifest icons. Original source JPEG is preserved; no true vector source was supplied.
- Responsive semantic-token UI, light/dark mode, locally bundled font/icons, mobile navigation, contained table scrolling, reduced-motion handling, empty/error/retry states, and application error page.
- Business routes: overview, map, vehicles/detail, trips/detail, alerts/detail, geofences/detail/create/edit, team, settings.
- Platform concept routes: overview, organizations, roles/permissions, with explicit sample/security labels.
- Real Leaflet/markercluster maps: geographic coordinates, tiles/attribution, clustering, selection, search, layers, fit/reset, provider-error recovery, touch/keyboard navigation, polygons/circles in meters, and runtime-configurable tiles.
- Preview-only workflows: search/filter, vehicle creation, alert transitions, geofence drawing/editing, notification preferences, simulated tracking, and trip replay.
- Clear preview/live copy. Live identity is not invented; static preview identity is shown only in preview. Live team fails closed instead of listing sample people.

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

- Login/session issuance/refresh/revocation, secure HttpOnly sessions, CSRF protections, route guards, and verified effective permissions.
- Authenticated end-to-end validation against the real Java backend; live mode has only been exercised fail-closed with no upstream configured plus unit-tested adapters.
- Tenant-isolation enforcement, role catalog/assignment APIs, team/invitation workflows, and protected `/admin`.
- Live alert transitions, vehicle creation, geofence create/edit, and preferences.
- Tenant-scoped telemetry, recorded GPS history, live trip replay, device commands, and notification streaming.
- Server-side export, alert rules, trackers, billing, ride-hailing, and payouts.
- Production map-provider approval, licensing/privacy/capacity review, CSP, non-Chromium/real-device verification, and whole-app performance/security audits.

## Next slice

Production-readiness work takes priority over adding more concept screens:

1. Agree backend identity/effective-permission and tenant-isolation contracts.
2. Add a secure server session boundary and verify authenticated read-only fleet behavior against a real backend.
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
