# Final slice review — 2026-10-29

Coordinator source inspection, not an independent reviewer verdict. Publication
is pending the current full vendor gate and resolution of the findings below.

## Findings and resolution

- Subscription API: a meter longer than the persistence column's 100 characters
  is not explicitly validated. Its database error can be mislabeled as a
  duplicate-plan conflict. Add an HTTP validation regression and reject it before
  persistence. **Resolved:** new HTTP regression reproduced 409 instead of 400;
  explicit nonblank/100-character validation now rejects it before persistence.
- Subscription price guard: compute integer-digit length using widened arithmetic
  before comparing against the durable numeric bound. Extreme scientific notation
  could overflow the `int` subtraction. **Resolved:** real HTTP regression reached
  H2 BigInteger overflow and error-dispatch 401; widening the subtraction to `long`
  now rejects it with 400 before persistence.
- Frontend fixture cleanup: keeping exited group leaders fixes the normal orphan
  case, but SIGKILL escalation still skips groups whose leader already exited.
  A descendant that ignores SIGTERM may survive. Escalate against all owned groups,
  test a deliberately exited wrapper with a surviving descendant, and retain
  explicit signal-path limitations until verified. **Resolved at the helper seam:**
  escalation now signals every retained process group regardless of leader exit.
  Four Node process-lifecycle tests pass, including a real TERM-resistant orphan.
  Helper coverage is 100% lines/branches/functions; the complete runner's signal
  races remain outside this bounded test.

## RED/GREEN evidence

- Backend RED: `/tmp/opencode/tracker-subscription-review-red.log`, two executed
  failures (409/401 versus required 400). RED checkpoint `8805ba9` includes the
  enrollment implementation and reproducers; GREEN fix checkpoint `ebcac8c`.
- Backend GREEN: `/tmp/opencode/tracker-subscription-review-green.log`, all nine
  subscription HTTP cases passed with no failures/errors/skips.
- Frontend initial RED: missing lifecycle module imported by the new regression,
  `/tmp/opencode/tracker-fixture-cleanup-red.log`, checkpoint `31c0ebf`. The
  infrastructure helper was then extracted and corrected in `fb7af6d`.
- Frontend GREEN/coverage: `node --test --experimental-test-coverage
  --test-coverage-include='tools/fixture-lifecycle.mjs'
  tools/fixture-lifecycle.test.mjs`, four passed and 100% helper coverage,
  `/tmp/opencode/tracker-fixture-cleanup-green.log`. Uses Node's built-in runner
  because this is a Node CLI/process fixture, not a Vue/Vitest UI test.

## Bounded security inspection

- Catalog writes require the existing `system.admin` authority; all subscription
  routes require authentication, and enrollment reads/cancellation filter both
  current organization and current user. Foreign IDs return 404. JWT and session
  validation are reused rather than accepting caller-supplied ownership.
- Repository queries use Spring Data parameter binding; no input is interpolated
  into SQL. Plan/meter/cadence/currency/amount and idempotency-key bounds are
  checked before writes. Persisted snapshots expose no payment instrument/token.
- The new endpoint stores only pending intent and cannot mint a paid entitlement.
  No new provider credentials, deployment secrets or hardcoded real recipients
  were introduced. Fixture secrets are randomly generated and private logs are
  restricted; synthetic test credentials are not production credentials.
- Production-wide quotas/rate limiting, catalog/enrollment pagination, paid
  activation, production TLS and dependency audit remain release follow-ups.
  This inspection is not an independent penetration test or complete security
  certification. Existing bearer-token API and sealed-cookie BFF boundaries are
  unchanged by this slice.

## Current gate

The first full-gate attempt was interrupted by a server restart and left no
receipt. Its fresh rerun **passed backend699/device91**, no failures/errors/skips,
both executable JARs built, `/tmp/opencode/tracker-final-vendor-gate.log`. After
the two validation fixes, the final serialized gate **passed backend701/device91**,
zero failures/errors/skips and both executable JARs built:
`/tmp/opencode/tracker-reviewed-vendor-gate.log`. Browser acceptance of this rebuilt
artifact **passed** (`tracker-reviewed-browser-gate.log`), private fixture logs
`/tmp/opencode/wuu-java-uWqSb5/`, artifact SHA256
`a452439bf220fa78a7cbcaaf975348531fb9ded8fbdbad2c6c9da3b2049e6ecb`.
Label-filtered container and Java/Nitro/Playwright process audits found no leftovers.
Historical
`/tmp/opencode` receipts may not survive host
restarts; committed documentation records their scope, not current log availability.

## Self-evaluation (not independent acceptance)

| Axis | Score | Evidence / remaining improvement |
|---|---|---|
| Accuracy | 4 | Full vendor and rebuilt-artifact browser receipts pass; no independent final reviewer verdict. |
| Completeness | 2 | The end-to-end product objective is unfinished: no durable grants/consumption, payment, recipient delivery or frontend writes. |
| Clarity | 4 | Slice contracts and limits are explicit; historical status entries still require reading newest-first. |
| Actionability | 4 | Reproducible commands and regression checkpoints exist; production provider/policy decisions remain. |
| Conciseness | 3 | Durable restart evidence is useful but historical documents repeat superseded states. |

Overall **3.4/5**. Highest-impact follow-ups: durable authenticated grant/usage
integration; recipient/payment decisions with real evidence; consolidate historical
status prose without discarding receipts. Completeness needs further engineering,
not a documentation-only fix. The user should regard this as a verified checkpoint,
not completion of the autonomous directive. `STATUS` remains `IN_PROGRESS`.
