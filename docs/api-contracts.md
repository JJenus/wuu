# Backend capability inventory

Source: sibling `../sidie/`, read-only inspection on 2026-10-21. **No endpoint below is connected in the frontend yet.** Endpoint existence is not runtime/integration proof. Recheck controller/DTO/service changes before each slice.

| Domain                | Controller base              | Observed capabilities                                        | Status                                                         |
| --------------------- | ---------------------------- | ------------------------------------------------------------ | -------------------------------------------------------------- |
| Auth                  | `/api/v1/auth`               | register, login, refresh, logout, change-password            | API exists; integration unverified                             |
| Current identity      | `/api/v1/users/me`           | User ID, names, org ID, role names; no effective permissions | API exists; contract gap                                       |
| Users                 | `/api/v1/users`              | scoped list, create, read/update/delete, assign role IDs     | API exists; authorization needs verification                   |
| Organizations         | `/api/v1/organizations`      | CRUD and paged list                                          | API exists; platform scoping needs verification                |
| Vehicles              | `/api/v1/vehicles`           | CRUD/list                                                    | API exists; tenant isolation needs verification                |
| Trackers              | `/api/v1/trackers`           | CRUD/list                                                    | API exists; integration unverified                             |
| Trips                 | `/api/v1/trips`              | list/detail; `/export` streams CSV                           | API exists; parameter/DTO integration unverified               |
| Device commands       | `/api/v1/commands`           | create/list/detail plus send/deliver/fail lifecycle updates  | Unsafe to expose directly as end-user workflows                |
| Alerts                | `/alerts`                    | list/detail, acknowledge, resolve, bulk operations, stats    | API exists; integration unverified                             |
| Alert rules           | `/alerts/rules`              | CRUD, templates, enable/disable, batch                       | API exists; overlapping services noted in backend instructions |
| Geofences             | `/alerts/geofences`          | CRUD, vehicle queries, violation check                       | API exists; geometry/scoping contract unverified               |
| Notifications         | `/api/notifications`         | delivery list/read state, preferences, templates             | API exists; ownership/platform boundaries unverified           |
| Notifications socket  | `/ws/notifications` (SockJS) | PING/SUBSCRIBE/UNSUBSCRIBE acknowledgments                   | Not verified secure or a vehicle telemetry feed                |
| Roles/permissions     | No REST controller found     | Entities/repositories and role seeds exist                   | Backend missing                                                |
| Billing/rides/payouts | No REST controllers found    | Design documents only                                        | Future scope                                                   |

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

Backend fixes require separate authorization and an isolated backend workstream. Keep `device-comm` unchanged; its independent deployment constraint remains in force. This frontend must not silently restructure business modules or touch active backend changes.
