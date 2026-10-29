# Effective access inspection — 2026-10-29

The backend now exposes a request-time access snapshot at
`GET /api/v1/users/me/access`. The frontend maps only the exact resource through
`GET /api/_wuu/access`; nested paths and writes are denied. The contract is:

```json
{
  "user_id": 7,
  "organization_id": 12,
  "permissions": ["vehicles.read"],
  "access_expires_at": "2099-01-01T00:00:00Z"
}
```

Numbers/timestamps above are illustrative. User/tenant identifiers must be safe
positive integers; organization may be null for an authenticated global identity
without an active tenant. Permission keys and expiry are validated. The endpoint
does not expose credentials or the backend session identifier.

Java derives permissions from the database-backed request principal after session,
membership and expiry checks. It does not trust JWT role-name claims. A real
PostgreSQL regression verifies that removing an org-role permission changes the
next response and denies the corresponding action; logout then denies the access
endpoint. A global administrator is not silently assigned a tenant.

Settings shows the snapshot independently from notification preferences. An
unavailable preference endpoint cannot hide valid access data. Recheck asks the
server again. This is not a long-lived grant: every business operation is still
authorized on the server, and platform concept pages remain blocked even when
descriptive frontend templates suggest an administrator role.

## Verification and limits

- Backend RED `e28fdea`, GREEN `8f8307b`, full718/device91 PASS, published evidence
  checkpoint `6841270` on origin/main.
- Frontend RED `2a6c259`, GREEN `830cf75`; unit tests cover validated transport,
  malformed responses,401, nullable active tenant and the exact resource alias.
- Complete auth suite **23 PASS** including access refresh and no role-template
  platform elevation; actual-Java production browser PASS on the isolated JAR
  SHA256 `0866662ffa28b86072a262fa3c6236af12759048b94d264c6180cf9f80d4d99e`.
  See `subscriptions.md` for combined final receipts and reviewer findings.

This does not deliver tenant switching, platform CRUD, role edits, coordinated
refresh, MFA/password recovery or production authorization certification. A
separate tenant-scoped read-only catalog now exists at `GET /api/v1/roles`
(`roles.read`); it is backend-only and is not yet consumed by this frontend. That
catalog grants no role-assignment authority and does not replace the current
access snapshot. The actual-Java run verifies tenant-admin role reads, ignores a
foreign `orgId` override, and denies a same-tenant OPERATOR without `roles.read`.

## Bounded self-evaluation

| Axis          | Score | Evidence and improvement                                                                                                              |
| ------------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Accuracy      | 4/5   | Backend729/device91, frontend110 units and real Java/browser receipts support these contracts; independent review did not rerun them. |
| Completeness  | 3/5   | Owner subscriptions and access inspection work; paid billing, automatic metering, broader writes and a role UI remain open.           |
| Clarity       | 4/5   | Pending versus access/payment is explicit; historical docs still carry superseded statements below dated notices.                     |
| Actionability | 4/5   | Isolated artifact and repeatable browser fixture exist; no production configuration or provider authorization is supplied.            |
| Conciseness   | 4/5   | Existing service/gateway/query patterns reused; dated historical recovery notes remain verbose.                                       |

Overall3.8/5. Highest-impact improvements: expose the verified tenant role catalog
in the team/settings UI, then connect real live workflows without treating
permission snapshots as authority. These are active engineering tasks, not a
reason to mark RELEASE_READY. The owner would reasonably expect continued work;
`IN_PROGRESS` remains set.

## Dependency gate after remediation

The initial `pnpm audit --prod --json` found one high and three low advisories in
Nuxt build/dev transitives (`/tmp/opencode/tracker-frontend-production-audit.json`).
pnpm 11 requires resolution overrides in `pnpm-workspace.yaml`, not
`package.json`; the workspace now pins `node-forge` 1.4.1,
`serialize-javascript` 7.1.3 and `fontless>esbuild` 0.28.2. Vitest and its
coverage provider were upgraded from 3.2.7 to 4.1.12, removing the dev-only
Vitest/mocker path-traversal and tinypool prototype-pollution/RCE advisories.

A fresh full `pnpm audit --json` now reports **zero critical/high/moderate** and
one low: `deepmerge` 4.3.1 via
`nuxt > @nuxt/nitro-server > nitropack > @rollup/plugin-node-resolve`. The
advisory names `>=4.3.2` as fixed, but no such version is published; an untested
fork override would be riskier than retaining the build-time transitive. The
scanner path is not evidence of deployed endpoint exploitability, but the audit
is **not clean** and no security certification is claimed. Receipts:
`/tmp/opencode/frontend-audit-vitest4.json`,
`/tmp/opencode/vitest4-coverage.log`, `/tmp/opencode/vitest4-build.log`.
Production deployment remains unauthorized.
