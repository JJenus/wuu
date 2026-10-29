# Tenant role catalog slice — 2026-10-29

## Contract

`GET /api/v1/roles` is a read-only, tenant-scoped catalog for authenticated users
holding the database-derived `roles.read` permission.

| Property | Behaviour |
| --- | --- |
| Authority | `roles.read`; forged JWT role-name claims grant nothing |
| Tenant | Server-selected from the current principal; `orgId`/`organization_id` query overrides are ignored |
| Tenantless principal | `403`, including a global administrator with no active tenant |
| Pagination | `page` defaults to `0`; `size` defaults to `50` and must be `1..100`; invalid input is `400` |
| Ordering | `name`, then `id`, for deterministic pages |
| Response cache | `Cache-Control: private, no-store` |
| Entry | Tenant-owned `id`, `name`, `description`, sorted permission keys |

Reading an entry grants no authority. Role creation, mutation, assignment and
global-role exposure remain outside this slice. Permissions are mapped inside the
read-only transaction so Hibernate does not page a fetch-joined to-many
collection. Paging is performed in SQL through `RoleRepository.findByOrgId`.

The controller rechecks the authority declared by the HTTP security matcher; the
service also carries `@PreAuthorize("hasAuthority('roles.read')")` and rejects a
missing tenant before repository access.

## Evidence

- RED `46e25e2` added the controller boundary and real-PostgreSQL regression;
  compilation failed on the intentionally missing endpoint.
- Focused `RoleControllerIntegrationTest` **PASS** covers tenant paging,
  forged-claim denial, anonymous denial, tenantless denial, four invalid
  pagination cases, and direct use-case denial without permission or tenant.
  Receipt: `/tmp/opencode/role-catalog-green.log`.
- Real PostgreSQL `PostgresDeploymentIT#roleCatalog_authenticatedTenantAdmin_pagesOnlyCurrentTenantWithoutGlobalRoles`
  **PASS**. It creates two independent tenants and same-tenant catalog roles,
  proves page sizing/total/order, tenant isolation, `roles.read` presence and
  `size=101` rejection. Receipt: `/tmp/opencode/role-catalog-postgres-v4.log`.
- Unit-only commands are not a full backend gate; the complete Maven gate and
  executable JAR build are recorded separately after this slice.

## Limits

No frontend role picker calls this endpoint yet. Role write APIs, role-assignment
workflows, global-role listings, ETag/conditional reads and multi-node cache
coherence are not implemented. This slice does not change release readiness;
`STATUS` remains `IN_PROGRESS`.
