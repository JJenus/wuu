# REST v1 routing and documentation

## Contract

Application controllers declare `@ApiV1` and a resource-relative class mapping,
for example `@RequestMapping("/vehicles")`. `main/api/ApiVersionConfig` supplies
`/api/v1` once. Do not put `/api/v1` or `/api` in those controller mappings.
Application component scanning loads the configuration; isolated MVC tests must
import it. Standalone tests use `main/api/ApiMockMvc`, which installs the same
prefix/type predicate.

Every current application REST operation belongs to v1. Compatible additions
remain in v1; a genuinely breaking contract requires a distinct version marker
and adapter, not changing the meaning of `ApiV1`. Unsupported versions do not
fall back. Infrastructure `/actuator`, `/v3/api-docs`, `/swagger-ui` and the
notification WebSocket `/ws/notifications` are not moved under the REST prefix.

Old `/alerts...` and `/api/notifications...` paths have been removed. There are
no dual mappings, redirects or compatibility aliases. Existing resource suffixes
are retained, including distinct paginated and unpaginated `/list` operations.

## OpenAPI

Fleet-management addition (2026-11-13): `PATCH /api/v1/trackers/{trackerId}/profile`
and `POST /api/v1/trackers/{trackerId}/reassign` bring the current inventory to
**106 operations**. Both require `vehicles.assign_device`; vehicle PUT remains
`vehicles.write`. See `../../app/docs/fleet-registration.md` for strict frontend
payloads, guarded unused correction, checks and limitations. Historical 104-operation
verification counts below describe the earlier cutover.

The `api-v1` group is served at `/v3/api-docs/api-v1`; access requires
`system.admin`. springdoc is pinned to **2.8.17**, whose published parent targets
Spring Boot 3.5.x; 2.2.0 crashed with this application's Spring Framework at runtime.
The schema resolver uses the application's Jackson mapper so generated field names
match actual snake_case JSON rather than Swagger's default camelCase. The contract has
metadata, stable unique operation IDs, resource tags, per-operation descriptions,
bearer security, public auth exceptions and security-error descriptions. Actual
request/response DTO signatures remain the source of generated schemas; selected
request schemas add examples and server-owned field explanations. Validation
annotations continue to describe existing constraints, not newly imposed rules.

Paths already contain `/api/v1`; do not also append it to OpenAPI `servers`.
`info.version` is metadata, not a runtime route selector. Do not describe an
operation as available merely because a Java handler is mapped.

### Important behavior and limitations

- Webhook callbacks, command send/deliver/fail and alert cleanup/automated HTTP
  handlers remain explicitly denied, including for administrators. Their docs
  identify the restriction and omit successful HTTP responses. No provider
  signature verification or command transport is introduced by this change.
- Organization permissions include method-level security, not just URL matchers.
- Notification endpoints are self-service in the active tenant; a supplied user
  identifier cannot select a different user. Templates require `system.admin`.
- `unreadOnly=true` now excludes deliveries with a persisted `OPENED` event,
  filtering before pagination and total counts. Read state is independent of
  delivery status; `DELIVERED` does not imply read. Default false still includes
  both read and unread deliveries. Bulk read now covers all owned unread deliveries
  visible to its database statement, without a page cap; later arrivals remain unread.
- Notifications retain Spring `Page`; other resources retain their existing
  project pagination envelopes. This is documented, not silently redesigned.
- MVC errors use `ErrorResponse`, while security filters may return only `code`.
  ErrorResponse's current default timestamp is the epoch, not occurrence time.
- Tracker list filters have precedence rather than conjunction: status, online,
  battery threshold, then stale. Descriptions reflect this existing behavior.
- CSV export requires `trips.export`, returns `text/csv` and an attachment header.
- Enrollment has 201 for creation and 200 for receipt replay; it does not prove
  payment or paid access. Catalog version is distinct from API major version.
- The optional frontend local account picker targets a separately supported
  local-profile backend route; this backend does not implement that directory.

