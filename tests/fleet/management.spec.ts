import { randomUUID } from 'node:crypto'
import { createConnection } from 'node:net'
import { expect, test, type Page } from '@playwright/test'

const origin = 'http://localhost:3002'
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

test('edit metadata and correct unused assignment without changing device identity @java', async ({
  page,
}) => {
  await signIn(page)
  const suffix = randomUUID().slice(0, 8)
  const source = `source-${suffix}`,
    target = `target-${suffix}`,
    tracker = `gps-${suffix}`
  const device = `9${Math.floor(Math.random() * 900000000 + 100000000)}`
  for (const vehicle of [source, target]) {
    const response = await page.request.post('/api/_wuu/vehicles', {
      headers: { origin },
      data: { vehicle_id: vehicle },
    })
    expect(response.status()).toBe(201)
  }
  expect(
    (
      await page.request.post('/api/_wuu/trackers', {
        headers: { origin },
        data: { tracker_id: tracker, device_id: device, vehicle_id: source, model: 'Original GPS' },
      })
    ).status(),
  ).toBe(201)
  await page.goto(`/vehicles/${source}`)
  await page.getByRole('button', { name: 'Edit vehicle', exact: true }).click()
  const edit = page.getByRole('dialog', { name: 'Edit vehicle' })
  await expect(edit.getByLabel('Model', { exact: true })).toHaveValue('')
  await expect(edit.getByLabel('Registration', { exact: true })).toHaveValue('')
  await edit.getByLabel('Model', { exact: true }).fill('Updated van')
  await edit.getByLabel('Registration', { exact: true }).fill(`EDIT-${suffix}`)
  await edit.getByLabel('VIN', { exact: true }).fill('12345678901234567')
  await edit.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(edit).not.toBeVisible()
  await expect(page.getByRole('heading', { name: `EDIT-${suffix}`, exact: true })).toBeVisible()
  const assigned = page.getByRole('region', { name: 'Assigned trackers' })
  await assigned.getByRole('button', { name: `Edit tracker ${tracker}`, exact: true }).click()
  const profile = page.getByRole('dialog', { name: 'Edit tracker' })
  await expect(profile).toContainText(device)
  await profile.getByLabel('Tracker model', { exact: true }).fill('Updated GPS')
  await profile.getByLabel('Firmware version', { exact: true }).fill('2.3')
  await profile.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(profile).not.toBeVisible()
  await expect(assigned).toContainText('Updated GPS')
  await assigned.getByRole('button', { name: `Reassign tracker ${tracker}`, exact: true }).click()
  const move = page.getByRole('dialog', { name: 'Reassign unused tracker' })
  await move.getByLabel('Destination vehicle', { exact: true }).selectOption(target)
  await move
    .getByLabel('I confirm this unused tracker should move to the selected vehicle.', {
      exact: true,
    })
    .check()
  await move.getByRole('button', { name: 'Confirm reassignment', exact: true }).click()
  await expect(move).not.toBeVisible()
  await expect(assigned).not.toContainText(device)
  await page.goto(`/vehicles/${target}`)
  await expect(assigned).toContainText(device)
  await expect(assigned).toContainText('Updated GPS')
  await page.reload()
  await expect(assigned).toContainText('2.3')
  // In native mode the first real fix after the correction must belong to the target.
  if (process.env.TRACKING_TEST_TCP_PORT) {
    const at = new Date()
    const date = `${String(at.getUTCDate()).padStart(2, '0')}${String(at.getUTCMonth() + 1).padStart(2, '0')}${String(at.getUTCFullYear()).slice(-2)}`
    const packet = `*HQ,${device},V1,${at.toISOString().slice(11, 19).replaceAll(':', '')},A,5130.3000,N,00006.0000,W,012.96,000,${date},FBFBBFF,460,00,10342,4283,10,25,128#`
    const peer = createConnection({
      host: '127.0.0.1',
      port: Number(process.env.TRACKING_TEST_TCP_PORT),
    })
    try {
      await new Promise<void>((resolve, reject) => {
        peer.once('connect', resolve)
        peer.once('error', reject)
      })
      peer.write(packet)
      await expect
        .poll(
          async () =>
            (await (await page.request.get(`/api/_wuu/vehicles/${target}`)).json()).current_location
              ?.latitude,
          { timeout: 20000 },
        )
        .toBe(51.505)
    } finally {
      peer.destroy()
    }
    await assigned.getByRole('button', { name: `Reassign tracker ${tracker}`, exact: true }).click()
    await move.getByLabel('Destination vehicle', { exact: true }).selectOption(source)
    await move
      .getByLabel('I confirm this unused tracker should move to the selected vehicle.', {
        exact: true,
      })
      .check()
    await move.getByRole('button', { name: 'Confirm reassignment', exact: true }).click()
    await expect(move.getByRole('alert')).toContainText('Only unused trackers')
    await expect(move.getByLabel('Destination vehicle', { exact: true })).toHaveValue(source)
    await move.getByRole('button', { name: 'Cancel', exact: true }).click()
  }
})

