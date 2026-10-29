# BFF session renewal

## Contract and boundaries

Java login and refresh return `access_token`, `refresh_token`, `expires_in`,
`refresh_expires_in` and `token_type: "Bearer"`. Backend commit `255be2c` adds
the refresh lifetime without weakening single-use rotation or reuse revocation.
The refresh lifetime is bounded by the existing absolute database session; it is
not a sliding extension or paid entitlement.

The BFF seals both credentials and absolute expiries in its encrypted HttpOnly,
SameSite=Strict cookie. The browser receives only validated identity and an
`expires_at` access deadline in milliseconds, never bearer or refresh tokens.
Cookies remain Secure outside loopback. Older access-only cookies require sign-in.
Each BFF process starts with a fresh random local epoch. Restarting it requires
sign-in rather than risking replay after its in-memory receipts have been lost.

- The client schedules renewal one minute before access expiry. The exact-origin
  `POST /api/auth/refresh` accepts no browser-supplied credential or tenant choice.
- Session inspection renews within one minute of expiry; allowlisted gateway
  requests renew within 30 seconds. SSR forwards replacement/deletion cookies to
  the outer HTML response.
- Concurrent requests coordinate by authenticated refresh credential and private
  backend/session configuration, not randomized encrypted cookie text. A bounded
  30-second receipt recovers the same generation after a lost BFF response,
  including when access tokens live for less than five minutes.
- Ambiguous upstream failure clears the cookie and rejects further use of that
  credential. Rejection tracking is separate from the 256-slot transient renewal
  admission budget. At 256 retained rejections, a fail-closed local epoch cutover
  requires old sessions to sign in again; fresh logins can renew immediately after
  upstream recovery. No active recovery receipt is evicted to admit new work.
- Logout renews an expired access token before account-wide Java revocation, then
  clears the local cookie and invalidates its receipt/in-flight work. Failed
  revocation is still disclosed. Client renewal cannot adopt an in-flight result
  after local sign-out. Identity/tenant changes force a full navigation, clearing
  identity-scoped services and query state.

## Deployment limitation

This implementation supports **one BFF process only**. Sharing the cookie secret
does not coordinate refresh tokens across workers or replicas; sticky routing is
not distributed coordination. Distributed durable receipts, ingress/TLS/Host and
rate-limit review, and independent production acceptance remain release gates.
No production deployment is authorized. Backend session/permission checks remain
authoritative on every request, including requests using a recovery receipt.

## TDD and review evidence

Journeys: stay signed in through access expiry; recover a lost response without
reusing a consumed credential; preserve logout/revocation and fail-closed errors.

| Guarantee                                                                                                      | Evidence                                                                                                                                   |
| -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Independently encrypted cookies coalesce one refresh credential; SSR forwards its replacement cookie           | `refresh.spec.ts`, RED `dbc6d63`, `/tmp/opencode/refresh-hardening-red.log`                                                                |
| An ambiguous failure rejects later dispatched requests; short access TTL does not rotate again during recovery | `session-renewal.test.ts`, `refresh.spec.ts`, RED `70ea423`, `/tmp/opencode/renewal-review-red.log`, `/tmp/opencode/renewal-short-red.log` |
| Expiry, timer renewal, gateway renewal, Origin denial, logout, unrelated-user receipt preservation             | `tests/auth/session.spec.ts`, `tests/auth/refresh.spec.ts`                                                                                 |
| Bounded tracking, no in-window eviction, in-flight logout cancellation, recovery after 256 failures            | `tests/unit/session-renewal.test.ts`, `/tmp/opencode/renewal-capacity-red.log`                                                             |

Process-restart rejection is also covered by `session-renewal.test.ts`, with RED
receipt `/tmp/opencode/renewal-restart-red.log`.

Final local gates: **133 unit tests / 19 files PASS**, selected-source coverage
**92.79% lines / 85.56% branches / 95.11% functions**. Renewal utility coverage is
**95.2% lines / 84.05% branches / 100% functions**; this is not whole-UI coverage.
Typecheck, production build and **32 auth browser tests PASS**.
Receipts: `/tmp/opencode/renewal-coverage-final.log`, `renewal-types-final.log`,
`renewal-build-final.log`, `renewal-auth-final.log`.
GREEN implementation: `7a85cbe`. Boundary checks and scoped formatting pass.
The full formatting command still reports pre-existing `app/layouts/default.vue`;
that unrelated tracked file was not modified by this slice.

Actual-Java final acceptance **1/1 PASS**, using a disposable two-minute access
TTL to force real rotation, then an authorized vehicle read and account-wide
revocation. Receipt `/tmp/opencode/renewal-java-final2.log`, private browser log
`/tmp/opencode/wuu-java-50ejEH/browser.log`. The runner builds the production
frontend and verifies the unchanged backend JAR SHA256
`4f4a24c329537252e06f6f5aab97347054172046a2e32196fa9c260030767454`.
PostgreSQL/Redis/Artemis health and owned cleanup passed; no owned fixture
containers remained afterward. The earlier `/tmp/opencode/refresh-java-acceptance.log`
proved the refresh route but did not force rotation at the default 15-minute TTL;
the two-minute final run supersedes that narrower evidence.

A bounded read-only security review identified the late-replay, short-TTL and
outage-capacity issues; each was corrected with regressions. The final follow-up
found no additional concrete medium/high defect in the single-process bounded
cutover flow. The reviewer did not run tests and this is not a release/security
certification. `STATUS=IN_PROGRESS` remains appropriate.

## Self-evaluation (not independent acceptance)

| Axis          | Score | Evidence / remaining improvement                                                                                      |
| ------------- | ----- | --------------------------------------------------------------------------------------------------------------------- |
| Accuracy      | 4/5   | 133 unit, 32 auth and actual-Java gates identify what ran; production ingress is untested.                            |
| Completeness  | 3/5   | Single-process renewal is delivered; durable distributed coordination and other product/release work remain open.     |
| Clarity       | 4/5   | Restart, outage cutover and single-process limits are explicit; older dated docs still contain superseded statements. |
| Actionability | 4/5   | Published source and repeatable disposable acceptance; no authorized production configuration or deployment.          |
| Conciseness   | 4/5   | Existing gateway/auth seams reused; cross-request rotation adds unavoidable state and recovery documentation.         |

Overall **3.8/5** against the full delivery goal. Highest-impact follow-ups: durable
shared rotation receipts before multi-worker operation; then verified live writes
and the outstanding device/provider acceptance. The owner would expect continued
work, not a release-ready claim based on these green gates.
