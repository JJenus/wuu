# Independent-agent execution contract

Updated: 2026-10-31. Owner requested separate, manually launched task-owning agents,
with at most two bounded subagents per task owner. This supersedes sequential-only scheduling and the old
two-lane limit. Production requirements and FULL_START acceptance are unchanged.

## Start here

1. Read workspace AGENTS.md, AUTONOMOUS_DIRECTIVE.md, DELIVERY_PLAN.md and the
   target repository's AGENTS.md. Read the production plan and the task below.
2. Inspect the **shared live claims** before choosing work. Select a READY task
   whose dependencies have been integrated, not merely implemented on a branch.
3. Claim the task and its write resources atomically as described below. Only
   then create isolated worktrees and change application files.
4. Keep the claim current; deliver commits, tests and a handoff for another
   independent agent to review. READY_FOR_REVIEW is not DONE.

Top-level task agents are independent sessions started by the owner. A task owner
may use **at most two active subagents**, including background agents, for bounded
subtasks or independent verification **inside its currently claimed task**. A
subagent never claims or implements an entire different top-level task, spawns
more agents, changes the task's scope, or pushes delivery branches. The task owner
records delegated scope/results in its live claim and remains responsible for
integration and acceptance; a failed verifier is not approval.

One independent session may claim integration/review instead of implementation.
Choose one top-level task at a time; finish or explicitly block/handoff before
taking another. Follow AUTONOMOUS_DIRECTIVE.md continuously: after acceptance or
an explicit frozen handoff, inspect live claims and choose the next eligible task
without routine approval. A passing intermediate gate is not a reason to stop.

## Shared live board: same workspace host

All sessions, including those in worktrees, use the **same absolute directory**:

`/home/jjenus/workspace/apps/tracker/.coordination/`

This directory is outside the Git repositories and outside temporary storage.
Do not make a worktree-local copy. `claims/<TASK-ID>/STATUS.md` is the authoritative
live ownership/progress record; the table below is the versioned task definition.
Reading a table row or writing “I am working on it” in chat does not claim it.
On another machine, stop: these filesystem locks do not coordinate separate hosts.
The owner must provide one shared transactional board before multi-host work.

### Atomic claim protocol

Use a unique owner token, for example `<session-id>-<random-suffix>`. List the
existing claims and read their statuses first. Create the parent directories
with `mkdir -p` if absent. Then:

```sh
mkdir /home/jjenus/workspace/apps/tracker/.coordination/claims/FLEET-01
```

Use ordinary **mkdir, not mkdir -p**, for the individual claim. Success elects
one owner; an existing directory means stop and pick another task. Immediately
write STATUS.md using the template below. An empty claim is still occupied.
An occupied DONE task is complete, not available for another implementation.

Acquire each listed resource using the same atomic-mkdir rule under
`.coordination/resources/<resource>`, in alphabetical order. Write its OWNER.md
with owner token and task ID. If any resource is held, do not edit source: release
only resources you just acquired, leave task status WAITING_RESOURCE and retry
later, or explicitly relinquish the untouched claim. Start implementation only
after **all** resources are owned. Shared path edits require their resource too.

Task ownership and resource ownership are distinct: task claims prevent duplicate
work; resource claims prevent different tasks from editing the same surfaces.
For a new overlapping file, request a scope amendment from the integration owner
before editing. No informal “small shared fix” exceptions.

```markdown
# FLEET-01
Owner: <unique token / session ID>
State: CLAIMED
Updated: <UTC timestamp>
Task definition: <plan revision>
Backend base / branch / worktree: <SHA / branch / absolute path, or n/a>
Frontend base / branch / worktree: <SHA / branch / absolute path, or n/a>
Resources: <owned resource names>
Current step: <specific action in progress>
Completed: <observable result; initially none>
Next: <next executable action>
Blockers / requested decisions: <none or concrete dependency>
Tests / evidence: <commands, outcomes, retained paths; initially not run>
Handoff / commits: <none or exact SHAs and report path>
```

