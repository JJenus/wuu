# Architecture contract

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
5. The gateway resolves `WUU_API_ORIGIN`/`NUXT_API_ORIGIN` and `WUU_API_BEARER_TOKEN`/`NUXT_API_BEARER_TOKEN` at runtime. It also accepts an optional `wuu_access_token` cookie.
6. Only `vehicles`, `trips`, `alerts`, `geofences`, and `notifications` are allowlisted. Encoded separators, traversal segments, unknown resources, unsupported methods, unsafe origins, and redirects fail closed. The upstream request has a 15-second timeout and `no-store` response handling.

Live queries are disabled during SSR and execute in the browser against the same-origin BFF. Preview caches are seeded during both SSR and hydration for deterministic rendering. Live failures remain visible as errors (with retry); there is no fixture fallback, even for empty or contract-invalid responses. `204` responses pass through the normal schema path so an incompatible 204 becomes a contract error.

Timestamps are formatted with a fixed `en-US` locale and explicit UTC zone. This avoids server/client hydration differences while preserving the underlying instants.

## Query and state ownership

- Server-shaped fleet/alerts/trips/geofences/preferences: Vue Query, keyed by `mode`, `organizationId`, and `userId`.
- Page-local filters, dialogs, and map selection: Vue refs/computed.
- Preview mutations update only the in-memory adapter and invalidate/update relevant caches. They reset on reload.
- Live mutations are never inferred from UI availability. Vehicle creation fails closed, alert changes require an actor and resolution notes, geofence updates are unavailable, and live preferences are unavailable. The current UI hides or disables these actions.
- Notification preferences persist only in preview memory. No localStorage credentials or token persistence exists.
- Geofence drafts and undo history belong to the editor composable. `GeofenceMap` owns viewport/pointer state, not persistence. Domain geometry rules live in `features/geofences/domain/geofence.ts`.
- Tracking fixtures live in `features/tracking/api/preview-tracks.ts`. Composables own the opt-in clock and computed positions; simulated movement never enters workspace records, exports, or a live API request. Playback starts paused, pauses in hidden tabs, and cancels on unmount.
- Leaflet/markercluster is shared by fleet, vehicle, replay, and geofence views. `useGeographicMap` owns client-only initialization, tiles, layers, gestures, resize, and teardown. Domain contracts accept geographic positions, never screen percentages.

## UI boundary

`app/components`, pages, and feature components may import pure error messages, data-mode composables, and feature composables. They may not import API adapters, `$fetch`/`fetch`/`useFetch`, or hardcoded light/dark surface classes. `pnpm check:boundaries` enforces this. UI text is rendered with Vue interpolation; no raw HTML rendering is used for API content.

## Security posture

- There is no login or session issuance. The BFF is not an authentication authority; upstream authorization must enforce every resource and action.
- Client role templates are descriptive only. They never grant live access or hide the need for server enforcement.
- The preview organization/user IDs in public runtime config isolate query keys; they are not trusted identity claims.
- `/admin` remains an unprotected concept preview.
- Production deployment still requires HTTPS, CSP, CSRF/session design, secret management, provider approval, and independent security review.

## Future integration rules

1. Add a real server session boundary with secure HttpOnly cookies, CSRF controls, fixed upstream origin, allowlisted endpoints, bounded bodies, and audited request IDs.
2. Verify effective permissions and tenant scope from the server, then model permission-denied and expired-session states. Do not derive authorization from JWT role names or client templates.
3. Verify alert actor/review-note, geofence update geometry/assignments, vehicle/tracker creation, and preference contracts before enabling those mutations.
4. Add tenant-scoped telemetry/history only after authenticated subscription, ordering, staleness, gap, reconnect, and historical-GPS rules are proven. Never treat the notification socket as a GPS feed.
5. Map backend DTOs/pagination/errors inside adapters. Keep rendering/search adapters independent of transport.
6. Preview and live modes stay explicit. A failed live request must never be replaced by fictional data.
7. Approve the production tile provider, licensing, privacy, capacity, CSP, and non-Chromium/device checks before launch.

## No hidden service sprawl

Do not add pass-through use-case classes, duplicate query stores, multiple HTTP clients, a second UI library, or a generic open proxy. Keep helpers local until two real consumers justify sharing. Do not import private implementations across features.
