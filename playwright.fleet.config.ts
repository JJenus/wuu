import { defineConfig } from '@playwright/test'
import base from './playwright.config'

const java = process.env.FLEET_TEST_JAVA_ORIGIN
if (java && !/^http:\/\/127\.0\.0\.1:\d+$/.test(java))
  throw new Error('Use an owned loopback Java test server')
export default defineConfig({
  ...base,
  testDir: './tests/fleet',
  use: { ...base.use, baseURL: 'http://localhost:3002' },
  webServer: [
    ...(java
      ? []
      : [
          {
            command: 'node tests/fixtures/fleet-backend.mjs',
            url: 'http://127.0.0.1:18081/health',
            reuseExistingServer: false,
          },
        ]),
    {
      command: `PORT=3002 NUXT_PUBLIC_DATA_MODE=live WUU_API_ORIGIN=${java || 'http://127.0.0.1:18081'} WUU_SESSION_PASSWORD=test-only-fleet-cookie-secret-at-least-32-chars node .output/server/index.mjs`,
      url: 'http://localhost:3002/sign-in',
      reuseExistingServer: false,
      timeout: 120000,
    },
  ],
})
