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

The `api-v1` group is served at `/v3/api-docs/api-v1`; access requires
`system.admin`. Existing springdoc dependencies are unchanged. The contract has
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
- `NotificationQueryService.getUserNotifications` currently ignores `unreadOnly`.
  The operation description records this mismatch; business behavior was not
  changed as part of endpoint/documentation cleanup.
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

Work is isolated in `/home/jjenus/workspace/apps/tracker-api-standard`, with
independent `sidie` and `app` worktrees on `feat/api-v1-standard`. The original
workspace and its build outputs are not edited.

Before the owner imposed the no-install/no-build constraint, an initial Maven
test attempt compiled the new test but failed on ambiguous handler-mapping bean
injection, before routing assertions ran. A qualifier corrects that test wiring.
This is not valid RED/GREEN behavior evidence. Frontend dependency installation
also completed before that constraint; no dependency manifests or versions were
changed. No further installs, builds, app servers or runtime suites are authorized.

Updated tests cover the canonical route inventory, operation metadata, generated
OpenAPI security/paths, absence of old paths and consumer path policies. They
remain unrun after changes. Static checks do not establish runtime correctness,
springdoc compatibility, tenant-isolation acceptance or complete schema fidelity.
Static checks passed for 15 marked controllers and 104 unique documented operation
IDs, relative controller mappings, preserved security rule order/authorities,
TypeScript parsing, UI/server API boundaries and unchanged dependency/device/shared
modules. The baseline includes a trip-history operation added since the original
103-operation audit. These counts describe source, not runtime acceptance.
Run the focused suites and real affected workflows only when resources are
authorized. Do not merge/publish this branch as runtime-verified on static checks.
