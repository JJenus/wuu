# Fleet registration — 2026-11-10

Recovered the pending FLEET-01 implementation into `finish/fleet-registration`
without changing its original worktree. Vehicle/tracker create and update requests
are validated; IDs are normalized; creates preserve tenant ownership and reject
duplicates instead of overwriting data. Creation timestamps are initialized.

Closed the handoff's remaining cross-table device-identity gap with V7/V8 in both
Flyway histories. A shared primary key plus transactional insert/update/delete
triggers guarantees one vehicle-or-tracker owner per non-null device ID, including
direct SQL and concurrent writers. The PostgreSQL-only advisory-lock workaround
was removed. Existing multi-tracker cardinality and assignment policy are unchanged.

## Verification

- RED: `./mvnw -B -Dtest=DeviceIdentityMigrationTest test` — 6 executed,
  4 assertion failures and 2 missing-registry errors, before implementation.
- GREEN: the same tests with optional `-Dfleet.test.postgres.url=<disposable URL>`
  — 12/12 across H2 and native PostgreSQL 18. The private loopback PostgreSQL
  instance was initialized under `/tmp/opencode/fleet-postgres-0q8lao3n`, then
  stopped. No application database was used.
- `./mvnw -B -Dtest=CreateIsolationIntegrationTest,FlywayMigrationIT,MigrationParityTest test`
  — 17/17. Includes 14 controller/service/real-H2 cases, committed persistence,
  duplicate rollback, validation, and fresh-schema Hibernate validation.
- `./mvnw -B verify` — 666/666 tests passed; executable backend JAR packaged.
- `git diff --check` — passed. SQL values are bound parameters; authorization
  routing and tenant checks are preserved. Coverage was not measured.

Logs: `/tmp/opencode/fleet-registration-{red,postgres,api-final,verify}.log`.
The Docker-backed full `PostgresDeploymentIT`, browser/full-stack journeys and
device gateway build were not run. Backend checks do not certify these boundaries.

## Migration operations

V7 adds the registry; V8 backfills and installs maintenance triggers. PostgreSQL
locks both source tables for the V8 transaction. **Pause all writers for H2**,
whose DDL implicitly commits; do not migrate a live H2 writer pool. Allow for
backfill time and PostgreSQL write blocking when scheduling a production upgrade.

Preflight existing data (read-only):

```sql
select device_id, count(*) from (
    select device_id from vehicles where device_id is not null
    union all
    select device_id from trackers where device_id is not null
) owners group by device_id having count(*) > 1;
```

Collisions deliberately fail migration without changing the source records.
Resolve them through an approved data-repair decision, not automatic deletion.
On H2, a failed migration requires inspecting Flyway history/partial DDL before a
reviewed repair/retry; do not blindly rerun or disable validation. Do not edit
applied migrations. Rollback requires a reviewed forward migration; retain the
versioned H2 trigger class while these database triggers exist.

Next user-facing task: vehicle/create and assigned-tracker registration UI using
these contracts. Geofence editor recovery remains separate unfinished work. No
push, production migration or deployment was performed.
