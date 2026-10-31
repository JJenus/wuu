# FLEET-01 handoff — fleet create / tracker registration contract

State: READY_FOR_REVIEW
Owner: `ses_e6d4cee94ffe0soMAS0OPwIgxf-fleet01`
Base: backend `origin/main` `f82a5df`
Implementation commit: `6a184de1930db41c09ee7f5ab93471504ee48355`
Branch: `agent/FLEET-01/ses_e6d4cee94ffe0soMAS0OPwIgxf-fleet01`
Frontend: not changed. No migration was written (resources: `fleet`, `build-slot-1`).

## Delivered contract

### Create vehicle — `POST /api/v1/vehicles` (`vehicles.write`)

| Field | Rule |
|---|---|
| `vehicleId` | required, non-blank, ≤50; global primary key; duplicate (same or foreign tenant) → 409 and no overwrite |
| `deviceId` | optional; trimmed; blank becomes null; ≤50; when present it must not exist in either vehicle or tracker device namespace (global) → 409 |
| `model` | optional, ≤100 |
| `licensePlate` | optional, ≤20; not uniqueness-constrained |
| `vin` | optional, ≤17; not uniqueness-constrained |

Tenant is taken only from the authenticated `TenantContext`; the request cannot
choose an organization. `createdAt`/`updatedAt` are initialized on persist.

### Register tracker — `POST /api/v1/trackers` (`vehicles.assign_device`)

| Field | Rule |
|---|---|
| `trackerId` | required, non-blank, ≤50; global primary key; duplicate → 409 |
| `deviceId` | required, non-blank, ≤50; global across vehicles and trackers; collision → 409 |
| `vehicleId` | required, non-blank, ≤50; must belong to caller tenant (foreign/same-tenant missing → 400, no tracker created) |
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
telemetry lookup is global by device id. The API now rejects cross-table
collisions before insert (`409`). **Race caveat:** without a cross-table database
constraint, two concurrent inserts (one vehicle, one tracker) can still both pass
the pre-check. A migration/registry constraint is required for a complete
guarantee; migrations are coordinator-allocated, so none is included here. Do not
claim race-safe cross-table device uniqueness until that migration is accepted.

## RED / GREEN evidence

Commands run in the isolated worktree with `TMPDIR=/tmp/opencode`.

- RED: `PostgresDeploymentIT#fleet_deviceIdentity_isUniqueAcrossVehiclesAndTrackersPerTenantAndGlobally+fleet_createValidation_rejectsMissingAndOversizedContractFields+fleet_createdTimestamps_areCurrentInsteadOfEpoch`
  → **3 failures**: cross-table device create returned 201, missing tracker
  device returned 201, timestamps persisted as `1970-01-01`. Log:
  `/tmp/opencode/fleet01-red.log`.
- GREEN focused fleet: the same three plus five existing fleet methods →
  **10/10 tests, 0 failures**, 119.8 s. Log: `/tmp/opencode/fleet01-green1.log`.
- Fleet unit slice: `VehicleControllerTest, TrackerControllerTest,
  VehicleCommandServiceTest, VehicleQueryServiceTest, VehicleEventHandlerTest,
  VehicleTest` → **53/53**, 0 failures. Log: `/tmp/opencode/fleet01-unit.log`.
- Full backend unit suite: `./mvnw -B test` → **645 tests, 0 failures/errors/skips**.
  Log: `/tmp/opencode/fleet01-root-test.log`.
- Baseline before edits: focused fleet IT **7/7** pass
  (`/tmp/opencode/fleet01-baseline.log`).

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

Revert `6a184de` on the task branch. No schema change, no data rewrite, no shared
file edit. Existing vehicles/trackers with epoch timestamps are not backfilled by
this change; that is a separate data-repair decision.
