import { test, expect } from '@playwright/test'
// Test the tile engine deterministically; live provider availability is a separate smoke check.
test.beforeEach(async ({ page }) => {
  if (process.env.WUU_TEST_LIVE_TILES === '1') return
  await page.route('https://tile.openstreetmap.org/**', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256"><rect width="256" height="256" fill="#e2e8f0"/><path d="M0 128H256M128 0V256" stroke="#94a3b8"/><text x="16" y="24" fill="#334155">Test tile</text></svg>',
    }),
  )
})
test('nearby markers cluster and list selection reveals the selected geographic asset', async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 1000 })
  await page.goto('/map')
  const canvas = page.locator('.geographic-map-canvas')
  await expect(canvas).toHaveAttribute('data-map-ready', 'true')
  const cluster = page.getByRole('button', { name: /vehicles\. Zoom or expand group/ }).first()
  await expect(cluster).toBeVisible()
  const zoom = await canvas.getAttribute('data-map-zoom')
  await cluster.click()
  await expect(canvas).not.toHaveAttribute('data-map-zoom', zoom!)
  await page.locator('.tracking-vehicle-card').filter({ hasText: 'Transit 01' }).click()
  const marker = page.getByRole('button', { name: 'Transit 01, moving', exact: true })
  await expect(marker).toBeVisible()
  await expect(marker).toHaveAttribute('aria-pressed', 'true')
  await expect(marker).toHaveClass(/is-selected/)
})
test('real geographic tiles, attribution, highlighted markers and selection stay synchronized', async ({
  page,
}) => {
  await page.goto('/map')
  const map = page.getByRole('region', { name: 'Fleet map viewport', exact: true })
  await expect(map.locator('.leaflet-container')).toHaveAttribute('data-map-ready', 'true')
  await expect(map.locator('.leaflet-tile-loaded').first()).toBeVisible({ timeout: 20000 })
  await expect(map.getByRole('link', { name: 'OpenStreetMap contributors' })).toBeVisible()
  const marker = map.getByRole('button', { name: 'Transit 02, moving', exact: true })
  await marker.click()
  await expect(marker).toHaveAttribute('aria-pressed', 'true')
  await expect(marker).toHaveClass(/is-selected/)
  await expect(
    page.locator('.tracking-vehicle-card').filter({ hasText: 'Transit 01' }),
  ).toHaveAttribute('aria-pressed', 'false')
  await expect(page.getByRole('link', { name: 'View Transit 02', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Close vehicle details', exact: true }).click()
  await expect(marker).toHaveAttribute('aria-pressed', 'false')
  await page.getByRole('searchbox', { name: 'Search map' }).fill('Dogpatch')
  await page.getByRole('button', { name: 'Dogpatch depot · Place', exact: true }).click()
  await expect(map.locator('.search-location-marker')).toBeVisible()
  await expect(map.getByText('Loading map…', { exact: true })).toHaveCount(0, { timeout: 20000 })
  await page.screenshot({ path: 'test-results/geographic-map-desktop.png', fullPage: true })
})
test('tile failures remain visible, retry recovers without replacing the map with an illustration', async ({
  page,
}) => {
  const failTiles = (route: import('@playwright/test').Route) => route.abort()
  await page.route('https://tile.openstreetmap.org/**', failTiles)
  await page.goto('/map')
  await expect(
    page.getByText('Map tiles unavailable. Vehicle data is unchanged.', { exact: true }),
  ).toBeVisible({ timeout: 20000 })
  await expect(page.locator('.map-art')).toHaveCount(0)
  await page.unroute('https://tile.openstreetmap.org/**', failTiles)
  await page.getByRole('button', { name: 'Retry map tiles', exact: true }).click()
  await expect(page.locator('.leaflet-tile-loaded').first()).toBeVisible({ timeout: 20000 })
  await expect(
    page.getByText('Map tiles unavailable. Vehicle data is unchanged.', { exact: true }),
  ).toHaveCount(0)
})
