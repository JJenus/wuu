# Architecture contract

## Decisions

- Nuxt 4, Vue 3, TypeScript, Nuxt UI v4, Tailwind 4; pnpm 11 lockfile.
- One application with business and platform navigation; no duplicate admin component system.
- Feature-first structure. Add a layer only when it owns a real responsibility.
- TanStack Vue Query owns server/adapter state. No Pinia yet: no shared client-state requirement justifies another store.
- Local, self-hosted Inter font and Lucide icon collection; no runtime third-party font/icon request.
- Preview adapter only. Authentication/backend transport will be a separate tested slice; no generic unauthenticated proxy.

## Dependency direction

```text
pages / feature components → feature composables → WorkspaceService
                                                 ↑
                                    preview API adapter (fixtures)

pure domain models/rules ← composables and adapters
shared UI components ← props/events only
```

`app/plugins/query.ts` is the composition root. It creates a QueryClient and fresh preview adapter per Nuxt app. Mutable sample state is not shared across SSR requests. Adapter reads return defensive copies. UI cannot import API adapters or call HTTP directly (`pnpm check:boundaries`).

## State ownership

- Server-shaped fleet/alerts/trips/preferences: Vue Query, keyed `workspace / preview / northstar`.
- Page-local filters, dialogs, map selection: Vue refs/computed.
- Navigation uses genuine routes; back links are ordinary links.
- Production organization IDs must be server-validated and included in query keys. Clear identity/tenant caches at logout or context switch; never trust a client tenant override.
- Preferences and mutations last only until reload. No localStorage credentials or token persistence.
- Geofence form drafts and undo history belong to the editor composable, separate from the saved workspace cache. Only validated saves reach the preview adapter. `GeofenceMap` is props/events only; it owns viewport/pointer state, not persistence. Domain geometry rules live in `features/geofences/domain/geofence.ts`.
- Tracking fixture paths belong to `features/tracking/api/preview-tracks.ts`. Composables own the opt-in clock and computed geographic positions; they never write simulated telemetry into workspace records/exports. Playback pauses in hidden tabs, cancels on unmount and starts paused. Mock paths are not recorded GPS or routed journeys.
- Leaflet/markercluster rendering is shared by fleet, vehicle, replay and geofence views. `useGeographicMap` owns client-only initialization, tiles, keyed layers, gestures, resize and teardown. Domain contracts accept geographic positions, never screen percentages. Components accept injected search/data-state props and emit selection/navigation/retry. Search adapters own network/catalog contracts, not UI. `maps-integration.md` records provider configuration and remaining backend release gates.

## Future integration rules

1. Introduce explicit server-side session handling with secure HttpOnly cookies, CSRF protections, fixed upstream origin, allowlisted endpoints, and bounded request sizes/timeouts.
2. Map backend DTOs/pagination/errors to frontend models inside adapters—not pages.
3. Preview and real mode must be explicit. A failed live request must never fall back to fictional data.
4. Model permission denial and expired-session states; server must enforce all actions and resource scoping.
5. Geographic rendering is implemented using Leaflet; provider configuration is runtime-driven. Keep fleet API transport/search adapters independent of the rendering engine.
6. Do not interpret a notification WebSocket as an authenticated GPS stream. Verify contracts first.

## No hidden service sprawl

Do not add pass-through use-case classes, duplicate query stores, multiple HTTP clients, or a second UI library. Keep helpers local until two real consumers justify sharing. Don't import private implementations across features.
