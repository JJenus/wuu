# Backend and live adapter contracts

Status: **live read-only adapters and the server gateway are implemented; no authenticated end-to-end run against the Java backend has been performed.** Backend observations come from read-only inspection of `../sidie/` on 2026-10-21/22 (commit `822f2a2b7314cd2f0df7184c6762e28028b76e6d`, with uncommitted work present). Endpoint existence is not integration proof.

## Frontend live path

The browser calls only same-origin `/api/_wuu/<resource>`. The Nitro gateway resolves the private upstream origin/token at runtime and forwards allowlisted requests:

| Browser resource | Upstream base        | Client service | Methods used     |
| ---------------- | -------------------- | -------------- | ---------------- |
| `vehicles`       | `/api/v1/vehicles`   | Fleet          | GET list/detail  |
| `trips`          | `/api/v1/trips`      | Trips          | GET list/detail  |
| `alerts`         | `/alerts`            | Alerts         | GET list/detail  |
| `geofences`      | `/alerts/geofences`  | Geofences      | GET list/detail  |
| `notifications`  | `/api/notifications` | None yet       | Allowlisted only |

The gateway also accepts verified-upstream methods for those resources, but the UI currently invokes only GETs. Unknown resources, encoded separators, traversal segments, unsafe origins, unsupported methods, and redirects fail closed. The upstream timeout is 15 seconds and responses are marked `no-store`.

Origin/token resolution:

1. Origin: `NUXT_API_ORIGIN` (private runtime config) or `WUU_API_ORIGIN`.
2. Token: `wuu_access_token` cookie, then `NUXT_API_BEARER_TOKEN`, then `WUU_API_BEARER_TOKEN`.
3. HTTPS is required for remote origins; `http://localhost`, `http://127.0.0.1`, and loopback IPv6 are allowed for development.
4. No login/session-issuance flow exists. The optional cookie must be issued by a trusted deployment boundary.

## Pagination and normalization

- The frontend assumes Spring-style paged responses with `content`, page metadata, `first`/`last`/`empty`, and size/page-number fields. `pagedResponseSchema` accepts the observed `pageNumber` shape; `springPageSchema`/`toPage` accept the `number` shape where a module differs.
- Vehicle list: page size 200, maximum 50 pages.
- Trip/alert/geofence lists: page size 100, maximum 50 pages.
- Stop on `last`, `empty`, or an empty page. A malformed page body is a contract error.
- Prefixes/envelopes differ by module. There is no global assumed `/api/v1` prefix.
- `204` responses pass through the normal schema path. A schema-incompatible 204 is a contract error, not a server/empty success.

## Observed upstream capabilities

| Domain                | Controller base              | Observed capability                                          | Frontend status                                  |
| --------------------- | ---------------------------- | ------------------------------------------------------------ | ------------------------------------------------ |
| Auth                  | `/api/v1/auth`               | register, login, refresh, logout, change-password            | Not integrated; no session UI                    |
| Current identity      | `/api/v1/users/me`           | User ID, names, org ID, role names; no effective permissions | Not consumed; contract gap                       |
| Users / team          | `/api/v1/users`              | list/create/read/update/delete, assign role IDs              | Live team page fails closed                      |
| Organizations         | `/api/v1/organizations`      | CRUD and paged list                                          | Concept preview only                             |
| Vehicles              | `/api/v1/vehicles`           | CRUD/list                                                    | GET list/detail live; create disabled            |
| Trackers              | `/api/v1/trackers`           | CRUD/list                                                    | Not integrated                                   |
| Trips                 | `/api/v1/trips`              | list/detail; no path/history/export handler observed         | GET list/detail live; no replay/live GPS         |
| Device commands       | `/api/v1/commands`           | create/list/detail plus lifecycle updates                    | Not exposed                                      |
| Alerts                | `/alerts`                    | list/detail, acknowledge, resolve, bulk, stats               | GET list/detail; live transitions disabled in UI |
| Alert rules           | `/alerts/rules`              | CRUD/templates/toggle/batch                                  | Not integrated                                   |
| Geofences             | `/alerts/geofences`          | CRUD, vehicle queries, violation check                       | GET list/detail; live create/edit disabled in UI |
| Notifications         | `/api/notifications`         | delivery list/read, preferences, templates                   | Allowlisted; no client contract                  |
| Notifications socket  | `/ws/notifications` (SockJS) | PING/SUBSCRIBE/UNSUBSCRIBE acknowledgments                   | Not connected; not a GPS feed                    |
| Roles/permissions     | No REST controller observed  | Entities/repositories/seed roles                             | Concept templates only                           |
| Billing/rides/payouts | No REST controllers observed | Design documents only                                        | Future scope                                     |

