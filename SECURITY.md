# Authentication deployment notes

## Required deployment configuration

- Supply `JWT_SECRET` from a secret manager: at least 32 bytes of cryptographically
  random material. There is deliberately no checked-in signing secret.
- Terminate HTTPS at a trusted ingress; do not expose the application HTTP port
  directly to the internet. Protect Redis, Artemis and the database on private
  networks with deployment-specific credentials.
- Set `CORS_ALLOWED_ORIGINS` to explicit, comma-separated frontend origins.
  Wildcards are rejected. Empty configuration allows no cross-origin frontend.
- Leave `REGISTRATION_ENABLED=false` for a closed deployment. Provision the first
  administrator through a reviewed out-of-band process; no public bootstrap-admin
  endpoint is supplied. Enabling registration creates new tenant administrators.
- Apply a shared ingress rate limit and request-body limits. The application's
  bounded 30-auth-requests/minute/IP limiter is per process, not distributed.
  Forwarded client-IP headers are not trusted by default. Behind a proxy, this can
  group all clients under the proxy address; configure trusted proxy handling
  deliberately rather than trusting arbitrary `X-Forwarded-For` values.

## Rollout and client compatibility

Local-only exception: `LOCAL_ACCOUNTS_ENABLED` defaults to false. Opting in requires
the `local` profile without `prod`/`production` and a literal loopback `server.address`;
otherwise startup fails. It seeds one VIEWER and one TENANT_ADMIN using private
environment passwords (no defaults), and exposes a loopback-peer-only minimal
pre-login identity directory. This intentionally enumerates local users and must
never be enabled on public infrastructure or a database containing real users.
Existing accounts are never reset/elevated by the runner. All logins still require
password verification and the ordinary session/rate-limit rules. This is not
production administrator provisioning or a passwordless login facility.

- Flyway V3 adds session organization binding and revokes existing sessions and
  refresh tokens. All existing users must log in again. Back up the database and
  test migrations against a copy before rollout; do not edit applied migrations.
- Access tokens last 15 minutes by default. Requests require an active database
  session and current tenant membership. JWT role claims do not authorize access;
  roles and permissions are loaded from the database on each request.
- Refresh tokens are single-use and rotated under a database lock. Reusing a
  consumed token revokes its session and refresh family. Clients must serialize
  refresh requests; parallel refresh attempts intentionally trigger revocation.
- Logout revokes **all** sessions for the user. Password changes also revoke all
  sessions. New passwords require at least 12 characters and at most 72 UTF-8
  bytes (BCrypt's limit); existing shorter passwords can still be verified.
- The API still uses bearer tokens, not authentication cookies. Keep tokens out
  of URLs and logs, and protect frontend token storage from XSS. JSON uses
  snake_case; Java polymorphic type IDs are not accepted by the HTTP mapper.

## HTTP authorization boundaries

- All unmatched routes are denied. Business routes have explicit permission
  checks; tenant-scoped repositories remain responsible for row isolation.
- Organization administration is system-admin-only except permission-checked
  reads/updates of the caller's organization. Tenant role assignment cannot grant
  system or other-tenant roles or remove the final tenant administrator.
- Notification operations are self-service and tenant-scoped. Shared notification
  templates require `system.admin`.
- Webhooks are denied, including for authenticated callers, until provider-specific
  signature and replay verification is implemented. Delivery status callbacks
  therefore do not work over these HTTP routes in this configuration.
- Device-command creation requires `devices.command`; the persisted initiator is
  the authenticated user, not a client-provided identity. HTTP command
  send/deliver/fail status mutation routes are denied.
- H2 console access is disabled. Only actuator health/info are exposed; health
  has no details, and info requires `system.admin`. Documentation endpoints also
  require `system.admin`.

## WebSocket and device boundaries

- WebSocket identity comes from an authenticated handshake principal, never a
  query-parameter user ID. Origins use the configured allowlist. Notification
  delivery matches both user and organization and rechecks session/membership.
- Clients need a transport capable of an authenticated bearer-header handshake.
  Native browser WebSocket APIs cannot attach arbitrary Authorization headers;
  no cookie or short-lived handshake-ticket bridge is supplied in this pass.
- An idle socket is not proactively disconnected on token expiry or logout;
  session validity is rechecked on client messages and notification delivery.
- Device TCP/protocol behavior is unchanged. This HTTP hardening does not add
  device credentials, TLS/mTLS, transport replay protection or broker hardening.
  Do not describe it as end-to-end device security.

## Verification evidence

From the repository root, using a disposable Redis on port 16479:

```sh
./mvnw -B '-Dtest=*Test,*IntegrationTest,FlywayMigrationIT,RedisAlertingCacheIT,RedisCacheTransactionIT,CoreRepositoryContextIT' -Dtracker.test.redis.port=16479 package
```

The verified run passed 506 tests with no failures/errors/skips and produced the
executable JAR. This is selected integration coverage, not a claim that every
opt-in `*IT` or a production PostgreSQL deployment was tested. Coverage percentages
were not measured.

Packaged-JAR smoke checks verified login identity/logout revocation over HTTP,
and anonymous user/actuator-env/H2 requests returned 401. The isolated smoke test
used in-memory H2 and disabled JMS listeners; Artemis was unavailable. Normal
health correctly remained DOWN for the missing broker; disabling only the JMS
health check for the isolated test yielded UP. Do not disable it to mask a
production broker outage.
