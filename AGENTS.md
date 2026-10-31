# Tracking Engine

## Build and focused verification

- Run commands from this directory, not its parent workspace. Use `./mvnw`: this custom wrapper selects an **installed Java 21** and downloads Maven 3.9.9 (first run needs network, curl/wget, and unzip). It does not install Java.
- Current layout is two independent Maven JAR projects, not the historical reactor: root business backend and `device-comm/pom.xml`. Both include `shared/src` through build-helper; no sibling artifact installation is needed.
- Backend build: `./mvnw -B verify`; tests: `./mvnw -B test`. Device build: `./mvnw -B -f device-comm/pom.xml verify`. Root Surefire uses `forkCount=0`.
- Single backend test: `./mvnw -B -Dtest=VehicleTest test`; use `ClassName#methodName` to narrow further.
- Backend run: `./mvnw spring-boot:run`, or `java -jar target/tracking-engine-1.0.0-SNAPSHOT.jar` after packaging. Both POMs activate Boot repackage. Device launcher/package must remain independently verified; a root build does not build device-comm.
- Custom parent imports the Spring Boot 3.5.9 BOM; there is no Boot parent. Plugin management alone does not activate plugins/executions.

## Tests

- Before adding/changing tests, read `TESTING_GUIDELINES.md` for naming, builders, given/when/then, AssertJ, deterministic time/IDs, and mocking rules. `TESTING.md` is a scope reference, not build configuration.
- Surefire discovers `*Test` and `*IntegrationTest`, but not `*IT` by default; no Failsafe execution is configured. Run `*IT` explicitly with `-Dtest=ClassNameIT` and check its infrastructure requirements.
- Coverage percentages in the guidelines are targets, **not enforced build gates**. In-process Surefire does not attach a forked-JVM JaCoCo agent. Do not claim coverage without checking instrumentation and execution data.
- Serialize root Maven runs: `testCompile` compiles all tests, even with `-Dtest`. Concurrent RED/clean builds against the same `target/` can invalidate another lane's evidence. Device builds have a separate target.
- Full disposable-infrastructure gate: `sh tools/verify-release.sh` (Docker required). It explicitly selects `*IT`. Review its latest result before claiming the gate passes.

## Actual wiring and pitfalls

- Active backend entrypoint: `src/main/java/com/surf/tracker/main/TrackingEngineApplication.java`. Business packages are `core`, `alerting`, `notification`, `userauth`; device sources are outside the backend source set.
- Backend defaults: `src/main/resources/application.yaml`, in-memory H2, external Redis/Artemis, required JWT secret. Device defaults: `device-comm/src/main/resources/application.yaml`, TCP 8888. The product frontend is the independent Nuxt repository `../app/`; read its `AGENTS.md` and current contracts before frontend work.
- Flyway histories in `src/main/resources/db/migration/` (H2) and `db/postgresql/` own the schema; Hibernate validates. Supply compatible migrations for both histories; never switch histories or edit applied migrations to bypass checksums. See SECURITY.md for the postgres profile.
- Telemetry flows `LocationDataEvent` → core tracking → `VehicleUpdatedEvent` → alerting → `AlertRaisedEvent` → notification via Artemis. `ArtemisEventPublisher` derives topic names from lowercased Java simple class names; `JmsConfig` uses `_type` message headers. Renaming/moving events can break consumers/serialization.
- Spring `@EventListener` and JMS `@JmsListener` are distinct delivery mechanisms. Trace actual publishers, JSON conversion, destination domain, tenant resolution and consumers before accepting a flow. A string queue round trip is not telemetry/notification pipeline evidence.
- Tenant scoping uses `shared/security/TenantContext` (ThreadLocal), set/cleared by `JwtAuthFilter`. Preserve organization filters in repository access; HTTP tenant context does not automatically cross JMS, scheduler, or Reactor execution seams.
- `Vehicle` and `Trip` are JPA entities in `core/domain/entity`; keep their business methods rather than recreating parallel pure-POJO models.

## Design direction and device constraint

- The pre-existing working tree consolidates business modules and renames packages from `com.jjenus` to `com.surf`. Preserve that migration and unrelated uncommitted work.
- Keep `device-comm` unchanged when simplifying business modules: its independent deployment is required. Preserve protocol parsing, TCP sessions, and command transport there; retain business validation and command persistence in core tracking.
- Standalone device packaging does not prove remote business-command transport: backend `DeviceCommandConfig` currently supplies no functioning transport/builders. Keep command delivery/ACK and untrusted-input/connection sanitation visible as release gates; no direct business-module dependency inside device-comm. Autoseeker devices do not require a newly invented credential scheme (owner clarification); API authentication remains required.
- For business-module simplification, assess **depth** at the interface, not Maven-module count. Hide persistence/cache/messaging orchestration behind small use-case interfaces; keep implementation private and tests at the same seam as callers. Add an adapter seam where behavior actually varies, not a pass-through per class.
- `AlertRuleService` is the single alert-rule management interface used by `AlertRuleController`; the unused command/query duplicates were removed. Keep response mapping and cache orchestration internal rather than recreating parallel implementations.
- Rule caches use versioned organization namespaces in both Spring and manual Redis paths. Preserve `AlertRuleService`'s cache resolver/key generator and explicit organization IDs for background reads; a missing vehicle index is a cache miss, not proof that no rules exist. Run `AlertRuleCachingIntegrationTest` for proxy-level isolation/invalidation coverage.
- `shared` currently mixes event/command interfaces with Redis, JMS, Reactor Netty, and tenant context. Keep business implementation out of it; business-module consolidation must not pull business dependencies into the device-facing interface.

## Autonomous recovery

Read `docs/autonomous/SESSION_STATE.md`, TASK_QUEUE.md, FEATURE_MATRIX.md, DEFECT_REGISTER.md and TEST_REPORT.md before resuming delivery. Treat interrupted agent changes as unverified until tests and review pass. Owner scope clarification: only ride-hailing is deferred; subscriptions must support configurable token usage and monthly/yearly periodic models. Inspect existing frontend/backend evidence and choose safe reversible implementation defaults autonomously; paid entitlement requires real evidence, not simulated payment success. Production credentials/deployment remain outside the authorization. STATUS remains IN_PROGRESS/BLOCKED unless all release gates actually pass.

Independent-agent coordination: before choosing/editing work, read workspace DELIVERY_PLAN.md and `docs/autonomous/PARALLEL_EXECUTION.md`. Separate owner-started sessions atomically claim tasks/resources in the one shared workspace `.coordination/` and maintain current progress. Use isolated task worktrees, launch no subagents and push only task branches. Only the claimed integration coordinator updates delivery branches/shared contracts; final review is a different independent session. Two heavyweight builds/tests maximum on the host; integration/FULL_START is serialized. This owner update supersedes the older D007 subagent/two-lane scheduling policy.
