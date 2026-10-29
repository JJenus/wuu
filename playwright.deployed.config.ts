import { defineConfig } from '@playwright/test'
import base from './playwright.config'

if (!process.env.WUU_DEPLOYED_FIXTURE || !process.env.PLAYWRIGHT_BASE_URL) {
  throw new Error('Run through node tools/verify-java-integration.mjs')
}

export default defineConfig({
  ...base,
  testDir: './tests/java',
  testMatch: 'deployed-backend.spec.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 120000,
  webServer: undefined,
  outputDir: process.env.WUU_DEPLOYED_OUTPUT,
  use: {
    ...base.use,
    baseURL: process.env.PLAYWRIGHT_BASE_URL,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    launchOptions: {
      ...base.use?.launchOptions,
      // No provider lookup is part of this acceptance slice; local app/API remain real.
      args: ['--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE 127.0.0.1'],
    },
  },
})
