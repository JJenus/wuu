# Architecture contract

## Account self-service — 2026-11-13

`AccountSettings` owns local drafts only; `useAuth` owns identity adoption, shared
renewal-generation invalidation and reauthentication, while `authRequest` owns
browser transport. Shared strict schemas feed dedicated authenticated BFF account
handlers, not a general user-write proxy. Server-only adapters own Java paths,
identity checks and uncertain-write policy. Both new mutations explicitly disable
client retries. Account controls are disabled until hydration and remain outside
the independent notification-preference error boundary. See `self-account.md`.

## Live team management — 2026-11-13

`TeamManager` uses `useTeam` and `WorkspaceServices.team`; typed adapters own HTTP
and schemas, Vue Query owns context/page-scoped reads, and the workflow owns
permission refresh, mutation state, invalidation and honest uncertain-write errors.
Shared strict team input schemas feed the narrow server-only gateway policy.
Preview keeps sample members without simulated writes. Backend tenant response
scoping and membership/default-role safeguards are covered by tests. See
[team-management.md](team-management.md) for contracts and acceptance evidence.

## Canonical upstream API boundary — 2026-11-12

`server/utils/upstream-api.ts` is the only application source declaring the Java
`/api/v1` prefix. It owns upstream routes for gateway resolution, authentication
and write allowlists. Policies match resource-relative suffixes at exact resource
boundaries. Components and pages never import this server-only registry or call
HTTP directly; composables use feature services/adapters. Browser adapters retain
their existing BFF resource paths (`/api/_wuu/*`), not Java URLs.

`tools/check-boundaries.mjs` checks Vue/TypeScript UI, workflow HTTP calls,
browser imports of server/upstream routes and duplicated server version literals.
Tests may assert full canonical URLs independently of the registry. No legacy
Java path aliases or automatic version fallback are supported.

## Live map snapshots — 2026-11-12

`useMapTracking` owns a page-local observer on the existing context-keyed fleet
query. Live positions come directly from the fleet adapter, with visible-tab
polling, explicit pause/refresh, read-error hiding and no simulated paths. Preview
still uses the opt-in playback clock. This supersedes simulated live-map behavior;
it does not add a GPS stream or historical replay. See [live-map.md](live-map.md).

## Live geofence editing — 2026-11-10

Recovered the existing geofence adapter/editor through the same service/gateway
boundary. Editor composables own draft/history, permission rechecks and saves;
once admitted, pages retain drafts across permission/read failures. Save snapshots
freeze controls, invalid/blank coordinates cannot become zero, and ambiguous
failures do not trigger automatic retries. The gateway admits only strict bounded
create/edit payloads and preserves existing fleet/subscription/alert routes.
Numeric geometry outside map editing bounds stays lossless rather than clamped.
Rule configuration and telemetry evaluation remain separate from boundary saving.

## Fleet setup — 2026-11-10

Live fleet setup follows the existing service/adapter/gateway path.
`useFleetSetup` owns permission refresh, mutation state and fleet-cache invalidation;
`useAssignedTrackers` owns context/vehicle-scoped queries. Preview creation is
unchanged and tracker registration is not simulated. Shared Zod schemas define
strict collection-create payloads; the BFF enforces exact origin and bounded JSON.
Backend tenant/permission/uniqueness enforcement remains authoritative. Requests
are never automatically replayed; uncertain results retain drafts and tell the
user to check persisted IDs before retrying. Historical disabled-fleet statements
below are superseded for those creates and tracker reads. The 2026-11-13 management
increment also adds strict profile edits and confirmed unused-tracker correction
through this same boundary. Raw profile values remain separate from display
placeholders; failed/uncertain writes retain drafts. Java serializes correction
with ingestion and rejects recorded activity, active trips and stale sources.
See `fleet-registration.md` for the updated contract and checks.

## Subscription integration exception — 2026-10-29

Subscription state follows the same service-container → adapter → gateway path
and Vue Query ownership, keyed by verified context. Only pending enrollment and
owner cancellation are now allowlisted writes; both use exact-origin checks,
strict bounded JSON and Java's owner authorization. All other resource writes
remain blocked. Owner/version-scoped sessionStorage holds only operation IDs for
ambiguous enrollment retries, never credentials. Preview billing is unavailable,
not fabricated. See `subscriptions.md` for contracts, tests and limits.

