# FLEET-01 handoff — fleet create / tracker registration contract

State: READY_FOR_INTEGRATION
Owner: `ses_e6d4cee94ffe0soMAS0OPwIgxf-fleet01`
Base: backend `origin/main` `f82a5df`
Implementation commits: `6a184de1930db41c09ee7f5ab93471504ee48355` (contract),
`0a0776f` (advisory-lock race fix, update/trimming tests)
Branch: `agent/FLEET-01/ses_e6d4cee94ffe0soMAS0OPwIgxf-fleet01`
Frontend: not changed. No migration was written (resources: `fleet`, `build-slot-1`).

## Delivered contract

### Create vehicle — `POST /api/v1/vehicles` (`vehicles.write`)

| Field | Rule |
|---|---|
| `vehicleId` | required, non-blank, trimmed, ≤50; global primary key; duplicate (same or foreign tenant) → 409 and no overwrite |
| `deviceId` | optional; trimmed; blank becomes null; ≤50; when present it must not exist in either vehicle or tracker device namespace (global) → 409 |
| `model` | optional, ≤100 |
| `licensePlate` | optional, ≤20; not uniqueness-constrained |
| `vin` | optional, ≤17; not uniqueness-constrained |

Tenant is taken only from the authenticated `TenantContext`; the request cannot
choose an organization. `createdAt`/`updatedAt` are initialized on persist.

### Register tracker — `POST /api/v1/trackers` (`vehicles.assign_device`)

| Field | Rule |
|---|---|
| `trackerId` | required, non-blank, trimmed, ≤50; global primary key; duplicate → 409 |
| `deviceId` | required, non-blank, trimmed, ≤50; global across vehicles and trackers; collision → 409 |
| `vehicleId` | required, non-blank, trimmed, ≤50; must belong to caller tenant (foreign/same-tenant missing → 400, no tracker created) |
| `model` | optional, ≤100 |
| `protocol` | optional, ≤20; no invented protocol enum |
| `firmwareVersion` | optional, ≤50 |
| `simNumber` | optional, ≤20 |

Registration always assigns the tracker to the supplied tenant-owned vehicle.
Tenant scope is derived through `trackers.vehicle_id → vehicles.organization_id`;
there is no tracker organization column.

### Update (supporting validation)

`PUT /api/v1/vehicles/{id}` and `PUT /api/v1/trackers/{id}` now reject oversized
`model`, `licensePlate`, `vin`, `protocol`, `firmwareVersion` and `simNumber`
values with 400 before persistence. Tenant ownership lookup is unchanged.

### Device namespace and remaining database gap

`vehicles.device_id` and `trackers.device_id` share one physical namespace because
telemetry lookup is global by device id. The API rejects cross-table collisions
before insert (`409`) and, on PostgreSQL, both create paths serialize through a
transaction-scoped advisory lock (`DeviceIdentityGuard`) keyed by device id.
Tracker creation validates vehicle ownership before acquiring the lock, then
re-checks both tables while holding it. The real-PostgreSQL race test proves one
`201` and one `409` with exactly one persisted owner across both tables.

**Remaining caveat:** the lock is a PostgreSQL-only stopgap. H2/dev deployments
skip it, and a writer that bypasses these services is still unconstrained. The
durable cross-database guarantee remains a coordinator-owned migration or device
registry; do not claim database-wide enforcement beyond the service layer.

## RED / GREEN evidence

Commands run in the isolated worktree with `TMPDIR=/tmp/opencode`.

- RED: `PostgresDeploymentIT#fleet_deviceIdentity_isUniqueAcrossVehiclesAndTrackersPerTenantAndGlobally+fleet_createValidation_rejectsMissingAndOversizedContractFields+fleet_createdTimestamps_areCurrentInsteadOfEpoch`
  → **3 failures**: cross-table device create returned 201, missing tracker
  device returned 201, timestamps persisted as `1970-01-01`. Log:
  `/tmp/opencode/fleet01-red.log`.
