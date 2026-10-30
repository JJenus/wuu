# Autonomous delivery plan

## PINNED CURRENT ROADMAP — 2026-10-30

Authority: workspace `AUTONOMOUS_DIRECTIVE.md`; target production readiness,
not merely passing test suites. **Current detailed version:**
[PRODUCTION_DELIVERY_PLAN.md](PRODUCTION_DELIVERY_PLAN.md). Workspace entry point:
`DELIVERY_PLAN.md`. The older plan below is historical context, not the queue.

Execution order: B0 complete startup/recovery → B1 usable alerts → B2 vehicle/
tracker setup → B3 geofences/rules → B4 team → B5 tracking/device workflows →
B6 subscriptions → B7 notifications → B8 production operations/shared sessions →
B9 independent readiness acceptance. See detailed dependency edges for safe
parallel work; maximum two agents. No intended inventory feature is excluded by
this grouping, and only ride-hailing is deferred.

After every feature addition/update, the detailed plan's **FULL_START** gate is
mandatory: real database/cache/broker/backend/independent-device/frontend startup,
changed browser workflow, persistence/denial checks and service restart. A gate
failure blocks acceptance, not unrelated safe work. Never call local/mock evidence
production verification. Current task: B0 Docker diagnosis and startup proof.

## Historical planning context

## Authority and baseline
- Mission: deliver evidence-backed readiness, not assume existing claims are true.
- Started 2026-10-26; base commit `551db79`. Extensive pre-existing uncommitted migration is preserved. Do not stage/commit it as autonomous work.
- Actual build: Java 21 / Maven wrapper / Spring Boot 3.5.9; root business JAR plus independent `device-comm` JAR; shared sources added by build-helper.
- README/AGENTS now describe independent builds; other historic plans retain older reactor examples. Preserve their safety constraints while following current source/POM paths.

## Execution gates
1. Reconstruct feature inventory and independent architecture/security findings.
2. Establish root and device baselines; explicitly execute opt-in integration suites.
3. Resolve confirmed startup/security/data-integrity blockers test-first; obtain independent review.
4. Exercise actual processes and supported interfaces; do not invent an unspecified frontend or business rules.
5. Validate packaging, migrations, integration workflows and deployment instructions.
6. Fresh adversarial review; RELEASE_READY only if every required gate passes.

## Team and tools
- Direct work by default; owner maximum TWO active subagents. Use bounded independent review/delegation only when its value justifies startup/context cost (D007). Both last agent attempts hit usage limits; resume directly rather than auto-respawn.
- OpenCode v2.0.17. Current model GPT-6.1 Sol; available OpenAI model families discovered through models tool. No cross-family independence claimed.
- Built-in file/shell/browser tools; ECC local plugin. `opencode mcp list`: no configured servers; resource discovery empty. No missing tool installed yet.
- Docker restored locally Oct28; disposable PostgreSQL/Redis/Artemis fixtures run. Daemon availability can change across environment restarts; check before infra tests. Do not substitute mocks for real integration receipts.

## Next bounded durability cycle: FLOW-05
After the current full gate completes, preserve its result before schema changes.
1. Regression journey: a committed alert followed by failed/ambiguous broker publication and redelivery of the same source event must not insert another alert. Confirm a valid failing database-backed test.
2. Use a durable source-event identity with a database unique constraint; do not rely on Redis cooldown or in-memory maps for replay safety. Persist the alert and publication intent in one DB transaction. Add new compatible migrations to both vendor histories; keep applied histories intact.
3. Relay committed publication intents with stable event identity, bounded batches/backoff and database concurrency control. Broker-send success followed by process failure before marking published remains at-least-once; downstream consumers need idempotency, not an exactly-once claim.
4. Verify persistence rollback leaves neither orphan intent nor alert; publication failure retains retryable intent; same-event replay/concurrency yields one alert; restarted relay publishes the original persisted ID/time/payload. Exercise H2 and PostgreSQL, actual Artemis, and disabled-provider-safe notification boundaries.
5. Recheck tenant authority/context restoration, event converter contracts, caches after commit, migrations and full gate. Keep wider telemetry publication atomicity and nondurable topics visible if this bounded fix does not cover them.