## Live DTO mapping

### Vehicles

Accepted fields include `vehicleId`, `deviceId`, `model`, `licensePlate`, `vin`, engine/ACC/fuel/odometer/fuel-cut fields, `organizationId`, `lastTelemetryTime`, and nullable `currentLocation` (`latitude`, `longitude`, `speedKmh`, `recordedAt`).

- ID: `vehicleId`.
- Display name: license plate when present, otherwise the vehicle ID; no owner/driver name is invented.
- Driver: `null` because no verified driver assignment is in this DTO.
- Status: derived only from fresh telemetry. Missing/invalid/stale timestamps are `offline`; speed above 1 km/h is `moving`; zero speed with `accStatus === false` is `parked`; otherwise `idle`.
- Position: normalized only when both coordinates are valid. Missing position is never replaced with a sample location.
- Fuel, odometer distance, relative update time, and last observed time come from DTO fields or remain explicitly missing.

### Trips

Accepted fields: `tripId`, `vehicleId`, `startTime`, `endTime`, `isActive`, `totalDistanceKm`, `averageSpeedKmh`, `maxSpeedKmh`, `fuelConsumedLiters`, `startReason`, `endReason`.

- `vehicle` is the vehicle ID (not a friendly name); `driver`, origin, and destination are `null`.
- `isActive` or a missing end time marks a trip in progress.
- Start/end/duration and distance are normalized; missing values are explicit.
- The API provides no GPS path, so live trip replay is not offered.

### Alerts

Accepted fields include numeric `alertId`, `vehicleId`, `alertType`, `severity`, `message`, coordinates, speed, trigger/acknowledge/resolve timestamps, actor fields, `resolutionNotes`, and flags.

- Status: `resolved` → `acknowledged` → `active` from server flags.
- Severity falls back only to `info` for unknown/null values; it is not fabricated from a client role.
- Title/icon are presentation derivations of `alertType`/`message`; description uses supplied text or an explicit “No further detail” message.
- Acknowledge requires an actor; resolve requires an actor plus review notes of at least 10 characters. The current live UI has no verified identity, so it does not offer these actions.

### Geofences

Accepted fields include numeric `geofenceId`, a single nullable `vehicleId`, `name`, `shapeType`, circle center/radius, `active`, polygon `points`, description, and timestamps.

- A valid circle requires center/radius. A valid polygon requires at least three points; otherwise geometry is `null` and the UI shows that no boundary is available.
- The API projects one vehicle ID, not a multi-vehicle set. Missing description/events/location are labeled “not provided”.
- The service has a narrow live circle-create request path, but the UI keeps live creation disabled until the route/action contract and update geometry/assignment behavior are verified.
- `UpdateGeofenceRequest` does not map polygon points or vehicle IDs into the entity. Update is deliberately unavailable.

### Preferences

No verified current-user preferences contract is consumed. Live preferences return a typed `unavailable` error rather than fixtures. Preview switches remain session-only.

## Release blockers

- `PermissionService.resolveForUser()` currently collects all permission keys without using role/org inputs. JWT role names are not effective permissions.
- SecurityConfig mostly requires authentication, not resource-level authorization.
- User organization/login primary organization are derived from the first org-scoped role; secure context switching is undefined.
- Role assignment accepts IDs but no role-catalog endpoint was found.
- No login/session issuance, refresh, CSRF, or revocation boundary is implemented in this app.
- Notification WebSocket identity can fall back to a query `userId`; it must not be treated as a trusted telemetry or session boundary.
- Geofence update/assignment and alert actor/review contracts remain incomplete.
- Vehicle/tracker creation needs a verified tracker assignment and ID contract.
- Trip history/GPS, tenant-scoped telemetry, and server-side export are absent.
- Production maps require an approved provider, licensing/privacy/capacity review, CSP, and non-Chromium/device validation.

Backend fixes require separate authorization and an isolated workstream. Do not modify the active checkout or snapshot from the frontend repository.
