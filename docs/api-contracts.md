# Backend and live adapter contracts

## Superseding subscription contract — 2026-10-29

Java now has V5/V6 durable subscription catalog/enrollment/grant/usage APIs.
The frontend exposes only authenticated catalog/owner reads and same-origin
pending enrollment/owner cancellation, not grant/catalog administration or paid
activation. Other domains remain read-only. See `subscriptions.md` for the exact
allowlist, precision limits, retry recovery and current acceptance evidence.
Older no-billing-controller/read-only-only statements below are historical.

`GET /api/_wuu/access` maps exactly to `/api/v1/users/me/access` and returns the
current database-derived `{user_id,organization_id,permissions,access_expires_at}`.
The organization may be null; no tenant switching or implicit tenant selection is
offered. Settings shows the snapshot, not role-name guesses. Both Java response
and BFF are no-store; every subsequent action still rechecks server authorization.
No nested `access/*` routes, tokens or session UUID are exposed. This supersedes
the older no-effective-permissions-endpoint limitation only for this read use case.

Status (2026-10-23): **authenticated live read-only frontend implemented; no authenticated end-to-end run against the actual Java deployment has been performed.** Read-only inspection found the active backend refactored under `../sidie/src/main/java/com/surf/tracker/`, with auth hardening documented in `../sidie/SECURITY.md`. HEAD is `551db79`, but the inspected auth/refactor is uncommitted working-tree code. Backend files were not changed or built by this frontend task. Browser tests use a test-only contract server, not Java.

Local runtime update (2026-10-25): the checkout defaults to `http://127.0.0.1:8080` without a context prefix. A limited HTTP smoke against the existing executable JAR verified Wuu login/session/logout and some reads, but alerts returned 401 and health returned 503 (Artemis unavailable). This is not current-source build certification or full browser/authorization verification; see `verification.md`.

## Frontend live path

### Opt-in local account directory (2026-10-25)

Implemented on the pushed `origin/backend-updates` branch, not yet integrated into the active `../sidie` checkout. The temporary `../backend-accounts` directory was deleted after push verification. `GET /api/v1/auth/local-accounts?page=0` returns `{accounts:[{email,display_name}],next_page:number|null}` in pages of 100, sorted by email, freshly querying enabled/unlocked users. It exposes no role, password/hash or token fields. The route is denied by default; enabling requires the explicit local flag, a `local` profile without `prod`/`production`, a literal loopback server binding, and a loopback request peer. Unsafe enabled configurations fail startup.

Wuu's `GET /api/auth/accounts` adds an independent private opt-in (`WUU_LOCAL_ACCOUNT_PICKER=true`) and loopback Host/socket/upstream checks. Disabled requests return `{enabled:false,accounts:[]}` without contacting Java. Enabled responses add `enabled:true`, validate/strip each account, and combine at most 50 pages; malformed pages, over-limit lists, redirects, and upstream errors remain errors. Responses and fetches are no-store. The UI refreshes on mount/refocus/manual request; selecting an account only fills its email, clears the password, and focuses that field. Normal login, identity verification, encryption, rate limits and logout remain unchanged.

The seed runner creates VIEWER and TENANT_ADMIN accounts only when their case-insensitive email is absent. It holds a transaction-scoped database row lock before existence checks and uses existing uniqueness constraints as the final guard. Passwords are supplied via private environment values and bcrypt-hashed; existing accounts and role definitions are never rewritten or elevated by reruns. No passwordless endpoint was added.

The browser calls only same-origin `/api/_wuu/<resource>`. The Nitro gateway resolves the private upstream origin/token at runtime and forwards allowlisted requests:

| Browser resource | Upstream base        | Client service | Methods used     |
| ---------------- | -------------------- | -------------- | ---------------- |
| `vehicles`       | `/api/v1/vehicles`   | Fleet          | GET list/detail  |
| `trips`          | `/api/v1/trips`      | Trips          | GET list/detail  |
| `alerts`         | `/alerts`            | Alerts         | GET list/detail  |
| `geofences`      | `/alerts/geofences`  | Geofences      | GET list/detail  |
| `notifications`  | `/api/notifications` | None yet       | Allowlisted only |

The gateway requires a valid sealed access session and accepts only GETs. Unknown resources, encoded separators, traversal segments, unsafe origins, writes, and redirects fail closed. The upstream timeout is 15 seconds and responses are marked `no-store`.

Origin/token resolution:

1. Origin: `NUXT_API_ORIGIN` (private runtime config) or `WUU_API_ORIGIN`.
2. Token: decrypt `wuu_session` using `NUXT_SESSION_PASSWORD` or `WUU_SESSION_PASSWORD` (at least 32 characters of random secret material). No shared token fallback or legacy plaintext bearer cookie is accepted.
3. HTTPS is required for remote origins; `http://localhost`, `http://127.0.0.1`, and loopback IPv6 are allowed for development.
4. `/api/auth/login` issues an encrypted HttpOnly, SameSite=Strict session after validating credentials and backend identity. Secure is required outside loopback. Login/logout require exact matching Origin and Host. Protected HTML is private/no-store.

### Auth contract verified in current source

- `POST /api/v1/auth/login`: `{email,password}` → `{access_token,refresh_token,expires_in,token_type:"Bearer"}`. Login password maximum 128 characters; whitespace is not trimmed.
- `GET /api/v1/users/me`: bearer-authenticated numeric `id`, `email`, nullable `first_name`/`last_name`/`organization_id`, descriptive `roles`, `enabled`, `locked`. Disabled/locked or malformed identities fail closed. Effective permissions are not exposed by this DTO.
- `POST /api/v1/auth/logout`: bearer-authenticated, 204; revokes **all sessions** for the account. Local cookie clears even if upstream revocation fails, with explicit failure copy.
- `POST /api/v1/auth/refresh`: single-use rotating refresh tokens; reuse revokes the session family. This frontend discards refresh tokens and requires reauthentication when access expires (backend default 15 minutes). No parallel refresh/replay is attempted.
- Backend permissions now come from current database roles, with explicit HTTP permission checks and active session/membership checks. Tenant-scoped data isolation still requires deployment-level verification. `/me` organization selection remains first org-scoped membership; no frontend tenant switching is offered.

