# Requirements inventory and design reconciliation

**Last updated:** 2026-10-26. Companion to [FEATURE_MATRIX.md](FEATURE_MATRIX.md).

## Authority, evidence and completeness rules

1. Current mission/master plan authorizes evidence-backed assessment and defect repair, preservation of existing work, and the independently deployable device boundary. It does **not** approve every proposed product in every design document.
2. Existing APIs/domain behaviors are inventory obligations, not proof their behavior satisfies an approved specification. Matrix rows give backend, frontend, DB, API, auth, validation/errors, tests, dependencies and remaining acceptance evidence.
3. Auth/notification/subscription/ride design documents are retained as **unapproved design hypotheses** wherever implementation and explicit acceptance decisions are absent. Approval/defer/rejection must be recorded by the owner; no exclusion is inferred from missing code. Proposed acceptance below is not authorization to implement new business rules.
4. There is no frontend source or approved UX specification. For **every requirement below**, frontend is `NOT_STARTED`, UI/API interaction details are undecided, and browser E2E evidence is absent. Do not invent maps, dashboards, customer portals, payment forms or native apps.
5. Fresh coordinator baseline is root 489/device 36 passed, not feature E2E. No tests executed by this documentation pass. Historical verification reports are source provenance only. Current parallel repairs are not accepted fixes.
6. Status vocabulary is exactly `NOT_STARTED`, `PARTIAL`, `IMPLEMENTED_UNVERIFIED`, `TESTING`, `FAILED`, `VERIFIED`, `BLOCKED`. No requirement is VERIFIED by this pass.

## Source ledger (all repository design/planning Markdown reviewed)

| Source | Coverage retained / reconciliation |
|---|---|
| `README.md` | Real-time GPS/state/trips, GT06/Autoseeker, speed/geofence/idle, fuel control/safety/queue, event/error/scale claims → TRACK, DEV, CMD, ALERT, OPS. Old reactor/build examples are not executable truth. |
| `AGENTS.md` | Device independent-deployment constraint, tenant/cache seams, event semantics and verified workflow warnings. Structural paths are stale relative to current root source migration. |
| `SESSION_PLAN.md`, `TODO.md` | State persistence/dedup/disconnect/dwell, auth, retries/DLQ, CSV, metrics, tracing/correlation; contradictory completed/deferred checkmarks do not override source or evidence. |
| `auth-authz-system-db-design.md` | Identity/session/refresh/RBAC/organization/login audit principles → AUTH-01–09 and AUTH-X01–05 below. |
| `subscription-system-db-design.md` | **All §§1–8 retained** as SUB-01–12, including tax boundary and unresolved product choices. |
| `plan-docs/ride-hailing-complete-design.md` | **All §§1–11 retained** as RIDE-01–16; no ride workflow silently conflated with vehicle-tracking trips. |
| `plan-docs/ride-billing-driver-payout-design.md` | Both money directions, holds/capture/refunds/collections/earnings/batch/review → RIDE-10–16; cross-document timing ambiguity retained below. |
| `plan-docs/notification-system-db-design.md` | Hub/delivery/events, preferences/mandatory/opt-in, devices, typed failures/backoff, queue/order/DLQ/callbacks → NOTIFY-01–07 and NOTIFY-X01–03. |
| `plan-docs/alert-rule-cleanup-verification.md` | Single management seam, tenant batching/association reassignment, caches → ALERT-01/07; historical tests not release certification. |
| `plan-docs/alert-rule-caching-verification.md` | Tenant query shapes/keys/TTL/invalidation/missing index → ALERT-07. |
| `plan-docs/alert-geofence-caching-verification.md` | Alert bulk mutation/statistics and geofence pages/associations/outage → ALERT-03/06/07. |
| `plan-docs/real-redis-caching-verification.md` | Real Redis serializer/proxy/transaction evidence and limits → ALERT-07/OPS-02, fresh rerun required. |
| `SECURITY.md` | Active security configuration, denied callbacks/status routes, session migration, transport limitations, vendor profile; historical 514/Docker receipt is not today's 489/36 baseline. |
| `TESTING.md`, `TESTING_GUIDELINES.md` | Required layers/testing conventions and coverage targets, not measured or enforced coverage. `*IT` requires explicit run. |
| `docs/autonomous/MASTER_PLAN.md` and sibling status/audit/queue/report/decision/readiness documents | Execution authority and live gate context; no readiness claim is imported as acceptance evidence. Scope of this pass is only this inventory and matrix. |

## Current implementation reconciliation