## Frontend boundary

The sibling frontend's `server/utils/upstream-api.ts` declares the upstream prefix
and routes once. Gateway policies, authentication and local-directory clients use
it. Browser adapters continue to address BFF resource aliases; components/pages
use composables/services and do not call APIs or import upstream route details.
The frontend static boundary checker enforces those rules. Backend security
matchers continue to use effective external URLs, not relative controller paths.

## Verification status

Verified in the original repositories after local merges `c4b0d74` / `e6d3ac7`,
with the owner's runtime-check authorization, on **2026-11-13**. The prior isolated
worktree/static-only status is superseded. The unrelated local `app/nuxt.config.ts`
title edit was preserved and excluded from verification commits.

Two defects were reproduced as RED and fixed to GREEN:

1. `/v3/api-docs/api-v1` threw `NoSuchMethodError: ControllerAdviceBean(Object)`.
   Updated springdoc 2.2.0 → 2.8.17, retaining Boot 3.5.9. The published
   [2.8.17 POM](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi/2.8.17/springdoc-openapi-2.8.17.pom)
   targets Boot 3.5.13; no framework major upgrade or exception-handler bypass.
2. Generated request/response fields were camelCase despite actual snake_case HTTP
   JSON. Registered a `ModelResolver` with the existing application mapper; runtime
   field-name, required-property and password-minimum assertions now pass.

Checks actually run:

- Initial focused backend run: 60 passed, one OpenAPI linkage error. After fixes,
  `./mvnw -B verify` passed **708 tests** and executable packaging.
- `ApiContractIntegrationTest` covers all **104** mapped/generated operations,
  unique IDs/descriptions, bearer/public exceptions, resolvable local schema refs,
  snake_case DTO properties, required registration fields, eight-character password
  minimum, CSV media/attachment headers, enrollment 200/201/409 and empty 204 bodies.
  It checks anonymous access to every protected operation, admin-only docs/UI,
  rejection of legacy/unknown versions, and disabled operations remaining denied
  even to administrators. This inventories contracts, not all business scenarios.
- Frontend focused route/policy tests: **41 passed**. Ten additional boundary-checker
  fixtures verify forbidden component/workflow HTTP, API imports, client-side
  upstream details and repeated server prefixes, plus admitted adapter/registry use.
- Full frontend unit run: **227 passed, 1 failed** (existing branding assertion
  against the preserved local title edit). With only `tests/unit/branding.test.ts`
  explicitly excluded: **226 passed**, **94.83% lines / 86.37% branches**; the server
  route registry is now included in coverage. These are scoped domain/adapter/policy
  measurements, not whole-UI coverage. Java coverage is not instrumented.
- `pnpm typecheck`, `pnpm check:boundaries`, and `pnpm build` passed. Browser suites:
  `playwright.auth.config.ts` **42**, fleet **4**, geofence **14** passed. These use
  contract fixtures; they are not presented as actual-Java acceptance.
- `python3 tools/verify-telemetry.py --artemis-home /home/jjenus/.cache/tracker-tests/apache-artemis-2.40.0 --verify-api-v1`
  passed real loopback TCP/Artemis/PostgreSQL/Redis/Java/browser acceptance. Actual
  authenticated HTTP generated the 104-operation spec, rejected anonymous/non-admin
  docs access and legacy/version fallback. A dedicated test account receives
  `system.admin` only through an out-of-band fixture in the disposable database.
  The actual BFF/browser created and reloaded a geofence, created/reloaded/disabled
  its rule, acknowledged/resolved/re-read a real alert, and read notifications.
  Existing outage/restart/history/tenant-denial checks still passed. An initial
  expanded browser attempt omitted the editor's coordinate-shape initialization;
  the corrected test follows the real UI and passes without changing app behavior.

