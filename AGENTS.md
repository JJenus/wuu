# Wayline frontend

This directory is an **independent Git repository** on `frontend-app`. Run frontend Git and pnpm commands here. The parent workspace's Maven instructions apply to `../sidie/`, not this frontend.

## Before each slice

1. Read `docs/scope.md` and `docs/progress.md`; choose one acceptance-sized slice.
2. For integration/access changes, read `docs/api-contracts.md` and `docs/access-matrix.md` and verify the current backend contract read-only.
3. For UI/structure changes, read `docs/design-system.md` and `docs/architecture.md`.

## Fixed boundaries

- Keep the active `../sidie/` checkout and `../sidie-snapshot-*` untouched. Backend fixes need separately approved isolation.
- Use pnpm, Nuxt/Vue, and Nuxt UI v4. Keep UI HTTP-free; adapters own contracts and composables own workflows.
- Preview remains public sample data. Live reads require the implemented sealed-session sign-in boundary; preserve explicit preview labels, backend-authoritative access, and fail-closed unsupported actions. Read `docs/api-contracts.md` before changing authentication; contract-server tests are not actual-deployment verification.
- Production access is enforced on the server, never inferred from client role templates or hidden navigation.
- Live API failures must stay errors; never replace them with preview fixtures.

## Completion gate

Run the appropriate package scripts for typecheck, coverage, boundaries, formatting, browser tests, and build. Browser tests accept a system/cached Chromium; see `README.md` for overrides. Capture actual outcomes in `docs/verification.md` and update progress. Coverage currently measures pure domain/policy/adapter files, not the entire UI.
