# Notification provider safety cycle

## Scope and findings

Task-derived guarantee: an enabled but unimplemented SMS/push provider must never
report successful transport or create sent delivery state/events. No providers,
credentials, recipient policy, root YAML, Email, WebSocket, or dispatcher production
code were changed. Existing migration/user work was preserved; no commits created.

The current source layout is `src/main/java/com/surf/tracker/notification/`, not
the historical notification Maven module described by AGENTS.md.

Before the fix both service stubs logged “would be sent”, called
`delivery.markSent()`, and returned `DeliveryResult.success()` without any provider
transport. The dispatcher consequently persisted `SENT`, a sent timestamp, and a
`SENT` delivery event. Neither these services nor the dispatcher currently has a
sent metrics collaborator/increment; tests cover the actual sent state and event
side effects rather than inventing a metrics implementation.

## Minimal implementation

- Enabled SMS/push now return permanent `Unsupported ... provider` failures for
  every provider value, including configured examples, unknown, `none`, and blank.
- Neither service mutates the delivery; `isAvailable()` is always false because
  no provider is implemented.
- Disabled behavior remains unchanged: permanent `... notifications are disabled`
  failure, unavailable, no sent state, and existing channel names preserved.
- Removed unreachable stub push error classification, not a real provider adapter.

## Tests and exact evidence

Tests added:

| Class | Cases | Guarantee |
| --- | ---: | --- |
| `SmsNotificationServiceTest` | 6 | Four unsupported providers fail permanently without sent state; configured service unavailable; disabled contract unchanged |
| `PushNotificationServiceTest` | 6 | Same guarantees for push |
| `NotificationDispatcherProviderIntegrationTest` | 2 | Real SMS/push services through dispatcher persist FAILED only, no sent/delivered timestamp or SENT event, and no retry for permanent configuration failure |

Service tests use a minimal Spring ApplicationContextRunner for real `@Value`
injection, not reflective private-field writes. Dispatcher tests wire real service
beans and mock external repository/channel collaborators; clock is fixed. No
network, broker, database, random IDs, or sleeps are required.

RED command:

```sh
./mvnw -B -Dtest=SmsNotificationServiceTest,PushNotificationServiceTest,NotificationDispatcherProviderIntegrationTest test
```

Executed before production changes: **14 run, 12 failures, 0 errors, 0 skipped**.
The two disabled tests already passed. Enabled sends incorrectly returned true,
configured availability returned true, and dispatcher assertions found SENT rather
than FAILED with sent timestamps/SENT events. Log:
`/tmp/opencode/notification-provider-red.log`.

GREEN command:

```sh
./mvnw -B -Dtest=SmsNotificationServiceTest,PushNotificationServiceTest,NotificationDispatcherProviderIntegrationTest surefire:test
```

**14 run, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
Log: `/tmp/opencode/notification-provider-green-focused.log`.
This runs the tests compiled during RED against production classes successfully
recompiled after the fix. It deliberately bypasses unrelated test compilation.

Full lifecycle rechecks using the RED command were not green:

1. `/tmp/opencode/notification-provider-green.log`: unrelated concurrently added
   AlertEventHandlerTest/VehicleEventHandlerTest fail testCompile due to missing
   telemetry tenant constructor/methods. Production compilation succeeded.
2. `/tmp/opencode/notification-provider-green-recheck.log`: timed out after 120s
   while compiling tests; no test result claimed.
3. `/tmp/opencode/notification-provider-green-final.log`: unrelated telemetry
   test compilation failures persist, plus missing target class files during
   concurrent workspace changes/builds. No source outside owned scope was repaired.

No full-suite pass or coverage percentage is claimed. Re-run the lifecycle command
after orchestrator work is synchronized. Coverage instrumentation was not run.

## Read-only recommendations for orchestrator

1. Root `src/main/resources/application.yaml` places settings beneath
   `spring.notification`, but services read `notification.*`. Move to the correct
   root namespace centrally; do not treat YAML example `twilio`/`firebase` values
   as implemented support. Email similarly uses the root namespace.
2. `NotificationDispatcher.handleFailure` invalidates a MOBILE_PUSH device for
   **any permanent error** when a device ID is present. Unsupported-provider (and
   disabled-channel) failures describe server configuration, not bad recipient
   tokens. This patch does not invent recipient policy or modify the dispatcher;
   orchestrator must prevent configuration errors from invalidating valid devices.
   The dispatcher regression intentionally has no device ID and does not claim
   device-invalidation coverage.
3. Email actually calls JavaMailSender before marking sent; WebSocket checks
   connection authorization and actual handler send result before marking sent.
   These were inspected read-only, not changed or independently provider-tested.

## Self-check

Accuracy 4/5: runtime RED/GREEN proved, but full lifecycle currently blocked.
Completeness 4/5: requested fail-closed contract covered; no coverage measurement.
Clarity 4/5: exact commands/results and compile limitation recorded separately.
Actionability 4/5: rerun command and configuration/device-policy follow-ups supplied.
Conciseness 4/5: detailed evidence retained here rather than in the handoff.
Overall 4.0/5. Highest-value follow-up: synchronize concurrent builds, rerun the
normal lifecycle target, then address configuration-error device invalidation.
The orchestrator should agree this is a focused fix, not a full notification audit.
