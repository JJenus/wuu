# Alert-rule tenant caching verification

## Scope and implementation

Follow-up to `alert-rule-cleanup-verification.md`: fix rule-management/evaluation cache isolation and invalidation without changing device communication.

- Spring caching resolves logical caches into `<cache>:v2:org:<organizationId>` regions. Missing/nonpositive HTTP tenant context is rejected before repository access. The production Redis manager preserves each logical cache's TTL, serialization, key prefix, and null policy for dynamic regions.
- Read keys snapshot query fields and sorted vehicle IDs as JSON, instead of mutable DTO identity or 32-bit hash values. Enabled-page keys normalize the enabled filter before the method mutates the request.
- Every mutation, including batches and templates, evicts all three rule-query regions for the writing tenant only. Void mutations no longer try to cache null. Renaming additionally evicts the old manual detail key.
- Manual rule detail, active index, pagination, vehicle list, and vehicle-index keys use `alerting:rule:v2:org:<id>:`. Explicit background organization arguments override ambient HTTP context. Cached rule entities are checked against the requested organization before being returned. Manual bulk clearing scans only the current tenant's rule prefix.
- A missing vehicle index now falls back to the scoped rule lookup. Redis LIST entries are never read with GET; STRING empty markers are never read with LRANGE. This prevents cold versioned caches from permanently skipping vehicles.

## Test evidence

Journeys: each organization sees only its own rules; repeated equivalent queries reuse cached results; every mutation refreshes all query shapes without evicting another tenant; cold-cache evaluation still loads rules.

1. **RED** — `./mvnw -B -pl alerting -am -Dtest=AlertRuleCachingIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test`: 10 executed tests, 8 assertion failures and 2 errors. Demonstrated cross-tenant hits in lists/details/pages, colliding query/set hashes, stale renamed keys, and the nonexistent `getCurrentOrgId()` SpEL method. Initial fixture compilation errors were fixed before collecting this runtime RED result.
2. **Configuration RED** — `TenantRuleCacheConfigurationTest` failed because new regions initially inherited one-hour defaults instead of the configured 12-hour rule TTL. The Redis manager now inherits the logical cache policy.
3. **Cold-cache RED** — `RuleRedisTenantCacheTest` reproduced false negatives from the absent vehicle index and incorrect GET usage on positive rule lists. Both paths were fixed.
4. **GREEN** — targeted tests pass through a real Spring caching proxy, a real ConcurrentMapCacheManager with null values disabled, actual cache/key modules, and mocked Redis operations/repositories. `TenantRuleCacheConfigurationTest` separately checks production RedisCacheManager metadata without opening a Redis connection.
5. **Full reactor GREEN** — `./mvnw -B test`: **399 tests**, zero failures/errors/skips, including 136 in alerting. This adds 33 regression cases to the earlier cleanup's 366-test baseline. Default Surefire discovery still excludes `*IT` classes.

| Guarantee | Regression test |
|---|---|
| Lists, details, enabled lists, pages, and grouped vehicles are tenant-separated | `AlertRuleCachingIntegrationTest` |
| Equivalent requests hit cache; colliding String/Set hashes do not mix results | `AlertRuleCachingIntegrationTest` |
| Create/update/enable/disable/delete/batches invalidate every query shape | `mutateRule_warmQueryCaches_invalidatesEveryQueryShape` (7 cases) |
| Another tenant's warm list and manual pagination caches survive writes | `updateRule_warmCaches_invalidatesOnlyWritingTenant` |
| Renames invalidate old details; void enable does not store null | `updateRule_renamedKey_evictsOldDetailAndLists`, `enableRule_voidMutation_evictsRatherThanCachingNull` |
| Manual cache reads/writes honor explicit ownership and scoped clear patterns | `RuleRedisTenantCacheTest` |
| Cold indices and mixed Redis data types do not suppress rule evaluation | `RuleRedisTenantCacheTest` |
| Dynamic tenant regions retain 12h/6h/1h TTLs and reject null values | `TenantRuleCacheConfigurationTest` |

Coverage was collected with the Maven-JVM JaCoCo attachment described in the cleanup report, running `./mvnw -B -pl alerting -am test` with execution output `/tmp/opencode/tracker-rule-cache.exec`, then reporting with `./mvnw -B -pl alerting -Djacoco.dataFile=/tmp/opencode/tracker-rule-cache.exec org.jacoco:jacoco-maven-plugin:0.8.12:report`.

| Module implementation | Lines | Branches |
|---|---:|---:|
| `TenantAlertRuleCacheResolver` | 10/10 | 3/4 |
| `AlertRuleCacheKeyGenerator` | 14/16 | 8/8 |
| `AlertRuleService` | 343/388 | 71/116 |
| `AlertRuleCacheService` | 72/82 | 14/16 |
| `VehicleRuleCacheService` | 56/98 | 18/30 |
| `RedisKeyGenerator` (includes other cache families) | 22/41 | 7/14 |

Coverage is not uniformly 80% across existing implementations; bulk vehicle warming/invalidation, several failure branches, and other cache families remain incompletely covered. No coverage threshold was added or claimed to pass. Build-generated changes to tracked compiler metadata were restored; earlier cleanup/instruction work was retained. No commits were created.

## Rollout and limits

New code deliberately ignores legacy global entries; they may expire naturally. Replace all backend instances serving rule requests when deploying the fix: old instances still use unsafe global caches. No Redis flush or data migration was performed.

These tests do not start an external Redis/JMS server, exercise real wire serialization, or prove transaction/concurrency behavior. Other cache families (`AlertService`, geofences, rule-state/dedup caches) retain their existing design and require a separate tenant-isolation review; this is not a whole-application security clearance. The earlier cleanup report's timestamp-default limitation remains unchanged.
