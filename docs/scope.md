# Scope and acceptance contract

## Owner-directed scope continuation — 2026-10-29

The owner's autonomous-delivery directive includes configurable subscriptions;
only ride-hailing is deferred. The subscription owner UI now covers configured
catalog/owner reads, pending enrollment and confirmed cancellation, not paid
billing. See `subscriptions.md`. The older future-scope/exclusion wording below
does not defer these now-authorized subscription use cases. Production
deployment, payment evidence and external-provider authorization remain gated.

## Product

Wuu is a calm fleet-operations workspace for customers/businesses and a separate platform-administration experience. One Nuxt application, feature-first modules, semantic design tokens, pnpm only. No React.

## Current delivery: preview workspace and live read-only seam

| Capability           | Acceptance condition                                                                                                                                               | State                                                      |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------- |
| Data modes           | Preview is the default. Live mode is explicit. Live failures remain errors and never become fixtures.                                                              | Implemented; tested fail-closed                            |
| Authentication       | Live sign-in validates backend identity; encrypted HttpOnly session, Origin-protected logout, expiry/reauthentication, and guarded routes. Preview remains public. | Implemented; contract-tested, not Java-deployment verified |
| Overview             | Counts derive from the active fleet records and current alert state in both modes.                                                                                 | Implemented                                                |
| Fleet                | Search by vehicle/registration/driver/location; filter status; route to details. Live friendly names/drivers are never invented.                                   | Implemented                                                |
| Vehicle details      | Show available telemetry, geofence membership, alerts, and trips; label missing values explicitly.                                                                 | Implemented                                                |
| Vehicle addition     | Validate required fields and duplicates; session-only preview confirmation. Live creation stays disabled until a verified tracker/ID contract exists.              | Preview only                                               |
| Map                  | Real geographic tiles, projected markers, selection, clustering, gestures, search, fit/reset and provider errors.                                                  | Leaflet + markercluster                                    |
| Tracking movement    | Opt-in simulated moving markers, trail, follow, pause/reset and speed/status filters.                                                                              | Labeled simulation in both modes; not live GPS             |
| Trip replay          | Explicit illustrative paths with play/pause/restart, seeking, speed and follow.                                                                                    | Preview only; not recorded GPS                             |
| Alerts               | Preview active → acknowledged → resolved and updated overview counts. Live list/detail are read-only because actor/review-note contracts are absent.               | Preview mutations; live read-only                          |
| Trips                | Search and detail from the active service; filtered client-side CSV (`wuu-preview-trips.csv` / `wuu-trips.csv`).                                                   | Implemented; no server export endpoint                     |
| Notifications        | Preview alert list and session-only preference switches. Live notification forwarding is allowlisted at the BFF, but no live preferences contract is consumed.     | Preview UI; live unavailable                               |
| Geofences            | Preview circle/polygon creation/editing and assignments. Live list/detail are read-only because updates can lose geometry/assignments.                             | Preview editor; live read-only                             |
| Team, organizations  | Preview sample members. Live team management fails closed instead of showing fictional people. Admin organizations remain a concept preview.                       | Preview proof; live unavailable                            |
| Roles                | Four code-derived presentation templates and proposed-role register.                                                                                               | Read-only concept; not effective permissions               |
| Platform             | Separate nav, capability inventory, security gates, sample data only.                                                                                              | Concept preview; not access-controlled                     |
| Theme/responsiveness | Mobile nav, contained table scrolling, light/dark components, reduced-motion handling.                                                                             | Implemented; see verification evidence                     |

Miniature trend charts and geofence occupancy are illustrative, not computed historical analytics. Preview timestamps belong to a fixed sample day. Live timestamps display deterministically in UTC.

Local development convenience (2026-10-25): optional backend-driven account selection still requires passwords. The isolated `../backend-accounts` implementation seeds one VIEWER and one TENANT_ADMIN idempotently without overwriting existing accounts. The directory and seeds are explicitly local-only and disabled by default; this is not a public account directory, passwordless login, or platform-access feature. See `api-contracts.md` and `verification.md` for boundaries and evidence.

Detail routes are specified in `detail-pages-plan.md`. Real geographic map rendering and provider requirements are in `maps-integration.md`. Global address geocoding and live GPS transport remain separate integrations, not substitutes for rendering.

## Explicit exclusions

No automatic refresh, registration/password recovery, effective-permissions UI, tenant-isolation guarantee, telemetry polling/streaming, recorded GPS history, device commands, server-persisted geofence editing, violation monitoring, invitation sending, role editing, tracker management, alert-rule editing, subscription billing, payouts, riders, or driver workflows are delivered. Live vehicle creation, alert transitions, geofence mutations, team administration, and preference saves remain disabled. Sign-in/session integration is delivered; production authentication is not certified by contract-server tests.

The frontend's live reads are implemented; the application is **not production-release ready**. Real map tiles do not make preview data or an unauthenticated deployment production-ready.

## Subsequent slices (one at a time)

1. Validate the implemented sign-in/session boundary against the configured Java deployment and verify permission/tenant-isolation behavior.
2. Add an effective-access/active-tenant contract and a coordinated refresh design before broader live workflows.
3. Verify alert actor/review-note and geofence geometry/assignment contracts before enabling mutations.
4. Verify vehicle/tracker creation and assignment workflows with authorization tests.
5. Add tenant-scoped telemetry/history and a reviewed GPS transport.
6. Add organization, member, invitation, and role-assignment APIs with effective server enforcement.
7. Approve production map provider, licensing, privacy, CSP, capacity, and non-Chromium/device testing.
8. Separately approve future business domains only.

Every slice needs contract verification, acceptance tests, honest loading/error/empty states, boundary checks, type checking, browser tests, and a progress update. New dependencies/roles/features need explicit rationale and approval; do not expand the scope just to make a screen look complete.
