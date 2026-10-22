# Geographic maps and integration

## Requirement and implementation

Real map rendering is required even when fleet data is mocked. The prior SVG/canvas-percent illustration is replaced by **Leaflet 1.9.4** and **leaflet.markercluster 1.5.3**. These dependencies provide geographic projection, raster tiles, keyboard/mouse/touch navigation, geographic vectors, clustering and overlapping-marker expansion without a WebGL requirement. They do not provide authentication or fleet telemetry.

All fleet, vehicle, trip and geofence views use the same `GeographicMap` surface. `useGeographicMap` owns client-only engine initialization, raster transport, keyed marker updates, ResizeObserver resizing and map/layer/timer teardown. Vue components remain HTTP-free. The engine is dynamically imported after mount, not during SSR. Tiles are real network resources, not fixtures or a local illustration.

## Operator behavior

- Geographic dragging, touch pinch, keyboard arrows/+/- and Home reset; wheel zoom activates on map focus so normal page scrolling remains available.
- Fit fleet/route, reset, local vehicle/place search, label and route layers, metric scale and visible provider attribution.
- Status markers use distinct symbols, labels and colors. Hover/focus reveals names; selection adds a persistent ring, `aria-pressed`, detail card and synchronized vehicle-list state. Details can be dismissed.
- Nearby assets cluster at low zoom. Groups zoom to their bounds and spiderfy coincident points at maximum zoom. Selecting an asset from search/list reveals it through the cluster. Group selection highlighting reflects the selected child.
- Follow is opt-in, starts off, and is released on manual pan/zoom/keyboard navigation. Simulation/replay still start paused and stop in background tabs.
- Geofence circle radii use Leaflet's meter-based geographic circles, not pixel scaling. Polygon/center/radius handles support pointer dragging and keyboard nudging. Search, pan, fit and zoom never save or alter geometry.
- Query loading/error states hide plotted fleet data. Tile failures display a separate retryable error while preserving fleet state. No tile or data failure becomes a sample illustration.

## Geographic data contracts

`Vehicle.position` is `{ lat, lng } | null`; absent positions remain absent. Newly added vehicles have `position: null` until a tracker supplies a position, not an invented map center. Invalid/nonfinite/out-of-range or polar coordinates unsupported by Web Mercator are not plotted and are counted in the map's missing-location notice.

`MapScene` accepts geographic markers, selected ID, routes/trails, search coordinate, geometry and interaction flags. `MapSearchService.search(query, { signal })` returns normalized results with `{ latitude, longitude }`. Both preview and live geographic results can be focused by the renderer. Components do not infer geography from screen percentages. The legacy illustrated camera/projection is removed.

`normalizeLocation` is the null-safe DTO seam. Zero coordinates are valid geographic values, not used as a missing-data fallback. Backend `VehicleResponse.currentLocation` and `LocationResponse.latitude`, `longitude`, `speedKmh`, `recordedAt` remain the observed source contract; no backend transport is connected. Friendly names, drivers, status and freshness policy still require adapter normalization and verified contracts.

Preview positions and replay paths now contain explicit geographic coordinates. They remain fictional telemetry, not historical GPS or routed/snap-to-road journeys. Search currently covers fleet fields and a small labeled sample-place catalog; global address geocoding requires an injected, approved search adapter. Search is abortable/debounced, discards late responses, and surfaces provider errors without fixture fallback.

Geofence validation supports site-scale circles/polygons throughout the Web Mercator latitude range (±85.0511°) and valid longitudes. Polygon areas are spherical square-meter estimates, not surveys. Antimeridian-crossing polygons are explicitly rejected rather than drawn/validated incorrectly. Server persistence and violation monitoring are not implemented.

## Tile provider and deployment

Default: `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, with linked **© OpenStreetMap contributors** attribution. Requests are normal on-screen raster requests using browser caching and referrer headers. No bulk prefetching, offline downloads, cache-busting, service-worker tile storage or public geocoding requests are implemented. OpenStreetMap public tiles have **no SLA**; they are not an enterprise hosting commitment.

Set these runtime variables for an approved production provider with sufficient capacity and appropriate licensing:

```text
NUXT_PUBLIC_MAP_TILES_URL=https://your-provider.example/{z}/{x}/{y}.png
NUXT_PUBLIC_MAP_TILES_ATTRIBUTION=Your map provider
NUXT_PUBLIC_MAP_TILES_ATTRIBUTION_URL=https://your-provider.example/attribution
NUXT_PUBLIC_MAP_TILES_MAX_ZOOM=19
```

URL templates must use HTTPS and include `{z}`, `{x}`, `{y}`; attribution and supported zoom limits are validated. Attribution text is inserted as text, not provider HTML. Runtime configuration is public: use only domain-restricted public browser keys, never secret server credentials. Tile traffic exposes the browser IP/referrer and viewed map area to the provider; approve the provider under the deployment privacy policy. No user-location permission is requested.

An operator must verify provider terms, usage capacity, key restrictions, allowed origins/CSP and attribution requirements before deployment. Existing headers preserve cross-origin origin referrers. Provider URL/attribution configuration is independent of fleet-data source; a live API failure must never fall back to mock vehicle data.

## Remaining application release gates

Real rendering is implemented, not deferred. Typed live read adapters and an allowlisted runtime gateway now exist, but the **whole app is not yet production-release ready**: authenticated sessions, server-enforced effective permissions/tenant isolation, validated live-backend behavior, telemetry reconnect/order/staleness rules, durable geofence saves and commands remain separate backend/integration gates. The active backend checkout remains untouched.

Real-device touch, manual screen-reader review, non-Chromium engines, provider-specific deployment tests, large-fleet performance measurements and surveyed/geodesic edge-case validation are not implied by Chromium tests. Selected-file unit coverage excludes the engine lifecycle and Vue UI; browser tests exercise the integrated map behavior.

## Verification

`geographic-map.test.ts` covers geographic/null/provider validation, meter-scale radius handles, global boundaries and spherical area. `map-search-provider.test.ts` / `map-search-workflow.test.ts` cover sample search, missing positions, cancellation, late responses and provider errors. Browser suites cover actual tile loading/attribution, tile failure/recovery, marker selection, clustering, geographic navigation, gestures, boundary editing, replay and mobile/theme accessibility. Actual final results are recorded in `verification.md`.
