# Verification — Wayline preview

2026-10-21. This verifies the isolated frontend preview, **not backend integration or production readiness**.

## Commands and results

### Detail routes and scrollbars — latest slice

Source plan: `detail-pages-plan.md`, slice 1. No backend changes or new dependencies.

- RED checkpoint `9f40321`: new unit target failed because the shared trip CSV module did not exist. The first attempted browser RED timed out navigating to a missing route on the older preview; it is not counted as valid behavior-failure evidence.
- GREEN: `pnpm test:coverage` passed **10 unit tests**, with 100% lines/statements/functions/branches across the explicitly configured domain/policy/adapter/CSV scope. UI/composable coverage is not included in that percentage.
- `pnpm typecheck` passed (an initial run timed out during type preparation; a longer rerun and the final rerun passed).
- `pnpm check:boundaries`, `pnpm format:check`, and `pnpm build` passed. The build emitted plugin-timing warnings, not compilation errors.
- `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e`: **15 tests passed in 1.4 minutes** against the rebuilt production preview, including all nine original tests.
- The new detail suite checks list-to-detail navigation, alert actions and vehicle links, trip-to-vehicle-to-trip navigation, direct reload, single-record download content, geofence membership disclosure/links, missing-record recovery, and keyboard horizontal scrolling.
- Responsive checks cover three new routes × light/dark × 375/768/1440px = **18 combinations**, with no document-width overflow. Automated WCAG A/AA scans passed for the three routes in both themes at desktop size.
- A real pointer-interception failure in the first browser run exposed an uncontained geofence illustration pseudo-element. Its positioning/overflow styles were made shared before the unchanged tests passed. Mobile detail headers and the three-stat trip grid were also refined after screenshot inspection.
- Additional Chromium inspection with hidden-scrollbar defaults disabled confirmed a 12px rail, 999px thumb radius, distinct light/dark thumb colors, system-color forced-colors fallback, and no browser page errors. Artifacts: `test-results/scrollbars-light.png`, `test-results/scrollbars-dark.png`, and `test-results/details-{alerts,trips,geofences}-{light,dark}-{375,768,1440}.png`.
- Firefox and OS-specific overlay scrollbar behavior were not browser-tested. Native scrolling remains intact; browser/OS settings may hide rails until scrolling.

Code review: new routes use the existing workspace composable and stable IDs; no HTTP or adapter imports in UI, no raw HTML rendering, no fabricated route history, and no unsupported geofence mutations. CSV escaping and spreadsheet-formula neutralization are unit-tested. Export failures show a toast; alert-update failures show an inline error, but those injected failure paths were not browser-tested.

Self-review: accuracy 4/5 (actual checks above), completeness 4/5 (three detail types delivered; remaining types explicitly planned), clarity 4/5 (preview labels and recovery routes), actionability 4/5 (working links/actions and current server), conciseness 4/5 (shared CSV workflow; focused plan). Remaining gaps are real integration, cross-browser checks, and manual assistive-technology testing.

### Initial preview evidence

Restart recovery: restored the production preview with `pnpm preview --port 3000`. The first browser run began before the listener was ready (one connection-refused failure; eight passed). After startup, the full unchanged suite passed: **9/9 in 44.7 seconds**, using `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e`.

| Check                            | Actual command                                            | Result                                                                                |
| -------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| TypeScript / Vue templates       | `pnpm typecheck`                                          | PASS                                                                                  |
| Domain, policy, preview adapter  | `pnpm test:coverage`                                      | 8 tests PASS; 100% lines, statements, functions, and branches in the configured scope |
| UI/API separation                | `pnpm check:boundaries`                                   | PASS; no HTTP calls/direct API imports in Vue UI, no forbidden surface classes        |
| Production bundle                | `pnpm build`                                              | PASS; Nuxt 4.6.2, Vue 3.5.44                                                          |
| Browser journeys + accessibility | `PLAYWRIGHT_BASE_URL=http://localhost:3000 pnpm test:e2e` | 9 tests PASS against the production build; 39.9 seconds                               |

Browser: existing cached Chromium `chromium-1243`, discovered without a successful browser download. Playwright's exact-version browser download was initially unavailable; configuration now supports existing system/cached builds and an explicit executable override. No OS/browser packages were installed globally.

Final hygiene checks: `pnpm format:check` and `git diff --check` both passed. Read-only backend status showed ongoing changes beyond the initial snapshot; those changes were left untouched, not reverted or treated as frontend work.

## User-facing guarantees covered

