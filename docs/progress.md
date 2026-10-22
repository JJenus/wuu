# Progress and next slice

## Current checkpoint

Latest verified requirement correction: Leaflet/markercluster replace the illustration across fleet, vehicle, replay and geofence views. **30 unit tests and 30 Chromium browser tests pass**, with typecheck/build/boundaries/formatting passing. Selected-file coverage is **99.30% lines/statements**, not whole-UI coverage. Geographic fixtures, meter-based boundaries, selection highlighting, clustering, real tiles/attribution and retry are implemented. See `verification.md`. This does not declare the whole app production-release ready.

Wuu frontend preview (previously named Wayline), built on independent orphan branch `frontend-app` at `/home/jjenus/workspace/apps/tracker/app`.

Before alterations, `/home/jjenus/workspace/apps/tracker/sidie` was copied to `/home/jjenus/workspace/apps/tracker/sidie-snapshot-20261021-141524`. All 952 regular files were byte-verified at snapshot time. The frontend repository was cloned with independent Git metadata from that snapshot without checkout, then switched to an orphan branch. The active backend checkout was not modified.

Backend source commit observed during this session: `822f2a2b7314cd2f0df7184c6762e28028b76e6d`. The source also has uncommitted ongoing work; recheck contracts and diffs before subsequent integration. The independent frontend has no configured remote, so it cannot accidentally push into the backend or backup.

## Implemented

- Product identity and responsive semantic-token UI, light/dark mode, locally bundled font/icons.
- Wuu identity with supplied blue logo, transparent responsive PNG/WebP exports, multi-size ICO/favicon, Apple icon and manifest icons. Original source JPEG is preserved; no true vector source was supplied.
- Business routes: overview, map, vehicles/detail, trips, alerts, geofences, team, settings.
- Platform concept routes: overview, organizations, roles/permissions.
- Isolated service adapter, defensive fixture reads, Vue Query composable, pure fleet rules, presentation access policy.
- Local preview workflows: search/filter, vehicle creation, alert transitions, trip detail/export, preferences, search dialog, map controls.
- Linked alert, trip, and geofence detail routes with reload/missing-record handling; alert response actions; single-trip export; vehicle-linked alerts and recent journeys.
- Rounded theme-aware native scrollbars, keyboard-scrollable trip table, mobile detail headings, and current-section navigation on nested routes.
- Geofence create/edit preview: circle/polygon canvas, pointer/keyboard/coordinate controls, radius slider, validated assignments, isolated drafts and save/cancel/discard protection. Detail pages show plotted geometry and inspectable coordinates.
- Tracking/replay preview: opt-in simulated fleet movement, selected trail/follow, pause/reset/speed controls, and illustrative per-trip replay with seekable timeline. No live telemetry or historical GPS connection; saved fleet/trip data is not modified.
- Real Leaflet geographic maps: drag/pinch/wheel/keyboard navigation, fit/reset, highlighted status markers, hover/focus labels, synchronized selection, clustering/expansion, geographic route/trail overlays, query/tile-error states, visible provider attribution and runtime-configurable tiles. Geofence search preserves geometry; circles use meters and polygon areas use spherical estimates. Old screen-percent positions/projection are removed. See `maps-integration.md` for deployment configuration and limits.
- Drift-control documentation and automated UI/API boundary checker.

## Verification

Latest Wuu branding slice (2026-10-22): **22/22 unit tests** and **23/23 browser tests** passed against the rebuilt production preview (2.2 minutes). Typecheck, build, boundaries, formatting and whitespace checks passed. New checks cover PNG dimensions/transparency format, WebP/ICO assets, manifest wiring, Wuu titles/navigation/logo loading, and mobile/dark rendering. Existing CSV export now downloads `wuu-preview-trips.csv`. Full evidence and source preservation hash are in `verification.md`.

Latest tracking/replay slice: **20 unit tests PASS**; **99.04% lines/statements, 95.56% branches, 96.77% functions** within the configured pure-file coverage scope (new tracking modules: 100%). Typecheck, build and boundaries passed. Final rebuilt production browser run on port 3000: **22/22 PASS in 1.8 minutes**, including movement, stationary/pause/reset/filter/follow, replay seeking/completion/background-pause and mobile/dark accessibility. Simulation is not live telemetry or recorded GPS. Details and remaining gaps are in `verification.md`.

Geofence slice: `pnpm typecheck`, `pnpm build`, and `pnpm check:boundaries` passed. Coverage: **15 tests**, **98.62% lines/statements, 94.3% branches, 96% functions** within the configured pure-file scope. Rebuilt production browser run on port 3001: **19/19 tests passed in 1.5 minutes**, including editor create/edit/drawing/navigation and responsive accessibility checks. See `verification.md` for limitations and prior-slice evidence below.

- RED: `pnpm test` failed because the new domain/policy/adapter modules did not yet exist (checkpoint `15bf8a9`).
- GREEN: `pnpm test:coverage`: passing unit tests cover the **explicitly scoped pure-domain/policy/preview-adapter files**, not whole-app coverage. Final percentages and browser outcomes are recorded in `docs/verification.md`.
- `pnpm typecheck`: passed on final implementation.
- `pnpm build`: production build passed (Nuxt 4.6.2 / Vue 3.5.44).
- `pnpm check:boundaries`: passed.
- Earlier detail interaction/accessibility suite: 15 tests passed against the production build. Detail coverage includes 18 route/theme/viewport combinations (three routes, light/dark, 375/768/1440px). Initial preview verification also included a 36-combination responsive sweep. See `docs/verification.md` for scoped coverage, evidence, screenshots, and limitations.

## Not done

Live fleet integration, session/auth flows, secure role/tenant enforcement, live GPS transport, telemetry streaming, trackers/rules editing, server-persisted geofence mutation/monitoring, real notification delivery, organization/user mutations, billing/ride-hailing/payouts. Real geographic map rendering is implemented, not on this deferred list.

## Next slice

Real geographic rendering, geofence create/edit and mock-data tracking/replay are delivered. Production-readiness work takes priority over adding more concept screens: approve/configure the deployment tile provider, resolve backend identity/effective-access and tenant-isolation contracts, then implement authenticated read-only fleet adapters. Remaining organization/member/device/rule detail work is tracked in `detail-pages-plan.md`; it is not a substitute for release gates.

Before implementing real authentication or connecting fleet reads, agree the backend security contract and verify effective permissions, tenant identity, token/session lifecycle, DTOs, pagination, and error envelopes. Keep preview isolated; do not silently connect the present UI to insecure operations. Any backend changes require separate authorization and isolation from the active checkout.

## Drift protocol

1. Read canonical scope, API, access, architecture, design, and progress docs.
2. Pick one acceptance-sized vertical slice; verify contracts against current backend changes.
3. Add regression tests first, capture RED, then implement and capture GREEN.
4. Run typecheck, boundary check, unit tests, relevant browser tests, and production build.
5. Record what is verified, what is merely illustrative, and the next blocker.
6. Do not mark a screen integrated because its preview works. Do not add a second state/UI/HTTP architecture.