test('management boundary rejects identity, measured-state, query and origin overrides', async ({
  page,
}) => {
  await signIn(page)
  const path = '/api/_wuu/trackers/missing/profile'
  const profile = { model: null, protocol: null, firmware_version: null, sim_number: null }
  for (const extra of [
    { device_id: 'new' },
    { organization_id: 9 },
    { battery_level: 100 },
    { vehicle_id: 'new' },
  ])
    expect(
      (
        await page.request.patch(path, { headers: { origin }, data: { ...profile, ...extra } })
      ).status(),
    ).toBe(400)
  expect(
    (
      await page.request.patch(`${path}?organization_id=9`, { headers: { origin }, data: profile })
    ).status(),
  ).toBe(400)
  expect(
    (
      await page.request.patch(path, {
        headers: { origin: 'https://foreign.example' },
        data: profile,
      })
    ).status(),
  ).toBe(403)
  expect(
    (
      await page.request.put('/api/_wuu/trackers/missing', { headers: { origin }, data: {} })
    ).status(),
  ).toBe(405)
})

test('mobile edit retains its draft when permission is revoked before save @java', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await signIn(page)
  const vehicle = `revoked-${randomUUID().slice(0, 8)}`
  expect(
    (
      await page.request.post('/api/_wuu/vehicles', {
        headers: { origin },
        data: { vehicle_id: vehicle, model: 'Original' },
      })
    ).status(),
  ).toBe(201)
  await page.goto(`/vehicles/${vehicle}`)
  await page.getByRole('button', { name: 'Edit vehicle', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Edit vehicle' })
  await dialog.getByLabel('Model', { exact: true }).fill('Retained draft')
  let writes = 0
  page.on('request', (request) => {
    if (request.method() === 'PUT') writes++
  })
  await page.route('**/api/_wuu/access', async (route) => {
    const response = await route.fetch()
    await route.fulfill({
      response,
      json: { ...(await response.json()), permissions: ['vehicles.read', 'devices.read'] },
    })
  })
  await dialog.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('do not currently have permission')
  await expect(dialog.getByLabel('Model', { exact: true })).toHaveValue('Retained draft')
  expect(writes).toBe(0)
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Edit vehicle', exact: true })).not.toBeVisible()
})

test('native management keeps foreign tracker and vehicle ownership inaccessible @java', async ({
  page,
}) => {
  test.skip(!process.env.FLEET_TEST_JAVA_ORIGIN, 'Requires the owned Java tenant fixtures')
  await signIn(page)
  const response = await page.request.post('/api/_wuu/trackers/tracker-foreign/reassign', {
    headers: { origin },
    data: { source_vehicle_id: 'telemetry-foreign', target_vehicle_id: 'tracking-map-vehicle' },
  })
  expect(response.status()).toBe(404)
  const used = await page.request.post('/api/_wuu/trackers/tracker-owner/reassign', {
    headers: { origin },
    data: { source_vehicle_id: 'tracking-map-vehicle', target_vehicle_id: 'telemetry-foreign' },
  })
  expect(used.status()).toBe(404)
  const profile = await page.request.patch('/api/_wuu/trackers/tracker-foreign/profile', {
    headers: { origin },
    data: { model: 'forbidden', protocol: null, firmware_version: null, sim_number: null },
  })
  expect(profile.status()).toBe(404)
})