See the matrix for individual existing features. Concrete inspected route families are `/api/v1/auth`, `/api/v1/users`, `/api/v1/organizations`, `/api/v1/vehicles`, `/api/v1/trackers`, `/api/v1/trips` (including `/export`), `/api/v1/commands`, `/alerts`, `/alerts/rules`, `/alerts/geofences`, `/api/notifications`, denied `/api/notifications/webhooks`, and authenticated `/ws/notifications/**`.

- Existing DB: users, identities, organizations, roles, permissions, user_roles, role_permissions, sessions, refresh_tokens, login_attempts; vehicles, trackers, tracker_assignments, tracker_location_data, tracker_raw_data, trips, trip_points, device_commands; alert_rules, geofences, geofence_points, tracker_alerts; notification_hubs, notification_preferences, notification_templates, devices, deliveries, delivery_events (plus association/junction tables). Exact DDL is owned by the dual vendor migrations, not these summaries.
- Entities for raw packets, assignments, social identity or push devices do **not** prove an ingestion/history/account-linking/device-registration workflow exists. No standalone public location-history/raw-packet/assignment-history management controller was located; determine intended access before inventing one.
- Known failures pending independent repair verification: tenant context across telemetry JMS, duplicate alert evaluation/rule-key alert identity, vehicle/tracker create with supplied existing ID, provider false-success. Alert notification orchestration uses hardcoded admin/fabricated contacts and does not initially dispatch its pending deliveries. Device standalone launcher is under active test. Fuel-cut local listener versus remote broker transport still needs actual cross-process acceptance.
- Test layers located: domain/service/controller tests, H2/security/session integration, tenant cache proxy tests and opt-in repository/Flyway/real-Redis/PostgreSQL deployment ITs. No frontend suite; no complete real TCP→broker→DB→alert→provider workflow receipt.

## Design-only contract inherited by every row below

Unless a row explicitly says otherwise: **backend/use case absent; API absent/not specified; database tables are proposals only, no feature migration found; frontend NOT_STARTED; auth/tenant policy and error response contract unapproved; validation rules listed are proposed invariants, not implemented validation; tests and acceptance execution absent**. Status is NOT_STARTED, even when an existing identity/entity shares a name. Each acceptance cell is a candidate for owner review. Financial/provider work additionally depends on credentials, sandbox contracts, reconciliation and secure webhook policy; no real-money action is authorized.

### Auth extensions

| ID / proposed feature | Status | Backend / DB / API distinction | Proposed auth, validation/errors, dependencies and acceptance |
|---|---|---|---|
| AUTH-X01 OAuth social sign-in + explicit account linking/unlinking | NOT_STARTED | Identity entity/repository exists; no exchange/callback/link API found; design §4 provider UID mapping | Choose provider and confirmation policy; never auto-merge on email; test UID uniqueness/email changes, explicit existing-credential confirmation, account takeover, callback state/replay and unlink last credential. |
| AUTH-X02 Provider token retention/encryption | NOT_STARTED | Design identities token columns are not an approved encryption/integration workflow | Login-only should discard provider tokens; API access would require encryption/key rotation/revocation scopes; test DB/log secrecy and revoked grant; depends AUTH-X01. |
| AUTH-X03 Anomaly detection/new device or country alerts | NOT_STARTED | Login attempts exist, not geo/anomaly engine or notification workflow; auth §6 | Define trusted IP/geo privacy/false positive policy; test unknown devices/failure→success patterns and tenant-safe alert delivery; notification dependency. |
| AUTH-X04 Audit retention and durable security events | NOT_STARTED | login_attempts exists; no approved partition/pruning/security_events workflow | Define retention/legal constraints and access rights; test pruning preserves exceptional events and operational lockout windows; DB scheduler dependency. |
| AUTH-X05 Role-management extension and security-account follow-ons | NOT_STARTED | Role/permission entities and user role assignment exist; no general role/permission CRUD routes. Password reset, verification and MFA are not implemented; MFA/password events are mentioned only as audit examples | Owner must choose actual workflows (mention is not approval); prevent privilege escalation/global-role mutation; define recovery/proof/rate limits and errors, then tests. Do not infer password-reset delivery from mandatory-template examples. |

### Subscription design (separate from ride billing)

