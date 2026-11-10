# Fleet setup — 2026-11-10

Live users with current permission can create a vehicle from Vehicles/Overview,
then register a tracker from its details. Vehicle details display persisted VIN;
assigned tracker records display device/model/protocol/firmware/SIM fields.

| Request                   | Browser fields                                                                                                   | Permission               |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------ |
| POST `/api/_wuu/vehicles` | `vehicle_id` required; `model`, `license_plate`, `vin` optional                                                  | `vehicles.write`         |
| POST `/api/_wuu/trackers` | `tracker_id`, `device_id`, `vehicle_id` required; `model`, `protocol`, `firmware_version`, `sim_number` optional | `vehicles.assign_device` |
| GET `/api/_wuu/trackers`  | Bounded paginated tenant collection, filtered by vehicle in the adapter                                          | `devices.read`           |

The gateway preserves existing session/origin/body-size protections, rejects
unknown fields and query overrides, and permits no other fleet writes. Forms
recheck current effective permissions before submitting. Java chooses the tenant
and enforces duplicate-device protection across both source tables. Registration
supports multiple trackers; no reassignment, unassignment or hardware-online
guarantee is invented. Model/plate/VIN are optional, and plates are not required
to be unique. Preview creation stays session-only.

## Checks performed

- New adapter/gateway tests were added before implementation (initial RED:
  missing registration boundary). Final `pnpm test:coverage`: 166 tests passed;
  selected coverage 94.57% lines / 86.07% branches. This excludes most UI/composables.
- `pnpm typecheck`, `pnpm check:boundaries`, production `pnpm build` and scoped
  Prettier/diff checks passed.
- Fleet browser suite: 4 passed, covering saved/reloaded fields, denied writes,
  forged fields/query/origin rejection, mobile draft retention and revocation.
- Existing authenticated browser suite: 42 passed; preview workspace suite: 9 passed.
- Actual Java/H2/private-Redis browser create → register → reload → fleet-list
  workflow passed, including VIN and tracker field readback. A second Java launch
  confirmed the earlier persisted vehicle/VIN survived restart before rerunning
  the workflow. Owned Java/Redis processes were stopped afterwards.

Logs: `/tmp/opencode/fleet-ui-{coverage,types,build,browser,auth-regressions,preview-regressions,java-browser}.log`.
Private Java/H2 evidence: `/tmp/opencode/fleet-ui-java-qenq81_4/`.

The Java check used the verified backend JAR from the preceding fleet task,
private file-backed H2 and Redis, and disabled JMS listeners because no broker
was supplied. It proves fleet setup, not telemetry, the device gateway, production
PostgreSQL UI integration or whole-stack readiness. Backend PostgreSQL uniqueness
was checked separately in the preceding task. No deployment was performed.

Next: recover the existing GEO-02 geofence editor work rather than rebuilding it.
