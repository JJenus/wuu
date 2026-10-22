# Wuu design system

## Personality and layout

Calm, clear, approachable. Operational clarity, not a wall of telemetry. The supplied blue Wuu mark, clean slate typography, generous grouping, fine borders, and restrained status colors. Existing teal interface accents remain unchanged. Avoid added gradients/glows except a subtle sidebar illustration; preserve the original logo's blue shading.

Brand assets: `public/brand/logo.png` is the transparent 512px square master; 96/192px PNG and lossless WebP variants serve the 44px sidebar mark at standard/high density. `public/favicon.ico` contains 16/32/48px sizes, with a 32px PNG fallback, 180px Apple icon, and 192/512px manifest icons. All preserve aspect ratio and padding. `tools/prepare-brand.py` (Pillow) regenerates these from the untouched supplied JPEG. White-background cleanup is a raster color key, not a vector reconstruction. Do not stretch, tint, or wrap the raster in an SVG and claim it is vector artwork. There is no service worker or offline capability implied by the manifest.

- Fixed desktop sidebar, compact breadcrumb/search header, responsive content canvas.
- Overview hierarchy: heading → preview disclosure → derived metrics → map/attention → fleet table.
- Mobile: off-canvas nav, 2-column metrics, stacked panels, contained horizontal data-table scroll.
- Keep controls usable with keyboard, give icon-only buttons names, and label sample/offline/stale states explicitly.
- Status uses words and dots/icons, never color alone. Respect reduced motion.
- Scrollbars: native scrolling with 12px rounded inset thumbs, surface rails, theme-aware slate tokens, and teal hover/drag. Preserve keyboard/table scrolling and forced-colors system controls; never hide the only scrolling affordance.

## Tokens

Nuxt UI v4 semantic aliases: primary teal, secondary blue, neutral slate, success green, warning amber, error red, info blue. Primary brand is `#0B796F` in light mode; theme-specific semantic CSS variables control surfaces/text/borders. Text/status colors are contrast-tuned for their intended surfaces. Never hardcode `bg-white` or manual dark/light component surface pairs.

`app/assets/css/main.css` owns raw design tokens and shared layout classes. `app/app.config.ts` owns Nuxt UI aliases. Per-component exceptions use documented `ui` slots, not internal DOM hacks.

- Default surface → `--ui-bg`; page → `--ui-bg-muted`; overlay → `--ui-bg-elevated`.
- Main ink → `--ui-text-highlighted`; body → `--ui-text`; secondary → `--ui-text-muted`.
- Quiet accent → `--wayline-tint`; status backgrounds use semantic variables in both themes.
- Font: self-hosted Inter Variable with system fallback.
- Cards: 9–10px radius, 1px border, generally no shadow.
- Spacing: 4/8px rhythm; 18–24px card paddings and gaps; 34px desktop content gutters.

## Shared patterns

`UiPageHeading`, `UiStatusBadge`, `UiEmptyState`, `VehicleTable`, `AddVehicleModal`, `FleetMap`, `MetricCard`. Nuxt UI owns buttons, inputs, forms, modals, switches, toasts, popovers, and color mode.

All actions need a real result or explicit unavailability. Do not show inert “Create”, “Save”, or “Send” controls for unsupported capabilities. Errors stay close to the relevant control; successful session-only changes say “preview”.

## Map and charts

Camera controls: preserve map-art proportions, keep vehicle hit targets constant while zooming, and keep controls/search separate from the transformed world. Fleet/replay gestures include drag and pinch; wheel zoom requires map focus or Ctrl/Meta. Show a compact keyboard/gesture hint, make fit/reset available, and release follow on manual navigation. Search results are keyboard-operable buttons with clear preview/live provenance; errors and no-results states must remain visible. See `maps-integration.md` for geographic limitations.

Movement/replay: keep the simulation label beside controls and disclose invented paths beside replay. Start paused; use explicit play/pause, speed, reset and keyboard-accessible range controls. Selected trails are teal and the full illustrative route is dashed. Follow-camera is opt-in. Pause background tabs and clean up animation on navigation; never announce every animation frame to assistive technology.

Maps use real geographic Leaflet tiles with visible provider attribution and metric scale. Markers have constant-size hit targets, distinct status symbols, hover/focus names and persistent selection rings. Nearby markers cluster and overlapping groups expand. Keep basemap errors separate from fleet-data errors. Mock telemetry is disclosed beside controls, not by calling real map tiles an illustration. Sparkline shapes remain sample trend illustrations, not verified analytics.

## Verification limits

Geofence maps use geographic circles with meter radii and geographic polygons. Handles are draggable/keyboard-nudged, with numeric alternatives and fit/reset controls. Geometry changes remain session-only, not server monitoring or surveyed boundaries. Preserve ordinary scrolling outside the map. Preview assignments are not live occupancy.

Responsive browser screenshots and automated checks are evidence of tested states, not a complete accessibility certification. Manual screen-reader, contrast, and keyboard review remain necessary before production.
