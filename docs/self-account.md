# Account self-service — 2026-11-13

Outcome: live Settings now supports editing the signed-in user's first/last names
and changing their password. Account forms remain usable independently of the
unavailable notification-preferences panel. Preview does not simulate account
writes. Email changes, email recovery and administrative password resets are out
of scope; email is displayed read-only.

## Contract and safety

| Browser endpoint                 | Java endpoint                       | Body / result                                                                |
| -------------------------------- | ----------------------------------- | ---------------------------------------------------------------------------- |
| POST `/api/auth/profile`         | PUT `/api/v1/users/me`              | Strict `firstName`, `lastName`; returns validated identity and access expiry |
| POST `/api/auth/change-password` | POST `/api/v1/auth/change-password` | Strict `oldPassword`, `newPassword`; confirmed success is 204                |

The BFF maps approved fields to snake_case and retains same-origin, sealed-session,
no-store, no-redirect and streaming 4096-byte limits. Query overrides and extra
fields are rejected. The new Java profile DTO also rejects unsupported fields.
Profile changes require a valid active session, not `users.write`; the actor comes
only from that session. Names are account-global, both strings are required and
bounded to 100 characters, and blank values clear them. Email, password, tenant,
roles and account status cannot change through this endpoint. The response identity
is checked against current `/me` before the client adopts it.

Profile updates use narrow SQL rather than merging a detached identity graph.
User entity updates now write only changed columns, preventing a concurrent
password-only update from restoring stale names. Names-only edits do not revoke
sessions. Failed/ambiguous profile writes retain the draft and require an explicit
retry; uncertain results advise reloading first.

Passwords are never trimmed. The minimum remains **8 Unicode codepoints** and the
new password is bounded to **72 UTF-8 bytes**; confirmation is client-side and is
not forwarded. The existing Java endpoint verifies the current password, changes
the hash and revokes account sessions. Tests verify revocation of two existing
sessions' access and refresh tokens, old-password refusal and new-password login.
A definite wrong-current-password error is shown inline without destroying a
valid session. Validation/rate-limit errors also preserve it.

On confirmed change the BFF clears the encrypted cookie and renewal receipts;
the client invalidates in-flight renewal adoption and performs a full navigation
to sign-in with an explicit all-sessions-ended notice. An ambiguous reply is never
called success or automatically replayed: the BFF fails closed where reached, and
the client best-effort signs out and advises trying the new password first. A
total network outage cannot guarantee server-side revocation until connectivity
returns. No password is put in application state shared across pages, query caches,
URLs, localStorage or sessionStorage. Form fields clear after attempted submission
and on unmount; browser password-manager behavior is separate.

Both account forms stay disabled until client hydration attaches their handlers,
then freeze during writes. This prevents fast input from being overwritten during
hydration. The existing single-BFF-process session-renewal limitation remains;
this increment does not claim distributed session coordination or account-email
verification.

## Checks and task evidence

- Backend RED: authenticated non-admin self-profile PUT returned 403. GREEN:
  self-profile/password HTTP tests passed, including forged fields, tenant/other
  user isolation, clear/length validation and session revocation. A separate RED
  reproduced a stale password entity overwriting saved names; changed-column
  writes made the concurrency regression pass.
- `./mvnw -B verify`: **771 tests passed**, packaging passed. No Java coverage
  percentage is claimed. There is one additive API operation, now **107** total;
  no schema migration or device/protocol implementation change was needed.
- `pnpm exec vitest run --coverage --exclude tests/unit/branding.test.ts`:
  **260 passed**, **94.65% lines / 87.02% branches** over the configured scope,
  expanded to include the new account schemas, BFF utilities and auth client.
  This is not whole-UI coverage. The existing branding assertion remains excluded
  because it disagrees with the previously committed title edit; it is not claimed
  as passing.
- Types, production build, boundaries and changed-file formatting passed. The
  account browser suite passed **6 tests**; existing auth suite **42**, preview
  workspace/details **15**. Preview includes existing automated accessibility
  checks, not a manual live-account accessibility certification.
- Native command in `sidie/`:
  `python3 tools/verify-telemetry.py --artemis-home <local-home> --verify-account --verify-api-v1`
  passed **all 6 real Java/PostgreSQL/browser workflows**: persisted names/reload,
  blocked overrides, current-password error, two-session revocation/new sign-in,
  lost-reply uncertainty, mobile validation, retained profile drafts and pre-hydration
  protection. The existing outage/restart/deduplication checks and real authenticated
  OpenAPI checks also passed. An intermediate run caught lost pre-hydration input;
  a deterministic JavaScript-disabled test reproduced it before the fix.

Logs: `/tmp/opencode/self-account-{backend-final,coverage-final,types-final,build-final,browser-final,auth-regressions,preview,native-final}.log`.
Owned native evidence: `/tmp/opencode/telemetry-native-92u2l3l9/`; owned processes
were stopped. No deployment, real-account operation, email delivery or production
readiness certification is claimed. Stop after this milestone.