Remaining work is **field-level documentation and separately scoped behavior
fixes**, including epoch error timestamps. The bulk-read cap is fixed below.
No compatibility aliases were added; no production deployment or production
database/broker changes were performed. Physical device/command transport and
release certification remain separate from this API contract verification.

### Notification unread follow-up — 2026-11-13

Outcome: authenticated GET `/api/v1/notifications/user/{userId}?unreadOnly=true`
now uses a tenant/recipient-scoped `NOT EXISTS` query against `OPENED` delivery
events. This avoids joining duplicate events into duplicate rows and applies
filtering/counting in SQL before pagination. Existing false/default behavior and
provider delivery lifecycle are unchanged. The user must be the authenticated
database user; no caller-selected recipient or tenant is admitted. No migration,
new response fields, provider calls or frontend capabilities were introduced.

Documented all ten `NotificationResponse` fields and the `unreadOnly` query
parameter. The OpenAPI test verifies generated descriptions. At this stage the
bulk-read 1000-delivery limitation was disclosed; the next increment below removes it.

RED: four persistence regressions ran, three failed (unread count was 3 rather
than 2; individually/bulk-read records still appeared). GREEN:
`./mvnw -B -Dtest=NotificationReadIntegrationTest,NotificationReadHttpIntegrationTest,NotificationScopeTest,ApiContractIntegrationTest verify`
passed **16 focused tests** and executable packaging. Checks cover database
pagination/totals, false/default semantics, duplicate open events, non-open provider
events, persisted individual/bulk-read changes for a small inbox, tenant/recipient
isolation, missing context, and generated field docs. A real random-port HTTP test
logs in with a real session, reads pages, marks a delivery read, marks the small
inbox read, re-reads saved state and checks anonymous/foreign-recipient denial.
This uses the migrated H2 test database, not PostgreSQL or an external provider.
No whole-stack or frontend tests were rerun for this backend-only increment; the
earlier whole-stack evidence above belongs to API standardization, not this fix.

### Complete bulk read — 2026-11-13

Outcome: POST `/api/v1/notifications/user/{userId}/read-all` no longer reads only
the first 1000 deliveries. After existing authenticated-user/tenant checks, one
parameterized `INSERT ... SELECT` records `OPENED` events for all scoped deliveries
without such an event. It is a single atomic database statement in the existing
transaction; the application does not materialize or retain an inbox-sized list.
Existing events, provider status and other recipients/tenants remain unchanged.
Empty inboxes and sequential retries add no duplicate open events. Concurrent
read requests may still append duplicate audit events under the existing event
model; read results remain correct, and no unique-event guarantee is claimed.
Rows arriving after the statement are not marked read. No schema migration,
provider call, compatibility alias or frontend capability was added.

RED: the large-inbox persistence test left an unread page (26 pages at size 20)
after the old implementation's capped operation. GREEN:
`./mvnw -B -Dtest=NotificationReadIntegrationTest,NotificationReadHttpIntegrationTest,NotificationScopeTest,ApiContractIntegrationTest verify`
passed **18 tests** and executable packaging. The expanded persistence check covers
1508 owned deliveries including one already read, foreign recipients/tenants,
empty/denied operations and sequential retries without new events. The actual HTTP
workflow covers 1507 owned deliveries, a real login/session, persisted bulk-read
results, repeat requests, foreign-recipient denial and a later arrival staying unread.

The same HTTP test also passed separately against a fresh owned loopback
**PostgreSQL 18** cluster with actual PostgreSQL Flyway migrations. The cluster was
stopped afterward; no production data/services or provider were accessed. To repeat
with a separately provisioned disposable cluster/user/database `notification_read_test`:
`NOTIFICATION_READ_TEST_DB_URL=jdbc:postgresql://127.0.0.1:<port>/notification_read_test ./mvnw -B -Dtest=NotificationReadHttpIntegrationTest -Dspring.profiles.active=postgres test`.
The test rejects non-loopback/differently named database URLs. H2 remains the default
when that variable is absent. No unrelated full-stack/frontend suites were rerun.
