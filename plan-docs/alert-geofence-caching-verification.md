# Alert and geofence tenant caching verification

## Scope

Follow-up to `alert-rule-caching-verification.md`. Journeys: organizations see only their own alerts/geofences; writes refresh their own query caches without evicting another organization; equivalent requests reuse entries; Redis outages retain geofence database fallback. Device communication, event contracts, schema, and deployment wiring are unchanged.

## Implementation

- Reuse the existing tenant cache resolver for alert details, queries, statistics, and geofence pages. Logical regions resolve to `<cache>:v2:org:<id>`; missing/nonpositive context is rejected before cached operations execute.
- `AlertCacheKeyGenerator` snapshots every search field and separates methods/classes using JSON values, not mutable request identity, concatenated query strings, or 32-bit hashes.
- Alert detail reads and all bulk batches use organization-scoped repository methods. Entity reads used by acknowledge/resolve no longer share a cache entry with response DTOs; mutations load the scoped entity from the repository.
- Every alert mutation evicts the writing organization's detail/query/statistic regions, including recent queries with differing limits and paged searches. Geofence mutations evict the tenant's entire page region.
- Geofence pagination now uses Spring caching instead of three repeated manual Redis implementations. The five-minute TTL and best-effort behavior are preserved. The production error handler suppresses Redis get/put/clear failures only for tenant geofence-page regions; other Spring caches retain their existing failure policy.
- Manual alert/geofence namespaces are `alerting:alert:v2:org:<id>:` and `alerting:geofence:v2:org:<id>:`. Detail writes use entity ownership, not ambient context; geofence vehicle invalidation also follows the entity owner. Detail/list reads reject foreign cached entities; mixed-owner list writes are rejected. Manual clears/invalidation are tenant-scoped.
- Geofence reassignment invalidates the union of old/new vehicle IDs using a separate set, without modifying associations or failing on immutable input sets.

## Executed evidence

1. **RED:** `./mvnw -B -pl alerting -am -Dtest=AlertCachingIntegrationTest,GeofenceCachingIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test` failed at runtime, including cross-tenant page/detail/list hits, hash/query-shape collisions, unscoped bulk mutations, and an `AlertResponse` to `TrackerAlert` class cast after a warm detail lookup. Log: `/tmp/opencode/tracker-alert-geofence-red.log`.
2. **Manual RED:** the same command with `-Dtest=AlertGeofenceRedisTenantCacheTest` failed before the manual namespace/ownership fixes. Log: `/tmp/opencode/tracker-manual-red.log`.
3. **Outage RED:** `-Dtest=GeofenceCachingIntegrationTest#readAndMutateGeofence_redisUnavailable_preservesDatabaseFallback` failed before the scoped error handler, reproducing the Redis availability regression introduced by moving pagination to Spring caching. Log: `/tmp/opencode/tracker-geofence-outage-red.log`.
4. **GREEN:** focused regressions passed, then the full reactor passed **469 tests**, zero failures/errors/skips (**206 alerting**). This adds **70 cases** to the previous 399-test baseline. Final log: `/tmp/opencode/tracker-alert-geofence-final.log`. An earlier instrumented run timed out during the main-app schema test; the final run used a longer timeout and completed successfully.
5. **Review:** `git diff --check` passed. Code/diff review checked tenant region resolution, payload ownership, scoped repository batches, invalidation, TTL inheritance, and best-effort failure policy. No independent reviewer, external dependency audit, or whole-application security audit was run. No commits or deployments were performed.

