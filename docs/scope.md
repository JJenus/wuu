# Scope and acceptance contract

## Product

Wuu is a calm fleet-operations workspace for customers/businesses and a separate platform-administration experience. One Nuxt application, feature-first modules, semantic design tokens, pnpm only. No React.

## Current delivery: isolated interactive preview

| Capability           | Acceptance condition                                                                                                                     | State                                      |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| Overview             | Counts derive from eight fictional vehicles and current preview alert state                                                              | Implemented                                |
| Fleet                | Search by vehicle/registration/driver/location; filter status; route to details                                                          | Implemented                                |
| Vehicle addition     | Validate required fields and duplicates; explicit session-only confirmation                                                              | Implemented                                |
| Map                  | Real geographic tiles, projected markers/routes/boundaries, selection/highlighting, clustering, gestures, fit/search and provider errors | Leaflet renderer; mock fleet data          |
| Tracking movement    | Opt-in simulated moving markers, selected-vehicle trail/follow, pause/reset/speed/status filters                                         | Preview simulation, not live telemetry     |
| Trip replay          | Explicit illustrative paths, play/pause/restart, timeline seeking, speed and follow controls                                             | Preview simulation, not recorded GPS       |
| Alerts               | Active → acknowledged → resolved; update related overview counts                                                                         | Implemented                                |
| Trips                | Search, detail dialog, filtered preview CSV                                                                                              | Implemented                                |
| Notifications        | Preview alert list and session-only preference switches                                                                                  | Implemented                                |
| Team, organizations  | Read-only, clearly illustrative; no fake save controls                                                                                   | Implemented concept screens                |
| Geofences            | Session-only circle/polygon creation/editing, map and coordinate controls, validated assignments, save/cancel                            | Interactive preview; not server monitoring |
| Roles                | Four code-derived templates and proposed-role register                                                                                   | Implemented read-only                      |
| Platform             | Separate nav, capability inventory, security gates; sample data only                                                                     | Implemented concept screen                 |
| Theme/responsiveness | Mobile nav, contained table scrolling, light/dark components                                                                             | Implemented; see verification evidence     |

Miniature trend charts and geofence occupancy are illustrative, not computed historical analytics.

Detail-page/action expansion is specified in `detail-pages-plan.md`: alert, trip, and geofence routes first, followed by separately scoped organization/member/device/rule details. This does not authorize live integration or unsupported mutations.

Real geographic map rendering is required even with mocked data. Map acceptance and provider deployment requirements are defined in `maps-integration.md`. Global address geocoding and live GPS transport remain separate integrations, not substitutes for rendering.

## Explicit exclusions

No live fleet API connection, login, secure sessions, effective RBAC, telemetry polling/streaming, device commands, server-persisted geofence editing or violation monitoring, invitation sending, role editing, tracker management, alert-rule editing, subscription billing, payouts, riders, or driver workflows in this delivery. Real map tiles are implemented; mock data does not make the whole app production-release ready. These remaining capabilities are not complete simply because a related route exists.

## Subsequent slices (one at a time)

1. Agree backend permission and tenant-isolation fixes; verify identity/effective-access contract.
2. Authenticated session boundary and read-only fleet/vehicle integration with normalized DTOs.
3. Read-only trips, real streaming CSV, and approved provider-backed map.
4. Alerts and notifications with server-authoritative mutations.
5. Vehicles/trackers and assignment workflows with authorization tests.
6. Rules/geofences using verified geometry and lifecycle contracts.
7. Business/admin management APIs and actual role assignments.
8. Separately approved future business domains only.

Every slice needs contract verification, acceptance tests, honest loading/error/empty states, boundary checks, type checking, browser tests, and a progress update. New dependencies/roles/features need explicit rationale and approval; do not expand the scope just to make a screen look complete.
