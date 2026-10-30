# Pinned delivery roadmap

Authority: workspace [AUTONOMOUS_DIRECTIVE.md](../../AUTONOMOUS_DIRECTIVE.md).
Workspace entry point: [DELIVERY_PLAN.md](../../DELIVERY_PLAN.md).

**Current detailed execution plan:** backend
[PRODUCTION_DELIVERY_PLAN.md](https://github.com/JJenus/wuu/blob/main/docs/autonomous/PRODUCTION_DELIVERY_PLAN.md).
It is versioned on backend main, with TASK_QUEUE/SESSION_STATE/FEATURE_MATRIX and
RELEASE_READINESS as the detailed queue, recovery, inventory and acceptance ledger.
This independent frontend keeps its own scoped progress and verification records.

Target production readiness, not just a usable read-only demonstration. Follow
the batches: full startup → alerts UI → vehicle/tracker setup → geofences/rules →
team → live tracking/device transport → subscriptions → notifications → production
operations/shared sessions → independent final acceptance. Detailed dependencies
permit at most two independent lanes. Only ride-hailing is deferred.

After **each feature addition/update**, require the plan's FULL_START gate:
database/cache/broker/backend/independent device/frontend cold startup, actual
browser workflow and persisted result, permission/tenant/error checks, changed
service restart and clean owned teardown. UI controls are not complete on mocked
tests. Existing Java harness does not by itself prove independent device startup.
If runtime is blocked, record it honestly and continue independent safe work.

Preserve pre-existing detail/list work. Do not rewrite working slices or silently
exclude unfinished required workflows. Production-only credentials, policy and
hardware approval remain explicit gates. No production deployment authorization.
