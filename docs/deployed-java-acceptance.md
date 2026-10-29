# Disposable actual-Java acceptance — 2026-10-29

## Scope and reproduction

Run from this independent frontend repository:

```sh
node tools/verify-java-integration.mjs
```

Requires existing Java 21, Docker, pnpm dependencies, cached/system Chromium, and the existing `../sidie/target/tracking-engine-1.0.0-SNAPSHOT.jar`. No Maven command, backend edit, backend build, local-account directory, ignored dotenv file, or existing credentials are used. The runner builds Nuxt with `--dotenv /dev/null`, starts production Nitro, and runs only `tests/java/deployed-backend.spec.ts` through `playwright.deployed.config.ts`.

The runner owns fresh PostgreSQL 17, Redis 7 and Artemis 2.40 containers, bound only to randomly mapped IPv4 loopback ports. Database, Redis, broker, JWT, session and account secrets are randomly generated. Public registration is enabled only in this disposable Java process to create two real tenant owners. Vehicle setup uses the actual authenticated Java API, not SQL seed insertion. The frontend's mutation UI stays disabled. Backend health must pass before browser acceptance starts.

Each container has a unique `wuu.fixture` label. Cleanup uses one shared promise across success, failure, SIGINT and SIGTERM, checks the exact label before removing named containers and anonymous volumes, and never prunes shared services. Child process-group leaders are retained after exit so cleanup can signal surviving descendants. The normal-completion process audit passed; signal races and descendants that ignore termination are not yet independently verified. SIGKILL/host failure cannot run cleanup; the printed unique run/receipt identifies recovery targets. Java/Nitro ports are reserved until immediately before launch, reducing but not eliminating a competing-bind race. HTTP readiness alone is not proof of process ownership.

Receipts are retained in a mode-0700 `/tmp/opencode/wuu-java-*` directory (logs mode 0600); their path and the existing JAR SHA256 are printed, never credentials. Treat private failure logs as sensitive: Playwright diagnostics may include form values. Traces, screenshots and videos are disabled. No browser/API acceptance request is mocked. Chromium DNS resolution is restricted to loopback; external map-provider availability is deliberately outside this slice, and fixture vehicles have no location.

## Acceptance assertions

- Both registered accounts have TENANT_ADMIN and distinct organizations; each creates one vehicle via authenticated Java HTTP.
- Actual browser password sign-in reaches a live, one-record fleet and its own vehicle detail, with no fabricated telemetry.
- Each tenant's list excludes the other record; foreign detail returns 404 and renders the explicit “Vehicle not found / not part of the current workspace” state, never preview data.
- Current Java seeds grant TENANT_ADMIN `trips.read`; authenticated trips therefore return 200. A genuinely denied domain is exercised instead: `GET /api/v1/notifications/templates` requires `system.admin` and returns 403, and the `/admin` UI renders “Platform access is unavailable” while permitted trips and fleet views remain available.
- Login response/browser storage/hydration do not expose bearer tokens; the cookie is HttpOnly, SameSite Strict and opaque. Local HTTP intentionally does not prove production Secure/TLS deployment.
- Signing out one alpha browser revokes a second alpha browser and an independently obtained alpha Java token, while beta retains access. The local cookie is deleted.
- A forged cookie cannot access fleet API or protected HTML.

## Execution receipts and current result

**Latest rebuilt-artifact run: PASS**, `/tmp/opencode/tracker-reviewed-browser-gate.log`;
private logs `/tmp/opencode/wuu-java-uWqSb5/`. SHA256:
`a452439bf220fa78a7cbcaaf975348531fb9ded8fbdbad2c6c9da3b2049e6ecb`.
This run includes the final process-group escalation helper. Production build,
Java dependency health and all deployed browser assertions passed; subsequent
container and Java/Nitro/Playwright process audits found no leftovers. The hashes
and receipts below are historical runs, not the latest artifact.

Existing JAR SHA256: `885541c3eb38906a2a41c5367a3c5b9964bad71042568b145e76576a80d47e14`.

