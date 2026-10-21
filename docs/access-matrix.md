# Access matrix and role register

## Current frontend security posture

All routes are a **public sample-data preview**, including `/admin`. There is no authentication or authorization claim. No real tenant data, credentials, commands, or administrative mutations are available. UI templates are descriptive, not security boundaries.

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
- Role assignment uses IDs but there is no role catalog REST endpoint.
- The current resolver ignores role/org and returns every stored permission.
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
