# Local accounts — 2026-10-25

Scope: idempotent local user/business-admin seeds, minimal dynamically queried pre-login directory, and a Wuu email picker that still requires passwords. This repository is an independent snapshot of the current active backend working tree; `../sidie`, snapshots, shared and device code were not edited.

## Evidence

- `46c7df5`: RED, new policy/seed tests failed to compile because `LocalAccountSeeder` did not exist. `7629ae7`: initial GREEN, five focused tests passed.
- `f63e58e`: executed mixed-case regression failed (expected 2 users, got 3). `144bd05`: case-insensitive skip fixed it; full package passed 494 tests after updating security-context wiring and a registration role-repository test double.
- `390d2fa`: real-Java frontend smoke revealed false session expiry after successful login; focused MockMvc test reproduced `No argument for named parameter ':sortBy'`. Removed the unbound CASE ordering from alert search; sorting remains owned by the existing service-supplied Pageable. General error-dispatch status masking was not redesigned.
- Final `./mvnw -B package`: **495 tests PASS**, zero failures/errors/skips, executable JAR produced. The standard test suite does not include every opt-in `*IT`; no coverage percentage is claimed.
- Seed integration checks cover concurrent empty starts, repeat runs, preservation of existing hashes/names/disabled state/memberships, mixed-case collisions, bcrypt login, invalid-password rejection, real alerts read, newly created directory users, locked-user exclusion, no secret/role fields, invalid pagination, and remote-peer denial. Default-disabled directory denial is checked in the composed security test; unsafe local/profile/binding configurations are checked separately.
- In `../app`, `pnpm test:e2e:java` against this freshly packaged JAR and live Wuu passed **1/1 Chromium smoke**: VIEWER and TENANT_ADMIN logins/session verification/logout, then authenticated creation/reuse of `additional@wuu.local`, default directory discovery, and password-based login. This named local verification account is retained in the isolated database. Credential traces/videos/screenshots are disabled.

## Boundaries

No passwordless endpoint, hardcoded seed password, public account list by default, token/password response fields, automatic password reset or existing-account elevation was added. The frontend independently gates directory access and validates minimal wire contracts. The database row lock is scoped to startup seed transactions; existing uniqueness constraints remain the final guard against unrelated concurrent writers.

Runtime uses private generated environment files, loopback HTTP, file-backed H2 and disposable Redis on 16379. JMS listeners are disabled for this auth/read smoke because Artemis is unavailable. H2 concurrency results are not PostgreSQL certification. Full event/device flows, broad tenant/role isolation, production deployment, PostgreSQL concurrency and whole-app coverage remain outside this slice. Nothing was pushed or merged into the active checkout.
