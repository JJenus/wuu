# Geofence editors — 2026-11-10

Recovered the existing GEO-02 branches without modifying their worktrees.
Authorized users can create circles/polygons and edit description, active state,
geometry and complete vehicle links, including unlink-all. Drafts survive failed
permission checks/saves; large or out-of-map geometry remains numerically editable.
Saving a boundary does not automatically configure alert rules or delivery.

Closed recovery gaps: live new/detail pages were still disabled, the gateway needed
to preserve the newly shipped fleet routes, and clearing numeric coordinates
silently substituted zero. Runtime RED tests reproduced the disabled form and
empty→zero conversion; final browser tests verify both corrections. Retained the
backend's atomic complete-create compatibility, parameter-mapper isolation and
authenticated rule-creator/current-time fixes. Updated the obsolete unit assertion
that expected an Epoch creation timestamp to use the injected clock.

## Verification

- Backend `./mvnw -B verify`: **672 tests passed**, executable JAR packaged.
- Frontend `pnpm test:coverage`: **194 tests passed**, selected coverage **94.95%
  lines / 86.84% branches**. Most UI/composables are outside that coverage scope.
- Typecheck, production build, boundary checks, scoped Prettier and diff checks passed.
- Geofence contract/browser suite: **11 passed**. Existing preview editor/detail
  checks: **10 passed**; fleet checks: **4 passed**; alert-transition checks: **5 passed**.
- Real Java/PostgreSQL browser workflow: **1 passed**. Created a circle with two
  links, converted it to a polygon, cleared description/all links, disabled it,
  and reloaded every geometry value. Invalid geometry/foreign vehicle edits rolled
  back; another tenant's reads/writes were denied.
- Restarted the owned PostgreSQL and Java processes with fresh Redis: polygon,
  empty description, disabled state and empty assignments persisted. Additional
  native HTTP checks exercised custom, batch, overspeed, idle and geofence rule
  creation; all five committed PostgreSQL rows used the authenticated actor/current
  time despite spoofed input attribution.

Logs: `/tmp/opencode/geofence-{backend-verify,coverage,typecheck,build,browser,java-browser,preview-browser,fleet-regression,alert-regression}.log`.
Native runtime/restart evidence: `/tmp/opencode/geofence-java-t4e6zb2f/`.
All owned Java/Redis/PostgreSQL processes were stopped; no application DB was used.

## Limits and next task

Native integration used PostgreSQL 18 with migrated production-profile schema,
private Redis and disabled JMS listeners (no broker). Docker/Testcontainers
lifecycle suites were not run. This is not device-ingestion, hardware, notification
delivery, or whole-stack certification. Rule creation/attribution checks do not
prove telemetry-triggered evaluation. Next: finish supported geofence-rule setup
and verify its evaluation path; preserve the working editor rather than rebuilding.

Run the fixture suite with `pnpm exec playwright test --config playwright.geofence.config.ts`.
For an owned Java test instance, set `GEOFENCE_TEST_JAVA_ORIGIN`, alpha/beta emails
and `GEOFENCE_TEST_PASSWORD`, seed alpha vehicles `VH-1/VH-2` with plates
`LONDON-1/LONDON-2` plus beta vehicle `FOREIGN-VH`, then select `--grep @java`.
The geofence fixture is separate; unrelated local auth-fixture deletion is preserved.
