# Current effective-access contract — 2026-10-29

Implemented in an isolated `feat/effective-access` worktree, leaving the canonical
checkout's unrelated README/start helper intact. This is a read-only diagnostic
and frontend capability seam, not new permissions, tenant switching or platform
CRUD.

## API

`GET /api/v1/users/me/access` requires a currently authenticated session but does
not require `users.read`. It returns `user_id`, nullable `organization_id`, sorted
effective `permissions` and `access_expires_at`, with `Cache-Control: private,
no-store`. No access/refresh token, session UUID or private account data is echoed.

The response derives from `AuthPrincipal`, populated by the existing JWT filter
through current database roles, tenant membership, account state, session validity
and expiry. JWT role-name claims never become capabilities. Foreign tenant role
permissions are excluded. A global administrator without a selected tenant keeps
`organization_id: null`; no tenant is invented or silently assigned.

This is a request-time snapshot, not a client-side authorization boundary. Every
business request still passes its own current server authorization checks. UI
templates and cached capability results must never override a 401/403. No existing
role, permission seed, session issuance, refresh or mutation policy was relaxed.

## Tests and receipts

- RED `e28fdea`: 15 security boundary tests, two intended failures (new viewer
  endpoint returned403); `/tmp/opencode/tracker-effective-access-red.log`.
- GREEN `8f8307b`: read-only route/DTO/security matcher plus regressions.
  Source-scoped tests verify viewer access without users.read, sorted current
  tenant permissions, foreign exclusion, forged SUPER_ADMIN role-claim rejection,
  permission refresh, session revocation, anonymous denial and global/no-tenant.
- Real PostgreSQL HTTP case removes a persisted permission between requests,
  verifies it disappears from the response and the corresponding vehicle request
  becomes403, then logs out and verifies access becomes401. Receipt:
  `/tmp/opencode/tracker-effective-access-vendor.log`.
- Full disposable gate **backend718/device91 PASS**, zero failures/errors/skips,
  both executable JARs built, `/tmp/opencode/tracker-effective-access-full.log`.
- Frontend actual-Java acceptance of the isolated artifact **PASS**:
  `/tmp/opencode/effective-access-java-final.log`; private browser receipt
  `/tmp/opencode/wuu-java-vfPHdZ/browser.log`. JAR SHA256
  `3ad87f9a7bce65cbdfade542dd9b92666800a0ee5a91d6bb0d6c8d300849b8e6`.
  Real settings panel and BFF return current-tenant vehicles.read but no
  system.admin for the ordinary tenant administrator; no session identifier
  exposed. Existing fleet/isolation/revocation plus new subscription owner
  workflows also pass. Owned fixture cleanup completed with no labeled leftovers.

No new whole-backend coverage claim or independent release/security certification.
No production deployment is authorized. Effective permission inspection closes a
contract gap, not all admin workflows. STATUS remains IN_PROGRESS.