| ID / proposed feature | Status | Proposed backend / database / API scope | Proposed auth, validation/errors; dependencies; candidate acceptance |
|---|---|---|---|
| SUB-01 Payment methods and customer ownership | NOT_STARTED | Customer payment-method use cases; payment_methods/user links; routes unspecified | Define owner/tenant scope and gateway tokenization (never store card secrets); invalid/default/deleted method errors; sandbox token/default-method tests. |
| SUB-02 Plan catalogue/lifecycle | NOT_STARTED | plans active/deprecated/archived; catalogue administration | Define billing-admin rights; FK restrict referenced deletes, block new signup on deprecated plan; prove history remains and delete only never-used plans. |
| SUB-03 Price versions/grandfathering | PARTIAL | **V5 immutable plan-version catalog implemented** with exact price, model/cadence, unique `(plan_id, version)`; subscriptions pin a plan-version FK | No effective dates, deprecation/migration or repricing; no cohort billing test. See subscription-enrollment-slice.md. |
| SUB-04 Entitlements/overrides/metering | NOT_STARTED | plan_features, subscription_features, usage_records | Define hard-limit block vs soft warning, atomic usage and override authority; type/window/overage errors; concurrent usage enforcement and override precedence. |
| SUB-05 Subscription creation/trial/activation | PARTIAL | **Authenticated durable `PENDING` enrollment implemented** with tenant/user ownership and operation-key idempotency; no trial/activation route | Trial/paid conversion, entitlement checks and configured grants remain absent; pending enrollment grants nothing. See subscription-enrollment-slice.md. |
| SUB-06 Renewals/dunning/recovery | NOT_STARTED | subscriptions periods/retry_count/next_retry_at; background billing scheduler | active→past_due→active/canceled; configured schedule unapproved; gateway failures/backoff, no duplicate renewal, crash recovery/exhaustion; depends invoice/payment use cases. |
| SUB-07 Cancellation and transition audit | PARTIAL | **Tenant/user-scoped immediate, idempotent cancellation to terminal `CANCELED` implemented**; original POST receipt retained | No period-end cancellation, transition-event audit, reactivation/return flow or boundary-time tests. See subscription-enrollment-slice.md. |
| SUB-08 Invoice numbering/line-item ledger | NOT_STARTED | invoices + invoice_line_items; invoice read/issue workflows | Sequential unique invoice_number, unique retry key; immutable financial history, sum amounts/currency, no missing/duplicated issue; accounting jurisdiction rules need approval. |
| SUB-09 Upgrade/proration/downgrade scheduling | NOT_STARTED | Signed credit/charge invoice items, version update; pending_plan_id/change_at | Cycle reset vs retain and immediate vs next-bill collection unresolved; approve rounding/currency policy; reproduce design $6.67 credit/$20 charge, month edge cases and deferred changes. |
| SUB-10 Gateway payments/idempotency/reconciliation | NOT_STARTED | payments gateway IDs/payload/fee/net + invoice/method; gateway callbacks | Define provider signatures/replay/privacy, unique invoice/payment keys; reject mismatched amounts/currency and handle duplicate events; prove one charge, fee/net reconciliation and recovery after timeout. |
| SUB-11 Coupons/redemption ledger | NOT_STARTED | coupons, subscription_coupons, coupon_redemptions | Active/date/global+per-user limits, atomic conditional increment and rollback in transaction; disabled/exhausted/expired failures; concurrent final-slot tests, no redemption lost on failed transaction. |
| SUB-12 Tax service boundary | NOT_STARTED | Provider tax output as invoice item, not local rate engine | Choose tax provider, jurisdictions/exemptions, dedicated tax vs one_off item; secure customer address handling; sandbox totals/rounding/outage reconciliation. Design explicitly leaves this external. |

### Ride-hailing and money movement

