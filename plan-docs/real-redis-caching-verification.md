# Real Redis caching verification

## Outcome

Used the installed native Redis server, bound to `127.0.0.1:16389`, with snapshot/AOF persistence disabled. Docker was installed but its daemon was unavailable. No application Redis instance was accessed. Tests removed their own reserved tenant regions/keys; the disposable server had zero keys after verification and was shut down afterward.

Real serialization uncovered defects absent from the earlier in-memory tests:

1. The application mapper rejected `java.util.ArrayList` type IDs on alert-list reads.
2. Final immutable statistics maps lacked root type information.
3. `PagedResponse` had no constructor usable by Jackson.
4. The rule pipeline redundantly converted already-deserialized entities with the HTTP mapper, failed on collection typing, and silently fell back to the database.

## Changes

- Added `shared/redis/RedisJsonSerializer`: copies the supplied mapper, configures Redis-only type information including final immutable containers, and validates root/nested types. Permits project classes and selected collection/value classes, not arbitrary JDK types. Reads as `Object` so root type resolution goes through the validator too.
- Spring caches and the primary object RedisTemplate use that codec. The HTTP/JMS mapper, connection-info RedisTemplate, event contracts, device module, and protocol/session/command code remain unchanged. The primary object template is shared infrastructure: these checks cover alerting payloads, not every template consumer.
- Added the no-argument constructor needed by `PagedResponse`.
- Rule pipelines reuse actual `AlertRule` objects instead of converting them again; existing conversion remains for non-entity payloads.

## Evidence

Explicit opt-in Redis tests (fail if `tracker.test.redis.port` is missing; default Surefire discovery excludes `*IT`):

```sh
./mvnw -B -pl main-app -am -Dtest=RedisAlertingCacheIT,RedisJsonSerializerTest -Dtracker.test.redis.port=16389 -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B test
```

- Initial Redis run: **5 cases, 3 serialization errors**. `/tmp/opencode/tracker-real-redis-red.log`.
- Expanded tests reproduced rule pipeline fallback; fixed before final validation. `/tmp/opencode/tracker-real-redis-expanded.log` records that failure.
- Final focused run: **9 real Redis tests + 2 serializer unit tests passed**, zero failures/errors/skips. `/tmp/opencode/tracker-real-redis-final.log`.
- Default full reactor: **471 tests passed**, zero failures/errors/skips; the **9 Redis IT cases were run separately**, not counted in that default total. `/tmp/opencode/tracker-real-redis-full.log`.
- `git diff --check` passed. No commits or deployments performed. No new coverage report was collected for this follow-up; earlier coverage numbers describe the earlier code state.

Verified against real Redis: response DTOs/Instants, typed alert lists, geofence pages/content, immutable statistics maps and numeric types, manual alert/geofence detail and vehicle lists, rule set/pipeline reads without repository fallback, tenant-local clear, alert TTL, and alert-service proxy cache hits/invalidation that preserve another tenant's entries. Serializer unit tests verify the original mapper is unchanged and unapproved root classes are rejected.

## Limits and next work

- These use real Redis but mocked repositories: not real DB transactions, rollback, concurrent fills, or after-commit invalidation. Test lifecycle uses explicitly managed native Redis instead of Testcontainers because Docker was not running and native Redis was available/authorized.
- Do not mix old/new backend binaries during rollout. Older cache payloads may be unreadable by the revised codec; no live-key migration, compatibility guarantee, or broad flush was attempted. Deploy to a clean/disposable cache or plan targeted cache invalidation before production use, especially for consumers of the primary RedisTemplate outside alerting.
- Next priority: transaction-aware invalidation (writes visible only after commit, rollback must not leave stale cached entities), then tenant review of rule-state/dedup/cooldown caches. Full application startup and separate device deployment remain separate work.

Self-review: accuracy 4/5 (real Redis verified, real DB excluded); completeness 4/5 (serialization/invalidation covered, concurrency excluded); clarity 4/5 (commands and limits explicit); actionability 4/5 (repeatable opt-in test, native server required); conciseness 4/5 (narrow fixes, separate technical codec). Overall 4.0/5. Highest-value improvement is real DB/Redis transaction coverage; these results should not be mistaken for whole-application deployment approval.
