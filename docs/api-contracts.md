# Backend capability inventory

Source: sibling `../sidie/`, read-only inspection on 2026-10-21. **No endpoint below is connected in the frontend yet.** Endpoint existence is not runtime/integration proof. Recheck controller/DTO/service changes before each slice.

| Domain                | Controller base              | Observed capabilities                                                 | Status                                                         |
| --------------------- | ---------------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------- |
| Auth                  | `/api/v1/auth`               | register, login, refresh, logout, change-password                     | API exists; integration unverified                             |
| Current identity      | `/api/v1/users/me`           | User ID, names, org ID, role names; no effective permissions          | API exists; contract gap                                       |
| Users                 | `/api/v1/users`              | scoped list, create, read/update/delete, assign role IDs              | API exists; authorization needs verification                   |
| Organizations         | `/api/v1/organizations`      | CRUD and paged list                                                   | API exists; platform scoping needs verification                |
| Vehicles              | `/api/v1/vehicles`           | CRUD/list                                                             | API exists; tenant isolation needs verification                |
| Trackers              | `/api/v1/trackers`           | CRUD/list                                                             | API exists; integration unverified                             |
| Trips                 | `/api/v1/trips`              | list/detail; current controller has no path/history or export handler | API exists; parameter/DTO integration unverified               |
| Device commands       | `/api/v1/commands`           | create/list/detail plus send/deliver/fail lifecycle updates           | Unsafe to expose directly as end-user workflows                |
| Alerts                | `/alerts`                    | list/detail, acknowledge, resolve, bulk operations, stats             | API exists; integration unverified                             |
| Alert rules           | `/alerts/rules`              | CRUD, templates, enable/disable, batch                                | API exists; overlapping services noted in backend instructions |
| Geofences             | `/alerts/geofences`          | CRUD, vehicle queries, violation check                                | API exists; geometry/scoping contract unverified               |
| Notifications         | `/api/notifications`         | delivery list/read state, preferences, templates                      | API exists; ownership/platform boundaries unverified           |
| Notifications socket  | `/ws/notifications` (SockJS) | PING/SUBSCRIBE/UNSUBSCRIBE acknowledgments                            | Not verified secure or a vehicle telemetry feed                |
| Roles/permissions     | No REST controller found     | Entities/repositories and role seeds exist                            | Backend missing                                                |
| Billing/rides/payouts | No REST controllers found    | Design documents only                                                 | Future scope                                                   |

## Important contract details

`VehicleResponse` includes `vehicleId`, `deviceId`, `model`, `licensePlate`, `vin`, engine/ACC/fuel/odometer/fuel-cut fields, `organizationId`, `lastTelemetryTime`, and `currentLocation`. The preview's friendly name, driver assignment, status, distance-today, and map coordinates are fictional UI fixtures—not direct mappings from this DTO.

Pagination wrappers and endpoint prefixes differ between modules. Normalize at the adapter boundary. Do not assume a common envelope or invent a global `/api/v1` prefix for alerting/notifications.

## Release blockers

- SecurityConfig mostly requires authentication, not resource permissions.
- `PermissionService.resolveForUser()` currently collects all permission keys without using role/org inputs.
- JWT carries role names, not effective action/resource scopes.
- User-response organization and login primary organization are derived from the first org-scoped role; secure context switching is not defined.
- Role assignment accepts IDs, but there is no role-catalog management endpoint.
- Notification WebSocket identity can fall back to query `userId`; subscriptions currently acknowledge requests. Do not connect using an untrusted user identifier.
- Device lifecycle endpoints are not a safe replacement for verified transport acknowledgments. UI must not mark a command delivered.

## Production implementation boundary

### Maneuverable map UI recheck (2026-10-22)

Read-only inspection reconfirmed `VehicleResponse.currentLocation` / `lastTelemetryTime` and `LocationResponse.latitude`, `longitude`, `speedKmh`, `recordedAt`. Location fields are nullable reference types. The real Leaflet renderer now accepts geographic positions, with `normalizeLocation` as a null-safe adapter seam; friendly names, driver and freshness/status policy must not be guessed from DTOs. No fleet transport was connected and no backend file changed. Actual geographic tile rendering, provider configuration and remaining authentication/transport gates are documented in `maps-integration.md`.

### Tracking and replay contract recheck

Read-only inspection of the current `core-tracking/.../TripController.java`: trip responses contain vehicle ID, start/end time, active flag and aggregate distance/speed/fuel/reasons, but no recorded GPS points. The current controller only declares list/detail routes; the earlier export observation is no longer present in that controller. `notification/.../VehicleTrackingWebSocketHandler.java` still handles PING/SUBSCRIBE/UNSUBSCRIBE and notification sends; it derives identity from session attributes or query `userId`. Its registration remains `/ws/notifications`. No verified tenant-scoped telemetry/history endpoint or GPS streaming contract was found in this recheck.

Movement and replay therefore remain explicit opt-in simulations backed only by dedicated preview tracks. They never connect this socket or claim freshness, true travel history, or predicted destinations. Live tracking requires separately verified authenticated subscriptions, tenant/asset scope, ordered timestamps, staleness/gap handling, reconnect rules, and historical GPS samples. Live failures must never be replaced with these demo tracks.

### Geofence editor contract recheck

Read-only recheck at backend commit `822f2a2b7314cd2f0df7184c6762e28028b76e6d` (active checkout has uncommitted work). `CreateGeofenceRequest` supports a single vehicle ID, shape type, circle center/radius, ordered polygon points, active, and createdBy. `UpdateGeofenceRequest` has no polygon points; it exposes description and vehicleIds, but `GeofenceController.convertToEntity(UpdateGeofenceRequest)` does not map those fields. The response also projects one vehicle ID from the set. These are unresolved integration gaps, not frontend contracts to guess around.

The new frontend editor uses a deliberately separate session-only model: name/description/location label, multiple sample vehicle IDs, event setting, enabled flag, and circle/polygon geometry. No API call, violation check, or real rule activation is performed. Live polygon editing, assignments, effective permissions, tenant checks, and event delivery must be verified in a separately authorized backend workstream.

Backend fixes require separate authorization and an isolated backend workstream. Keep `device-comm` unchanged; its independent deployment constraint remains in force. This frontend must not silently restructure business modules or touch active backend changes.
