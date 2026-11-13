# Fleet setup and management — updated 2026-11-13

## Editing and unused assignment correction

Live vehicle details now allow editing model, registration and VIN with current
`vehicles.write`. Assigned tracker cards allow editing model, protocol, firmware
and SIM metadata with `vehicles.assign_device` (tracker reads need `devices.read`).
The editors retain raw nullable values rather than saving display placeholders;
blank inputs explicitly clear metadata. Vehicle/tracker/device identities and
measured telemetry are not editable. Protocol metadata does not configure hardware.

Confirmed reassignment requires an explicit expected source and destination, both
in the current tenant. Only unused trackers can move: stored fixes, raw data,
last-seen/online activity, commands or assignment-history records block correction.
An active trip on either vehicle also blocks it. The server returns 409 for these
conflicts or stale source assignments, and indistinguishable 404s for missing/foreign
trackers or destinations. No unassignment, deletion or cross-tenant transfer is exposed.

Correct installation mistakes **before the device starts transmitting**. The guard
checks committed backend records under the ingestion tracker lock; it cannot inspect
undelivered gateway/broker packets or attest that hardware has never transmitted.
The first ingested fix uses the corrected assignment. Moving used devices with
historical ownership preserved remains a separate capability, not a hidden fallback.

| Browser mutation | Wire body | Upstream behavior |
| --- | --- | --- |
| PUT `vehicles/{id}` | `model`, `license_plate`, `vin` (all explicit, nullable) | Existing Java vehicle profile update, now locked and transactional |
| PATCH `trackers/{id}/profile` | `model`, `protocol`, `firmware_version`, `sim_number` (all explicit, nullable) | New profile-only update, preserves measured state |
| POST `trackers/{id}/reassign` | `source_vehicle_id`, `target_vehicle_id` | New guarded assignment correction |

Browser paths are beneath `/api/_wuu/`; Java paths are beneath `/api/v1/`.
The gateway admits only these exact shapes/methods, rejects unknown fields and query
overrides, and retains sealed-session, same-origin and 4 KiB body limits. Every
submission refreshes current permissions; Java authorization remains authoritative.
Vehicle/profile changes avoid detached graph merges; dynamic field updates preserve
concurrent telemetry. Reassignment locks tracker first, then vehicles in stable order,
matching canonical ingestion. No data migration or historical rewrite is needed.

Forms freeze during writes, retain drafts on errors/revocation and do not automatically
retry ambiguous results. Reassignment requires choosing a currently loaded destination
and checking confirmation. Failed destination reads block submission. Successful
writes invalidate fleet details, lists and assigned-tracker queries for both vehicles.
Preview behavior is unchanged; these management writes are not simulated.

### Task checks — 2026-11-13

Journeys were derived from the owner's approved conservative scope. Backend RED:
new management tests executed and failed on missing routes; frontend units failed
on the missing boundary, and browser RED waited for the absent Edit vehicle button.
Checkpoint commits preserve RED/GREEN evidence; no tests were merely written and
claimed as executed.

- `./mvnw -B verify` in `sidie/`: **749 tests passed**, including management,
  ingestion, API inventory and real-login denial tests. A stale controller mock and
  operation-count assertion were updated to the new contract. Java coverage is not claimed.
- `pnpm exec vitest run --coverage --exclude tests/unit/branding.test.ts`:
  **238 passed**, **94.64% lines / 86.41% branches** (domain/policy/adapters, not full UI).
  The existing branding assertion still disagrees with the previously committed
  title edit; it was explicitly excluded, not reported as passing.
- Types, build, boundaries and changed-file formatting passed. Fleet browser suite:
  **7 passed**, one native-only tenant test skipped in fixture mode and then passed
  with Java. Includes edits, reload, corrected assignment, mobile draft retention,
  revocation, strict gateway/origin/query checks and existing setup regressions.
- Auth browser regressions: **42 passed**. The old assertion that vehicle PUT always
  returns 405 now expects 400 for an incomplete profile; other methods remain denied.
- Preview workspace/detail regressions: **15 passed**, including responsive and
  automated accessibility checks. After the final pending-packet warning, the build
  and **3 management browser tests** passed again (the native-only case stays skipped
  in fixture mode).
- Native command in `sidie/`:
  `python3 tools/verify-telemetry.py --artemis-home <local-home> --verify-fleet --verify-api-v1`
  passed **5 Java/browser checks**, real TCP attribution after correction,
  recorded-history move denial, cross-tenant 404s, restart/outage/deduplication
  regressions and authenticated OpenAPI's **106 operations**. Owned disposable
  PostgreSQL/Redis/Artemis and Java processes were stopped afterwards.

Evidence: `/tmp/opencode/fleet-management-{backend-final,coverage,types,build,browser-final,auth-final,native-final}.log`;
native fixture `/tmp/opencode/telemetry-native-flk7judt/`. No deployment or physical
device/production-readiness certification is claimed.

## Original registration contract — 2026-11-10

Live users with current permission can create a vehicle from Vehicles/Overview,
then register a tracker from its details. Vehicle details display persisted VIN;
assigned tracker records display device/model/protocol/firmware/SIM fields.

| Request                   | Browser fields                                                                                                   | Permission               |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------ |
| POST `/api/_wuu/vehicles` | `vehicle_id` required; `model`, `license_plate`, `vin` optional                                                  | `vehicles.write`         |
| POST `/api/_wuu/trackers` | `tracker_id`, `device_id`, `vehicle_id` required; `model`, `protocol`, `firmware_version`, `sim_number` optional | `vehicles.assign_device` |
| GET `/api/_wuu/trackers`  | Bounded paginated tenant collection, filtered by vehicle in the adapter                                          | `devices.read`           |

The gateway preserves existing session/origin/body-size protections, rejects
unknown fields and query overrides. The management writes above supersede the
original create-only restriction. Forms
recheck current effective permissions before submitting. Java chooses the tenant
and enforces duplicate-device protection across both source tables. Registration
supports multiple trackers; guarded unused correction is now supported above, but
no unassignment or hardware-online guarantee is invented. Model/plate/VIN are optional, and plates are not required
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

The original next-step note is superseded by workspace `DELIVERY_PLAN.md`; stop
after the requested fleet-management outcome.
