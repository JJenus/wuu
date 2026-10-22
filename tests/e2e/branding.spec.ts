import { test, expect } from '@playwright/test'

test('Wuu branding and supplied logo render across themes, navigation and downloads', async ({
  page,
}) => {
  await page.goto('/')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await expect(page).toHaveTitle(/Wuu/)
  const home = page.getByRole('link', { name: 'Wuu home', exact: true })
  await expect(home).toBeVisible()
  await expect(home.getByRole('img', { name: 'Wuu logo' })).toBeVisible()
  expect(
    await home.locator('img').evaluate((img) => (img as HTMLImageElement).naturalWidth),
  ).toBeGreaterThan(0)
  await page.getByRole('button', { name: 'Switch to dark mode' }).click()
  await page.screenshot({ path: 'test-results/wuu-desktop-dark.png', fullPage: true })
  await page.goto('/admin')
  await expect(page).toHaveTitle(/Wuu/)
  await expect(page.getByText('Wuu platform', { exact: true })).toBeVisible()
  await page.setViewportSize({ width: 375, height: 900 })
  await page.getByRole('button', { name: 'Open navigation' }).click()
  await expect(home).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'test-results/wuu-mobile-logo.png', fullPage: true })
  const manifest = await page.request.get('/site.webmanifest')
  expect((await manifest.json()).name).toBe('Wuu')
  for (const path of [
    '/favicon.ico',
    '/favicon-32.png',
    '/apple-touch-icon.png',
    '/icon-192.png',
    '/icon-512.png',
  ])
    expect((await page.request.get(path)).ok()).toBe(true)
})
