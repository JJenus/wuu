# GEO-01 — contract implementation, acceptance pending

Owner: tracker-geo01-20261031. Integration coordinator: tracker-int00-20261031
(same owner session). Backend base64715fb; frontend base0452acc. Active source
revisions: backend02671c1/frontend8e182b7. Do not enable GEO-02 editors until the
combined final FULL_START acceptance is recorded.

## Contract

- Existing PUT remains partial-update behavior: omitted optional fields preserve
  values; explicit empty vehicle_ids unlinks all. Existing single-vehicle create
  request remains compatible. Updates retain descriptions and ordered polygon
  points rather than silently discarding them.
- Details and all collection response mappers include sorted complete vehicle_ids;
  singular vehicle_id remains a deterministic compatibility alias. Frontend reads
  prefer the complete array, including an explicit empty array, falling back to
  the legacy singular field only when the array is absent. No UI write or BFF
  geofence-mutation capability is enabled by this task.
- Circle coordinates must be finite/in geographic bounds and radius positive.
  Polygon rings require3..50 points, finite geographic coordinates, distinct
  vertices, nonzero area and noncrossing/nontouching nonadjacent edges. A repeated
  closing vertex is accepted/preserved; the bound includes it. Fifty follows the
  existing editor complexity bound and bounds pairwise validation work. This does
  not copy preview-specific radius/minimum-area business rules into the API.
- Validate merged geometry and explicit new vehicle references before managed
  entity mutation. Foreign and missing vehicle references return the same400;
  missing/foreign zone updates return404. Name-only updates do not erase geometry
  or assignments. Circle/polygon transitions clear irrelevant stored geometry.
- Geofence HTTP creation records the authenticated database user ID, not a
  submitted creator name. Creation timestamps are assigned server-side. This is
  persisted attribution, not an immutable audit ledger.
- Polygon Redis serialization uses managed/back references to preserve child
  back-links without recursive nesting. Transaction-aware invalidation remains
  after commit; no shared serializer policy or migrations changed.

## RED/GREEN evidence

Raw private logs retained under workspace `.coordination/evidence/GEO-01/`.

| Behavior | RED checkpoint / observed failure | GREEN evidence |
|---|---|---|
| Omitted/explicit association mapping |86bca30,2 controller failures |96e37a0,24 focused PASS |
| Polygon mapping/replacement, radius validation, complete response, update404 |62f62ae,5 expected failures |9fdccfc,29 focused PASS |
| Create validation, foreign references, null points |76ff28a, real HTTP201/200 and null crash masked401 |c9154c0,36 PASS including7 PostgreSQL cases |
| Recursive polygon serialization, absent Redis cache entry |bce6a9f, production serializer depth1001 + real Redis null |96b71cd,38 PASS |
| Spoofed creator/epoch timestamp, crossing/unbounded polygon |47cbb1d, real HTTP wrong actor/epoch/200 |02671c1,42 PASS including12 PostgreSQL cases |
| Frontend complete assignment array |d106018, drops second assigned ID |8e182b7,145 units/typecheck PASS |

Selected frontend pure policy/adapter coverage94.74%lines/86.08%branches/
95.15%functions; not whole-UI coverage. No backend coverage percentage claimed:
in-process Surefire does not attach a forked JaCoCo agent by default.

First full vendor gate ran749 tests, zero assertion failures, one legacy
RedisCacheTransactionIT context-wiring error from the new scoped vehicle lookup
dependency. Fixture now registers that dependency and uses valid circle geometry;
full vendor rerun pending. No production source correction was needed for that
fixture error. Device/build/browser/FULL_START gates remain pending.

## Independent review and remaining acceptance

Bounded verifier ses_e6c4ee39bffe2YXFOgsHJdKcS9 reviewed exact frozen backend
64715fb..02671c1/frontend0452acc..8e182b7 in separate review worktrees: approved,
no substantive medium/high findings. Source-only, no commands/builds/tests/edits.
Subsequent fixture-only adjustment is outside that review. Prior final reviewer
hit usage limit; that attempt is not approval. Earlier geometry reviewer ran an
unexpected timed-out build despite read-only scope; no PASS is claimed from it,
no process survived, and the owner reran affected focused tests serially.

Before acceptance: vendor backend/device gate; authenticated/preview browser
regressions and boundaries; actual-Java cold/restart acceptance on final clean
tuple; artifact hashes and receipt; delivery publication and board update.
No physical hardware, paid billing, production deployment or completed editor
claim follows. Rollback uses scoped reviewed revert/forward commits in isolation,
never resets dirty owner checkouts; no migration rollback applies.
