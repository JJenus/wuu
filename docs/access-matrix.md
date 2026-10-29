# Access matrix and role register

## Subscription owner exception — 2026-10-29

Authenticated subscription reads, pending enrollment and confirmed cancellation
are available to the current Java-authorized tenant/user. The BFF exposes only
those two mutation shapes with exact-origin validation, bounded strict JSON and
sealed sessions. Role names confer no authority; Java enforces owner isolation.
Catalog administration, grants and trusted usage recording remain unavailable
through this UI/gateway. Enrollment grants no access or payment success. See
`subscriptions.md`; other live resource writes remain disabled.

The new current-access endpoint/panel reports request-time effective permissions
resolved by Java for the active tenant (or null tenant), not JWT role names. It
does not enable platform concept pages, tenant switching or role writes. Revoking
a persisted permission is verified to remove it from the next response and deny
the corresponding action server-side. Settings also renders a read-only,
paginated tenant role catalog when that snapshot contains `roles.read`; nested
role paths and writes never pass the BFF, and the panel is absent without the
permission. Older endpoint-absence statements below are historical; full
production access certification remains outstanding.

## Current frontend security posture

Preview mode is a **public sample-data workspace**, including `/admin`. There is no authentication or authorization claim. No real tenant data, credentials, commands, or administrative mutations are available. UI templates are descriptive, not security boundaries.

Live mode is opt-in through deployment configuration and uses authenticated read-only GET adapters through the same-origin BFF:

- Login validates backend tokens and `/users/me` before issuing a sealed HttpOnly access cookie. Exact same-origin mutation checks and live route guards are implemented. Expiry requires reauthentication; refresh tokens are discarded.
- Logout clears the local cookie and asks the backend to revoke all account sessions. Legacy plaintext bearer cookies and shared environment-token fallbacks cannot grant access.
- Client role templates never grant access. Live `/admin` fails closed pending an effective-capabilities contract, even if `/me` reports an administrator role. Preview `/admin` remains public sample data.
- Live vehicle creation, alert transitions, geofence create/edit, team management, and preferences are disabled in the UI.
- The current backend source enforces active sessions, current database permissions, and explicit HTTP authorization. Real-deployment permission/tenant-isolation tests are still required; no Java runtime was tested by this frontend task.

Local development update (2026-10-25): the isolated `../backend-accounts` implementation seeds `user@wuu.local` as VIEWER and `admin@wuu.local` as TENANT_ADMIN, with environment-supplied passwords and no existing-account elevation/reset. Its opt-in loopback account directory lists minimal identities, not effective permissions. Wuu's separately opted-in local picker requires the selected account's password. It never grants platform access, including to the seeded business administrator. The earlier statement about no Java runtime reflects the original frontend slice; see `verification.md` for subsequent local checks and their limits.

The preview/live split remains a data-source boundary. The sealed session gateway adds an authentication boundary; backend authorization remains authoritative. A denied domain cannot hide another domain's permitted records.

## Verified backend roles

Source: `AuthService.seedOrgRoles()` and migration `V2__seed_permissions_and_super_admin.sql`.

| Role         | Scope        | Actual seeded intent                                                                                                                                   |
| ------------ | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| SUPER_ADMIN  | Global       | All 19 seeded permission keys                                                                                                                          |
| TENANT_ADMIN | Organization | users read/write/delete/assign_roles; organizations.read; roles read/write; vehicles read/write/assign_device; alerts read/write; devices read/command |
| OPERATOR     | Organization | users.read; vehicles.read; alerts read/write; trips.read; notifications read/write                                                                     |
| VIEWER       | Organization | vehicles.read; alerts.read; trips.read                                                                                                                 |

`app/policies/access.ts` exposes presentation role templates for unit tests and the read-only role explorer. `platform.access` is a **frontend-proposed capability**, not a seeded backend permission. SUPER_ADMIN's frontend template is a presentation bundle, not an effective-permission API result.

## Inconsistencies to resolve, not silently “fix” in UI

- TENANT_ADMIN does not include trips.read, trips.export, or notification permissions.
- No organization owner distinction, per-asset scope, invitation lifecycle, or billing permissions exist in the observed seed.
- Role assignment uses IDs. A read-only tenant role catalog now exists at
  `GET /api/v1/roles`; it is not a write, assignment or global-role interface.
- The current backend exposes effective permissions at `/users/me/access`; the
  frontend consumes the exact alias. The former all-permissions resolver is fixed
  in backend source.
- Having a JWT role claim does not prove endpoint enforcement.

## Proposed role bundles (not implemented)

| Scope        | Role                         | Responsibility / limit                                                         |
| ------------ | ---------------------------- | ------------------------------------------------------------------------------ |
| Platform     | Platform administrator       | Organization lifecycle, delegated configuration; sensitive actions audited     |
| Platform     | Support agent                | Narrow support context; no default impersonation, commands, or finance writes  |
| Platform     | Security administrator       | Identity policy, role policy, access audits                                    |
| Platform     | Device operations technician | Provisioning/connection diagnostics; no arbitrary customer data access         |
| Platform     | Finance administrator        | Invoices/subscriptions/refunds only after billing exists                       |
| Platform     | Auditor                      | Read-only audit/configuration scope                                            |
| Business     | Organization owner           | Ownership/delegation; ownership transfer needs a dedicated flow                |
| Business     | Organization administrator   | Members, policies, settings within one organization                            |
| Business     | Fleet manager                | Fleet configuration and operations; separately granted sensitive commands      |
| Business     | Dispatcher/operator          | Monitoring and incident handling; dispatch exists only for future ride-hailing |
| Business     | Device installer             | Assigned tracker/vehicle provisioning scope only                               |
| Business     | Billing manager              | Organization billing, no fleet location access by default                      |
| Business     | Asset viewer                 | Read-only explicitly assigned vehicles                                         |
| Customer     | Individual owner             | Own organization/assets; not arbitrary platform/customer records               |
| Future rides | Rider                        | Own rides/payment history                                                      |
| Future rides | Driver                       | Own assignments, compliance documents, earnings                                |
| Future rides | Compliance reviewer          | Driver verification, no payout authority                                       |
| Future rides | Payout operator              | Reviewed driver payout operations; no arbitrary ride/identity changes          |

Do not create a role for every possible job title. Add a role only when a distinct responsibility/resource scope justifies it; permissions are the reusable primitive. Multiple-role unions must stay inside the active server-authorized tenant and resource scope. Commands, refunds, and role elevation need separate explicit authority.