| ID / proposed feature | Status | Proposed backend / database / API scope | Proposed auth, validation/errors; dependencies; candidate acceptance |
|---|---|---|---|
| RIDE-01 Rider/driver identity, vehicles, ride categories | NOT_STARTED | users phone/default_currency; drivers 1:1 user; vehicles driver/ride_type/active; ride_types pricing/capacity | Current tracking vehicles are not this model; define rider/driver/admin permissions, identity mapping and category eligibility; prevent foreign-driver vehicle mutation. |
| RIDE-02 Driver document compliance | NOT_STARTED | driver_documents license/insurance/registration/background check; derived driver status | Verified and unexpired required documents gate active status; suspend/expiry/rejection workflow; test clock expiry immediately removes eligibility and auditable reviewer access; file storage unspecified. |
| RIDE-03 Idempotent ride request | NOT_STARTED | rides rider/pickup/dropoff/type/requested_at/client key | Validate coordinates/type/rider ownership and collections eligibility; unique retry key and payload mismatch behavior; concurrent repeated mobile request yields one ride; depends RIDE-01/12. |
| RIDE-04 Nearby driver availability/matching | NOT_STARTED | driver_locations current upsert, online/on_trip/offline, geo index; dispatch service | Define geospatial engine/PostGIS vs Redis geo, freshness/radius/eligibility and matching race policy; tests exclude stale/unverified/busy drivers and avoid double-assignment. |
| RIDE-05 Dispatch offers/accept/decline/timeouts | NOT_STARTED | dispatch_attempts offered/responded/response, rides assignment | Driver-only own offer acceptance; reject late/duplicate acceptance, timeout audit; concurrent offers choose one driver/vehicle and preserve every attempt. |
| RIDE-06 Ride lifecycle/cancellation/audit | NOT_STARTED | rides requested→matched→arriving→in_progress→completed, early canceled; ride_events actor/reasons | Participant/actor-specific transitions and system timeout policy; invalid/out-of-order transitions denied; append audit and prove no double completion/cancellation fee ambiguity. |
| RIDE-07 Live ride GPS/history | NOT_STARTED | ride_locations trail, driver location stream; retention/partitioning proposal | Participant/tenant read policy, coordinate/time validation, stale/reordered points; live transport unspecified; trail/stream privacy and partition/retention load acceptance. Tracking telemetry does not fulfill this. |
| RIDE-08 Multi-stop rides and saved places | NOT_STARTED | ride_stops ordered/arrived_at; saved_places user/label/location | Own places/participant stop edits, sequence uniqueness, invalid coordinates/order errors; test stop arrivals/reordering and isolation. |
| RIDE-09 Two-way ratings | NOT_STARTED | ratings ride/rater/ratee/score/comment; computed average | Define scoring range, eligible participant/completed ride, duplicate policy and moderation (not specified); test both directions and derived averages without hand-maintained counter drift. |
| RIDE-10 Fare estimate/final ledger/currency | NOT_STARTED | ride_types rates/capacity, fare_line_items base/distance/time/surge/promo/toll/tip/cancellation/platform fee | Snapshot request-time surge; append signed line items; consistent explicit currency/rounding; approve cancellation/fee/surge formula; tests reconstruct final price independently from estimate. |
| RIDE-11 Promotions | NOT_STARTED | promotions + promo_redemptions ledger | Date/status/global/per-user limits and atomic counter guard; actor/tenant policy; no overshoot under concurrency or duplicate mobile requests; integrate discount line item. |
| RIDE-12 Rider methods/hold/capture/unpaid collections | NOT_STARTED | payment_methods token/last4; payments authorized/captured amounts/currency/idempotency/gateway IDs; background retry/collections flag | Hold once matched, finalize/capture completed ride total; authorization timing differs in schema notes (request time) vs flow (match time): owner decision required; over-hold policy/provider capabilities not assumed; prove one charge/retry recovery and no inline retry storm. |
| RIDE-13 Refunds/support workflow | NOT_STARTED | refunds payment/amount/reason/requester/status; additive ledger | Rider/support/system permissions and approval policy unspecified; never overwrite historical charge; reject excess/duplicate refunds; sandbox partial/full/denied/refunded reconciliation. |
| RIDE-14 Driver earnings/commission accrual | NOT_STARTED | driver_earnings gross/commission/net/payout link | Design says driver owed independent of rider decline, but procedural accrual is after successful capture: resolve contradiction before coding; validate one earning per ride and net math/currency; define access/reversals. |
| RIDE-15 Payout batch/transfer/review/reconciliation | NOT_STARTED | payouts period/total/status/gateway_transfer_id/transferred_at; earnings claimed by payout_id | Define payout operators/bank onboarding; atomic batch claims, no double counting; failed transfer held_for_review retries same payout, never rebatches earnings; test concurrent schedulers/timeout/review/recovery with sandbox. |
| RIDE-16 SOS and expiring trip sharing | NOT_STARTED | sos_events actor/trigger/resolution/notes; trip_shares random token/contact/expiry | Define safety escalation/operators/privacy; participant trigger, unguessable least-data public share, completion/expiry revocation; test wrong ride/expired/replayed link and SOS response workflow (not merely row creation). |

### Notification design deltas

