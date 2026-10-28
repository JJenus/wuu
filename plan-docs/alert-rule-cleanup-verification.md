# Alert-rule cleanup verification

## Scope

Derived from the owner's request for an incremental modular-monolith cleanup. Keep the existing controller-backed alert-rule behavior, remove the unused command/query duplicates, and leave device communication unchanged.

`AlertRuleController` is the only production caller of `AlertRuleService`. The removed `AlertRuleCommandService`/`AlertRuleQueryService` pair had no callers outside that pair. Their behavior was not substituted for the live implementation: the query duplicate used unscoped `findAll`, and the command duplicate omitted the live custom-rule organization assignment and manual cache updates.

The retained constructor now requires nine dependencies instead of eleven; unused geofence dependencies are removed. Response mapping is private. HTTP routes, transaction/cache annotations, and business logic are unchanged.

## Evidence (2026-10-21)

- Baseline: `./mvnw -B -pl alerting -am -Dtest=AlertRuleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test` — 20 tests passed before changes.
- Compile-time RED: the updated test setup called the nine-dependency constructor before production changes; the same command failed at that constructor with “actual and formal argument lists differ in length.”
- Characterization: after reducing the constructor, the same command passed 31 tests. A new timestamp assertion initially exposed the existing `Instant.EPOCH` creation default for custom rules; the assertion was corrected to characterize existing behavior, not change it.
- Clean rebuild after deleting duplicates: `./mvnw -B -pl alerting -am clean test` — shared: 13 tests; alerting: 103 tests; zero failures/errors/skips. Clean compilation ensures deleted classes do not remain in output.
- Full reactor: `./mvnw -B test` — 366 tests across all seven child modules; zero failures/errors/skips.
- `git diff --check` passed. No device-communication source or configuration changed. Build-generated changes to tracked compiler metadata were restored; existing AGENTS.md work was retained. No commits were created.

| Guarantee | Test in `AlertRuleServiceTest` |
|---|---|
| Custom creation retains organization, vehicle associations, response fields, timestamps, and cache updates | `createRule_validRequest_preservesTenantVehiclesAndTimestamps` |
| Batched listing uses organization-scoped queries across pages | `getAllRules_multiplePages_readsOnlyCurrentOrganization` |
| Enabled rules use the current organization | `getEnabledRules_currentOrganization_returnsEnabledRules` |
| Detail/page cache hits avoid repository reads | `getRuleByKey_cachedRule_avoidsRepositoryLookup`, `getAllRulesPaged_cachedPage_avoidsRepositoryLookup` |
| Redis failures fall back to repository results | `getAllRulesPaged_cacheUnavailable_returnsRepositoryResults` |
| Enabled pagination forces the enabled filter | `getEnabledRulesPaged_requestWithoutFilter_forcesEnabledFilter` |
| Vehicle reassignment invalidates both old and new vehicle caches | `updateRule_vehicleAssociationsChanged_invalidatesOldAndNewVehicles` |
| Empty updates avoid persistence/cache writes | `updateRule_emptyRequest_preservesRuleWithoutSaving` |
| Vehicle grouping preserves empty results and scoped lookups | `getRulesByVehicleIds_currentOrganization_groupsRulesIncludingEmptyResults` |
| Batch enable continues after a missing rule | `batchEnableRules_missingRule_continuesWithRemainingRules` |

## Coverage and limits

Surefire's literal in-process configuration did not honor `-DforkCount=1` for agent attachment; ordinary `prepare-agent test report` produced no execution data. Coverage was instead collected by attaching JaCoCo to Maven itself:

```sh
MAVEN_OPTS="${MAVEN_OPTS:-} -javaagent:$HOME/.m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar=destfile=/tmp/opencode/tracker-alert-rule.exec,includes=com.surf.tracker.alerting.*,append=false" ./mvnw -B -pl alerting -am -Dtest=AlertRuleServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -pl alerting -Djacoco.dataFile=/tmp/opencode/tracker-alert-rule.exec org.jacoco:jacoco-maven-plugin:0.8.12:report
```

The runtime agent above was downloaded by the earlier explicit `org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent` invocation. Maven installs it only when that goal is invoked; it is not a repository dependency.

Retained `AlertRuleService`: **336/386 lines (87.0%)**, **67/114 branches (58.8%)**. This exceeds 80% line coverage, not the guidelines' 95–100% application target; validation/error branches remain incompletely covered. The focused tests construct the module directly and do not prove Spring cache-proxy behavior, real Redis/JMS delivery, or application startup. `*IT` tests were not run by the default full-reactor command. Existing timestamp defaults, tenant cache-key design, and the `getEnabledRules` cache expression are unchanged and warrant separate defect work, not silent changes in this behavior-preserving cleanup.
