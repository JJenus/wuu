# Recorded GPS trip-window replay — 2026-11-12

Outcome: live trip details replay saved GPS coordinates, speed and source timestamps,
not preview paths. Preview remains explicitly illustrative and unchanged.

## Contract and limits

GET `/api/v1/trips/{tripId}/history?limit=10000` requires current `trips.read`.
Both the trip lookup and location query enforce the current organization. Missing
and foreign trips return the same 404; no tenant context is forbidden. `limit`
must be 1–10,000 (400 otherwise). Rows are ordered by `recorded_at, location_id`,
with valid coordinates and validity `A`. No sort, tenant or vehicle override exists.

The concrete response contains `trip_id`, `vehicle_id`, `source`, `window_start`,
`window_end`, `truncated`, and `points` (location ID, recorded time, latitude,
longitude, nullable speed in km/h). The browser normalizes snake_case and validates
the complete shape, coordinate bounds, ordering and window membership.

Source is **VEHICLE_TIME_WINDOW**: ingestion currently saves locations without trip
associations. These are the vehicle's recorded fixes between trip start and end,
inclusive, not proof of trip membership. Active/future-ending windows stop at the
server's current time. Tracker reassignment remains unsupported; current tracker
ownership is used, so old work/history must be resolved before ownership changes.
Automatic trip creation/association is not implemented by this increment.

The response fetches at most `limit + 1` records and exposes truncation; it never
pretends a capped result is complete. There is no continuation/page interface yet:
trips above 10,000 fixes show explicitly partial history. Manual refresh includes
later arrivals; history is a read snapshot, not a live subscription.

Replay selects the last actual fix at or before the playback time. It holds that
sample for at most 120 seconds, then hides the marker and reports a recording gap.
The threshold is a display policy, not a promise about hardware reporting intervals.
Recorded timestamps remain visible; coordinates and speed are never interpolated.
No lines bridge fixes or gaps, no road route is inferred, and missing speeds remain
unknown. Leading/trailing absence, empty history, loading, truncation and failures
are explicit. Cached positions disappear on refresh/error. Playback starts paused,
supports seek/rate/restart, pauses in hidden tabs and cleans up on navigation.

V10 adds location `(tracker_id, recorded_at, location_id)` and tracker `(vehicle_id)`
indexes in both Flyway histories. It does not rewrite migrations or data. Normal
index creation can lock large tables; schedule/assess production rollout separately.
No production database, broker or device was changed.

## Checks

- Backend RED: history endpoint returned 404 instead of data/403. GREEN:
  `./mvnw -B -Dtest=TripHistoryIntegrationTest,TripControllerTest,ApplicationSecurityIntegrationTest verify`
  passed 11 tests; full `./mvnw -B verify` passed **699** tests. Tests cover real
  persistence/clear/reload, ordered boundaries, foreign locations/trips, empty data,
  bounds/truncation, missing context, anonymous access and denied permissions.
- Frontend RED: missing history module/service, then missing recorded-replay region.
  GREEN: 7 new history units; `pnpm exec playwright test --config playwright.tracking.config.ts`
  passed **4** browser tests, including gaps, unknown speed, partial data, hidden-tab
  pause, error hiding and retry. `NUXT_PUBLIC_DATA_MODE=preview pnpm exec playwright test tests/e2e/tracking.spec.ts`
  passed **3** preview tracking browser regressions (the first attempt inherited live
  mode and reached sign-in instead of the preview workspace).
  `pnpm typecheck`, `pnpm check:boundaries` and `pnpm build` passed.
- Full frontend coverage attempt: **214 passed, 1 failed** (215 total). The failure
  is the existing branding assertion against an unrelated local `nuxt.config.ts`
  title edit; that edit was preserved and excluded from task commits. Explicitly
  excluding only `tests/unit/branding.test.ts`, 213 tests passed with coverage
  **94.75% lines / 86.23% branches**, above the configured 80% thresholds. This is
  domain/adapter/policy coverage, not full UI coverage. Java coverage is not claimed.
- `python3 tools/verify-telemetry.py --artemis-home /home/jjenus/.cache/tracker-tests/apache-artemis-2.40.0`
  passed real TCP/Artemis/PostgreSQL/Redis/browser acceptance. Trip metadata alone
  is provisioned as a fixture; all GPS fixes come through the actual device pipeline.
  Backend restart precedes browser history reads. The browser verifies persisted
  recorded fixes, gaps, end sample, reload, empty history, foreign-trip 404,
  invalid-limit 400 and truncated reads. Existing outage recovery still passes.

Remaining limits: capped histories need a future continuation/filter workflow;
automatic trip lifecycle/association and physical tracker retransmission are still
unverified/unimplemented. This is not production-readiness certification.
