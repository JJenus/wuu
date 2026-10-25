import { defineConfig } from '@playwright/test'
import base from './playwright.config'

// Explicit local-only smoke: start the isolated Java backend and live frontend first.
// No traces/videos: unlike the contract suite, this uses generated local credentials.
export default defineConfig({
  ...base,
  testDir: './tests/java',
  fullyParallel: false,
  timeout: 90000,
  webServer: undefined,
  use: {
    ...base.use,
    baseURL: 'http://127.0.0.1:3001',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
})
