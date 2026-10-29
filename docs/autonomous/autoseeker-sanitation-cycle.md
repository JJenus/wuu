# Autoseeker sanitation cycle — 2026-10-29

## Scope

Owner clarified that Autoseeker devices do not need a newly invented credential scheme. API authentication remains mandatory. This cycle validates incoming telemetry without moving gateway code into the business backend.

## Reproduction and changes

- Initial parser RED:32 cases/29 failures, `/tmp/opencode/tracker-autoseeker-validation-red.log`; extended35-case RED `/tmp/opencode/tracker-autoseeker-validation-red-v2.log`.
- Invalid coordinates/date previously became zero/now; NaN/infinite speed, wrong hemisphere, invalid heading/minutes/calendar dates and control/non-ASCII/oversized frames were not consistently refused.
- D1/S20/SCF response offsets now match repository simulator/documented packet examples. No-GPS/invalid fixes fail instead of inventing a location. This is GPS extraction, not physical command acknowledgment acceptance.
- UTC is the timestamp default, independent of host timezone. Configure `tracking.tcp.autoseeker.timestamp-zone` explicitly for device clocks using another zone; nonexistent/ambiguous DST times are refused rather than normalized/guessed. Parser length follows `tracking.tcp.server.max-message-length` (default1024, validated1–65536).
- Real TCP ASCII decoding reports malformed bytes rather than replacing them with `?`; oversized-frame errors no longer embed untrusted frame contents.
- Socket binding RED: `/tmp/opencode/tracker-autoseeker-binding-red.log` showed malformed telemetry claiming the device routing mapping. Binding/activity now follows successful parser/publication processing, not string extraction.

## Verification and limits

- Initial full device verify passed; independent bounded parser review found no critical/high finding, requested DST/length/compact-coordinate/overflow/truncated-command tests. All added;48 new parser regressions now cover them. Review session `ses_e7b2984b7ffed9ztmP1AQoYJmW`.
- Real socket test covers split and coalesced frames, invalid telemetry no publication/no routing claim, high-bit byte connection rejection, reconnect and valid timestamp publication. Deterministic post-success/malformed/publication-failure tracking tests assert routing/activity only after successful processing. External Redis/JMS adapters are mocked in this socket test; do not claim real broker acceptance from it.
- Review follow-up: raw frame wiretap is now opt-in via `tracking.tcp.server.wiretap-enabled` (default `false`); normal operation does not log device identifiers/coordinates at DEBUG. `wiretap-enabled` and its environment override are documented in `device-comm/src/main/resources/application.yaml`.
- Latest standalone gate `/tmp/opencode/tracker-device-wiretap-test.log`: **91 tests, 0 failures/errors/skips**. This supersedes the earlier 86-test clean run; it is not a new coverage measurement. Prior instrumented86 run `/tmp/opencode/tracker-autoseeker-device-final.log` measured device/shared449/1235 lines (**36.36%**),143/412 branches (**34.71%**), BELOW_TARGET; parser90/144 lines,81/116 branches; TCP server128/180 lines,24/52 branches. Counts are not a coverage completion claim.
- Post-review socket/routing/wiretap changes require independent follow-up. Gateway queue/backpressure, connection identity switching/replacement cleanup races, durable messaging, GT06 binary framing, physical command transport/ACK and whole-release acceptance remain open.

## Next

Review final source, run fresh complete backend/device/vendor gates after subscription compilation ends, persist receipts, commit/push verified scoped source normally. Continue durable authenticated subscriptions and actual-Java frontend acceptance; only ride-hailing is deferred.