Update before changing steps, after meaningful results, on blockers and before
pausing; while active, heartbeat at least every 15 minutes. Write to an owner-
specific temporary file and rename over STATUS.md so readers see a complete record.
Keep secrets, tokens and private customer data out of the board and reports.

### State transitions and recovery

`CLAIMED → IN_PROGRESS → READY_FOR_REVIEW → REVIEWING → READY_FOR_INTEGRATION
→ INTEGRATING → DONE`. Corrections return to IN_PROGRESS under the same owner.
WAITING_RESOURCE and BLOCKED name the exact obstacle and resume condition.
Owner handoff is explicit: outgoing owner pauses and records commits/current
state; coordinator confirms the old session is stopped, then transfers the token.

Age is only a warning: a heartbeat older than 30 minutes, a missing owner file,
an empty claim, or a crashed shell is **not permission to steal it**. Another
agent records a recovery request and works elsewhere. Only the owner or the
claimed integration coordinator, after confirming the old worker has stopped
(or obtaining owner confirmation), may archive/reassign a claim. Preserve partial
work and claim history. Never automatically expire locks or kill another worker.

Keep task claims through DONE. The coordinator releases task resources after
integration, or after a reviewed BLOCKED handoff freezes the branch. To release
a lock, verify its owner token, remove only its own OWNER.md and use rmdir on
that exact resource directory. Never recursively clear the board. Recovery and
relinquishment archive the old STATUS.md under `.coordination/history/` first.
New work on an accepted task needs a new task ID, not reuse of its old claim.

## Worktrees, shared paths and machine resources

- Each worker uses its own branch and worktree in each repository it changes:
  `/home/jjenus/workspace/apps/tracker-worktrees/<task>-<owner>-backend` and/or
  `.../<task>-<owner>-frontend`. Branches: `agent/<task>/<owner>`.
- Base branches are backend `origin/main` and frontend `origin/frontend-app`.
  Record exact bases. Fetch/check worktree metadata without resetting another
  checkout. Keep active `sidie/`, `app/` and pre-existing dirty changes intact.
- The existing frontend detail/list changes are reserved for INT-00's explicit
  reconciliation. They are not available to discard, silently commit or copy
  wholesale into another lane. Independent work starts from committed bases;
  dependency changes are integrated deliberately with their provenance.
- Worker commits/pushes go only to task branches. Only the integration owner
  updates delivery branches, shared status/queue/matrix/readiness documents,
  shared app infrastructure and cross-domain contracts. No force pushes.
- Resource `shared-contracts`: backend `shared/`, migrations, SecurityConfig,
  cross-domain entity contracts; frontend core API, workspace composables/domain,
  query keys, central gateway, app config, dependency manifests/lockfiles,
  common CSS/layout and shared auth/session surfaces. Task-specific domain
  resources still apply. A proposed shared change goes into the task handoff;
  the coordinator schedules an exclusive integration window or approves a new task.
- Migrations are allocated by the coordinator immediately before integration.
  Never choose the same next migration version in two lanes or edit an applied
  migration. Review both H2/PostgreSQL histories and upgrade ordering.
- Builds use worktree-local target/.nuxt/.output/node_modules and private fixture
  data/ports. At most **two heavyweight build/test runs on this host**: acquire
  either `build-slot-1` or `build-slot-2` atomically before Maven/Nuxt/E2E runs;
  release in finally. More independent agents may code/read while those are busy.
  Never compile/clean the same target in parallel even with different task IDs.
- Fixed-port auth/preview browser fixtures and FULL_START additionally acquire
  `browser-fixtures`; shared acceptance takes `integration` too. Other workers
  may keep working in their isolated worktrees. Never edit an acceptance worktree
  or replace its artifacts while its revision tuple is frozen.

## Task board: initial dispatch

All tasks below are **initially unclaimed**. Live claim files override apparent
availability. READY permits investigation/implementation only within the stated
scope, not product acceptance. “After” requires the coordinator's accepted record.
Batch requirements in PRODUCTION_DELIVERY_PLAN.md remain exhaustive; these tasks
are the first dispatch wave, not a reduced replacement feature inventory.

