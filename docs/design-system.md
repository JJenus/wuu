# Wayline design system

## Personality and layout

Calm, clear, approachable. Operational clarity, not a wall of telemetry. A teal wayfinding mark, clean slate typography, generous grouping, fine borders, and restrained status colors. Avoid gradients/glows except a subtle sidebar illustration; avoid heavy shadows and excessive animation.

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

The current SVG map is a deterministic geographic illustration. Use fixed local assets, accessible marker buttons, and a non-navigation disclaimer. It is not a real map engine. Sparkline shapes are decoration/sample trend illustrations, not evidence of historical performance; replace with verified time-series data when analytics exist.

## Verification limits

Responsive browser screenshots and automated checks are evidence of tested states, not a complete accessibility certification. Manual screen-reader, contrast, and keyboard review remain necessary before production.
