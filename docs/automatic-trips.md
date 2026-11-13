# Automatic recorded trips — 2026-11-13

Outcome: real telemetry ingestion creates and completes persisted trips, associates
ordered GPS points, and exposes them in live replay. Existing receipt deduplication
and the location/trip transaction encompass these changes; tenant-scoped vehicle
locking serializes multiple trackers. No payment or deployment work is included.

## Policy and contract

| Environment variable | Default | Recorded evidence |
| --- | --- | --- |
| `TRIP_START_SPEED_KMH` | 5 | Speed at or above this starts a trip |
| `TRIP_STATIONARY_SPEED_KMH` | 1 | Speed at or below this begins/continues a pending stop |
| `TRIP_STOP_SECONDS` | 300 | Stationary samples spanning this duration complete the trip |
| `TRIP_MAX_GAP_SECONDS` | 900 | A larger gap ends the old trip at its last observed fix |

A speed above the stationary threshold clears the pending stop. A subsequent
moving fix after a reporting gap starts a new trip without bridging distance.
Silence alone never completes a trip: another recorded fix is required. Start
reason is `AUTO_DETECTED`; confirmed-stop and gap completion use `TIMEOUT`.
Policy validation requires finite speeds, nonnegative stationary speed, start speed
above stationary speed, positive stop duration and gap duration at least stop duration.

The durable per-vehicle GPS-time cursor excludes late/equal-time/future fixes from
lifecycle changes and membership, without removing received raw history. Rollout
initializes the cursor lazily from an existing non-future current position; there is
no historical trip reconstruction or membership backfill. Pending-stop state and
cursor survive restart. Legacy active trips retain their time-window provenance.

GET `/api/v1/trips/{id}/history` preserves `trips.read`, tenant isolation and 404
for foreign/missing trips. New trips return `source: TRIP_ASSOCIATED`; legacy trips
return `VEHICLE_TIME_WINDOW`. Associated history never falls back to window records.
Replay labels the difference and uses actual samples, not interpolated routes.
Distance is approximate straight-line GPS-segment distance, not road/odometer
distance; average speed uses fractional elapsed hours. History is capped at 10,000
points with explicit truncation and no continuation interface yet.

## Rollout and recovery

V11 adds nullable cursor/stationary columns and a false-default provenance flag in
both Flyway histories. V12 adds vehicle-active, trip-point-order and trip-recorded
indexes. PostgreSQL V12 uses `CREATE INDEX CONCURRENTLY` and its `.sql.conf` sets
`executeInTransaction=false`. Keep
`spring.flyway.postgresql.transactional-lock: false` in the postgres profile:
session-level advisory migration locking avoids blocking concurrent index creation.
Do not switch histories or edit migrations already applied to deployed databases.
V11 was revised only during this unpublished task against disposable databases.

Before production rollout, back up and test against representative data, assess DDL
lock windows/disk headroom, and inspect inconsistent legacy active trips:

```sql
SELECT vehicle_id, count(*) FROM trips
WHERE is_active = true GROUP BY vehicle_id HAVING count(*) > 1;
```

Resolve such records through an operator-reviewed correction before ingestion;
there is no automatic deletion/merge or unique-active database constraint. Canonical
ingestion relies on vehicle locking. No production-scale migration certification
is claimed.

If concurrent indexing fails, stop migration retries and inspect Flyway history and
`pg_index.indisvalid`/`pg_index.indisready` for the three V12 indexes. A failed
nontransactional migration can leave valid or invalid indexes. An authorized
operator must reconcile those exact partial objects and the failed Flyway entry
before retrying V12; do not blindly repair history, skip the migration, change
checksums or drop unrelated indexes. Recovery has not been exercised in production.

## Verification and remaining limits

- Backend `./mvnw -B verify`: **732 tests passed**. After the PostgreSQL locking
  configuration fix, focused lifecycle/history/policy/API verification and packaging:
  **38 passed**. Coverage percentage is not claimed.
- Frontend: **232 scoped unit tests**, **94.86% line / 86.52% branch coverage**
  (domain/policy/adapters), types, boundaries and build passed. Existing live/legacy
  browser suite: **4 passed**; preview tracking: **3 passed**. The unrelated title
  edit still fails its branding assertion; only `tests/unit/branding.test.ts` was
  excluded from scoped units/coverage. The owner subsequently requested committing
  all current changes, including that pre-existing title edit in the frontend.
- `python3 tools/verify-telemetry.py --artemis-home <local-artemis-home> --verify-trips`
  passed real TCP/Artemis/PostgreSQL/Redis/browser creation, persisted pending stop
  across backend restart, completion, duplicates, subsequent trip, late/future
  exclusion, tenant denial and associated replay. It seeds no trip metadata and
  shortens stop evidence to two seconds only in the owned test fixture. The existing
  crash/outage/broker-journal recovery checks also passed.

Physical device retransmission/ACK behavior remains unknown and is not assumed.
No production database, broker, device or deployment was changed. This completes
the automatic-trip milestone, not the whole app or release gate.
