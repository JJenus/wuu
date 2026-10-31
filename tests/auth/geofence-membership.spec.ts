import { expect, test } from '@playwright/test'

for (const ids of [['DIRECT-1', 'UNREADABLE-2'], []]) {
  test(`geofence renders authoritative ${ids.length}-assignment list without a singular fallback`, async ({
    page,
  }) => {
    await page.goto('/sign-in')
    await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
    await page.getByLabel('Password', { exact: true }).fill('test-only-password')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page).toHaveURL('/')
    await page.route('**/api/_wuu/**', async (route) => {
      const path = new URL(route.request().url()).pathname
      if (path === '/api/_wuu/geofences/903')
        return route.fulfill({
          json: {
            geofenceId: 903,
            name: 'Multi depot',
            shapeType: 'CIRCLE',
            active: true,
            vehicleId: 'DIRECT-1',
            vehicleIds: ids,
          },
        })
      if (path === '/api/_wuu/vehicles')
        return route.fulfill({
          json: {
            content: [{ vehicleId: 'DIRECT-1', licensePlate: 'DIRECT-PLATE' }],
            first: true,
            last: true,
            empty: false,
          },
        })
      return route.fulfill({ status: 503, json: { message: 'Unrelated collection unavailable' } })
    })
    await page.goto('/geofences/903')
    const members = page
      .locator('section')
      .filter({ has: page.getByRole('heading', { name: 'Linked vehicles', exact: true }) })
    await expect(members.getByText(`${ids.length} assigned`, { exact: true })).toBeVisible()
    if (ids.length) {
      await expect(
        members.getByRole('link', { name: 'View DIRECT-PLATE', exact: true }),
      ).toBeVisible()
      await expect(
        members.getByText('Vehicle details are unavailable for assigned vehicle UNREADABLE-2.', {
          exact: true,
        }),
      ).toBeVisible()
      await expect(
        members.getByText('No vehicles are linked to this zone.', { exact: true }),
      ).toHaveCount(0)
    } else {
      await expect(
        members.getByText('No vehicles are linked to this zone.', { exact: true }),
      ).toBeVisible()
      await expect(
        members.getByRole('link', { name: 'View DIRECT-PLATE', exact: true }),
      ).toHaveCount(0)
    }
  })
}

for (const failure of ['incomplete', 'forbidden', 'unavailable'] as const) {
  test(`geofence keeps known assignments visible when fleet details are ${failure}`, async ({
    page,
  }) => {
    await page.goto('/sign-in')
    await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
    await page.getByLabel('Password', { exact: true }).fill('test-only-password')
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page).toHaveURL('/')
    let recovered = false
    await page.route('**/api/_wuu/**', async (route) => {
      const path = new URL(route.request().url()).pathname
      if (path === '/api/_wuu/geofences/902')
        return route.fulfill({
          json: {
            geofenceId: 902,
            name: 'Assigned depot',
            shapeType: 'CIRCLE',
            active: true,
            vehicleId: 'DIRECT-1',
          },
        })
      if (path === '/api/_wuu/vehicles') {
        if (!recovered && failure !== 'incomplete')
          return route.fulfill({
            status: failure === 'forbidden' ? 403 : 503,
            json: { message: 'Fleet details unavailable' },
          })
        return route.fulfill({
          json: {
            content: [{ vehicleId: 'DIRECT-1', licensePlate: 'DIRECT-PLATE' }],
            first: true,
            last: recovered,
            empty: false,
          },
        })
      }
      return route.fulfill({ status: 503, json: { message: 'Unrelated collection unavailable' } })
    })
    await page.goto('/geofences/902')
    await expect(page.getByRole('heading', { name: 'Assigned depot', exact: true })).toBeVisible()
    const members = page
      .locator('section')
      .filter({ has: page.getByRole('heading', { name: 'Linked vehicles', exact: true }) })
    await expect(members.getByText('1 assigned', { exact: true })).toBeVisible()
    await expect(
      members.getByRole('heading', { name: 'Linked vehicle details could not load', exact: true }),
    ).toBeVisible()
    await expect(
      members.getByText('No vehicles are linked to this zone.', { exact: true }),
    ).toHaveCount(0)
    recovered = true
    await members.getByRole('button', { name: /try again|retry/i }).click()
    await expect(
      members.getByRole('link', { name: 'View DIRECT-PLATE', exact: true }),
    ).toBeVisible()
    await expect(
      members.getByRole('heading', { name: 'Linked vehicle details could not load', exact: true }),
    ).toHaveCount(0)
  })
}