## Pagination and normalization

- The frontend assumes Spring-style paged responses with `content`, page metadata, `first`/`last`/`empty`, and size/page-number fields. `pagedResponseSchema` accepts the observed `pageNumber` shape; `springPageSchema`/`toPage` accept the `number` shape where a module differs.
- Vehicle list: page size 200, maximum 50 pages.
- Trip/alert/geofence lists: page size 100, maximum 50 pages.
- Stop on `last`, `empty`, or an empty page. A malformed page body is a contract error.
- JSON is snake_case in the current backend. The shared gateway client recursively normalizes keys to camelCase before schema validation and rejects ambiguous duplicate spellings. Prefixes/envelopes differ by module; there is no global assumed `/api/v1` prefix.
- `204` responses pass through the normal schema path. A schema-incompatible 204 is a contract error, not a server/empty success.

## Observed upstream capabilities

| Domain                | Controller base              | Observed capability                                          | Frontend status                                                           |
| --------------------- | ---------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------------------- |
| Auth                  | `/api/v1/auth`               | register, login, refresh, logout, change-password            | Login/logout integrated; refresh/registration/password change not exposed |
| Current identity      | `/api/v1/users/me`           | User ID, names, org ID, role names; no effective permissions | Consumed and validated; roles do not grant frontend access                |
| Users / team          | `/api/v1/users`              | list/create/read/update/delete, assign role IDs              | Live team page fails closed                                               |
| Organizations         | `/api/v1/organizations`      | CRUD and paged list                                          | Concept preview only                                                      |
| Vehicles              | `/api/v1/vehicles`           | CRUD/list                                                    | GET list/detail live; create disabled                                     |
| Trackers              | `/api/v1/trackers`           | CRUD/list                                                    | Not integrated                                                            |
| Trips                 | `/api/v1/trips`              | list/detail; export controller now exists but not integrated | GET list/detail live; no replay/live GPS                                  |
| Device commands       | `/api/v1/commands`           | create/list/detail plus lifecycle updates                    | Not exposed                                                               |
| Alerts                | `/alerts`                    | list/detail, acknowledge, resolve, bulk, stats               | GET list/detail; live transitions disabled in UI                          |
| Alert rules           | `/alerts/rules`              | CRUD/templates/toggle/batch                                  | Not integrated                                                            |
| Geofences             | `/alerts/geofences`          | CRUD, vehicle queries, violation check                       | GET list/detail; live create/edit disabled in UI                          |
| Notifications         | `/api/notifications`         | delivery list/read, preferences, templates                   | Allowlisted; no client contract                                           |
| Notifications socket  | `/ws/notifications` (SockJS) | PING/SUBSCRIBE/UNSUBSCRIBE acknowledgments                   | Not connected; not a GPS feed                                             |
| Roles/permissions     | No REST controller observed  | Entities/repositories/seed roles                             | Concept templates only                                                    |
| Billing/rides/payouts | No REST controllers observed | Design documents only                                        | Future scope                                                              |

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
- The earlier adapter uses actor/review-note fields; current backend actor attribution and response contracts need re-verification before writes are enabled. Sign-in now provides verified identity, but the live response/notes workflow remains deliberately unavailable.

### Geofences

Accepted fields include numeric `geofenceId`, a single nullable `vehicleId`, `name`, `shapeType`, circle center/radius, `active`, polygon `points`, description, and timestamps.

- A valid circle requires center/radius. A valid polygon requires at least three points; otherwise geometry is `null` and the UI shows that no boundary is available.
- The API projects one vehicle ID, not a multi-vehicle set. Missing description/events/location are labeled “not provided”.
- The service has a narrow live circle-create request path, but the UI keeps live creation disabled until the route/action contract and update geometry/assignment behavior are verified.
- `UpdateGeofenceRequest` does not map polygon points or vehicle IDs into the entity. Update is deliberately unavailable.

### Preferences

No verified current-user preferences contract is consumed. Live preferences return a typed `unavailable` error rather than fixtures. Preview switches remain session-only.

## Release blockers

- The previously observed permission-resolver defect is fixed in current source. SecurityConfig now uses explicit permission checks; a real authenticated deployment run is still required.
- User organization/login primary organization are derived from the first org-scoped role; secure context switching is undefined.
- Role assignment accepts IDs but no role-catalog endpoint was found.
- Login, expiry, Origin checks, and logout are implemented. Coordinated refresh, registration, password changes, and password recovery are not part of this slice.
- Notification WebSocket identity now uses the authenticated handshake. Native browser bearer-header handshakes remain unsupported; no cookie/ticket bridge is supplied. It is not a GPS feed.
- Geofence update/assignment and alert actor/review contracts remain incomplete.
- Vehicle/tracker creation needs a verified tracker assignment and ID contract.
- Recorded GPS and tenant-scoped telemetry are not integrated. A trip-export controller exists in current source; its authorization/filter/streaming contract needs separate review before use.
- Production maps require an approved provider, licensing/privacy/capacity review, CSP, and non-Chromium/device validation.

Backend fixes require separate authorization and an isolated workstream. Do not modify the active checkout or snapshot from the frontend repository.
