# Bounded subscription domain slice — 2026-10-29

**Historical scope:** [V6 grant/usage integration](subscription-ledger-slice.md)
now wires validated domain state snapshots to durable grants/receipts and
tenant-scoped administrative commands. Original process-local-only limitations
below describe the initial kernel, not a claim that V6 grants are non-durable.
Payment and actual product-feature enforcement remain absent.

**PARTIAL: reusable domain invariants only. Not a completed subscription system.**
Owner scope authorizes configurable usage/token and monthly/yearly periodic models;
only ride-hailing is deferred. This slice implements neither payment processing
nor a production entitlement service. The existing [database design](../../subscription-system-db-design.md)
remains a broader design, not an implemented schema or acceptance receipt.

## Implemented contract

All production types are in `src/main/java/com/surf/tracker/subscription/domain/`:

- `Money.java`: immutable exact `BigDecimal` and explicit `Currency`; nonnegative
  configured price, without defaults, invented rates, rounding, conversion or charging.
- `PlanVersion.java`: immutable plan ID/version snapshot, exact meter and finite
  positive `long` usage limit. `USAGE_TOKEN` has no cadence; `PERIODIC` requires
  explicit `MONTHLY` or `YEARLY`. Every model is bounded; no unlimited sentinel.
  A subscription retains its original snapshot. Global uniqueness of plan versions
  must eventually be enforced by a repository, not by these constructors.
- `BillingPeriod.java`: half-open `[start,end)` UTC calendar windows. Boundary N
  and N+1 both derive from the original anchor. Jan31→Feb28/29→Mar31 and yearly
  Feb29→Feb28→Feb29 preserve time/nanoseconds without successive-clamp drift.
  Negative/unrepresentable indices fail rather than wrap. Index zero starts at
  the original anchor. This computes boundaries; it does not renew a subscription.
- `AdminGrant.java`: explicit trial or complimentary grant, administrator ID,
  reason, start/end and positive units. This is configuration data, **not proof
  of administrator authentication or successful payment**.
- `Subscription.java`: pending grants zero access; a single explicit grant may
  activate trial/complimentary access only up to the pinned plan limit. No implicit
  renewal, reset, top-up or paid activation. Future-start grants grant no access
  before their start. Expiry observed at/after end cancels terminally; immediate
  cancellation is terminal and idempotent. There is no scheduled-cancel workflow
  yet. Grant windows are explicit configuration, not inferred paid billing periods.

`Subscription` owns the append-only in-memory successful-consumption ledger. Exact
meter/positive integer units are checked; subtraction after a bounds check avoids
cumulative-addition overflow. An operation key is scoped to one subscription.
The same key and `(meter, units)` returns the original immutable receipt, including
its original timestamp/balance, even after expiry/cancellation; this is a replay,
not a new debit. Different payload conflicts. Rejected operations do not reserve
keys. Ledger reads return immutable snapshots. IDs are supplied explicitly and
time is supplied through `Clock`; no random IDs or wall-clock calls occur here.

The instance monitor serializes activation, consumption, replay and cancellation.
It provides **single-instance JVM atomicity only**. Separate instances/processes,
restarts and transaction boundaries are not coordinated. The ledger is retained
in memory with no eviction/persistence; its resource suitability is not production
verified. Do not wire this directly into a public credit/access endpoint.

## Test-first evidence

Tests mirror the package at `src/test/java/com/surf/tracker/subscription/domain/`:
`MoneyTest.java`, `PlanVersionTest.java`, `BillingPeriodTest.java`,
`AdminGrantTest.java`, `SubscriptionTest.java`; shared complex fixtures use
`SubscriptionTestBuilder.java`. All clocks and IDs are deterministic; concurrency
uses latches/futures, not sleeps. No Spring context or external services required.

From the backend repository:

```sh
./mvnw -B -Dtest=MoneyTest,PlanVersionTest,BillingPeriodTest,AdminGrantTest,SubscriptionTest test
```

- RED: `/tmp/opencode/subscription-domain-red.log`, intentional test-compilation
  failure because the five new domain types did not exist. No runtime RED claim.
- Initial GREEN: `/tmp/opencode/subscription-domain-green.log`, **28 passed**.
- Expanded instrumented GREEN: **33 passed, zero failures/errors/skips**,
  `/tmp/opencode/subscription-domain-instrumented.log`.

Instrumented invocation actually run (root Surefire runs in-process):

```sh
JAVA_TOOL_OPTIONS='-javaagent:/home/jjenus/.m2/repository/org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14-runtime.jar=destfile=/tmp/opencode/subscription-domain-coverage.exec,append=false,includes=com.surf.tracker.subscription.domain.*' ./mvnw -B -Dtest=MoneyTest,PlanVersionTest,BillingPeriodTest,AdminGrantTest,SubscriptionTest test
./mvnw -B org.jacoco:jacoco-maven-plugin:0.8.14:report -Djacoco.dataFile=/tmp/opencode/subscription-domain-coverage.exec -Djacoco.outputDirectory=/tmp/opencode/subscription-domain-jacoco
```

The report goal succeeded but did **not** honor that output-directory property;
actual XML is `target/site/jacoco/jacoco.xml`, report log
`/tmp/opencode/subscription-domain-report.log`. Inspection of just the
`com/surf/tracker/subscription/domain` package measured **103/103 lines (100%) and
68/68 branches (100%)**. This is package-only coverage from these tests, **not**
whole-backend coverage or a stronger concurrency/durability proof.

| Guarantee | Cases |
|---|---|
| Explicit money, finite plan/version/meter/cadence configuration | MoneyTest 2; PlanVersionTest 4 |
| UTC month-end, nonleap/leap-year restoration, half-open endpoints, index overflow | BillingPeriodTest 5 |
| Explicit administrator trial configuration and invalid grant rejection | AdminGrantTest 2 |
| Pending/future-start access denied, bounded trial and complimentary activation, both periodic cadences | SubscriptionTest |
| Replay returns original; payload mismatch conflicts; rejected writes leave no debit/key | SubscriptionTest |
| Zero/negative/excess units and Long.MAX_VALUE balance cannot wrap | SubscriptionTest |
| Terminal cancellation/expiry, no new consumption after end | SubscriptionTest |
| Eight concurrent distinct-key contenders cannot overspend one unit; eight same-key contenders debit once | SubscriptionTest |

`git diff --check` passed. Maven runs were serialized. No clean, commit, push,
controller, migration, payment provider, frontend or device changes were made.

## 2026-10-29 enrollment follow-up

The authenticated V5 catalog/enrollment vertical now exists; see
[subscription-enrollment-slice.md](subscription-enrollment-slice.md). It reuses
`PlanVersion`/`Money` for validation but does **not** yet persist or expose
`AdminGrant` activation, consumption, renewal or entitlement checks. The domain
aggregate therefore remains the reviewed invariant kernel, not a wired billing
service. Enrollment is durable and tenant/user-scoped, but it creates only a
`PENDING` record with zero balance and grants no access.

## Integration gates deliberately still open

Implement authenticated tenant/account ownership, durable plan-version uniqueness,
transactional ledger storage and scoped idempotency constraints with real DB
concurrency/restart tests before enforcing product access. Add actual payment
evidence and reconciliation before paid activation. Renewal, dunning, invoice
pricing/proration/tax/coupons, scheduled cancellation and real product metering
integration remain absent. Neither the aggregate nor this test receipt proves
production charging, durable entitlement, multi-node safety or completed subscriptions.