Settings also consumes `WorkspaceServices.access` through a separate Vue Query,
showing current effective permissions independently of notification preferences.
The fixed `/api/_wuu/access` alias maps only to Java's current-access endpoint;
there is no general users proxy. Role templates remain non-authoritative and
platform concept routes remain blocked. All gateway bases must be own properties
of the explicit allowlist, not inherited Object members.

## Decisions

- Nuxt 4, Vue 3, TypeScript, Nuxt UI v4, Tailwind 4; pnpm 11 lockfile.
- One application with business and platform navigation; no duplicate admin component system.
- Feature-first structure. Add a layer only when it owns a real responsibility.
- TanStack Vue Query owns server/adapter state. No Pinia yet: no shared client-state requirement justifies another store.
- Preview and live are explicit modes composed at one plugin boundary. There is no automatic fallback from live to preview.
- Live browser traffic uses a same-origin Nitro gateway (`/api/_wuu/*`) with a fixed, allowlisted upstream. The browser never receives the server-held bearer token or upstream origin.
- Local self-hosted Inter font and Lucide icon collection; no runtime third-party font/icon request.
- Node 24, pnpm 11, Zod 4, Leaflet 1.9.4 and markercluster 1.5.3.

## Dependency direction

```text
pages / feature components
        │  props, events, no HTTP
        ▼
feature composables (workflow, cache mutations)
        ▼
WorkspaceServices interfaces (fleet, alerts, trips, geofences, preferences)
        ├── preview adapters → in-memory fixtures
        └── live adapters → GatewayClient → /api/_wuu → allowlisted upstream

pure domain models/rules ← composables and adapters
shared UI components ← props/events only
```

`app/plugins/query.ts` is the composition root. It resolves the mode, creates a QueryClient and a fresh service container per Nuxt app, and seeds preview records into the query cache. Preview mutable state is never shared across SSR requests. Adapter reads return defensive copies.

The browser adapter calls only `/api/_wuu`. Alert, trip, geofence, and preference services never reach an arbitrary URL. Adapters own DTO mapping, pagination, validation, error normalization, and fail-closed capability checks.

## Request flow and failure policy

1. A page uses `useWorkspace` or `usePreferences`; the composable queries a domain service interface.
2. Preview services read an isolated in-memory copy of fixtures.
3. Live services call `GatewayClient`, which runs Zod validation at the boundary, applies a timeout, combines caller cancellation, and maps HTTP/contract failures to typed `ApiError` values.
4. `GatewayClient` calls the Nitro gateway at `/api/_wuu/<resource>`.
5. The gateway resolves `WUU_API_ORIGIN`/`NUXT_API_ORIGIN` and decrypts the per-user `wuu_session` cookie using `WUU_SESSION_PASSWORD`/`NUXT_SESSION_PASSWORD`. Shared tokens and legacy plaintext bearer cookies are not accepted.
6. Only GETs for `vehicles`, `trips`, `alerts`, `geofences`, and `notifications` are allowlisted. Encoded separators, traversal segments, unknown resources, unsupported methods, unsafe origins, and redirects fail closed. The upstream request has a 15-second timeout and `no-store` response handling. The transport normalizes nested snake_case JSON before Zod validation; ambiguous duplicate keys are rejected.

Live queries are disabled during SSR and execute in the browser against the same-origin BFF. Preview caches are seeded during both SSR and hydration for deterministic rendering. Live failures remain visible as errors (with retry); there is no fixture fallback, even for empty or contract-invalid responses. `204` responses pass through the normal schema path so an incompatible 204 becomes a contract error.

Timestamps are formatted with a fixed `en-US` locale and explicit UTC zone. This avoids server/client hydration differences while preserving the underlying instants.

## Query and state ownership