- GREEN focused fleet, final revision: 9 fleet methods → **9/9 tests, 0 failures**,
  89.2 s. Log: `/tmp/opencode/fleet01-green2.log`.
- Full real-PostgreSQL deployment IT, final revision:
  `./mvnw -B -Dtest=PostgresDeploymentIT test` → **43/43 tests, 0 failures**,
  93.2 s. This also covers the two fleet CRUD methods omitted from the focused
  run. Log: `/tmp/opencode/fleet01-full-it.log`.
- Fleet unit slice, prior revision: `VehicleControllerTest, TrackerControllerTest,
  VehicleCommandServiceTest, VehicleQueryServiceTest, VehicleEventHandlerTest,
  VehicleTest` → **53/53**, 0 failures. Log: `/tmp/opencode/fleet01-unit.log`.
- Full backend unit suite, final revision: `./mvnw -B test` → **645 tests,
  0 failures/errors/skips**. Log: `/tmp/opencode/fleet01-root-test2.log`.
- Baseline before edits: focused fleet IT **7/7** pass
  (`/tmp/opencode/fleet01-baseline.log`).

Added coverage on the final revision: oversized `PUT` fields for vehicle and
tracker (400 with unchanged response), negative-create non-persistence for
cross-table device collisions, padded identifier/device trimming, and concurrent
vehicle-vs-tracker creation for the same device (exactly one owner). The final
full-IT run includes all of them.

Review findings addressed: cross-table race (advisory lock plus real concurrency
test), update-validation coverage, full `PostgresDeploymentIT` execution, and
negative-create persistence assertions. The IT file remains large by design; the
full class is the acceptance run, so no partial-slice claim is made.

Artifact/JAR and FULL_START were **not** run by this worker: the integration
coordinator owns `integration` and must run the final FULL_START against the
accepted revision tuple, including the changed fleet browser workflow.

## Named decisions / FLEET-02 and FLEET-03 boundaries

1. **Cardinality is unchanged:** the schema and `Vehicle.trackers` permit more
   than one tracker per vehicle; this task does not add a uniqueness rule or
   reinterpret existing data. FLEET-02 must not invent a "one tracker per
   vehicle" UI. A policy decision is required before enforcing one.
2. **Unassigned tracker creation is unsupported:** POST requires `vehicleId`.
   Detached inventory/claiming needs a separate approved contract.
3. **Unassign/reassign/history remains blocked:** `tracker_assignments` exists but
   is not wired. FLEET-03 owns the ownership/history contract.
4. **`vehicle.deviceId` is a legacy optional field:** accepted only on create,
   immutable through the current update DTO. FLEET-02 should prefer registering
   the device on the tracker; whether to retire the vehicle field needs an owner
   decision. Until then both directions are collision-checked.
5. **No credential scheme invented:** `protocol` is free text ≤20. No Autoseeker
   secret/provisioning flow is implied.
6. **No telemetry/command claim:** this is HTTP fleet setup only; device
   transport, ACK, and physical hardware acceptance remain separate gates.

## FLEET-02 UI acceptance suggestion

Sign in as a writer; create a vehicle without `deviceId`; create a second vehicle
with an explicit device; register a tracker with a distinct device and protocol
`GT06`; reload detail/list and confirm persisted model/plate/VIN/protocol.
Attempt a duplicate vehicle/tracker id, a device already registered on a tracker,
a tracker with missing device, and a foreign-tenant vehicle id; expect actionable
400/409 errors with no list/detail state change. Confirm the UI does not offer
unassign/reassign or claim hardware is online.

## Rollback

Revert `0a0776f` then `6a184de` on the task branch. No schema change, no data
rewrite, no shared file edit. The advisory lock is code-only and disappears with
`0a0776f`; existing epoch timestamps are not backfilled by this change, which is
a separate data-repair decision.
