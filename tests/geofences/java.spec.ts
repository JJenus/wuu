import { expect, test, request as requestFactory } from '@playwright/test'
import { randomUUID } from 'node:crypto'

test('rule configured through UI persists and can be disabled @java', async ({ page }) => {
  const id = process.env.GEOFENCE_RULE_TEST_ZONE!
  await page.goto(`/sign-in?next=/geofences/${id}`)
  await page.getByLabel('Email', { exact: true }).fill(process.env.GEOFENCE_TEST_EMAIL!)
  await page.getByLabel('Password', { exact: true }).fill(process.env.GEOFENCE_TEST_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(`/geofences/${id}`)
  await page.getByRole('button', { name: 'Add geofence rule', exact: true }).click()
  const key = 'ui_rule_' + randomUUID().slice(0, 8)
  await page.getByLabel('Rule key', { exact: true }).fill(key)
  await page.getByLabel('Rule name', { exact: true }).fill('Persisted entry and exit rule')
  await page.getByRole('button', { name: 'Save rule', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.reload()
  const saved = page.getByRole('article', { name: `Rule ${key}`, exact: true })
  await expect(saved).toContainText('BOTH')
  await expect(saved).toContainText('Enabled')
  await saved
    .getByRole('button', { name: 'Disable Persisted entry and exit rule', exact: true })
    .click()
  await expect(saved).toContainText('Disabled')
  await page.reload()
  await expect(saved).toContainText('Disabled')
})

test('real Java circle creation, polygon edit, unlink-all and tenant rollback @java', async ({
  page,
}) => {
  await page.goto('/sign-in?next=/geofences')
  await page.getByLabel('Email', { exact: true }).fill(process.env.GEOFENCE_TEST_EMAIL!)
  await page.getByLabel('Password', { exact: true }).fill(process.env.GEOFENCE_TEST_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/geofences')
  await page.getByRole('link', { name: 'Create geofence', exact: true }).click()
  await page.getByLabel('Geofence name', { exact: true }).fill(`Depot ${randomUUID().slice(0, 8)}`)
  await page.getByLabel('Description', { exact: true }).fill('Persisted Java boundary')
  await page.getByRole('button', { name: 'Set circle using coordinates', exact: true }).click()
  await page.getByLabel('Center latitude', { exact: true }).fill('51.5')
  await page.getByLabel('Center longitude', { exact: true }).fill('-0.1')
  await page.getByLabel('Radius (meters)', { exact: true }).fill('250')
  await page.getByLabel('Assign LONDON-1', { exact: true }).check()
  await page.getByLabel('Assign LONDON-2', { exact: true }).check()
  await page.getByRole('button', { name: 'Create geofence', exact: true }).click()
  await expect(page).toHaveURL(/\/geofences\/[1-9][0-9]*$/)
  const id = new URL(page.url()).pathname.split('/').at(-1)!
  await page.reload()
  await expect(page.getByText('2 assigned', { exact: true })).toBeVisible()
  await expect(page.getByText('Persisted Java boundary', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Edit geofence', exact: true }).click()
  await expect(page.getByLabel('Radius (meters)', { exact: true })).toHaveValue('250')
  await page.getByRole('button', { name: 'Draw polygon', exact: true }).click()
  const points = [
    { lat: 51.5, lng: -0.1 },
    { lat: 51.5, lng: -0.09 },
    { lat: 51.51, lng: -0.09 },
  ]
  for (const [index, point] of points.entries()) {
    await page.getByRole('button', { name: 'Add vertex using coordinates', exact: true }).click()
    await page.getByLabel(`Vertex ${index + 1} latitude`, { exact: true }).fill(String(point.lat))
    await page.getByLabel(`Vertex ${index + 1} longitude`, { exact: true }).fill(String(point.lng))
  }
  await page.getByLabel('Description', { exact: true }).fill('')
  await page.getByLabel('Assign LONDON-1', { exact: true }).uncheck()
  await page.getByLabel('Assign LONDON-2', { exact: true }).uncheck()
  await page.getByRole('switch', { name: 'Enabled', exact: true }).uncheck()
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(page).toHaveURL(`/geofences/${id}`)
  await page.reload()
  await expect(page.getByText('0 assigned', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Edit geofence', exact: true }).click()
  for (const [index, point] of points.entries()) {
    await expect(page.getByLabel(`Vertex ${index + 1} latitude`, { exact: true })).toHaveValue(
      String(point.lat),
    )
    await expect(page.getByLabel(`Vertex ${index + 1} longitude`, { exact: true })).toHaveValue(
      String(point.lng),
    )
  }
  await expect(page.getByRole('switch', { name: 'Enabled', exact: true })).not.toBeChecked()
  await expect(page.getByLabel('Description', { exact: true })).toHaveValue('')

  const path = `/api/_wuu/geofences/${id}`
  const before = await (await page.request.get(path)).json()
  const headers = { origin: 'http://localhost:3001' }
  const foreign = await page.request.put(path, {
    headers,
    data: { name: 'Must not persist', vehicleIds: ['FOREIGN-VH'] },
  })
  expect(foreign.status()).toBe(400)
  const invalid = await page.request.put(path, {
    headers,
    data: {
      name: 'Must not persist',
      points: [
        { latitude: 51.5, longitude: -0.1 },
        { latitude: 51.51, longitude: -0.09 },
        { latitude: 51.5, longitude: -0.09 },
        { latitude: 51.51, longitude: -0.1 },
      ],
    },
  })
  expect(invalid.status()).toBe(400)
  expect(await (await page.request.get(path)).json()).toEqual(before)
  const beta = await requestFactory.newContext({ baseURL: 'http://localhost:3001' })
  try {
    expect(
      (
        await beta.post('/api/auth/login', {
          headers,
          data: {
            email: process.env.GEOFENCE_TEST_BETA_EMAIL,
            password: process.env.GEOFENCE_TEST_PASSWORD,
          },
        })
      ).status(),
    ).toBe(200)
    expect((await beta.get(path)).status()).toBe(404)
    expect((await beta.put(path, { headers, data: { name: 'Foreign edit' } })).status()).toBe(404)
  } finally {
    await beta.dispose()
  }
})