- Server-shaped fleet/alerts/trips/geofences/preferences: Vue Query, keyed by `mode`, `organizationId`, and `userId`.
- Page-local filters, dialogs, and map selection: Vue refs/computed.
- Preview mutations update only the in-memory adapter and invalidate/update relevant caches. They reset on reload.
- Live mutations are never inferred from UI availability. Vehicle creation fails closed, the alert response/notes workflow is not enabled, geofence updates are unavailable, and live preferences are unavailable. The current UI hides or disables these actions and the gateway blocks resource writes.
- Notification preferences persist only in preview memory. No localStorage credentials or token persistence exists.
- Geofence drafts and undo history belong to the editor composable. `GeofenceMap` owns viewport/pointer state, not persistence. Domain geometry rules live in `features/geofences/domain/geofence.ts`.
- Tracking fixtures live in `features/tracking/api/preview-tracks.ts`. Composables own the opt-in clock and computed positions; simulated movement never enters workspace records, exports, or a live API request. Playback starts paused, pauses in hidden tabs, and cancels on unmount.
- Leaflet/markercluster is shared by fleet, vehicle, replay, and geofence views. `useGeographicMap` owns client-only initialization, tiles, layers, gestures, resize, and teardown. Domain contracts accept geographic positions, never screen percentages.

## UI boundary

`app/components`, pages, and feature components may import pure error messages, data-mode composables, and feature composables. They may not import API adapters, `$fetch`/`fetch`/`useFetch`, or hardcoded light/dark surface classes. `pnpm check:boundaries` enforces this. UI text is rendered with Vue interpolation; no raw HTML rendering is used for API content.

## Security posture

- `/api/auth/login` validates bounded JSON, requires matching Origin, validates the backend token response and `/users/me`, then seals access/refresh credentials and expiries in an AES-256-GCM HttpOnly cookie. Only identity and the access deadline are returned to the browser. The backend is the authentication/authorization authority.
- `/api/auth/session` rechecks identity against the backend, including revocation/account state. `/api/auth/logout` clears the local cookie and requests account-wide backend revocation. Backend failures are sanitized; upstream revocation failure is disclosed, never shown as success.
- Automatic access renewal coalesces authenticated refresh credentials in one BFF process and retains bounded 30-second recovery receipts. SSR forwards replacement cookies; ambiguous upstream failures fail closed. Shared cookie secrets do not coordinate replicas: multi-worker deployment remains unsupported. See `session-renewal.md` for expiry, capacity cutover and release limits.
- `auth` initializes identity before `query`; live query keys use server-reported user/org identifiers. Sign-in/out force full navigation to clear prior identity/cache state. The backend still selects/enforces the active tenant; the frontend does not provide tenant switching.
- Client role templates are descriptive only. They never grant live access or hide the need for server enforcement.
- The organization/user IDs in public runtime config apply only to preview; live mode does not trust them as identity claims.
- `/admin` is a public concept preview only in preview mode. Live admin navigation is hidden and its routes fail closed pending effective platform capabilities; role names alone never unlock them. Protected live HTML is private/no-store.
- Production deployment still requires HTTPS, CSP, CSRF/session design, secret management, provider approval, and independent security review.
- The optional local-only account picker follows the same boundary: sign-in UI → `useLocalAccounts` → validated auth adapter → `/api/auth/accounts` → fixed loopback Java directory. Private opt-in plus Host/socket/upstream guards prevent public enumeration by default. The backend has its own local-profile/binding/peer gate. Selecting an identity never authenticates it; ordinary password/token/session checks still apply. See `api-contracts.md` for the isolated backend location and pagination limits.

## Future integration rules

1. Verify the implemented single-process renewal boundary against production ingress rate limits, Host/TLS configuration and revocation. Durable multi-instance refresh coordination remains a separate release gate.
2. Add a verified effective-permissions/active-tenant contract before enabling platform navigation or mutations. Permission-denied reads remain errors; per-domain query states prevent unrelated denied reads from hiding permitted content. Do not derive authorization from JWT role names or client templates.
3. Verify alert actor/review-note, geofence update geometry/assignments, vehicle/tracker creation, and preference contracts before enabling those mutations.
4. Add tenant-scoped telemetry/history only after authenticated subscription, ordering, staleness, gap, reconnect, and historical-GPS rules are proven. Never treat the notification socket as a GPS feed.
5. Map backend DTOs/pagination/errors inside adapters. Keep rendering/search adapters independent of transport.
6. Preview and live modes stay explicit. A failed live request must never be replaced by fictional data.
7. Approve the production tile provider, licensing, privacy, capacity, CSP, and non-Chromium/device checks before launch.

## No hidden service sprawl

Do not add pass-through use-case classes, duplicate query stores, multiple HTTP clients, a second UI library, or a generic open proxy. Keep helpers local until two real consumers justify sharing. Do not import private implementations across features.
