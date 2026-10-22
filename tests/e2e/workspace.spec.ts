import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

async function navigate(page: Page, path: string) {
  await page.goto(path)
  // SSR renders usable-looking fields before their Vue event handlers attach.
  // Wait for the explicit hydration signal, not arbitrary sleeps/network-idle.
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
    { timeout: 15000 },
  )
}

test('overview renders, filters fleet, and map markers expose vehicle details', async ({
  page,
}) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await navigate(page, '/')
  await expect(page.getByRole('heading', { name: 'Your fleet, in focus.' })).toBeVisible()
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(5)
  await page.getByRole('button', { name: 'Offline 1' }).click()
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
  await expect(page.getByRole('table')).toContainText('Transit 06')
  const marker = page.getByRole('button', { name: 'Transit 02, moving', exact: true })
  // Compact maps group nearby assets; expand the group before selecting its child.
  for (let i = 0; i < 4 && (await marker.count()) === 0; i++) {
    await page.getByRole('button', { name: /vehicles\. Zoom or expand group:.*Transit 02/ }).click()
  }
  await marker.click()
  await expect(page.getByRole('link', { name: 'View Transit 02', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Zoom in', exact: true }).click()
  await expect(page.locator('.geographic-map-canvas')).toHaveAttribute('data-map-ready', 'true')
  await page.getByRole('button', { name: 'Reset map view' }).click()
  await expect(page.locator('.geographic-map-canvas')).toHaveAttribute('data-map-zoom', '12')
  await page.getByRole('button', { name: 'Toggle map legend' }).click()
  await expect(page.locator('.map-legend')).not.toBeVisible()
  await page.getByRole('button', { name: 'Toggle map legend' }).click()
  await expect(page.locator('.map-legend')).toBeVisible()
  await page.screenshot({ path: 'test-results/overview-desktop.png', fullPage: true })
  expect(errors).toEqual([])
})

test('vehicle creation validates and remains isolated to the preview session', async ({ page }) => {
  await navigate(page, '/vehicles')
  await page.getByRole('button', { name: 'Add vehicle', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Add vehicle', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: 'Enter a vehicle name' })).toBeVisible()
  await dialog.getByLabel('Vehicle name').fill('Transit 09')
  await dialog.getByLabel('Registration').fill('NEW-123')
  await dialog.getByRole('button', { name: 'Add vehicle', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  await page.getByRole('textbox', { name: 'Search vehicles' }).fill('NEW-123')
  await expect(page.getByRole('table')).toContainText('Transit 09')
  await expect(page.getByRole('table')).toContainText('Awaiting tracker assignment')
  await page.reload()
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
    { timeout: 15000 },
  )
  await page.getByRole('textbox', { name: 'Search vehicles' }).fill('NEW-123')
  await expect(page.getByRole('heading', { name: 'No vehicles match' })).toBeVisible()
})

test('alerts transition across views and update the overview', async ({ page }) => {
  await navigate(page, '/alerts')
  const card = page.locator('article').filter({ hasText: 'Device disconnected' })
  await card.getByRole('button', { name: 'Acknowledge' }).click()
  await expect(card).not.toBeVisible()
  await page.getByRole('button', { name: /Acknowledged/ }).click()
  await card.getByRole('button', { name: 'Resolve' }).click()
  await page.getByRole('button', { name: /Resolved/ }).click()
  await expect(card).toBeVisible()
  await expect(card).toContainText('resolved')
  await page.getByRole('link', { name: 'Overview', exact: true }).click()
  await expect(
    page
      .locator('.metric-card')
      .filter({ hasText: 'Needs attention' })
      .locator('.metric-middle strong'),
  ).toHaveText('2')
})

test('global search navigates to detail, and missing vehicles have a helpful empty state', async ({
  page,
}) => {
  await navigate(page, '/')
  await page.getByRole('button', { name: /Search anything/ }).click()
  await page.getByPlaceholder('Search vehicles or drivers...').fill('Maya')
  await page
    .getByRole('dialog')
    .getByRole('link', { name: /Transit 01/ })
    .click()
  await expect(page.getByRole('heading', { name: 'Transit 01' })).toBeVisible()
  await navigate(page, '/vehicles/missing')
  await expect(page.getByRole('heading', { name: 'Vehicle not found' })).toBeVisible()
})

test('trip export downloads the filtered sample data', async ({ page }) => {
  await navigate(page, '/trips')
  await page.getByRole('textbox', { name: 'Search trips' }).fill('Maya')
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Export preview CSV' }).click()
  const download = await downloadPromise
  expect(download.suggestedFilename()).toBe('wuu-preview-trips.csv')
  const stream = await download.createReadStream()
  const chunks: Buffer[] = []
  for await (const chunk of stream!) chunks.push(chunk)
  const csv = Buffer.concat(chunks).toString()
  expect(csv).toContain('Maya Chen')
  expect(csv).not.toContain('James Wilson')
})

test('settings save session preferences and all routes render without overflow', async ({
  page,
}) => {
  await navigate(page, '/settings')
  await page.getByRole('switch', { name: 'Email alerts' }).click()
  await page.getByRole('button', { name: 'Save preferences' }).click()
  await expect(page.getByRole('switch', { name: 'Email alerts' })).toHaveAttribute(
    'aria-checked',
    'false',
  )
  for (const path of [
    '/map',
    '/geofences',
    '/team',
    '/admin',
    '/admin/organizations',
    '/admin/roles',
  ]) {
    await navigate(page, path)
    await expect(page.locator('h1')).toBeVisible()
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true)
  }
})

test('mobile navigation, fleet filters, and dark theme work', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await navigate(page, '/')
  await expect(page.getByRole('heading', { name: 'Your fleet, in focus.' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.screenshot({ path: 'test-results/overview-mobile.png', fullPage: true })
  await page.getByRole('button', { name: 'Open navigation' }).click()
  await page.getByRole('link', { name: 'Vehicles', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'A place for every vehicle.' })).toBeVisible()
  await page.getByRole('combobox', { name: 'Vehicle status' }).selectOption('offline')
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.setViewportSize({ width: 1440, height: 1080 })
  await navigate(page, '/')
  await page.getByRole('button', { name: /Switch to dark mode/ }).click()
  await expect(page.locator('html')).toHaveClass(/dark/)
  await page.screenshot({ path: 'test-results/overview-dark.png', fullPage: true })
})

test('key routes have no automated WCAG A/AA violations', async ({ page }) => {
  for (const path of [
    '/',
    '/vehicles',
    '/vehicles/VH-001',
    '/trips',
    '/alerts',
    '/geofences',
    '/team',
    '/settings',
    '/admin',
    '/admin/organizations',
    '/admin/roles',
  ]) {
    await navigate(page, path)
    const result = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
      .analyze()
    expect(result.violations, `Accessibility findings at ${path}`).toEqual([])
  }
})

test('dark overlays and mobile navigation pass automated accessibility checks', async ({
  page,
}) => {
  await navigate(page, '/')
  await page.getByRole('button', { name: /Switch to dark mode/ }).click()
  await page.getByRole('button', { name: 'Add vehicle', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  const dark = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
    .analyze()
  expect(dark.violations).toEqual([])
  await page.screenshot({ path: 'test-results/add-vehicle-dark.png' })
  await page.keyboard.press('Escape')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: 'Open navigation' }).click()
  const mobile = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
    .analyze()
  expect(mobile.violations).toEqual([])
  await page.keyboard.press('Escape')
  await expect(page.getByRole('button', { name: 'Open navigation' })).toBeFocused()
})