| Task | Initial eligibility / dependencies | Exclusive domain resource | Deliverable and acceptance boundary |
|---|---|---|---|
| INT-00 | READY; one coordinator | integration, shared-contracts | Recover B0/B1 sources/evidence, reconcile reserved frontend dirty detail/list work with its tests; publish accepted baselines and unblock dependent UI tasks. Own shared integration and board amendments. |
| FLEET-01 | READY; backend work independent of INT-00 | fleet | Characterize/repair supported create vehicle + register already-assigned tracker validation/isolation; explicit ID/model/plate/VIN/device/protocol contracts and real DB duplicate/foreign-tenant regressions. No invented reassignment/cardinality/credential policy. Produce frozen contract for FLEET-02 and named assignment decisions. |
| GEO-01 | READY; existing vehicles usable as fixtures | geofences | Lossless circle/polygon/vehicle-association update contract and regression tests, tenant denial and rollback-safe invalid input. Preserve existing rule evaluations. Domain backend code/tests only. |
| TEAM-01 | READY | team | Characterize and close user/member/role assignment anti-escalation, disabled-account and last-admin gaps with real DB/HTTP tests. Preserve authenticated audit actors. Invitations/provider linking remain blocked on approved policy. |
| DEVICE-01 | READY | device | Independent device framing/reconnect/backpressure defect fixes with socket/process tests. Changes confined to device-comm; preserve wire contracts. No invented Autoseeker secrets, dangerous-command activation or claim of hardware attestation. |
| OPS-01 | READY | ops | Prepare/test CI and reproducible dev start/stop/config documentation in isolated tooling; no live runners/secrets/DNS/TLS deployment changes. Changes to existing shared harness/manifest/config require shared-contracts scheduling. |
| SUB-01 | READY | subscriptions | Existing catalog and explicit non-paid grant/usage API regression gaps and admin contract hardening. Preserve pending ≠ paid; no new metering/financial rules. Report policy-dependent lifecycle obligations separately. |
| NOTIFY-01 | READY; contract/evidence investigation first | notifications | Trace real inbox/preferences/initial-dispatch and durability contracts; fix supported internal replay/retry defects with tests where existing intent is clear. No invented recipients/contact opt-in/provider success. Freeze contract or record precise policy blocker. |
| SESS-01 | After INT-00; acquire shared-contracts exclusively | sessions | Durable multi-worker refresh design and implementation preserving reuse revocation, ambiguous-failure semantics and restart behavior; cross-worker integration tests. Schedule separate from shared gateway/UI integration. |
| FLEET-02 | After INT-00 + accepted FLEET-01 contract | fleet | Explicit supported live vehicle/register-assigned-tracker forms and persisted detail state, duplicates/permission/partial-failure UI. Coordinate shared BFF/composable changes with integration owner. Unassign/reassign stays blocked until FLEET-03 contract is approved. |
| FLEET-03 | BLOCKED on recorded ownership/cardinality/history decisions | fleet | Detached tracker ownership + authorized assignment/unassignment/history; obtain approved contract before schema/UI writes. Hardware trust is a separate gate. |
| GEO-02 | After INT-00 + GEO-01 | geofences | Authorized lossless editors/rule configuration, persisted reload/invalid geometry/foreign-tenant UI, actual rule evaluation acceptance. |
| TEAM-02 | After INT-00 + TEAM-01 | team | Member CRUD/role assignment UI with current permissions, immediate revocation and two-user denial checks; honest unavailable invitations. |
| SUB-02 | After INT-00 + SUB-01 | subscriptions | Authorized existing catalog/non-paid admin workflows with replay-safe errors and no inferred paid entitlement. Financial/provider/metering tasks stay policy-blocked. |
| NOTIFY-02 | After INT-00 + accepted NOTIFY-01 contract/policies | notifications | Real inbox/preferences and approved delivery workflow; durable restart/replay and tenant isolation; unavailable channels stay explicit. |
| TRACK-01 | After accepted fleet/geofence contracts + DEVICE-01 | tracking | Independent-process telemetry → persisted history/trips/rules/alerts → browser; real recorded paths, reconnect/restart/isolation. Command transport/ACK and hardware gates remain distinct. |
| RELEASE-01 | After all required inventory obligations accepted/resolved | integration | Fresh independent release audit, all final gates and readiness report. No implementation self-approval or inferred deployment authority. |

