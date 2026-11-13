import { defineConfig } from '@playwright/test'
import auth from './playwright.auth.config'

const java = process.env.ACCOUNT_TEST_JAVA_ORIGIN
if (java && !/^http:\/\/127\.0\.0\.1:\d+$/.test(java))
  throw new Error('Account acceptance requires an owned loopback Java server')
export default defineConfig({
  ...auth,
  testDir: './tests/account',
  webServer: java
    ? [
        {
          command: `PORT=3001 NUXT_PUBLIC_DATA_MODE=live WUU_API_ORIGIN=${java} WUU_SESSION_PASSWORD=test-only-account-cookie-secret-at-least-32-chars node .output/server/index.mjs`,
          url: 'http://localhost:3001/sign-in',
          reuseExistingServer: false,
          timeout: 120000,
        },
      ]
    : auth.webServer,
})
