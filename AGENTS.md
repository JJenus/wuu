# Wayline frontend

This directory is an **independent Git repository** on `frontend-app`. Run frontend Git and pnpm commands here. The parent workspace's Maven instructions apply to `../sidie/`, not this frontend.

## Before each slice

1. Follow workspace `../AGENTS.md` and `../DELIVERY_PLAN.md`; complete one requested user-visible outcome. Read `docs/scope.md` when scope is unclear, and relevant progress history only to recover existing work.
2. For integration/access changes, read `docs/api-contracts.md` and `docs/access-matrix.md` and verify the current backend contract read-only.
3. For UI/structure changes, read `docs/design-system.md` and `docs/architecture.md`.

## Fixed boundaries

- Preserve unrelated backend edits and `../sidie-snapshot-*`. For cross-stack tasks, read backend instructions and isolate conflicting work as needed.
- Use pnpm, Nuxt/Vue, and Nuxt UI v4. Keep UI HTTP-free; adapters own contracts and composables own workflows.
- Preview remains public sample data. Live reads require the implemented sealed-session sign-in boundary; preserve explicit preview labels, backend-authoritative access, and fail-closed unsupported actions. Read `docs/api-contracts.md` before changing authentication; contract-server tests are not actual-deployment verification.
- Production access is enforced on the server, never inferred from client role templates or hidden navigation.
- Live API failures must stay errors; never replace them with preview fixtures.

## Completion gate

Run focused tests and the package checks relevant to changed files. For live
integration changes, verify the real affected browser/API workflow; use full-stack
checks for cross-stack changes or release candidates, not every small edit.
Browser tests accept a system/cached Chromium; see `README.md` for overrides.
Report exact outcomes and limits in one short task note; routine changes do not
require separate progress and verification log entries. Coverage currently
measures pure domain/policy/adapter files, not the entire UI. Stop after the task;
legacy delivery/coordination documents do not authorize automatic continuation.

Commit and push verified task changes when finished; exclude unrelated local edits.
Deployment and production operations remain separately authorized.