| Guarantee                                                                                                                          | Test target                    | Evidence                                           |
| ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ | -------------------------------------------------- |
| Search/filter preserves original fleet; counts derive from records                                                                 | `tests/unit/workspace.test.ts` | Unit PASS                                          |
| Required fields, registration shape/length, name length, duplicate plates validated                                                | Same                           | Unit PASS                                          |
| Unknown roles deny access; organization templates lack platform access; platform template contains seeded organization permissions | Same                           | Unit PASS; descriptive UI policy only              |
| Preview sessions are isolated; returned data cannot mutate adapter internals                                                       | Same                           | Unit PASS                                          |
| Alert states update; unknown alert IDs reject                                                                                      | Same                           | Unit PASS                                          |
| Overview filters, marker selection, zoom/reset, legend visibility work                                                             | `tests/e2e/workspace.spec.ts`  | Browser PASS                                       |
| Add vehicle validates and resets after reload                                                                                      | Same                           | Browser PASS                                       |
| Acknowledgment/resolution update alert views and overview counts                                                                   | Same                           | Browser PASS                                       |
| Global search navigates; unknown vehicles show a helpful state                                                                     | Same                           | Browser PASS                                       |
| Trip CSV contains only filtered preview records                                                                                    | Same                           | Browser PASS; actual downloaded contents inspected |
| Preferences save in the preview; routes render                                                                                     | Same                           | Browser PASS                                       |
| Mobile menu, filters, dark theme, modal contrast, Escape/focus return work                                                         | Same                           | Browser PASS                                       |
| Automated WCAG A/AA checks across 11 key routes, dark modal, mobile navigation                                                     | Same                           | No automated violations detected in tested states  |

An additional Chromium sweep checked **36 route/viewport combinations**: 12 routes at 375px, 768px, and 1440px. No document-width overflow, non-200 page response, or browser page errors were detected. Tables scroll inside their containers instead of widening the page.

## Visual artifacts

Locally generated, ignored files:

- `test-results/wayline-1440.png` — default desktop overview.
- `test-results/wayline-768.png` — tablet overview.
- `test-results/wayline-375.png` — small-screen overview.
- `test-results/overview-dark.png` — dark overview.
- `test-results/add-vehicle-dark.png` — dark modal.

Desktop/mobile/dark screenshots were visually inspected during implementation. There is no committed visual baseline: **automated visual regression comparison is INCONCLUSIVE**, not PASS. These are current-state screenshots, not a historical comparison.

## RED → GREEN evidence

Initial acceptance tests failed because the fleet domain, access policy, and preview adapter did not exist. Root checkpoint: `15bf8a9`. Implementation made those tests pass. A later regression case exposed incomplete SUPER_ADMIN presentation permissions (`users.read` missing); the explicit template was corrected, and all 8 unit tests passed.

Browser REDs also found early-interaction/hydration races and a mobile overflow from an absolutely positioned table label. Fixes: deterministic preview cache seeding, an explicit hydration-ready boundary, a positioned table scroll container, and readiness-aware browser tests. A notification icon selector and map legend behavior were corrected during UI review. Formatter-induced multi-statement template parsing errors were fixed by moving actions into named handlers; production build and Vue typecheck passed afterward.

## Security and coverage limits

- All data is fictional. No secrets, tokens, API credentials, real fleet information, or backend mutations are involved.
- No login, production RBAC, tenant boundary, session revocation, or safe remote-command implementation is claimed.
- User-entered vehicle names are rendered with Vue text interpolation, not raw HTML. Registration/name validation is local preview validation, not a server trust boundary.
- Basic response headers deny framing, disable MIME sniffing, set referrer policy, and disable camera/microphone/geolocation. Production CSP/session/CSRF controls remain part of the authenticated integration slice.
- Unit coverage excludes Vue templates, framework internals, and runtime API integrations; the reported 100% is **not whole-application coverage**.
- Automated accessibility checks do not replace manual screen-reader, full keyboard, browser/OS, or usability review. Only Chromium was exercised.
- Core Web Vitals, load/performance benchmarks, dependency vulnerability audit, and real backend readiness were not measured.

## Delivery assessment

Preview UX is verified; the production integration remains a separate workstream.

| Axis          | Score | Evidence / improvement                                                                                                           |
| ------------- | ----- | -------------------------------------------------------------------------------------------------------------------------------- |
| Accuracy      | 4/5   | Passing build/tests and explicit limits; live contracts are inspected, not integration-tested                                    |
| Completeness  | 3/5   | Broad preview UI delivered; authenticated backend workflows remain unfinished and require the separately gated integration slice |
| Clarity       | 4/5   | Preview versus real capability is visible; future docs should stay short as contracts mature                                     |
| Actionability | 4/5   | App runs immediately with pnpm and sample data; real usage still depends on backend security fixes                               |
| Conciseness   | 4/5   | Agent entrypoint is brief with scoped document pointers; canonical docs intentionally retain integration evidence                |

Overall: 3.8/5. Highest-impact follow-up: verified effective-permission/tenant contracts, then secure sessions and a read-only fleet adapter. That gap cannot safely be closed by changing this frontend alone. A production user should not mistake this preview assessment for release approval.
