# Subscription owner workflows — 2026-10-29

## Scope and contracts

The independent frontend now has `/subscriptions` in business navigation. Live
users read the configured catalog and their own enrollment states. Catalog plan
IDs/versions are immutable references, not friendly product names. Token, monthly
and yearly models are distinguished; periodic cadence does not promise renewal.

The first owner write use cases are deliberately narrow:

| Gateway                          | Java route                          | Behavior                                                      |
| -------------------------------- | ----------------------------------- | ------------------------------------------------------------- |
| GET subscriptions/catalog        | /api/v1/subscriptions/catalog       | Authenticated configured plans                                |
| GET subscriptions                | /api/v1/subscriptions               | Current tenant/user enrollments                               |
| GET subscriptions/{uuid}         | /api/v1/subscriptions/{uuid}        | Owner-scoped current state                                    |
| POST subscriptions               | /api/v1/subscriptions               | Pending enrollment with pinned plan/version and operation key |
| POST subscriptions/{uuid}/cancel | /api/v1/subscriptions/{uuid}/cancel | Owner cancellation, no refund/payment                         |

Only these subscription routes/methods are exposed. Catalog writes, grants,
consumption recording and arbitrary nested routes remain blocked by the BFF.
Ordinary vehicle/alert/geofence and other resource writes remain blocked.
Java remains the authoritative current-session, tenant and owner authorization
boundary; no frontend role template enables access.

Mutations require the existing exact Origin/Host check, sealed session, JSON,
strict payload schema and a streaming 4096-byte body limit. Client identity and
organization IDs cannot be injected in enrollment JSON. No redirects, shared
bearer fallback or browser-readable auth tokens were introduced.

The adapter normalizes snake_case responses and rejects unsafe JavaScript integer
values or inconsistent cadence/lifecycle contracts. Decimal catalog prices are
explicitly **indicative** because JSON numeric decoding can round exact backend
decimals. This UI performs no financial arithmetic, payment or invoicing.

Enrollment retry keys survive ambiguous failures and page reload in the same tab
through owner/plan-version-scoped sessionStorage. Only random operation IDs are
stored, not tokens, contact details or payment payloads. Storage failure prevents
an unremembered POST. A confirmed receipt releases the key. Closing the tab or
clearing storage ends this client recovery scope; a new tab is a new intent.
The backend durable operation key remains the final concurrency/replay boundary.

Cancellation requires explicit confirmation, warns that granted units are lost,
and re-reads current server state. It is not a paid cancel/refund flow. Enrollment
always starts pending/zero; active trial/complimentary states are shown only when
reported by Java. No simulated preview billing is provided. Live errors never
fall back to fixtures; catalog and owner-list failures are independent.

## TDD and verification

- `e290664`: RED adapter/policy tests referenced the absent subscription service.
- `06b564b`: GREEN adapter/policy tests (14 cases), typecheck/build/boundaries;
  browser enrollment/cancel, ambiguous same-key retry, CSRF/administrative denial,
  4096-byte input limit, error handling and accessibility.
- Browser's first attempt caught an existing shared search-button accessible-name
  mismatch. Removing its aria-label exposed a mobile-only unnamed button because
  its visible text is hidden at small widths. The final accessible name includes
  the full visible text and keyboard shortcut; desktop and mobile checks pass.
- `bcda972`: RED reload-recovery tests referenced an absent intent-store module.
  GREEN recovery checks include same-owner/version continuity, owner/version
  separation, confirmed-key release and storage-denial failure.
- Latest coverage/typecheck receipts:
  `/tmp/opencode/subscription-ui-coverage-v2.log` and
  `/tmp/opencode/subscription-ui-types-v2.log`. These include unrelated pre-existing
  detail-read/list-completeness work and are whole-working-tree test results, not
  proof that those edits belong to this slice's commits.
