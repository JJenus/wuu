import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
async function open(page: Page, path: string) {
  await page.goto(path)
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await expect(page.locator('.geographic-map-canvas')).toHaveAttribute('data-map-ready', 'true')
}
test('geographic map supports drag, wheel, keyboard, fit, search and layers', async ({ page }) => {
  await open(page, '/map')
  const canvas = page.locator('.geographic-map-canvas'),
    before = await canvas.getAttribute('data-map-center'),
    box = await canvas.boundingBox()
  await page.mouse.move(box!.x + 50, box!.y + 220)
  await page.mouse.down()
  await page.mouse.move(box!.x + 150, box!.y + 260, { steps: 8 })
  await page.mouse.up()
  await expect(canvas).not.toHaveAttribute('data-map-center', before!)
  await canvas.focus()
  const pan = await canvas.getAttribute('data-map-center')
  await page.keyboard.press('ArrowLeft')
  await expect(canvas).not.toHaveAttribute('data-map-center', pan!)
  const zoom = await canvas.getAttribute('data-map-zoom')
  await page.mouse.wheel(0, -180)
  await expect(canvas).not.toHaveAttribute('data-map-zoom', zoom!)
  await page.getByRole('button', { name: 'Fit visible vehicles', exact: true }).click()
  await page.getByRole('button', { name: 'Reset map view', exact: true }).click()
  await expect(canvas).toHaveAttribute('data-map-zoom', '12')
  await page.getByRole('searchbox', { name: 'Search map' }).fill('CA 7B19K4')
  await page.getByRole('button', { name: 'Transit 02 · Vehicle', exact: true }).click()
  await expect(page.getByRole('link', { name: 'View Transit 02', exact: true })).toBeVisible()
  await page.getByRole('searchbox', { name: 'Search map' }).fill('Dogpatch')
  await page.getByRole('button', { name: 'Dogpatch depot · Place', exact: true }).click()
  await expect(page.getByText('Map focused on Dogpatch depot', { exact: true })).toBeVisible()
  await page.getByRole('searchbox', { name: 'Search map' }).fill('no such place')
  await expect(page.getByText('No map results found.', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Clear map search', exact: true }).click()
  await page.getByRole('button', { name: 'Fit visible vehicles', exact: true }).click()
  await page.getByRole('button', { name: 'Map layers', exact: true }).click()
  await page.getByLabel('Show vehicle labels').check()
  expect(await page.locator('.geo-vehicle-marker.show-label').count()).toBeGreaterThan(0)
  await expect(page.locator('.geo-vehicle-marker:not(.show-label)')).toHaveCount(0)
})
test('manual navigation releases follow; geofence search preserves saved geometry', async ({
  page,
}) => {
  await open(page, '/map')
  await page.getByRole('button', { name: 'Follow selected vehicle', exact: true }).click()
  await page.locator('.geographic-map-canvas').focus()
  await page.keyboard.press('ArrowRight')
  await expect(
    page.getByRole('button', { name: 'Follow selected vehicle', exact: true }),
  ).toHaveAttribute('aria-pressed', 'false')
  await open(page, '/geofences/GF-001/edit')
  const before = await page.getByLabel('Center latitude', { exact: true }).inputValue()
  await page.getByRole('searchbox', { name: 'Search geofence map' }).fill('Mission')
  await page.getByRole('button', { name: 'Mission District · Place', exact: true }).click()
  await expect(page.getByLabel('Center latitude', { exact: true })).toHaveValue(before)
  await expect(
    page.getByText('No unsaved changes · Resets on reload', { exact: true }),
  ).toBeVisible()
})
test('real maps fit mobile and pass light/dark automated accessibility', async ({ page }) => {
  await page.setViewportSize({ width: 375, height: 1000 })
  await open(page, '/map')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
        .analyze()
    ).violations,
  ).toEqual([])
  await page.screenshot({ path: 'test-results/geographic-map-mobile.png', fullPage: true })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('button', { name: 'Switch to dark mode' }).click()
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
        .analyze()
    ).violations,
  ).toEqual([])
})
test('native touch pinch pans and zooms without modifying geographic samples', async ({ page }) => {
  const client = await page.context().newCDPSession(page)
  await client.send('Emulation.setTouchEmulationEnabled', { enabled: true })
  await page.setViewportSize({ width: 390, height: 1000 })
  await open(page, '/map')
  const canvas = page.locator('.geographic-map-canvas')
  await canvas.scrollIntoViewIfNeeded()
  const box = await canvas.boundingBox(),
    before = await canvas.getAttribute('data-map-zoom')
  const moving = page.getByRole('button', { name: 'Transit 01, moving', exact: true })
  const a = { x: box!.x + 100, y: box!.y + 200, id: 1 },
    b = { x: box!.x + 180, y: box!.y + 280, id: 2 }
  await client.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [a, b] })
  for (let i = 1; i <= 5; i++)
    await client.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: [
        { ...a, x: a.x - i * 12, y: a.y - i * 8 },
        { ...b, x: b.x + i * 12, y: b.y + i * 8 },
      ],
    })
  await client.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
  await expect(canvas).not.toHaveAttribute('data-map-zoom', before!)
  await page.getByRole('searchbox', { name: 'Search map' }).fill('Transit 01')
  await page.getByRole('button', { name: 'Transit 01 · Vehicle', exact: true }).click()
  await expect(moving).toHaveAttribute('data-lat', '37.7786')
  await client.detach()
})
