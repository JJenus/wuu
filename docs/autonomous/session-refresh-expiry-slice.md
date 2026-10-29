# Session refresh expiry contract

## Scope

The authenticated login and refresh responses now expose the refresh-token
lifetime as `refresh_expires_in` (seconds). This is an additive snake_case field
alongside the existing `access_token`, `refresh_token`, `expires_in` and
`token_type` fields. It does not change token issuance, rotation, reuse detection,
permission resolution or revocation policy.

`expires_in` remains the short-lived access-token lifetime. `refresh_expires_in`
describes the absolute session refresh lifetime, so a same-origin BFF can retain
the HttpOnly refresh credential for the correct duration instead of expiring its
cookie with the access token. The value is recomputed from the current database
session on rotation; it never extends the original session.

## Verification

- `AuthServiceTest` asserts the configured seven-day refresh lifetime on login and
  registration and a session-bound 3600-second lifetime on rotation.
- Full vendor gate: backend **729 / device 91 PASS**, zero failures/errors/skips;
  both executable JARs built (`/tmp/opencode/session-refresh-full-gate.log`).
  Backend JAR SHA256
  `4f4a24c329537252e06f6f5aab97347054172046a2e32196fa9c260030767454`;
  device JAR SHA256
  `8329c651be98d0dc00d9f8eb926e552a0d5a99f3c0baabff52edeeab1fa0dfcf`.
- Actual-Java production-Nitro acceptance **PASS** with the same JAR, including a
  real BFF refresh rotation followed by an authorized vehicle read. Receipt
  `/tmp/opencode/refresh-java-acceptance.log`; private browser log
  `/tmp/opencode/wuu-java-guAgb5/browser.log`. Owned fixture cleanup completed.

## Limits

This field is contract support for a single-process BFF rotation cache. It does
not implement distributed refresh coordination, multi-instance replay receipts,
production TLS/session hardening, refresh-token theft detection beyond the backend
reuse policy, or a deployed production acceptance. Those remain separate work.
