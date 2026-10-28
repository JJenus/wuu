# Architecture audit

## Confirmed baseline observations
- Root POM is a JAR, not a reactor. It adds shared sources and repackages Boot. Device POM independently adds shared sources and repackages Boot.
- Old business Maven directories are deleted in the pre-existing working tree; their source appears under `src/`. Preserve this migration.
- Active YAML no longer duplicates management keys. Do not fix the historic duplicate-key warning without current evidence.
- Independent architecture agent is tracing actual messaging, command transport, startup and migration boundaries. Findings pending.

## Scope reconstruction evidence
- Fleet backend: vehicle/tracker CRUD, tracking trips and CSV export, persisted commands; logical core package.
- Alerting: speed/idle/geofence/dwell/disconnect rules, management APIs, tenant Redis caches.
- Identity: JWT/database sessions, refresh rotation, RBAC and tenant administration.
- Notifications: inbox/preferences/templates and delivery/retry scaffolding, with disconnected alert fan-out/initial dispatch and unsupported providers.
- No frontend package manifest, JS/TS/Vue/Svelte/HTML/CSS product source found. Browser UI release gates cannot be asserted satisfied by API tests.
- Ride-hailing and subscription documents describe unimplemented domains. They remain in the inventory as design hypotheses, not silently approved exclusions or requirements to fabricate payment policy.
- Device launch/package and remote command transport are distinct: adding a launcher alone does not make physical commands operational or authenticate devices.