| Guarantee | Tests |
|---|---|
| Cold/warm alert detail reads cannot expose another organization | `AlertCachingIntegrationTest` detail cases |
| Acknowledge/resolve cannot consume response DTOs as entities | `mutateAlert_detailResponseAlreadyCached_usesScopedEntityLookup` |
| Active, recent, search, paged, counts, and statistics results are tenant-separated | `AlertCachingIntegrationTest` query/statistics cases |
| Bulk mutations scope every batch, including >100 IDs | Mixed-tenant and multi-batch cases in `AlertCachingIntegrationTest` |
| All nine alert mutation paths refresh all query shapes and counts | `mutateAlert_warmQueries_invalidatesAllShapesAndStatistics` |
| Geofence vehicle lists/pages are isolated; colliding hashes and different query operations remain separate | `GeofenceCachingIntegrationTest` |
| Geofence create/update/delete preserve another tenant's warm pages | `mutateGeofence_warmPages_invalidatesOnlyWritingTenant` |
| Reassignment preserves associations and invalidates old vehicle lists | `updateGeofence_vehicleReassignment_preservesNewAssociationsAndInvalidatesOldLists` |
| Manual detail ownership, poisoned payload rejection, legacy namespace avoidance, scoped clearing/invalidation | `AlertGeofenceRedisTenantCacheTest` |
| Redis outages preserve manual cache misses and geofence page DB fallback | 17 manual outage cases and geofence proxy outage case |
| Production dynamic region TTLs remain 20 minutes for alerts, five minutes for counts/geofence pages | `TenantRuleCacheConfigurationTest` |

## Coverage

Attached the installed JaCoCo agent to Maven because Surefire runs in-process:

```sh
MAVEN_OPTS="${MAVEN_OPTS:-} -javaagent:$HOME/.m2/repository/org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar=destfile=/tmp/opencode/tracker-alert-geofence.exec,includes=com.jjenus.tracker.alerting.*,append=false" ./mvnw -B test
./mvnw -B -pl alerting -Djacoco.dataFile=/tmp/opencode/tracker-alert-geofence.exec org.jacoco:jacoco-maven-plugin:0.8.12:report
```

| Implementation | Lines | Branches |
|---|---:|---:|
| `AlertService` | 168/185 | 31/50 |
| Nested `AlertQueryService` | 12/13 | 2/4 |
| `GeofenceService` | 100/121 | 16/32 |
| `AlertCacheService` | 83/83 | 18/24 |
| `GeofenceCacheService` | 76/78 | 25/28 |
| `AlertCacheKeyGenerator` | 17/19 | 6/6 |
| Existing tenant resolver | 10/10 | 3/4 |
| `RedisKeyGenerator` (all families) | 38/47 | 9/18 |
| `CacheConfig` / error handler | 21/21; 10/12 | —; 4/10 |

These exceed 80% line coverage for the listed implementations, not uniformly 80% branch coverage or the guidelines' 95–100% application-service target. No coverage enforcement was added.

## Rollout and remaining limits

- Replace **all backend instances serving alerts, rules, or geofences together**. Old binaries still use unsafe global keys. New namespaces deliberately ignore old entries; no Redis flush/migration was performed.
- Tests use real Spring cache proxies and cache/key implementations with mocked Redis operations/repositories. Production RedisCacheManager metadata is tested without connecting to Redis. Real wire serialization, live Redis/JMS, HTTP authorization, transaction ordering, concurrent fills/invalidation, and full application startup are not verified. Default Surefire discovery excludes `*IT` tests.
- Cache invalidation still occurs around method completion, not explicitly after transaction commit. Failed best-effort invalidation can leave geofence pages stale until their five-minute TTL; failed manual invalidation retains existing TTL-based recovery. No stronger concurrency or rollback guarantee is claimed.
- Alert statistics retain their existing lower-bound-only repository semantics: `endDate` is included in the key but is not applied by the repository. Timestamp defaults and other existing business behavior are unchanged.
- Rule-state, deduplication, cooldown, and other cache families remain separate follow-up work. This is not a whole-application security clearance. Independent device deployment is still a requirement, not implemented by these changes.

## Self-review

Overall **4.0/5**: accuracy 4 (469 tests pass, live Redis unverified); completeness 4 (scoped cache defects addressed, concurrency excluded); clarity 4 (evidence mapped above, historical resolver name still mentions rules); actionability 4 (rollout constraint explicit, deployment remains unperformed); conciseness 4 (query boilerplate removed, intentionally detailed regression evidence). Highest-value improvement: add real Redis serializer/transaction integration coverage before production rollout. The user should judge this as a scoped cache fix, not proof of end-to-end deployment readiness.
