import { defineConfig } from '@playwright/test'
import base from './playwright.config'
const java = process.env.GEOFENCE_TEST_JAVA_ORIGIN
if (java && !/^http:\/\/127\.0\.0\.1:\d+$/.test(java))
  throw new Error('Use an owned loopback Java test server')
export default defineConfig({
  ...base,
  testDir: './tests/geofences',
  testIgnore: java ? [] : ['**/java.spec.ts'],
  use: { ...base.use, baseURL: 'http://localhost:3001' },
  webServer: [
    ...(java
      ? []
      : [
          {
            command: 'node tests/fixtures/geofence-backend.mjs',
            url: 'http://127.0.0.1:18084/health',
            reuseExistingServer: false,
          },
        ]),
    {
      command: `PORT=3001 NUXT_PUBLIC_DATA_MODE=live WUU_API_ORIGIN=${java || 'http://127.0.0.1:18084'} WUU_SESSION_PASSWORD=test-only-geofence-cookie-secret-at-least-32-chars node .output/server/index.mjs`,
      url: 'http://localhost:3001/sign-in',
      reuseExistingServer: false,
      timeout: 120000,
    },
  ],
})