- Actual Java browser fixture seeds three configured plans **only in its own
  disposable PostgreSQL database**, without elevating accounts. Tests exercise
  real authenticated pending enrollment, reload, persisted cancellation, two-tenant
  foreign read/cancel denial, CSRF denial and admin-route refusal. Current outcome
  **PASS** against final catalog-enabled artifact SHA256
  `0866662ffa28b86072a262fa3c6236af12759048b94d264c6180cf9f80d4d99e`;
  receipt `/tmp/opencode/role-catalog-java-final.log`, private browser
  `/tmp/opencode/wuu-java-ht67wt/browser.log`. Same-tenant different-user
  subscription reads/cancels return404, the colleague without `roles.read`
  receives403, and owner role reads ignore a foreign `orgId` override.

## Effective access and latest gates

`GET /api/_wuu/access` maps **only** to Java `/api/v1/users/me/access`. No nested
user resource or arbitrary user proxy is exposed. The validated response contains
the authenticated user, nullable current tenant, permission keys and access expiry,
not tokens/session UUID. Settings displays it independently from unsupported
notification preferences; a preference error cannot hide permitted access data.
Recheck loads the server snapshot again. It does not enable platform concept pages
or replace backend authorization. Preview role templates remain illustrative.

The backend endpoint was delivered in an isolated worktree and published to
`origin/main` at `6841270`, with full **718 backend/91 device** tests passed and
real PostgreSQL permission-removal/revocation verification. The original backend
working tree and its unrelated startup edits remain untouched. The Java acceptance
runner now accepts an explicit `WUU_JAVA_JAR` for such isolated artifacts; it still
hashes/verifies the immutable artifact, never builds or edits backend source.

Latest frontend unit gate: **110 passed** under Vitest 4.1.12. Selected-source
coverage is **92.47% lines, 85.61% branches, 95.09% functions**
(`/tmp/opencode/vitest4-coverage.log`); the earlier 96.75/88.88/95.51 figures were
measured by Vitest 3, so the two percentages are not directly comparable. These
include the new subscription/access adapters and recovery helper, not whole-UI
coverage. Typecheck, build, boundaries and formatting passed. Complete auth suite
**23/23** passed (`/tmp/opencode/reviewer-fixes-auth.log`), including pre-existing
detail-read tests, mounted snapshot refresh and failed-cancellation retry. Preview
**31/31** passed (`subscription-ui-preview-v2.log`); final mobile/maps/editor/
tracking/subscription subset passed (`effective-access-final-mobile.log`).
Final own-property allowlist hardening has its own RED/GREEN 4-test gateway
regression, plus the 21-case gateway/subscription/access batch. It rejects
`constructor`, `__proto__` and `toString` instead of treating inherited Object
properties as configured resources; no prototype mutation/remote-origin bypass
was demonstrated.

Dependency remediation pins `node-forge` 1.4.1, `serialize-javascript` 7.1.3 and
`fontless>esbuild` 0.28.2 in `pnpm-workspace.yaml`, and upgrades Vitest to 4.1.12.
A fresh full audit reports zero critical/high/moderate and one low published as
`deepmerge` 4.3.1; the named fix `4.3.2` does not exist in the registry. See
`effective-access.md`. This is not a clean security audit.

Independent bounded code/security reviewer `ses_e78524f6dffeLRbuot2sTovNzd`
reported no confirmed critical/high regression and two actionable warnings:
missing explicit snapshot refresh and cancellation confirmation dismissed on
failure. Both are now fixed with new browser regressions: an explicit refresh
button reports a last-checked snapshot, and a failed cancellation keeps the
confirmation available for retry. The direct-Java same-tenant/different-user
ownership case now passes inside the final acceptance run. No independent
final-release approval is claimed. Review was read-only and did not independently
rerun the gate receipts.
Known limits: storage lifetime is same-tab only, prices are indicative, no
multi-device payment/entitlement state, no full UI coverage or production review.

This does not add paid billing, automatic usage reporting, actual feature gating,
admin catalog/grant UI, renewal, invoices, provider callbacks or production
deployment. `IN_PROGRESS` remains set. Existing detail/list changes are preserved
and must not be staged with this work.
