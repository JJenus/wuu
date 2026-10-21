# Wayline

**Your fleet, in focus.** A responsive, map-led fleet workspace built with Nuxt 4, Vue 3, TypeScript, and Nuxt UI v4.

## Run

Node 24 and pnpm 11 are used in this workspace.

```sh
pnpm install
pnpm dev
```

Open http://localhost:3000. No backend services, API keys, account, or real credentials are needed.

## What works today

- Responsive overview with counts derived from the sample fleet.
- Interactive illustrated map: vehicle selection, details, status filters, zoom, reset, legend.
- Searchable/filterable vehicle list and detail routes.
- Validated vehicle creation in the isolated preview session.
- Alert acknowledgment/resolution, reflected in overview and notifications.
- Searchable trips, detail dialogs, and filtered **preview** CSV downloads.
- Global search (button or Cmd/Ctrl+K).
- Session-scoped notification preferences and light/dark appearance.
- Read-only geofence, team, organization, role, and platform-administration previews.
- Helpful empty states, missing-vehicle handling, and application error page.

## Important boundaries

**This release is an interactive frontend preview, not a production-connected fleet application.** All records are fictional; `example.com` addresses are placeholders. Changes reset on reload. No network adapter connects to the Java backend. There is no authentication or production authorization. Administration routes intentionally show only sample information.

The map is a locally rendered geographic illustration, not a navigation-grade map or live GPS service. Mini-charts are illustrative and do not imply historical analytics. Remote commands, invitations, role editing, billing, ride-hailing, and live updates are unavailable.

The active backend in `../sidie/` was not checked out, stashed, reset, or edited. A byte-verified local snapshot was created before initializing this independent orphan branch. See [progress](docs/progress.md).

## Verify

```sh
pnpm typecheck
pnpm test:coverage
pnpm check:boundaries
pnpm test:e2e
pnpm build
```

Playwright uses a system/cached Chromium if available, or its own installed browser otherwise. Set `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` to override detection. Only if none is available, run `pnpm exec playwright install chromium`.

Playwright starts/reuses the dev server on port 3000. Set `PLAYWRIGHT_BASE_URL=http://localhost:3001` to test an already running production build instead. Screenshots and failing traces go to ignored `test-results/`. For a production preview, run `pnpm build` then `pnpm preview`.

## Continue without drift

Read [scope](docs/scope.md), [API inventory](docs/api-contracts.md), [access matrix](docs/access-matrix.md), [architecture](docs/architecture.md), [design system](docs/design-system.md), and [progress](docs/progress.md) before starting another slice.

The next slice is a separately approved, authenticated **read-only fleet integration**. Do not silently replace preview data with API data, invent endpoints, or repair the active backend in place.
