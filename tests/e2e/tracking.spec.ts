import { test, expect, type Page, type Locator } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
async function position(marker: Locator) {
  return marker.evaluate((el) => {
    const node = el as HTMLElement
    return `${node.dataset.lat},${node.dataset.lng}`
  })
}
async function open(page: Page, path: string) {
  await page.goto(path)
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
}
test('simulated tracking moves only moving vehicles and pauses, filters and resets', async ({
  page,
}) => {
  await open(page, '/map')
  await expect(page.getByText('Simulated tracking', { exact: true })).toBeVisible()
  const moving = page.getByRole('button', { name: 'Transit 02, moving', exact: true })
  const parked = page.getByRole('button', { name: 'Cargo 05, parked', exact: true })
  const start = await position(moving),
    stationary = await position(parked)
  await page.getByRole('button', { name: 'Start simulation', exact: true }).click()
  await expect.poll(() => position(moving)).not.toBe(start)
  expect(await position(parked)).toBe(stationary)
  await page.getByRole('button', { name: 'Pause simulation', exact: true }).click()
  const paused = await position(moving)
  await page.waitForTimeout(400)
  expect(await position(moving)).toBe(paused)
  await page.getByLabel('Simulation speed').selectOption('4')
  await page.getByRole('button', { name: 'Follow selected vehicle', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Follow selected vehicle', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true')
  await expect(page.locator('.geographic-map-canvas')).toHaveAttribute('data-map-ready', 'true')
  await page.getByLabel('Map vehicle status').selectOption('offline')
  await expect(moving).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Transit 06, offline', exact: true })).toBeVisible()
  await page.getByLabel('Map vehicle status').selectOption('all')
  await page.getByRole('button', { name: 'Reset simulation', exact: true }).click()
  expect(await position(moving)).toBe(start)
})
test('trip replay plays, pauses, seeks, finishes, restarts and stays separate from fleet data', async ({
  page,
}) => {
  await open(page, '/trips/TR-1048')
  await expect(page.getByRole('heading', { name: 'Illustrative trip replay' })).toBeVisible()
  const marker = page.getByRole('button', { name: /Transit 01, / })
  const start = await position(marker)
  await page.getByRole('button', { name: 'Play replay', exact: true }).click()
  await expect.poll(() => position(marker)).not.toBe(start)
  await page.getByRole('button', { name: 'Pause replay', exact: true }).click()
  await page.getByLabel('Replay speed').selectOption('32')
  const timeline = page.getByRole('slider', { name: 'Replay timeline' })
  await timeline.fill('720')
  await expect(page.getByTestId('replay-elapsed')).toHaveText('12:00')
  await timeline.fill('1439')
  await page.getByRole('button', { name: 'Play replay', exact: true }).click()
  await expect(page.getByText('Replay complete', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Play replay', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Restart replay', exact: true }).click()
  await expect(timeline).toHaveValue('0')
  expect(await position(marker)).toBe(start)
  await page.reload()
  await expect(page.getByRole('button', { name: 'Play replay', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Play replay', exact: true }).click()
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { value: true, configurable: true })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await expect(page.getByRole('button', { name: 'Play replay', exact: true })).toBeVisible()
})
test('motion starts paused with reduced motion and tracking/replay fit mobile and dark mode', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.setViewportSize({ width: 375, height: 1000 })
  for (const path of ['/map', '/trips/TR-1046']) {
    await open(page, path)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await expect(
      page.getByRole('button', {
        name: path === '/map' ? 'Start simulation' : 'Play replay',
        exact: true,
      }),
    ).toBeVisible()
    expect(
      (
        await new AxeBuilder({ page })
          .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
          .analyze()
      ).violations,
    ).toEqual([])
    await page.screenshot({
      path: `test-results/${path === '/map' ? 'tracking' : 'replay'}-mobile.png`,
      fullPage: true,
    })
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: 'Switch to dark mode' }).click()
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
        .analyze()
    ).violations,
  ).toEqual([])
  await page.screenshot({ path: 'test-results/trip-replay-dark.png', fullPage: true })
})
