import { defineConfig } from '@playwright/test'
import base from './playwright.config'

export default defineConfig({
  ...base,
  testDir: './tests/auth',
  use: { ...base.use, baseURL: 'http://localhost:3001' },
  webServer: [
    {
      command: 'node tests/fixtures/auth-backend.mjs',
      url: 'http://127.0.0.1:18080/health',
      reuseExistingServer: false,
    },
    {
      command:
        'PORT=3001 NUXT_PUBLIC_DATA_MODE=live WUU_LOCAL_ACCOUNT_PICKER=true WUU_API_ORIGIN=http://127.0.0.1:18080 WUU_SESSION_PASSWORD=test-only-session-password-at-least-32-characters node .output/server/index.mjs',
      url: 'http://localhost:3001/sign-in',
      reuseExistingServer: false,
      timeout: 120000,
    },
  ],
})
