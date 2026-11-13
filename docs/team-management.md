# Live team management — 2026-11-13

Outcome: `/team` now supports the existing backend's tenant directory, account
creation, profile editing, role replacement and confirmed membership removal.
Preview remains a public sample directory with no simulated administration.

## Contract and permission boundaries

- `users.read`: paginated directory/detail reads. The UI searches only the current
  page (50 members); errors hide directory rows rather than substituting samples.
- `users.write`: create an account or edit email/first/last name. Account creation
  requires an existing tenant OPERATOR role and assigns it initially. Missing
  defaults fail before creating an orphan identity. Initial passwords are 8–72
  characters and are cleared after submission/navigation; no invitation email is
  sent. Credentials must be shared securely outside the app.
- `users.assign_roles`: replace roles in the active tenant. The UI also requires
  `roles.read` to load the complete tenant catalog. Unknown/ambiguous assigned role
  names and failed/incomplete catalog reads disable role editing, never silently
  drop assignments. Role catalog reads are capped at 50 pages of 100; incomplete
  results remain an error. Roles are not client-defined permission templates.
- `users.delete`: confirmed removal of tenant membership, not deletion of the
  global identity. Other memberships remain intact; all account sessions are
  revoked. The UI discloses that scope before confirmation.

Every write refreshes current effective permissions; Java rechecks authorization,
tenant membership and last-TENANT_ADMIN protection. Role and removal failures stay
visible. Admitted non-password drafts survive failures/revocation. Ambiguous writes
are never automatically replayed; the user is told to refresh/check before retrying.
Role replacement has the existing API's last-write-wins semantics, not optimistic
versioning. Profile edits of shared/system identities require out-of-band
administration and may be denied; cross-tenant memberships are never edited here.

The BFF exposes only exact `/api/_wuu/team` GET/POST, positive individual GET/PUT/DELETE,
and individual PUT `roles`, mapping through the single server-only registry to
`/api/v1/users`. It is **not** a general users proxy: `/team/me`, session paths,
arbitrary nesting, unsupported methods and generic role administration are denied.
Reads permit only bounded page/size on the collection; writes and individual reads
reject query overrides. Strict camelCase schemas map only approved fields to Java
snake_case. Tenant/actor/permission overrides, duplicate/empty/unsafe role IDs and
unknown fields are rejected. Existing sealed sessions, exact-origin checks and a
4 KiB JSON body bound apply. Empty role assignment uses removal instead of silently
dropping membership without session revocation. `/access` retains its separate
existing route and is not captured by the team policy.

Backend team responses now report only active-tenant role names/organization IDs;
they no longer expose other-tenant/system role memberships. Role-denial messages do
not disclose non-tenant role names. `/users/me` authentication behavior is unchanged.
No enable/lock toggle, password-reset service, email invitation or new provider
capability is claimed. Existing account status is displayed read-only.

## Verification

- RED backend tests reproduced foreign/system roles leaking in team responses,
  orphan account creation without the default role, and non-tenant names in role
  denials. GREEN: `./mvnw -B verify` passed **717 tests** and executable packaging.
- RED frontend tests specified the missing gateway/service, then confirmed the
  absent live administration UI. GREEN: **5 team unit tests** cover all adapter
  operations, strict payload/query/path policy, pagination metadata, cancellation,
  unsafe IDs/records and propagated failures.
- `pnpm exec vitest run --coverage --exclude tests/unit/branding.test.ts`: **231
  tests passed**, **94.86% lines / 86.52% branches**. Team adapter: 100% lines /
  82.6% branches. Measurements cover configured domain/adapter/policy files, not
  whole UI or Java coverage. Full unit run: **232 passed, 1 failed** (233 total),
  solely the existing branding assertion against the preserved `nuxt.config.ts`
  title edit; this file was excluded from task commits, not fixed or reverted.
- `pnpm typecheck`, `pnpm check:boundaries`, `pnpm build` and focused Prettier
  checks passed. Contract browser checks cover create/edit/assign/remove, permission
  recheck without a write, draft retention, 51-member pagination, hidden rows on
  read failure, mobile overflow and account/role-form dark-mode accessibility.
  `pnpm exec playwright test --config playwright.team.config.ts` passed all **4** checks.
  The existing auth browser suite passed **42** tests. Contract fixtures are not
  presented as actual-Java evidence.
- `python3 tools/verify-telemetry.py --artemis-home /home/jjenus/.cache/tracker-tests/apache-artemis-2.40.0 --verify-team`
  passed real TCP/Artemis/PostgreSQL/Redis/Java/browser acceptance. With real
  registered tenant accounts, the browser created/reloaded/edited an account,
  changed roles, verified permission loss on an existing session, confirmed removal,
  and verified session revocation. It denied foreign-user reads/edits/removal,
  foreign-role assignment, actor/tenant/permission forgery, unsupported paths and
  cross-origin requests. The sole administrator could neither be removed nor
  demoted. Changes were re-read through actual APIs, not a contract server.
  Existing device outage recovery still passed before browser acceptance.

No deployment or production operation was performed. This completes the supported
team-management workflow, not provider-backed invitations or whole-app release
certification. See backend `AGENTS.md` and the telemetry runbook for native runner
prerequisites; the runner owns and stops disposable loopback infrastructure.
