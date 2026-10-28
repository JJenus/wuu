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

### PostgreSQL deployment profile

Activate `SPRING_PROFILES_ACTIVE=postgres` and provide:

| Variable | Purpose |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL (use deployment-appropriate TLS, e.g. `sslmode=verify-full`) |
| `DB_USERNAME`, `DB_PASSWORD` | Database credentials |
| `ARTEMIS_BROKER_URL` | Broker connection URL; defaults to `tcp://localhost:61616` for local validation |
| `ARTEMIS_USER`, `ARTEMIS_PASSWORD` | Required broker credentials; no admin/admin fallback in this profile |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Redis connection settings |
| `JWT_SECRET` | Required signing secret described above |

The profile selects `db/postgresql` migrations and PostgreSQL's JDBC driver and
Hibernate dialect. The default H2 history in `db/migration` is unchanged.
PostgreSQL V1 uses checked VARCHAR enum columns instead of H2's inline ENUM
syntax; `MigrationParityTest` verifies that this is the only schema difference
and that V2/V3 match across both histories. Future migrations must be supplied
for each supported database. This does **not** migrate existing H2 data into
PostgreSQL, and histories must not be switched on an existing database or
repaired to bypass checksum mismatches.

### Docker-backed regression and deployment verification (2026-10-26)

Run the self-contained deployment test with Docker running:

```sh
./mvnw -B -Dtest=PostgresDeploymentIT test
```

Testcontainers starts disposable PostgreSQL 16, Redis 7 and Artemis 2.40.0 with
random ports, and cleans up owned resources. No application database or broker
is used. The test proves:

- Fresh PostgreSQL V1–V3 migration and Hibernate validation of the composed app.
- V2→V3 upgrade revokes legacy sessions/refresh tokens in a separate test schema.
- Health is UP with database, Redis and JMS checks and JMS listeners enabled.
- Closed registration, real HTTP login/profile/logout and access-token rejection.
- PostgreSQL concurrent refresh locking and committed session/family revocation.
- Redis read/write and authenticated Artemis queue-message round trip.

RED evidence: the first run failed because `org.postgresql.Driver` was missing;
after supplying the JDBC/Flyway adapters, PostgreSQL rejected the original V1
with `ERROR: type "enum" does not exist`. The new profile/vendor history fixed
both failures; all six deployment tests passed. No H2 migrations were edited.

The complete selected regression/package run used a separate disposable Redis
mapped to loopback port 32768:

```sh
./mvnw -B '-Dtest=*Test,*IntegrationTest,FlywayMigrationIT,RedisAlertingCacheIT,RedisCacheTransactionIT,CoreRepositoryContextIT,PostgresDeploymentIT' -Dtracker.test.redis.port=32768 package
```

The run passed **514 tests**, with no failures/errors/skips, and produced the
executable JAR. This is selected integration coverage, not a claim that every
opt-in `*IT`, telemetry pipeline or production environment was tested. Coverage
percentages were not measured.

The packaged JAR was also started against separate disposable Docker services
with the PostgreSQL profile and **all health indicators/JMS listeners enabled**.
Health returned 200/UP. Registration was enabled only to create a disposable
fixture, then the app was restarted with registration disabled against the same
PostgreSQL database. Registration then returned 401; the persisted user's login
and profile returned 200, refresh returned 200, logout returned 204, and both
old/refreshed access tokens returned 401 afterward. Anonymous H2-console and
actuator-env requests returned 401. Owned smoke containers were stopped and
removed. This supersedes the earlier H2-only smoke check with unavailable JMS.