| ID / proposed feature | Status | Backend / database / API distinction | Proposed auth, validation/errors; dependencies; candidate acceptance |
|---|---|---|---|
| NOTIFY-X01 Opt-in categories and reachable mandatory fan-out | NOT_STARTED | Existing resolver defaults every missing channel preference enabled; design marketing default disabled is not implemented | Approve category policy and reachability rules; prove missing marketing preference emits no delivery, explicit opt-in works, mandatory messages bypass preference only for valid destinations; depends NOTIFY-01/04. |
| NOTIFY-X02 Push-device registration/rotation/revocation | NOT_STARTED | Device entity/repository exists; no dedicated registration API/controller found | Choose registration/token ownership/duplicate/expiry policy; own devices only, never leak tokens, invalid token suppression; tests rotation, logout/revocation, multi-device fan-out and hard-invalid feedback. |
| NOTIFY-X03 Ordered/idempotent queued fan-out/recovery | NOT_STARTED | Consumer/retry/DLQ implementation exists; complete design per-user ordering and durable no-double-processing guarantee not demonstrated | Define dedup/message key/outbox or equivalent consistency policy; tests same-user concurrent messages/preferences, crash before/after commit/provider send, queue redelivery vs scheduler race, DLQ replay without duplicate send. |

### Other README future hypotheses

The same design-only inherited contract applies: no backend/API/schema/frontend/test implementation is asserted and no owner approval inferred.

| ID / proposed feature | Status | Proposed contract/dependencies/acceptance |
|---|---|---|
| FUT-01 ML anomaly detection | NOT_STARTED | README long-term idea, distinct from login anomaly detection; define signals/model/data privacy, false-positive budget and labeled evaluation before designing persistence/API. |
| FUT-02 Predictive maintenance | NOT_STARTED | Define vehicle health inputs, prediction horizon/maintenance rules and alert recipients; validate against agreed maintenance outcomes, not generic speed alerts. |
| FUT-03 Fuel consumption analytics | NOT_STARTED | Fuel-cut status is not fuel measurement; define sensor calibration/units/aggregation and tenant report contract; test known fuel/distance fixtures and missing data. |
| FUT-04 Driver behavior scoring | NOT_STARTED | Define lawful driver identity/telemetry mapping, scoring formula, appeal and permission policy; no rating/scoring model or UI approved; test deterministic agreed metrics. |
| FUT-05 Configuration management/scaling alternatives | NOT_STARTED | README future configuration system and separate-service/Kafka/RabbitMQ/database alternatives are proposals, not mandated architecture. Select actual configuration/admin audit and availability/load requirements first; independent device constraint remains mandatory, business-module collapse is not authorized. |

## Acceptance ledger and unresolved approvals

| Gate / question | Current status | Required evidence or decision |
|---|---|---|
| Feature-specific runtime verification | IMPLEMENTED_UNVERIFIED | For each implemented row: authenticated success, denied/invalid/missing inputs, persisted state, emitted/consumed messages, error/failure/restart behavior; record exact command/artifact/log/assertion, not an aggregate count. |
| Backend/database vendor deployment | BLOCKED | Latest SESSION_STATE/TEST_REPORT supersede initial unavailable-Docker note: local Docker restored, but PostgreSQL suite hit Artemis readiness timeout (six context errors, assertions not reached). Require fresh successful disposable PostgreSQL/Redis/Artemis migration/startup/process evidence; no historical receipt substitution. |
| Security/data-integrity repair acceptance | TESTING | Other owners' tenant telemetry, POST takeover, provider honesty and launcher work require new regression + independent review; no completion assumption in this inventory. |
| Frontend delivery scope | NOT_STARTED | Owner must specify whether a frontend is required, supported screens/client platforms and interaction acceptance; source/UI absent today. |
| Auth/subscription/ride design approval | NOT_STARTED | Explicit approve/defer/reject each proposed feature; choose integration boundaries/schema names/tenant ownership, resolve noted financial contradictions and unspecified formulas/permissions. Keep proposals in inventory until decision. |
| Tracing/Prometheus/device security/availability guarantees | NOT_STARTED | Agree desired deployment/security/observability SLA; device TLS/auth, distributed rate limit, tracing and public Prometheus exposure are not proven existing features. |

### Verification protocol

- Root baseline command: `./mvnw -B test`; independent device: `./mvnw -B -f device-comm/pom.xml test`. Their reported current totals are 489/36, not a guarantee future parallel changes retain those counts.
- Explicitly select opt-in integration classes and required owned services. `*IT` files are not default-discovered acceptance. Preserve disabled HTTP callback and command-status protections while testing internal seams.
- No coverage percentage, frontend behavior, real provider send, physical tracker actuation or full telemetry pipeline is asserted. No production edits, external sends, financial operations or commits are part of this documentation pass.
