# Authenticated subscription catalog and enrollment — 2026-10-29

**PARTIAL: durable catalog and pending enrollment only. This is not billing,
payment, entitlement or a finished subscription product.** The reviewed
[domain slice](subscription-domain-slice.md) remains the only implementation of
grants, bounded token consumption and activation; it is not yet wired to these
tables or HTTP routes.

## Delivered surface

All JSON uses the application-wide Jackson `SNAKE_CASE` convention.

| Method | Path | Authorization | Behaviour |
|---|---|---|---|
| POST | `/api/v1/subscriptions/catalog` | `system.admin` | Create an immutable plan version; duplicate `(plan_id, version)` is `409` |
| GET | `/api/v1/subscriptions/catalog` | authenticated | List configured plan versions |
| GET | `/api/v1/subscriptions/catalog/{planId}/{version}` | authenticated | Read one immutable plan version; missing/invalid is `404` |
| POST | `/api/v1/subscriptions` | authenticated | Idempotently enroll the current tenant/user as `PENDING` |
| GET | `/api/v1/subscriptions` | authenticated | List only the current tenant/user's enrollments |
| GET | `/api/v1/subscriptions/{id}` | authenticated | Read own enrollment; foreign/missing is `404` |
| POST | `/api/v1/subscriptions/{id}/cancel` | authenticated | Idempotently move own enrollment to `CANCELED` |

Plan requests carry `plan_id`, `version`, `model` (`USAGE_TOKEN` or `PERIODIC`),
optional `cadence` (`MONTHLY`/`YEARLY`, only for periodic), `meter`,
`usage_limit`, exact decimal `amount` and ISO `currency`. Enrollment requests
carry `plan_id`, `version` and an owner-scoped `operation_key`. Enrollment
responses expose `id`, `plan_id`, `version`, `status`, `remaining_units`.

Cancellation is terminal and zeroes `remaining_units`; it never creates a
negative balance and does not alter the original request receipt. A repeated
enrollment `POST` with the same owner/key returns the **original creation
receipt**, even if the live state has since been canceled; clients that need
current state must `GET` the enrollment or list. A reused key with a different
plan payload is `409`. This receipt semantics mirrors the reviewed domain
aggregate's replay rule and is intentional, not a stale-state bug.

## Persistence and integrity

Migration `V5__subscription_enrollment.sql` exists identically in both H2 and
PostgreSQL histories:

- `subscription_plan_versions` pins the durable immutable snapshot and uniquely
  constrains `(plan_id, version)`. Quantity/price checks reject non-positive
  limits and negative amounts.
- `subscription_enrollments` stores the tenant (`organization_id`), user
  (`user_id`), owner-scoped `operation_key`, pinned plan-version FK, live status
  and balance, plus immutable receipt status/balance. The unique
  `(organization_id, user_id, operation_key)` constraint is the cross-instance
  idempotency boundary; there is no in-process lock claim.
- Enrollment responses are never cached. Every read/cancel is filtered by both
  organization and user; a matching ID from another tenant or another user in
  the same organization is indistinguishable from missing (`404`).
- The API rejects amounts whose scale/precision the `numeric(38,18)` column would
  silently round. Negative and non-finite domain values remain rejected by the
  reviewed types.
- `PENDING` never grants access. No entitlement check consumes these rows, and
  `remaining_units` is zero for every enrollment this slice can create.

## Verification executed

- `SubscriptionControllerIntegrationTest`: **7 tests, zero failures/errors/skips**;
  real random-port Spring HTTP, JWT filter, Flyway H2 PostgreSQL mode and
  Hibernate validation. Covers admin-only catalog creation, both billing models,
  exact 18-decimal price round trip, duplicate/immutable catalog, malformed and
  precision-losing configuration, missing auth, user/tenant scoping, foreign and
  replay cancellation, receipt replay, operation-key conflict precedence, and
  six concurrent same-key requests in one application instance producing one row
  and equal responses. Multi-instance operation remains unverified.
  Receipt: `/tmp/opencode/tracker-subscription-backend-test.log` (full suite).
- Full backend `./mvnw -B test`: **612 tests, zero failures/errors/skips**,
  `BUILD SUCCESS`, same receipt. This is the default Surefire suite, not the
  Docker vendor gate.
- `FlywayMigrationIT`: **1 test passed** after updating the exact count to five
  migrations; the H2 history applies cleanly and Hibernate validates every entity
  against that schema. PostgreSQL history verification is recorded separately below.
- `PostgresDeploymentIT#migration_upgradeFromV2_revokesLegacySessionsAndTokens`:
  **1 test passed** against disposable PostgreSQL after updating the expected
  upgrade count from two to three; the main Spring context also ran latest V5
  migrations with Hibernate validation. Receipt:
  `/tmp/opencode/tracker-postgres-v5-upgrade.log`.
- Independent frontend actual-Java fixture rerun after lifecycle hardening:
  **PASS**, `/tmp/opencode/tracker-deployed-lifecycle-v3.log`; see the frontend
  repository's `docs/deployed-java-acceptance.md`. That run used the previously
  built JAR and does not include this subscription source.

## Deliberate limits / open work

Review follow-up: the HTTP suite now has **9 passing cases** after two new
regressions reproduced oversized-meter 409 and extreme-exponent 401 instead of
400. Explicit meter length validation and overflow-safe integer-digit counting
reject both before persistence. RED/GREEN receipts and checkpoint commits are
indexed in [final-slice-review.md](final-slice-review.md). The final full vendor
gate after these fixes **passed backend701/device91**, zero failures/errors/skips,
`/tmp/opencode/tracker-reviewed-vendor-gate.log`; both executable JARs were built.

- No authenticated grant, activation, consumption, renewal, cancellation-at-
  period-end, invoice, proration, tax, coupon or dunning route exists. Configured
  trial/complimentary grants still require a separately reviewed use case that
  reuses the domain aggregate and a durable ledger.
- No payment provider, callback, reconciliation or paid-entitlement evidence is
  present. Enrollment is not payment success.
- Catalog listing is unpaginated and has no update/delete by design; plan
  immutability is enforced by absence of mutation and by the database constraint.
- No subscription UI is implemented in the independent frontend.
- No multi-node restart proof, whole-backend coverage claim or production
  deployment/release certification is made. `STATUS` remains `IN_PROGRESS`.
