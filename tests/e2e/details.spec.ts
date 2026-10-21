import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

async function navigate(page: Page, path: string) {
  await page.goto(path)
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute('data-workspace-ready', 'true')
}

test('alert details keep preview actions and related vehicle in sync', async ({ page }) => {
  await navigate(page, '/alerts')
  await page.getByRole('link', { name: 'View alert AL-001' }).click()
  await expect(page).toHaveURL(/\/alerts\/AL-001$/)
  await expect(page.getByRole('heading', { name: 'Device disconnected', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Acknowledge in preview' }).click()
  await expect(page.getByTestId('alert-state')).toHaveText('acknowledged')
  await page.getByRole('button', { name: 'Resolve in preview' }).click()
  await expect(page.getByTestId('alert-state')).toHaveText('resolved')
  await expect(page.getByRole('button', { name: 'Resolve in preview' })).toHaveCount(0)
  await page.getByRole('link', { name: 'View Transit 06' }).click()
  await expect(page.getByRole('heading', { name: 'Transit 06', exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Low fuel level', exact: true }).click()
  await expect(page).toHaveURL(/\/alerts\/AL-003$/)
})

test('trip details link to the correct vehicle and export one journey', async ({ page }) => {
  await navigate(page, '/trips')
  await page.getByRole('link', { name: 'Open trip TR-1048' }).click()
  await expect(page.getByRole('heading', { name: 'Journey TR-1048' })).toBeVisible()
  await expect(page.getByText('Route playback is unavailable', { exact: true })).toBeVisible()
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Export this preview trip' }).click()
  const download = await downloadPromise
  const stream = await download.createReadStream()
  const chunks: Buffer[] = []
  for await (const chunk of stream!) chunks.push(chunk)
  const csv = Buffer.concat(chunks).toString()
  expect(csv).toContain('TR-1048')
  expect(csv).not.toContain('TR-1047')
  await page.getByRole('link', { name: 'View Transit 01' }).click()
  await page.getByRole('link', { name: 'Journey TR-1048', exact: true }).click()
  await expect(page).toHaveURL(/\/trips\/TR-1048$/)
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Journey TR-1048' })).toBeVisible()
})

test('geofence details show illustrative membership without editing controls', async ({ page }) => {
  await navigate(page, '/geofences')
  await page.getByRole('link', { name: 'View Dogpatch depot' }).click()
  await expect(page).toHaveURL(/\/geofences\/GF-001$/)
  await expect(page.getByRole('heading', { name: 'Dogpatch depot', exact: true })).toBeVisible()
  await expect(page.getByText('Illustrative membership, not live containment.')).toBeVisible()
  await expect(page.getByRole('button', { name: /save|delete|edit/i })).toHaveCount(0)
  await page.getByRole('link', { name: 'View Cargo 05' }).click()
  await expect(page).toHaveURL(/\/vehicles\/VH-005$/)
})

test('missing detail records provide a working recovery link', async ({ page }) => {
  for (const [section, title, back] of [
    ['alerts', 'Alert not found', 'Back to alerts'],
    ['trips', 'Trip not found', 'Back to trips'],
    ['geofences', 'Geofence not found', 'Back to geofences'],
  ]) {
    await navigate(page, `/${section}/missing`)
    await expect(page.getByRole('heading', { name: title })).toBeVisible()
    await page.getByRole('link', { name: back, exact: true }).last().click()
    await expect(page).toHaveURL(new RegExp(`/${section}$`))
  }
})

test('detail pages fit small screens and pass light/dark accessibility checks', async ({ page }) => {
  for (const theme of ['light', 'dark']) {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await navigate(page, '/')
    if (theme === 'dark') await page.getByRole('button', { name: 'Switch to dark mode' }).click()
    for (const width of [375, 768, 1440]) {
      await page.setViewportSize({ width, height: 900 })
      for (const path of ['/alerts/AL-001', '/trips/TR-1048', '/geofences/GF-001']) {
        await navigate(page, path)
        await expect(page.locator('h1')).toBeVisible()
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        if (width === 1440) {
          const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']).analyze()
          expect(result.violations, `${theme}: ${path}`).toEqual([])
        }
        await page.screenshot({ path: `test-results/details-${path.split('/')[1]}-${theme}-${width}.png`, fullPage: true })
      }
    }
  }
})

test('styled scrollbars preserve keyboard scrolling in narrow tables', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 800 })
  await navigate(page, '/trips')
  const region = page.getByRole('region', { name: 'Trip records' })
  await region.focus()
  await page.keyboard.press('ArrowRight')
  await expect.poll(() => region.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
  const light = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--scrollbar-thumb'))
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: 'Switch to dark mode' }).click()
  const dark = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--scrollbar-thumb'))
  expect(light.trim()).not.toBe('')
  expect(dark.trim()).not.toBe(light.trim())
})
