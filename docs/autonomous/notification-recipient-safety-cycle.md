# Notification recipient safety — 2026-10-28

## Intent and scope
Never invent an administrator recipient, email address, phone number, device token or external channel address. Recipient/fan-out policy remains unspecified; this change refuses unsupported routing rather than inventing a replacement policy. External providers remain disabled/unimplemented as previously recorded.

## Evidence
- `NotificationOrchestratorTest`: six runtime RED failures, zero errors, in `/tmp/opencode/tracker-notification-recipient-red.log`. Alert processing published a fabricated `admin` intent; enabled external channels accepted fabricated/unsupported destinations.
- Fix: alert-to-recipient fan-out throws an explicit missing-policy exception. EMAIL/SMS/MOBILE_PUSH/SLACK/TEAMS require an actual mapping and currently fail explicitly. All enabled channel destinations are checked before hub/delivery persistence. Existing IN_APP/WEBSOCKET user-ID behavior is unchanged, not newly accepted as tenant-safe.
- Listener runtime RED: one failure while six orchestrator tests passed, `/tmp/opencode/tracker-notification-policy-listener-red.log`. The notification listener swallowed that error. It now propagates the original runtime exception to JMS rather than acknowledging failed processing.
- Focused orchestrator/listener/queue/preference/dispatcher batch passed: `/tmp/opencode/tracker-notification-policy-green.log`.
- Bounded read-only review `ses_e7e6027e2ffe5sw6UTs6X1Q2sC`: no new critical/high defect; requested real broker retry/terminal retention coverage. Added `PostgresDeploymentIT.notification_missingRecipientPolicy_redeliversAndRetainsOriginalEventInBrokerDlq`: actual listener invoked multiple times, original event retained in broker DLQ, zero notification hubs for that alert. First fixture attempt did not compile (checked JSON exception); second reached successful broker assertions but failed its final assertion on an incorrect SQL column name. Corrected fixture to actual `alertid` column; final full gate passes. These fixture failures are not production-defect RED receipts.
- Clean instrumented `tools/verify-release.sh`: **backend644/device37, zero failures/errors/skips**, both executable JARs built. `/tmp/opencode/tracker-notification-final-full-gate.log`.
- Fresh JaCoCo execution data: `/tmp/opencode/tracker-notification-final-coverage.exec`. Whole-bundle backend4879/7086lines (**68.85%**),950/1772branches (**53.61%**); device/shared476/1280lines (**37.19%**),70/362branches (**19.34%**). Below target; lower coverage after refusing formerly fabricated notification paths is reported without exclusions or inflation.
- Current-source independent executable TCP→backend→alert/outbox/owner-vs-foreign HTTP smoke passed: `/tmp/opencode/tracker-notification-final-socket.log`. It does not assert recipient/provider delivery.

## Remaining limits
Broker retry and DLQ retention here use the disposable Artemis fixture's settings. Production retry/DLQ configuration, monitoring and replay must be verified before deployment. Non-durable topic subscriptions still do not promise recovery across disconnects. Missing recipient policy intentionally blocks alert notifications; initial dispatch, tenant/member worker validation, downstream idempotency and external provider acceptance remain open.

User explicitly authorized committing all accumulated source/consolidation/tests/docs and pushing normally to origin/main. This is source publication, not deployment or RELEASE_READY approval. RED/GREEN evidence is preserved here rather than retroactively inventing checkpoint commits.
