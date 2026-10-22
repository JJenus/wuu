# Detail pages and actions

## Delivery contract

Extend the existing preview, not its trust boundary. Detail URLs must work on reload, link from their parent lists, keep the correct navigation section active, and provide a recovery link for an unknown ID. Related records use stable IDs, not display-name matching. No new dependencies or backend changes.

## Slice 1 — fleet context (current implementation)

| Page             | Detail content                                                                            | Working preview actions                                        | Acceptance                                                                                                                                                              |
| ---------------- | ----------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/alerts/:id`    | Severity, status, description, sample event age, related vehicle, response checklist      | Acknowledge, resolve, open vehicle, return to alerts           | Mutations update the same workspace cache as the alert list; resolved alerts have no further mutation controls; failures remain errors; no notification/command is sent |
| `/trips/:id`     | Origin/destination, start, duration, distance, driver, related vehicle, export disclosure | Export just this record, open vehicle, browse trips            | Download contains exactly the selected journey; explicit unavailable playback rather than an invented GPS path                                                          |
| `/geofences/:id` | Local preview geometry, location/event setting, linked sample vehicles                    | Edit preview boundary, inspect coordinates, pan/zoom/fit/reset | Membership explicitly illustrative; no server persistence or live occupancy claims                                                                                      |
| `/vehicles/:id`  | Existing readings plus linked alerts and recent journeys                                  | Open an individual alert or journey                            | Stable `vehicleId` relationships; no unrelated journeys; clear empty states                                                                                             |

Trip quick-view remains available from the vehicle-name button; the arrow opens the full route. CSV serialization is shared and pure, quotes embedded separators/quotes, and neutralizes spreadsheet formulas. Export failures surface as errors.

### Scrollbar design

- Rounded, inset slate thumbs; quiet surface-colored rails; teal hover/drag accent.
- Theme tokens for light/dark; 12px rails, no custom JavaScript scroll interception or hidden scrollbars.
- Stable page gutter, contained horizontal tables, keyboard-focusable trip scroll region.
- Standard-color fallback for Firefox; system-color fallback for forced-colors mode.
- Browser/OS overlay-scrollbar preferences still determine whether rails are always visible.

### Verification targets

`tests/e2e/details.spec.ts`: route entry from lists, linked-record navigation, alert transitions, selected-trip download, reload, missing-ID recovery, 375/768/1440px light/dark layouts, automated accessibility checks, keyboard horizontal scroll.

`tests/unit/trip-export.test.ts`: quoted CSV, empty selection, formula neutralization. Coverage is scoped to pure domain/policy/adapter/export code, not all UI.

## Detail-page expansion

### User-requested tracking movement and trip replay

- `/map`: opt-in moving-marker simulation with play/pause, reset, speed (1/2/4/8×), selected-vehicle trail, follow camera and status-filtered vehicle picker. Only moving fixtures animate; parked/idle/offline remain stationary. A seamless two-minute canvas loop is explicitly not a live feed.
- `/trips/:id`: an explicitly invented per-trip path, traveled segment, play/pause/restart, keyboard/pointer timeline, 1/8/32/64× speed, follow camera, simulated speed and completion state. In-progress fixtures disclose elapsed-demo-only behavior; unknown trips/unsupported replay IDs get no fabricated track.
- Clock lifecycle: start paused, clamp seeking, stop at replay end, pause hidden tabs, cancel frames on unmount, and never mutate workspace records or exported aggregates. No auto-motion even when reduced motion is not enabled.
- Tests: `tests/unit/playback.test.ts`, `tests/e2e/tracking.spec.ts`; current backend gaps and actual outcomes in `api-contracts.md` and `verification.md`.

### Added user-requested slice: geofence creation/editing (implemented preview)

Routes: `/geofences/new` and `/geofences/:id/edit`. Create/edit the local preview record through the existing adapter and query cache. Fields: name (1–100), description (up to 500), location label, circle/polygon boundary, event setting, enabled flag, and at least one valid sample vehicle.

Interactions: circle placement, draggable center/radius, radius slider; ordered polygon vertices, finish, add/remove/numeric editing, draggable/keyboard handles; grouped map-drag undo, clear, pan/zoom/fit/reset. Numeric coordinate entry is the keyboard alternative to pointer drawing. Reject duplicate names, non-finite/out-of-preview coordinates, radii outside 50–5,000 m or fractional meters, polygons outside 3–50 distinct vertices, crossings/touching non-adjacent edges, and area below 25 m². Pan/zoom never modifies saved geometry.

Saving updates the current session and routes to details. Cancel/internal navigation asks before discarding dirty changes; reload uses the browser's native unsaved-change warning. Drafts never mutate saved records before save. Unknown IDs fail closed. No geocoder, real tiles, server mutation, or actual event engine is implied.

Acceptance targets: `tests/unit/geofence.test.ts` and `tests/e2e/geofence-editor.spec.ts`; final results in `verification.md`.

### Remaining detail-page slices (not implemented)

| Slice                    | Detail page and content                                                                                            | Preview actions to consider                                                | Live prerequisites / boundaries                                                                                                                               |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2. Organization context  | `/admin/organizations/:id`: sample profile, organization status, capability readiness, linked sample fleet/team    | Browse sample resources with explicit organization context                 | Never switch a real tenant using a route parameter; verify server-scoped membership and administrative permissions first                                      |
| 3. Member & role context | `/team/:id`, `/admin/roles/:id`: fictional profile, organization membership, role template, capability explanation | Inspect role template and sample assignments                               | Invites, role assignment, suspension, and removal require server authorization, self-lockout protections, confirmation and audit contracts; no fake send/save |
| 4. Tracker context       | Vehicle-linked device details: connectivity, last observation, assignment status, sample event history             | Inspect linked vehicle and available readings                              | Verify device DTOs and tenant-safe assignment endpoints; no firmware/remote-command simulation presented as execution                                         |
| 5. Alert rule context    | Rule details: condition, scope, threshold, notification channel readiness                                          | Inspect rule examples and related alerts                                   | Verify rule evaluation, tenant/vehicle scope, version/concurrency semantics, and notification delivery before editing                                         |
| 6. Real trip context     | Recorded route, stops, timestamps, pagination, export status                                                       | Provider-backed map and telemetry playback only after approved integration | Authenticated historical points, timestamp/timezone rules, provider attribution/licensing, export authorization; never fabricate missing points               |

Each subsequent slice starts with current contract inspection, its own RED tests, and explicit confirmation of whether it is read-only/sample or authorized live behavior. These rows are planned work, not delivered capabilities.

## Implementation order

1. Deliver the three fleet-context routes and scrollbar refinement.
2. Review the organization/member detail experience before expanding sample catalogs.
3. Agree the backend security contract before any live read or mutation.

For outcomes and remaining limits, see `verification.md`. For production integration blockers, see `api-contracts.md` and `access-matrix.md`.
