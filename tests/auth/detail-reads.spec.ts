import { expect, test } from '@playwright/test'

const records = [
  {
    resource: 'vehicles',
    id: 'DIRECT-1',
    heading: 'DIRECT-PLATE',
    dto: { vehicleId: 'DIRECT-1', licensePlate: 'DIRECT-PLATE' },
  },
  {
    resource: 'alerts',
    id: '901',
    heading: 'Direct alert',
    dto: { alertId: 901, message: 'Direct alert' },
  },
  {
    resource: 'trips',
    id: 'DIRECT-TRIP',
    heading: 'Journey DIRECT-TRIP',
    dto: { tripId: 'DIRECT-TRIP' },
  },
  {
    resource: 'geofences',
    id: '902',
    heading: 'Direct zone',
    dto: { geofenceId: 902, name: 'Direct zone', shapeType: 'CIRCLE', active: true },
  },
]

for (const record of records) {
  test(`${record.resource} detail does not depend on successful collection reads`, async ({
    page,
  }) => {
    await page.goto('/sign-in')
    await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
      'data-workspace-ready',
      'true',
    )
    await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
    await page.getByLabel('Password', { exact: true }).fill('test-only-password')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page).toHaveURL('/')
    await page.route('**/api/_wuu/**', async (route) => {
      const path = new URL(route.request().url()).pathname
      await route.fulfill({
        status: path === `/api/_wuu/${record.resource}/${record.id}` ? 200 : 503,
        json:
          path === `/api/_wuu/${record.resource}/${record.id}`
            ? record.dto
            : { message: 'Collection unavailable' },
      })
    })
    await page.goto(`/${record.resource}/${record.id}`)
    await expect(page.getByRole('heading', { name: record.heading, exact: true })).toBeVisible()
  })
}
