# Alert transition gateway boundary

STATUS=IN_PROGRESS — gateway contract verified; UI remains disabled.

## Current continuation — 2026-10-30

Docker was restored. Actual-Java acceptance exposed the production Jackson
SNAKE_CASE contract: the BFF must translate public `resolutionNotes` to upstream
`resolution_notes`; raw alert responses use `alert_id`, `acknowledged_by` and
`resolved_by`. RED `2ff125e`, correction `cbdd54e`. Legacy live adapter mapping
is still a B1 integration task; UI stays disabled.

Corrected actual-Java browser acceptance **1/1 PASS**, including real tenant alert
creation, acknowledge/resolve, persisted authenticated actor/notes and cross-tenant/
Origin/forged-actor denial. Receipt `/tmp/opencode/plan-b0-java2.log`, private
browser `/tmp/opencode/wuu-java-AORcij/browser.log`; backend artifact SHA256
`e207fcc526d7585bf3985729f70cb591d87bff9ebf6c2b1416e7d89bc180d9af`.
Production frontend build and Java/PostgreSQL/Redis/Artemis health passed; owned
cleanup completed. Follow-up **136 units /33 auth browser tests PASS**, selected
coverage94.62%line/85.71%branch/95.15%function, typecheck/boundaries PASS. Scoped
independent review approved the naming correction; it did not run the gates.

The earlier Docker-blocked attempt below is historical. This is not yet the
new full-stack FULL_START acceptance: independent device-process startup and
restart checks must join the unified B0 harness. Follow `delivery-plan.md`.

## Verified contracts

Backend `77518dd` derives single/bulk transition actors from the authenticated
database user ID. Client actor fields cannot select audit identity; current
database `alerts.write` and tenant-scoped reads remain authoritative.

This frontend slice exposes only `POST /api/_wuu/alerts/{id}/acknowledge` with `{}`
and `POST /api/_wuu/alerts/{id}/resolve` with `{resolutionNotes}`. IDs are positive
canonical decimal Java Long values. Notes are trimmed and bounded to10–1000
characters. Exact-origin validation, sealed-session authentication, a4096-byte
body limit and the existing upstream timeout apply. All query parameters, actor
fields, tenant fields and unknown body fields are rejected. Bulk actions,
reopening, rules, geofences and other resource writes remain unavailable.

The BFF does not infer permissions from role names: Java authorizes every
transition. Upstream errors stay errors; no preview fallback or automatic POST
retry is added. The existing legacy live adapter and disabled UI are unchanged
because their workflow/confirmation/notes integration is a separate next slice.

## TDD evidence — 2026-10-30

Journey: an authenticated operator can submit a narrowly validated transition,
without selecting an audit actor or another tenant. Invalid requests must stop
at the BFF and Java must still deny unauthorized access.

- RED `1e7227f`: policy tests reference the missing boundary implementation;
  `/tmp/opencode/alert-bff-red.log` records intended missing-module failure.
- GREEN `ebe1e80`: policy **3/3 PASS**, selected-source full gate **136 PASS**,
  coverage **94.61% lines / 85.71% branches / 95.15% functions**; not whole-UI
  coverage (`alert-bff-green.log`, `alert-bff-coverage.log`).
- Full contract-auth browser suite **33 PASS** (`alert-bff-auth-full.log`).
  `tests/auth/alert-transitions.spec.ts` verifies successful response mapping,
  anonymous401, upstream403/404, cross-origin403, strict fields/query400,
  content-type415, size413, and unsupported-path405. This is fixture evidence,
  not real backend authorization proof.
- Typecheck, production build, boundaries and scoped formatting PASS
  (`alert-bff-types.log`, `alert-bff-build.log`, `alert-bff-boundaries.log`,
  `alert-bff-format-check.log`). Pre-existing unrelated edits remain untouched.
- Bounded read-only security review found no concrete introduced medium/high
  defect; it did not run tests or independently verify backend code.

## Actual-Java blocker

Historical attempt; superseded by the successful continuation above.

`tests/java/deployed-backend.spec.ts` now provisions owned alerts and asserts
cross-tenant denial, forged actor rejection, Origin denial, authenticated actor
attribution and persisted acknowledge/resolve flags through the production BFF.
The runner attempt failed **before service startup**: Docker's socket
`/var/run/docker.sock` is absent following environment restart. Receipt
`/tmp/opencode/alert-bff-java.log`; owned cleanup completed. Backend package
SHA256 `e207fcc526d7585bf3985729f70cb591d87bff9ebf6c2b1416e7d89bc180d9af` was
built for this attempt. No actual-Java alert workflow PASS is claimed.

Resume once Docker is available:
`WUU_JAVA_JAR=/tmp/opencode/tracker-alert-actor/target/tracking-engine-1.0.0-SNAPSHOT.jar TMPDIR=/tmp/opencode node tools/verify-java-integration.mjs`.
Then integrate the permission-aware UI, notes confirmation and cache refresh
without overwriting the pre-existing detail/list work. No production deployment
or payment/provider/hardware acceptance follows from these local gates.

Self-evaluation (not independent acceptance): accuracy4/5 (exact gates and
blocker recorded), completeness3/5 (UI/Java acceptance pending), clarity4/5
(boundary explicit), actionability4/5 (resume command), conciseness4/5 (existing
gateway reused). Highest-impact next step is restoring disposable infrastructure
and running actual-Java acceptance before enabling UI actions.
