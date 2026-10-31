# Full-stack startup gate

From the independent frontend repository:

```sh
WUU_JAVA_JAR=/absolute/backend/target/tracking-engine-1.0.0-SNAPSHOT.jar \
WUU_DEVICE_JAR=/absolute/backend/device-comm/target/device-comm-1.0.0-SNAPSHOT.jar \
pnpm verify:full-start
```

Build/test the backend and device artifacts first using their documented Maven
commands. Docker and Java21 are required. The gate builds production Nuxt, starts
owned loopback PostgreSQL/Redis/Artemis, business Java, independent device Java
and Nuxt; runs actual browser workflows; stops/restarts all application processes;
checks persisted alerts and reruns the browser; then verifies owned cleanup.
The synthetic device probe must publish a real LocationDataEvent through Artemis.
This is not physical hardware, commands/ACK or owner telemetry persistence proof.

`--full-start` requires explicit artifacts; the older non-full Java command
records PARTIAL, not a full-stack PASS. A stale PASS is invalidated before
preflight. `.verification/last-startup.json` is private, local and Git-ignored;
it includes commits/dirty-tree fingerprints and artifact hashes. PASS requires
all stages and unchanged source/artifacts; early failure or interruption cannot
reuse earlier success. Keep a sanitized outcome in versioned docs after a run.
The gate tears down its owned resources, so success does not leave a demo running.

## First verified run — 2026-10-30

Implementation frontend `8e211f9`, device binding `dfa883f`; actual first receipt
references their tested precommit source fingerprints: frontend HEAD3c062b7 plus
dirty hash441922ac…1cb; backend/device HEAD40d874d plus dirty hash1a3a4ea3…bad.
**FULL_START PASS**, all eight stages, unchanged revisions/artifacts, cold and
post-restart real browser acceptance; `/tmp/opencode/full-start-first.log`,
private `/tmp/opencode/wuu-java-XdYYyz`. Backend JAR SHA256
`e207fcc526d7585bf3985729f70cb591d87bff9ebf6c2b1416e7d89bc180d9af`;
device JAR SHA256 `8ffb2d448b7676ed4c0f064c40bcfb16163a5c2eb17528d9573ef04c65970578`.
Unit141/typecheck/boundaries passed; device91 verify/package passed. Independent
review findings (signal/startup race, surviving old listeners) were fixed and
regression-tested before this run. A device reviewer additionally attempted a
root build despite a no-build request; it timed out and is not extra evidence.

Next feature updates must rerun the gate against their final revisions. B0's
unified verification is delivered; a persistent interactive dev-stack launcher
remains distinct from this disposable acceptance command. No production readiness
or deployment authority is implied.
