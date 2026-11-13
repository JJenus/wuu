import { expect, test } from '@playwright/test'

const pageOf = <T>(content: readonly T[]) => ({ content, last: true, totalPages: 1 })

test.beforeEach(async ({ page }) => {
  await page.goto('/sign-in')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/')
})

for (const readings of [
  { name: 'known', values: [1204.4, 800.2], total: '2,005' },
  { name: 'zero', values: [0], total: '0' },
  { name: 'missing', values: [null], total: 'Not recorded' },
  { name: 'partial', values: [1204.4, null], total: 'Not recorded' },
  { name: 'empty', values: [], total: 'Not recorded' },
]) {
  test(`live ${readings.name} readings are odometers, not daily travel or historical trends`, async ({
    page,
  }) => {
    const vehicles = readings.values.map((value, i) => ({
      vehicleId: `REPORT-${i}`,
      licensePlate: `REPORT-${i}`,
      odometerKm: value,
    }))
    await page.route('**/api/_wuu/**', async (route) => {
      const path = new URL(route.request().url()).pathname
      await route.fulfill({
        json:
          path === '/api/_wuu/vehicles'
            ? pageOf(vehicles)
            : path === '/api/_wuu/vehicles/REPORT-0'
              ? vehicles[0]
              : pageOf([]),
      })
    })
    await page.goto('/')
    const metric = page.locator('.metric-card').filter({ hasText: 'Fleet odometer total' })
    await expect(metric.locator('.metric-middle strong')).toHaveText(readings.total)
    await expect(metric).toContainText('Not a period distance')
    await expect(page.locator('.sparkline')).toHaveCount(0)
    if (vehicles.length) {
      await page.goto('/vehicles/REPORT-0')
      await expect(page.getByRole('heading', { name: 'REPORT-0', exact: true })).toBeVisible()
      const stat = page.locator('.detail-stats > div').filter({ hasText: 'Odometer' })
      await expect(stat).toContainText(String(readings.values[0] ?? '—'))
      await expect(page.getByText('Distance today', { exact: true })).toHaveCount(0)
    }
  })
}

test('live trip description acknowledges recorded GPS replay without inventing endpoints', async ({
  page,
}) => {
  await page.route('**/api/_wuu/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    await route.fulfill({
      json:
        path === '/api/_wuu/trips/REPORT-TRIP'
          ? {
              tripId: 'REPORT-TRIP',
              startTime: '2026-11-12T10:00:00Z',
              endTime: '2026-11-12T11:00:00Z',
            }
          : path.endsWith('/history')
            ? {
                tripId: 'REPORT-TRIP',
                vehicleId: 'REPORT-0',
                source: 'TRIP_ASSOCIATED',
                windowStart: '2026-11-12T10:00:00Z',
                windowEnd: '2026-11-12T11:00:00Z',
                truncated: false,
                points: [],
              }
            : pageOf([]),
    })
  })
  await page.goto('/trips/REPORT-TRIP')
  await expect(
    page.getByRole('heading', { name: 'Journey REPORT-TRIP', exact: true }),
  ).toBeVisible()
  await expect(page.getByText(/Recorded GPS replay below uses saved samples/)).toBeVisible()
  await expect(page.getByText('Route endpoints are not available for this trip.')).toBeVisible()
  await expect(page.getByText(/historical GPS points are not part/)).toHaveCount(0)
  const distance = page.locator('.detail-stats > div').filter({ hasText: 'Distance' })
  await expect(distance.locator('strong')).toHaveText('Not recorded')
})