- Final reconciled run: **PASS**, `/tmp/opencode/tracker-deployed-reconciled-v2.log`; private logs `/tmp/opencode/wuu-java-ts7025/{build,java,nuxt,browser}.log`. Production build, PostgreSQL/Redis/Artemis health, three browser logins across two owners, HttpOnly/Strict cookie and bearer secrecy, own live fleet/detail, bidirectional foreign list/detail isolation with the explicit not-found state, account-wide revocation across two browsers plus the direct Java token, and forged-cookie denial all passed. The final contract assertions also passed: authenticated trips returned 200, the `system.admin`-only template endpoint returned 403, and the platform guard rendered “Platform access is unavailable” while fleet access remained available.
- Earlier diagnostic runs (`tracker-deployed-run.log`, `tracker-deployed-run-2.log`, `tracker-deployed-run-3.log`, `tracker-deployed-run-4.log`) exposed and corrected an obsolete role assumption, a Chromium full-navigation response-body race, and a generic-vs-explicit 404 copy mismatch. They are historical evidence only; they are not the current result.
- `pnpm test:coverage`: **91/91 tests, 14 files PASS**; selected-source coverage **97.14% lines/statements, 87.86% branches, 96.47% functions**. Receipt: `/tmp/opencode/tracker-deployed-coverage.log`. Not whole-UI coverage.
- `pnpm check:boundaries`: PASS (Vue HTTP/import/surface rules).
- `pnpm typecheck`: PASS, `/tmp/opencode/tracker-deployed-typecheck-final.log`.
- `pnpm format:check`: PASS, `/tmp/opencode/tracker-deployed-format-final.log`; formatting and typecheck were rerun after the final test/document changes.
- Post-lifecycle-hardening run: **PASS**, `/tmp/opencode/tracker-deployed-lifecycle-v3.log`; private logs `/tmp/opencode/wuu-java-76xX5K/`. The runner now shares one cleanup promise across success/failure/signal paths, retains exited group leaders until cleanup so surviving descendants are still signalled, and holds each loopback port reservation until immediately before launch. The same browser acceptance passed, label-checked cleanup removed all owned containers, and a process audit found no remaining Java/Nitro/Playwright descendants.
- Label-checked cleanup completed; the reconciled run's cleanup finished without removing shared services, and the JAR SHA256 remained identical. `git diff --check` passed. The acceptance run itself made no backend edits or builds and did not commit/push; this evidence file is published separately.

## Coordinator handoff

### Cleanup review follow-up

The normal-run receipt above predates the final process-group escalation fix.
Review found that SIGKILL still depended on the wrapper's exit status. The runner
now uses `tools/fixture-lifecycle.mjs`, which escalates against every owned group.
`node --test --experimental-test-coverage --test-coverage-include='tools/fixture-lifecycle.mjs' tools/fixture-lifecycle.test.mjs`
passed **4 tests**, with **100% helper line/branch/function coverage**, including
a real SIGTERM-resistant descendant whose wrapper already exited. Receipt:
`/tmp/opencode/tracker-fixture-cleanup-green.log`. The initial RED was an intended
missing helper import (`31c0ebf`); GREEN implementation/test checkpoint is
`fb7af6d`. This Node infrastructure test is separate from the Vitest/UI coverage.
A fresh complete fixture run against the final rebuilt backend passed; see the
latest receipt above. Normal completion and the helper's TERM-resistant orphan
case are verified; whole-run signal races remain unverified.

The Java role seed is the current contract: TENANT_ADMIN may read trips and vehicles but never receives `system.admin`. Keep the genuine denial assertion on a system-admin-only domain; do not remove assertions or rewrite permission rows to manufacture a 403. The single command above now includes the hardened cleanup/port lifecycle and passed. This remains a bounded local acceptance slice, not independent release certification.

Self-review (not independent acceptance): accuracy **4/5** (artifact hash and exact reconciled receipt, but no source/artifact equivalence proof); completeness **4/5** (real tenant isolation, session revocation, genuine system-admin denial; writes, expiry/refresh, TLS and production deployment remain unverified); clarity **4/5** (explicit commands and historical failures separated from the current result); actionability **4/5** (one reproducible command); conciseness **4/5**. Overall **4.0/5**. Highest-impact improvement: obtain independent coordinator review.

## Limits and next slice

This is a bounded local deployment slice, not release readiness or a comprehensive security audit. It does not test expiry, refresh, production ingress/TLS, arbitrary role combinations, tenant switching, external notifications, device transport, telemetry/history, writes through the frontend, subscriptions/billing or ride-hailing. The existing artifact's hash identifies what ran; this does not certify that the JAR matches every current source change. Existing scope/API/access/progress documents contain historical October 23/25 non-verification statements; this dated evidence supersedes them only for assertions actually exercised here.

Independent coordinator review is required before committing. Existing unrelated uncommitted frontend work is preserved and is a dependency of this run, not part of this slice's authorship.
