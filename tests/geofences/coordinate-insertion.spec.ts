import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

test('live southern-hemisphere numeric vertex insertion remains near the existing boundary', async ({
  page,
}) => {
  await page.route('**/api/_wuu/geofences/9', (route) =>
    route.fulfill({
      json: {
        geofence_id: 9,
        name: 'Sydney depot',
        description: '',
        shape_type: 'POLYGON',
        active: true,
        vehicle_ids: ['VH-1'],
        points: [
          { latitude: -33, longitude: 151 },
          { latitude: -33, longitude: 151.01 },
          { latitude: -33.001, longitude: 151.01 },
        ],
      },
    }),
  )
  await page.goto('/sign-in?next=/geofences/9/edit')
  await page.getByLabel('Email', { exact: true }).fill(`geo-ui-write-${randomUUID()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/geofences/9/edit')
  await expect(page.getByLabel('Vertex 3 latitude', { exact: true })).toHaveValue('-33.001')
  await page.getByRole('button', { name: 'Add vertex using coordinates', exact: true }).click()
  const latitude = Number(await page.getByLabel('Vertex 4 latitude', { exact: true }).inputValue())
  expect(latitude).toBeCloseTo(-33.002, 6)
  await expect(page.getByLabel('Vertex 4 longitude', { exact: true })).toHaveValue('151.01')
})
