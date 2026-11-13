# Reporting corrections — 2026-11-14

Outcome: odometer snapshots no longer masquerade as daily travel; unknown fleet
and trip distances remain unknown, measured zero remains zero, live cards omit
invented trends, preview charts disclose their illustrative nature, and live trip
copy acknowledges recorded replay. Server CSV filters now combine tenant, vehicle
and inclusive trip-start bounds before pagination, with deterministic ordering.
No driver management, historical analytics, payment or deployment work is included.

## Verification

Journeys were derived from the reporting analysis, not a separate plan file.

- RED: `pnpm test tests/unit/workspace.test.ts` reproduced unknown distance becoming
  zero (1 failure/12 tests). `pnpm test tests/unit/live-services.test.ts` reproduced
  missing trip distance becoming zero (1 failure/23 tests). The initial live browser
  run also failed on the old odometer label and stale replay description.
- RED: backend `./mvnw -B -Dtest=TripExportIntegrationTest test` executed 14 cases
  against migrated H2; seven failed on ignored bounds, reversed ranges and absent
  tenant context. Test checkpoints preserve this evidence in both Git histories.
- GREEN: `pnpm test:coverage --exclude tests/unit/branding.test.ts`: **264 passed**;
  scoped domain/adapter/policy coverage **94.66% lines / 87.22% branches**. The
  unexcluded run reproduced the existing branding assertion (hyphen versus em dash
  in `nuxt.config.ts`); neither that file nor the assertion was changed.
- `pnpm typecheck`, `pnpm check:boundaries`, `pnpm build` and changed-file Prettier
  checks passed.
- `pnpm exec playwright test --config playwright.auth.config.ts
tests/auth/reporting.spec.ts tests/auth/detail-reads.spec.ts`: **10 passed** in
  the final serialized run. Covers known/zero/missing/partial/empty odometers,
  live chart omission, vehicle labels, trip missing-distance/replay copy, and
  direct detail reads when collection reads fail. Uses contract fixtures, not Java.
- `NUXT_PUBLIC_DATA_MODE=preview pnpm exec playwright test
tests/e2e/workspace.spec.ts --grep 'overview renders|trip export'`: **2 passed**,
  preserving preview charts/disclosure, fleet interaction and filtered CSV download.
- Backend `REPORTING_JDBC_URL=<disposable PostgreSQL URL> ./mvnw -B
-Dtest=TripExportIntegrationTest,TripExportServiceTest,TripControllerTest,TripHistoryIntegrationTest,ApiContractIntegrationTest
verify`: **42 passed**, executable packaging passed. Export tests use the real
  migrated PostgreSQL repository/service/controller through MockMvc: ten filter
  combinations, tenant denial/isolation, reversed bounds, and 502 matching records
  across the 500-row page boundary. Remaining selected suites use their own H2
  fixtures; contract checks retain anonymous-route denial and generated API docs.
- With JaCoCo 0.8.14 attached to the Maven JVM (Surefire is in-process), export
  service coverage is **100% lines / 81.03% branches**; export controller line
  coverage is **100%**. These are focused class measurements, not backend-wide.

## Limits

Docker was unavailable. PostgreSQL verification used an owned local instance;
the first PostgreSQL attempt exposed a test-only H2 dialect default, corrected in
the new fixture without changing application configuration or migrations.
Concurrent browser/build runs interfered with assets/artifacts; final browser
verification is serialized. No actual-Java authenticated browser journey, hardware
or production rollout is claimed. CSV still buffers all matching rows and uses
start-time inclusion rather than clipping trips to the selected interval.
