import { defineConfig, devices } from '@playwright/test'
import { existsSync, readdirSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const browserCache = join(homedir(), '.cache/ms-playwright')
const cachedBrowsers = existsSync(browserCache)
  ? readdirSync(browserCache)
      .filter((name) => /^chromium-\d+$/.test(name))
      .sort((a, b) => Number(b.split('-')[1]) - Number(a.split('-')[1]))
      .map((name) => join(browserCache, name, 'chrome-linux64/chrome'))
  : []
const executablePath =
  process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ||
  ['/usr/bin/chromium', '/usr/bin/chromium-browser', ...cachedBrowsers].find((path) =>
    existsSync(path),
  )

export default defineConfig({
  testDir: './tests/e2e',
  workers: 1,
  timeout: 60000,
  // Leave headroom for cold, slower CI rendering without masking a permanently stuck page.
  expect: { timeout: 15000 },
  fullyParallel: true,
  retries: 0,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    trace: 'retain-on-failure',
    launchOptions: { executablePath },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 1080 } },
    },
  ],
  webServer: process.env.PLAYWRIGHT_BASE_URL
    ? undefined
    : {
        // Dev-mode Vite reloads while discovering dependencies; verify the production build instead.
        command: 'pnpm build && PORT=3000 node .output/server/index.mjs',
        url: 'http://localhost:3000',
        reuseExistingServer: false,
        timeout: 240000,
      },
})
