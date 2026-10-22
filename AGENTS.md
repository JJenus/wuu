# Tracking Engine

## Build and focused verification

- Run commands from this directory, not its parent workspace. Use `./mvnw`: this custom wrapper selects an **installed Java 21** and downloads Maven 3.9.9 (first run needs network, curl/wget, and unzip). It does not install Java.
- Full build: `./mvnw -B clean install`; tests only: `./mvnw -B test`. Surefire already defaults to `forkCount=0` in the parent POM.
- Module plus prerequisites: `./mvnw -B -pl core-tracking -am test` (substitute the module). Maven orders dependencies; no separate shared-first build is needed with `-am`.
- Single test, including prerequisites: `./mvnw -B -pl core-tracking -am -Dtest=VehicleTest -Dsurefire.failIfNoSpecifiedTests=false test`. Use `ClassName#methodName` to narrow further.
- Module-only runs without `-am` require installed sibling artifacts: `./mvnw -B -DskipTests install` first.
- Run the composed app after installing siblings: `./mvnw -pl main-app spring-boot:run`. `exec:java` has no configured main class. The Boot plugin has no repackage execution, so plain `package` does not produce an executable Boot jar.
- Custom parent imports the Spring Boot 3.5.9 BOM; there is no Boot parent. Plugin management alone does not activate plugins/executions.

## Tests

- Before adding/changing tests, read `TESTING_GUIDELINES.md` for naming, builders, given/when/then, AssertJ, deterministic time/IDs, and mocking rules. `TESTING.md` is a scope reference, not build configuration.
- Surefire discovers `*Test` and `*IntegrationTest`, but not `*IT` by default; no Failsafe execution is configured. Run `*IT` explicitly with `-Dtest=ClassNameIT` and check its infrastructure requirements.
- Coverage percentages in the guidelines are targets, **not enforced build gates**. JaCoCo is only in plugin management; in-process Surefire does not attach its forked-JVM agent. Do not claim coverage from `test jacoco:report` without checking instrumentation and execution data.

## Actual wiring and pitfalls

- `main-app/.../TrackingEngineApplication.java` is the active Boot entrypoint: it scans all modules, entities, and repositories. Each business module (`core-tracking`, `alerting`, `notification`, `user-auth`) and `device-comm` depends on `shared`; `main-app` depends on all of them. README dependency diagrams are stale.
- Runtime defaults live in `main-app/src/main/resources/application.yaml`: H2 in-memory, external Redis (`localhost:6379`), native Artemis (`localhost:61616`), TCP port 8888. That YAML currently repeats the top-level `management` key; investigate this before assuming startup works.
- Flyway migrations in `main-app/src/main/resources/db/migration/` own the composed app schema; Hibernate uses `ddl-auto: validate`. Entity changes need corresponding migrations, not Hibernate schema generation.
- Telemetry flows `LocationDataEvent` → core tracking → `VehicleUpdatedEvent` → alerting → `AlertRaisedEvent` → notification via Artemis. `ArtemisEventPublisher` derives topic names from lowercased Java simple class names; `JmsConfig` uses `_type` message headers. Renaming/moving events can break consumers/serialization.
- Spring `@EventListener` and JMS `@JmsListener` are distinct delivery mechanisms. Trace the actual publisher and listener before changing a flow; `FuelCutCommandHandler` listens in-process, while `VehicleEventHandler` also has a JMS fuel-cut handler that republishes the same event.
- Tenant scoping uses `shared/security/TenantContext` (ThreadLocal), set/cleared by `JwtAuthFilter`. Preserve organization filters in repository access; HTTP tenant context does not automatically cross JMS, scheduler, or Reactor execution seams.
- `Vehicle` and `Trip` are JPA entities in `core/domain/entity`; keep their business methods rather than recreating parallel pure-POJO models.

## Design direction and device constraint

- The owner is considering a simpler **modular monolith** for the business modules; this is a direction to evaluate, not authorization for a repository-wide collapse.
- Keep `device-comm` unchanged when simplifying business modules: its independent deployment is required. Preserve protocol parsing, TCP sessions, and command transport there; retain business validation and command persistence in core tracking.
- Independent deployment is **not wired today**: `main-app` embeds `device-comm`, its POM has no standalone Boot launcher, and `FuelCutCommandHandler` injects the in-process `DeviceCommandTransport` plus protocol builders. Do not describe this as an already deployable separate process or replace the device seam with direct business-module calls.
- For business-module simplification, assess **depth** at the interface, not Maven-module count. Hide persistence/cache/messaging orchestration behind small use-case interfaces; keep implementation private and tests at the same seam as callers. Add an adapter seam where behavior actually varies, not a pass-through per class.
- `AlertRuleService` is the single alert-rule management interface used by `AlertRuleController`; the unused command/query duplicates were removed. Keep response mapping and cache orchestration internal rather than recreating parallel implementations.
- Rule caches use versioned organization namespaces in both Spring and manual Redis paths. Preserve `AlertRuleService`'s cache resolver/key generator and explicit organization IDs for background reads; a missing vehicle index is a cache miss, not proof that no rules exist. Run `AlertRuleCachingIntegrationTest` for proxy-level isolation/invalidation coverage.
- `shared` currently mixes event/command interfaces with Redis, JMS, Reactor Netty, and tenant context. Keep business implementation out of it; business-module consolidation must not pull business dependencies into the device-facing interface.
