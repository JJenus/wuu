# Durable non-paid grants and usage — 2026-10-29

**Partial subscription delivery, not payment or release readiness.** Supersedes
the no-durable-grant/usage statements in the earlier enrollment/domain slice only
for the use cases below. No paid status, financial success or automatic renewal
was added.

## Contract

- `POST /api/v1/subscriptions/{id}/grants` accepts `operation_key`, `kind`
  (`TRIAL` or `COMPLIMENTARY`), `reason`, `start`, `end`, positive `units`.
  Both HTTP routing and service method security require `system.admin`.
  The administrator must have a valid current tenant context; the subscription
  must belong to that tenant. A global administrator with no tenant context is
  not silently assigned one. Actor identity comes from authenticated context,
  never the request body.
- Exactly one immutable configured grant per enrollment. Units cannot exceed
  its pinned plan limit. Replay of the same operation key and exact payload/actor
  returns the original grant, even after cancellation/expiry. Another payload/key
  conflicts; a canceled enrollment cannot receive a new grant.
- `POST /api/v1/subscriptions/{id}/consumptions` accepts `operation_key`, exact
  plan `meter`, positive integer `units`. It also requires a tenant-scoped system
  administrator. This is **trusted administrator recording**, not an open
  self-reported customer meter or automatic product telemetry integration.
- Consumption requires a current half-open grant window and sufficient units.
  Future grants retain their raw balance but expose zero spendable units. Expired
  grants project `CANCELED`/zero; this read-time projection is not a scheduler or
  a persisted expiry/audit event. Existing owner reads remain tenant/user scoped.
- Successful usage replay returns the original immutable receipt without a
  second debit, including after cancellation/expiry. Payload mismatch is 409.
  Missing/foreign enrollment is 404; non-admin is 403; malformed/insufficient
  units are 400; absent current grant or terminal transition is 409.
- Grant times are bounded to portable years 1–9999 and microsecond precision.
  Usage timestamps are normalized once to database microsecond precision, so
  the initial and replayed JSON receipts agree exactly.
- Token, monthly and yearly plan configurations support these explicit non-paid
  grants. Cadence does **not** implicitly renew or replenish a grant.

## Persistence and concurrency

V6 is added to both unchanged Flyway histories. It adds immutable grant metadata
to the enrollment and `subscription_usage_receipts`, with a foreign key, positive
usage/nonnegative balance checks and unique `(subscription_id, operation_key)`.

All grant, consumption and owner-cancellation commands take a tenant-filtered
`PESSIMISTIC_WRITE` lock on the same enrollment row. The balance and usage receipt
commit in one transaction; failed attempts reserve neither units nor a key.
An explicit rollback after receipt flush is tested. No process-local lock is
used as the durable serialization boundary.

The domain kernel now supports validated `State` restoration and snapshots.
It rejects impossible pending/active/canceled balance/grant combinations; future
grant raw balance survives reconstruction. The adapter restores only the balance
snapshot and performs durable receipt lookup **before** calling the kernel.
Snapshot restoration alone does not restore the process-local replay map and must
not be treated as a standalone idempotency mechanism.

## Executed verification

- RED: `/tmp/opencode/tracker-subscription-ledger-red.log`, intended compile-time
  missing state/receipt types. Test checkpoint `697d880`.
- Initial GREEN: 41 cases passed, including real HTTP/migration/domain state.
- PostgreSQL attempt: 20 tests, one failure (catalog403). The fixture's ordinary
  tenant role did not have `system.admin`; no production permission was relaxed.
  Corrected fixture explicitly assigns existing global `SUPER_ADMIN` plus tenant
  membership before authenticating through the real auth/session/JWT path.
- Vendor GREEN: **2/2** real PostgreSQL tests passed, no failures/errors/skips,
  `/tmp/opencode/tracker-subscription-ledger-vendor-v2.log`: V2→V6 upgrade plus
  six final-unit contenders (one winner), exact receipt replay, zero final
  balance and foreign-tenant grant/usage denial.
- Latest focused instrumented GREEN: **61 tests**, no failures/errors/skips,
  `/tmp/opencode/tracker-subscription-ledger-focused.log`. Includes **18 HTTP**
  cases, all subscription domain cases, and full migrated H2 schema validation.
  Same-key six-request concurrency, failed writes not reserving keys, grant/usage
  replay after cancel, explicit rollback after flush, future/expired windows,
  invalid bounds/precision, both periodic cadences and actor authorization pass.
- GREEN implementation checkpoint `9293dc8`.
- Instrumentation: explicit JaCoCo 0.8.14 JVM agent, package-filtered data
  `/tmp/opencode/tracker-subscription-ledger.exec`. Report command:
  `./mvnw -B org.jacoco:jacoco-maven-plugin:0.8.14:report -Djacoco.dataFile=/tmp/opencode/tracker-subscription-ledger.exec`.
  Subscription package total **372/398 lines (93.47%)**, **162/178 branches
  (91.01%)**; domain **123/123 lines, 98/98 branches (100%)**. Application
  service **152/163 lines, 57/70 branches**; below the repository's aspirational
  95–100% service target. This is not whole-backend or vendor-run coverage.
- Final full vendor gate **PASSED backend714/device91**, zero failures/errors/
  skips, both executable JARs built:
  `/tmp/opencode/tracker-subscription-ledger-full-gate.log`. The gate's owned
  services were cleaned. Two stopped, exact-label harness leftovers from an
  earlier interrupted run (created00:55Z, stopped01:13Z) were separately removed
  after verifying no Maven/harness process was running; no shared service removed.

## Review and limitations

Coordinator review checked tenant predicates before receipt lookup, current
actor derivation, service-level authorization, shared row locking for cancellation,
atomic flush/rollback and microsecond replay stability. No independent final
reviewer was used. Existing device deployment and frontend work are unchanged.

This does not implement paid activation, gateway callbacks/reconciliation,
durable automatic product metering, renewal/dunning, invoice/tax/proration,
scheduled cancellation, full transition audit, entitlement enforcement in tracker
features, subscription UI, or a literal process-restart/multi-JVM race test.
Database row-lock contention and state reconstruction are verified, not a claim
of every deployment topology. Production-wide quotas/rate limiting and pagination
remain open. No deployment is authorized. `STATUS=IN_PROGRESS`.

## Bounded self-evaluation

Accuracy4/5: vendor714/device91 and focused/domain coverage receipts verify the
claims; no independent reviewer verdict or literal restart test. Completeness3/5:
the grant/usage slice is delivered, but automatic business metering, subscription
UI and paid workflows remain missing. Clarity4/5: admin-only recording and pending
versus grant semantics are explicit; historical receipts still span several docs.
Actionability4/5: repeatable commands/schema/HTTP contracts exist; product-level
integration needs another slice. Conciseness4/5: one adapter/kernel seam, but the
expanded HTTP fixture could use dedicated request builders. Overall3.8/5. Highest
impact improvements: connect actual feature use cases to the ledger, add the
subscription UI, and obtain independent acceptance. These require engineering,
not a claim that tests alone complete the product.
