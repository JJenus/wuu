import { expect, test, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

async function signIn(page: Page, writer = true) {
  await page.goto('/sign-in?next=/vehicles')
  await page
    .getByLabel('Email', { exact: true })
    .fill(
      process.env.FLEET_TEST_EMAIL ||
        `fleet-${writer ? 'writer' : 'reader'}-${randomUUID()}@example.com`,
    )
  await page
    .getByLabel('Password', { exact: true })
    .fill(process.env.FLEET_TEST_PASSWORD || 'test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/vehicles')
}

test('create vehicle, register assigned tracker, and reload persisted detail @java', async ({
  page,
}) => {
  await signIn(page)
  const suffix = randomUUID().slice(0, 8)
  await page.getByRole('button', { name: 'Add vehicle', exact: true }).click()
  await page.getByLabel('Vehicle ID', { exact: true }).fill(`van-${suffix}`)
  await page.getByLabel('Model', { exact: true }).fill('Transit')
  await page.getByLabel('Registration', { exact: true }).fill(`PLATE-${suffix}`)
  await page.getByLabel('VIN', { exact: true }).fill('12345678901234567')
  await page.getByRole('button', { name: 'Save vehicle', exact: true }).click()
  await expect(page).toHaveURL(`/vehicles/van-${suffix}`)
  await expect(page.getByRole('heading', { name: `PLATE-${suffix}` })).toBeVisible()
  await page.getByRole('button', { name: 'Register tracker', exact: true }).click()
  await page.getByLabel('Tracker ID', { exact: true }).fill(`tracker-${suffix}`)
  await page.getByLabel('Device ID', { exact: true }).fill(`gps-${suffix}`)
  await page.getByLabel('Tracker model', { exact: true }).fill('Autoseeker')
  await page.getByLabel('Protocol', { exact: true }).fill('GT06')
  await page.getByLabel('Firmware version', { exact: true }).fill('1.2')
  await page.getByLabel('SIM number', { exact: true }).fill('555')
  await page.getByRole('button', { name: 'Save tracker', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.getByRole('region', { name: 'Assigned trackers' })).toContainText(
    `gps-${suffix}`,
  )
  await page.reload()
  const section = page.getByRole('region', { name: 'Assigned trackers' })
  await expect(section).toContainText(`tracker-${suffix}`)
  await expect(section).toContainText('GT06')
  await expect(section).toContainText('Autoseeker')
  await expect(section).toContainText('1.2')
  await expect(page.getByText('12345678901234567', { exact: true })).toBeVisible()
  await expect(page.getByText('This vehicle is offline.', { exact: false })).toBeVisible()
  await page.getByRole('link', { name: 'Back to your fleet', exact: true }).click()
  await expect(page.getByRole('link', { name: `View PLATE-${suffix}`, exact: true })).toBeVisible()
})

test('client role names do not enable writes and backend denial passes through', async ({
  page,
}) => {
  await signIn(page, false)
  await expect(page.getByRole('button', { name: 'Add vehicle', exact: true })).toBeDisabled()
  const response = await page.request.post('/api/_wuu/vehicles', {
    headers: { origin: 'http://localhost:3002' },
    data: { vehicle_id: 'denied' },
  })
  expect(response.status()).toBe(403)
})

test('gateway rejects forged tenant, unsupported writes, cross-origin and query overrides', async ({
  page,
}) => {
  await signIn(page)
  const post = (path: string, body: unknown, origin = 'http://localhost:3002') =>
    page.request.post(path, { headers: { origin }, data: body })
  expect(
    (await post('/api/_wuu/vehicles', { vehicle_id: 'bad', organization_id: 99 })).status(),
  ).toBe(400)
  expect(
    (
      await post('/api/_wuu/trackers', {
        tracker_id: 'bad',
        device_id: 'd',
        vehicle_id: 'v',
        actor_id: 7,
      })
    ).status(),
  ).toBe(400)
  expect(
    (await post('/api/_wuu/vehicles?organization_id=99', { vehicle_id: 'bad' })).status(),
  ).toBe(400)
  expect(
    (await post('/api/_wuu/vehicles', { vehicle_id: 'bad' }, 'https://outsider.example')).status(),
  ).toBe(403)
  expect((await post('/api/_wuu/trackers/t/assign', {})).status()).toBe(405)
  expect(
    (
      await page.request.delete('/api/_wuu/vehicles/v', {
        headers: { origin: 'http://localhost:3002' },
      })
    ).status(),
  ).toBe(405)
})

test('failed writes preserve draft and revoked permissions prevent submission', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await signIn(page)
  await page.getByRole('button', { name: 'Add vehicle', exact: true }).click()
  await page.getByLabel('Vehicle ID', { exact: true }).fill('draft-van')
  let attempts = 0
  await page.route('**/api/_wuu/vehicles', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    attempts++
    return route.fulfill({ status: 503, json: { message: 'Temporary fleet failure' } })
  })
  await page.getByRole('button', { name: 'Save vehicle', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Reload and check')
  await expect(page.getByLabel('Vehicle ID', { exact: true })).toHaveValue('draft-van')
  expect(attempts).toBe(1)
  await page.route('**/api/_wuu/access', (route) =>
    route.fulfill({
      json: {
        user_id: 7,
        organization_id: 12,
        permissions: ['vehicles.read'],
        access_expires_at: '2099-01-01T00:00:00Z',
      },
    }),
  )
  await page.getByRole('button', { name: 'Save vehicle', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText(
    'do not currently have permission',
  )
  expect(attempts).toBe(1)
})
