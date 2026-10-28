# Fleet HTTP / PostgreSQL verification

Scope: only `src/test/java/com/surf/tracker/main/PostgresDeploymentIT.java` and this report. No production edits or commits.

## Contracts inspected

- Vehicle/tracker controllers, create/update DTOs, services, entities and tenant-scoped repository calls.
- Trip query/export controllers and services; CSV header, vehicle/date filtering and response fields.
- Overspeed template/update DTOs, alert-rule controller/service and exception mapping.
- Existing fixture provisions tenant roles with seeded permissions, then logs in over HTTP for actual JWT credentials. No security bypasses or mocked infrastructure.

## Added guarantees

| Area | Assertions |
| --- | --- |
| Vehicles | Create, paginated tenant-only listing, foreign read/update/delete rejection, persisted updates, deletion and subsequent absence |
| Trackers | Create with vehicle association, tenant-scoped status/low-battery filters, foreign read/update/delete rejection, update, delete |
| Duplicate IDs | Separate same-tenant and cross-tenant POST cases for both vehicles and trackers: 409 required; original response and vehicle association must remain unchanged |
| Assignment validation | Foreign vehicle cannot receive a newly created tracker; neither tenant gains a tracker |
| Enum validation | Invalid tracker status in update/filter returns 400; rejected update leaves original state unchanged |
| Trips | Tenant-only listing/detail and foreign detail rejection; CSV content type, attachment header, exact fixture row count, duration and fixed timestamps; vehicle/date inclusion and exclusion; foreign-vehicle export contains header only |
| Alert rules | Invalid overspeed rejected; disabled template creation, cached read, foreign read/update/delete rejection, update, batch-enable, tenant-only enabled listing, delete and cache invalidation |

Trip fixtures are persisted explicitly because there is no trip-creation HTTP endpoint; HTTP still exercises all reads/exports. Telemetry pipeline testing is separately owned. IDs use the existing deterministic sequence; trip timestamps are fixed.

## Infrastructure diagnosis

The orchestrator's baseline `/tmp/opencode/tracker-postgres-baseline.log` failed before the application context loaded: Artemis timed out waiting for port 61616. Container output stopped during instance creation/journal auto-tuning (lines 89–108). This is not application RED evidence.

Inspected the pinned image's `/docker-run.sh` and ran its `artemis help create`: `EXTRA_ARGS` feeds broker creation, `--java-memory` defaults to **2G**, and `--no-autotune` skips the journal-buffer-timeout benchmark. The fixture now sets `--java-memory 256M --no-autotune` and a three-minute readiness timeout. Authentication remains required; persistence, fsync, listeners and health indicators are not disabled.

The first fleet invocation was terminated by the shell's 120-second execution timeout before completion. A fresh invocation uses a 600-second command timeout:

```sh
./mvnw -B -Dtest=PostgresDeploymentIT test > /tmp/opencode/tracker-fleet-http.log 2>&1
```

## Results

The second invocation completed with **BUILD FAILURE during testCompile; 0 tests executed**. `/tmp/opencode/tracker-fleet-http.log` lines 50–68 identify concurrently added telemetry tests referencing production APIs not yet present:

- `AlertEventHandlerTest:52`: organization-aware `VehicleUpdatedEvent` constructor unavailable.
- `VehicleEventHandlerTest:46,67,79`: `TrackerRepository.findForTelemetryByDeviceId` unavailable.
- `VehicleEventHandlerTest:57`: `VehicleUpdatedEvent.getOrganizationId` unavailable.

These files are owned by the telemetry agent and were not changed. This is an integration-time compilation blocker, not fleet regression RED evidence. A completed HTTP test run remains required after the concurrent edits are ready. No coverage percentage is claimed (the existing in-process Surefire setup does not establish JaCoCo instrumentation).
