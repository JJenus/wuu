import { expect, test, type Page } from '@playwright/test'

async function signIn(page: Page) {
  await page.goto('/sign-in?next=/map')
  await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/map')
}

function snapshot(latitude = 51.5, speed = 24, recordedAt = new Date().toISOString()) {
  return {
    content: [
      {
        vehicle_id: 'live-1',
        license_plate: 'LIVE MAP 01',
        acc_status: true,
        last_telemetry_time: recordedAt,
        current_location: { latitude, longitude: -0.1, speed_kmh: speed, recorded_at: recordedAt },
      },
    ],
    last: true,
    empty: false,
  }
}

test('live map refreshes exact server positions without simulation and stops on navigation', async ({
  page,
}) => {
  let latitude = 51.5
  let reads = 0
  await page.route('**/api/_wuu/vehicles?*', async (route) => {
    reads++
    await route.fulfill({ json: snapshot(latitude) })
  })
  await signIn(page)
  await expect(page.getByRole('heading', { name: 'Live fleet positions' })).toBeVisible()
  await expect(page.getByRole('button', { name: /simulation/i })).toHaveCount(0)
  const card = page.locator('.tracking-vehicle-card').filter({ hasText: 'LIVE MAP 01' })
  await expect(card).toContainText('51.50000, -0.10000')
  await expect(card).toContainText('24 km/h')
  latitude = 51.51
  await expect(card).toContainText('51.51000, -0.10000', { timeout: 15000 })
  await expect(page.getByText(/Snapshot refreshed/)).toBeVisible()
  await page.getByRole('button', { name: 'Pause updates', exact: true }).click()
  await page.clock.install()
  const pausedReads = reads
  await page.clock.runFor(11000)
  expect(reads).toBe(pausedReads)
  await page.getByRole('button', { name: 'Resume updates', exact: true }).click()
  await expect.poll(() => reads).toBeGreaterThan(pausedReads)
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  const hiddenReads = reads
  await page.clock.runFor(11000)
  expect(reads).toBe(hiddenReads)
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await expect.poll(() => reads).toBeGreaterThan(hiddenReads)
  await page.getByRole('link', { name: 'Vehicles', exact: true }).first().click()
  await expect(page).toHaveURL('/vehicles')
  const leftReads = reads
  await page.clock.runFor(11000)
  expect(reads).toBe(leftReads)
})

test('failed refresh hides stale markers and manual retry recovers without preview fallback', async ({
  page,
}) => {
  let denied = false
  let reads = 0
  await page.route('**/api/_wuu/vehicles?*', async (route) => {
    reads++
    await route.fulfill(
      denied ? { status: 403, json: { message: 'Vehicle access revoked' } } : { json: snapshot() },
    )
  })
  await signIn(page)
  await expect(page.locator('.tracking-vehicle-card')).toContainText('LIVE MAP 01')
  denied = true
  await page.getByRole('button', { name: 'Refresh positions', exact: true }).click()
  await expect(
    page.getByText('Vehicle access revoked Positions are hidden until a successful refresh.', {
      exact: true,
    }),
  ).toBeVisible()
  await expect(page.locator('.geo-vehicle-marker')).toHaveCount(0)
  await expect(page.locator('.tracking-vehicle-card')).toHaveCount(0)
  await page.clock.install()
  const failedReads = reads
  await page.clock.runFor(11000)
  expect(reads).toBe(failedReads)
  denied = false
  await page.getByRole('button', { name: 'Refresh positions', exact: true }).click()
  await expect(page.locator('.tracking-vehicle-card')).toContainText('51.50000, -0.10000')
})

test('stale and absent GPS remain explicit instead of becoming invented movement', async ({
  page,
}) => {
  await page.route('**/api/_wuu/vehicles?*', async (route) => {
    const old = snapshot(51.5, 24, '2020-01-01T00:00:00Z')
    await route.fulfill({
      json: {
        ...old,
        content: [
          ...old.content,
          { vehicle_id: 'no-gps', license_plate: 'NO GPS', current_location: null },
        ],
      },
    })
  })
  await signIn(page)
  const old = page.locator('.tracking-vehicle-card').filter({ hasText: 'LIVE MAP 01' })
  await expect(old).toContainText('Stale location')
  await expect(old).toContainText('2020')
  const missing = page.locator('.tracking-vehicle-card').filter({ hasText: 'NO GPS' })
  await expect(missing).toContainText('No GPS location')
  await expect(missing).not.toContainText('0.00000')
})