Workers may split an oversized task only through the coordinator: add new unique
IDs, exact write scope, resources, dependencies and acceptance before claiming.
The domain lock intentionally serializes tasks touching one domain. Backend and
frontend in different domains may progress concurrently. A new task's resources
must be checked against actual file ownership, not just its title.

The INT-00 claimant is the integration coordinator until an explicit recorded
handoff, including after INT-00 is DONE. Acquire/release integration and shared
resources per integration window; coordinator identity does not permanently hold
every shared-file lock. Other agents can read its claim to find the coordinator.

## Review and integration: one writer, parallel workers

1. Worker writes `docs/autonomous/handoffs/<TASK-ID>.md` on its task branch with
   base/commit SHAs for both repositories, paths changed, contract/migrations,
   completed vs blocked scope, RED/GREEN and coverage, test commands, rollback,
   current evidence retention and suggested actual browser acceptance. Update its
   shared claim to READY_FOR_REVIEW and keep resources reserved.
2. A different owner-started session, or a bounded independent verifier subagent
   within the owner's two-active-subagent limit, reviews a unique
   `REVIEW-<TASK-ID>-<SHA>` revision. The task owner atomically records the review
   claim for a delegated verifier; it is a verification subtask, not another
   implementation task. Worker freezes its source;
   reviewer inspects that exact SHA in a separate worktree and uses build/browser
   locks if executing tests. Review is read-only; findings/fixes go back to the
   worker. Reviewer reports actual scope,
   test execution and identity honestly. It launches no subagents.
3. Coordinator checks clean task boundaries and accepted dependencies, integrates
   scoped commits one task at a time into an isolated integration checkout,
   reconciles shared files/migration numbering and builds final artifacts.
   Never cherry-pick somebody's uncommitted workspace or reset owner changes.
4. Execute FULL_START for **each coherent integrated feature update**, including
   the changed actual browser workflow, persistence/restart, permission and tenant
   denials. A task's local test PASS or independent worktree FULL_START does not
   accept the later combined revision tuple. Corrections invalidate affected receipts.
5. Record accepted backend/device/frontend SHAs/artifact hashes and evidence in a
   per-task acceptance report; update queue/matrix/recovery and delivery branches.
   Only then set DONE and release resources. Earlier accepted features receive
   appropriate regression checks when another lane integrates.

INT-00's initial baseline: frontend has local committed alert UI changes through
`b00c92c`, not all published. Local `.verification/last-startup.json` retains
FULL_START PASS at 2026-10-30T13:38:43Z for frontend b00c92c + dirty fingerprint
18baeef0…a7f and backend/device e0193dd, with cleanup/persistence/restart stages.
Backend e0193dd was not published to main; recover the object from local Git if
available, otherwise retain its provenance and inspect published equivalent code.
Raw /tmp logs/worktree were lost. The last extra reviewer attempt hit a usage
limit; do not call that an approved review. Prior bounded UI review completed.
INT-00 verifies/reconciles this before accepting a clean combined baseline.

## Prompt to give a separately started agent

> Work as an independent tracker agent, not a subagent. Read workspace AGENTS.md,
> DELIVERY_PLAN.md and the current PARALLEL_EXECUTION.md it points to. Inspect the
> shared .coordination claims and atomically claim one eligible task and all its
> resources before editing. Record your owner/session, worktrees, current step,
> progress and blockers in its STATUS.md. Use isolated task branches, preserve
> active checkouts and do not push delivery branches. At most two active subagents
> may handle bounded subtasks/independent verification inside your task; they must
> not own whole other tasks or spawn agents. Continue to the next eligible task
> after acceptance or an explicit blocked/frozen handoff.
> Implement/test only the claimed scope; hand off exact commits for a separate
> reviewer and serialized FULL_START integration. If a task is occupied or blocked,
> select another READY task rather than duplicating it.
