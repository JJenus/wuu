import { createConnection } from 'node:net'
import { execFileSync } from 'node:child_process'
import { expect, test } from '@playwright/test'
import { alertDtoSchema, alertPageSchema } from '../../app/features/alerts/api/contracts'
import { normalizeWireKeys } from '../../app/features/core/api/wire-format'

test('real TCP telemetry crosses Artemis into persisted positions and automatic map refresh @java', async ({
  page,
}) => {
  if (process.env.TRACKING_TEST_API_V1) test.setTimeout(120000)
  const tcpPort = process.env.TRACKING_TEST_TCP_PORT ?? ''
  const pgPort = process.env.TRACKING_TEST_POSTGRES_PORT ?? ''
  if (!/^[0-9]+$/.test(tcpPort) || !/^[0-9]+$/.test(pgPort))
    throw new Error('Owned loopback TCP and tracking_map_test database ports are required')
  function countLocations() {
    return Number(
      execFileSync(
        '/usr/lib/postgresql/18/bin/psql',
        [
          '-h',
          '127.0.0.1',
          '-p',
          pgPort,
          '-U',
          'tracking_map_test',
          '-d',
          'tracking_map_test',
          '-At',
          '-v',
          'ON_ERROR_STOP=1',
          '-c',
          'SELECT count(*) FROM tracker_location_data',
        ],
        { encoding: 'utf8' },
      ).trim(),
    )
  }
  function packet(device: string, latitude: string) {
    const now = new Date()
    const time = now.toISOString().slice(11, 19).replaceAll(':', '')
    const date = `${String(now.getUTCDate()).padStart(2, '0')}${String(now.getUTCMonth() + 1).padStart(2, '0')}${String(now.getUTCFullYear()).slice(-2)}`
    return `*HQ,${device},V1,${time},A,${latitude},N,00006.0000,W,012.96,000,${date},FBFBBFF,460,00,10342,4283,10,25,128#`
  }
  await page.goto('/sign-in?next=/map')
  // The cold production page can show SSR inputs before hydration attaches v-model.
  await page.waitForLoadState('networkidle')
  await page.getByLabel('Email', { exact: true }).fill(process.env.TRACKING_TEST_EMAIL!)
  await page.getByLabel('Password', { exact: true }).fill(process.env.TRACKING_TEST_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/map')
  const card = page.locator('.tracking-vehicle-card').filter({ hasText: 'LIVE MAP 01' })
  await expect(card).toContainText(
    process.env.TRACKING_TEST_INITIAL_COORDINATES ?? 'No GPS location',
  )
  const before = countLocations()
  const socket = createConnection({ host: '127.0.0.1', port: Number(tcpPort) })
  try {
    await new Promise<void>((resolve, reject) => {
      socket.once('connect', resolve)
      socket.once('error', reject)
    })
    const first = packet('8168000008', '5130.0000')
    // Fragmentation crosses the real TCP framing boundary; no database fixture writes.
    socket.write(first.slice(0, 22))
    socket.write(first.slice(22))
    if (process.env.TRACKING_TEST_DURABILITY) socket.write(first)
    await expect.poll(countLocations, { timeout: 20000 }).toBe(before + 1)
    await expect(card).toContainText('51.50000, -0.10000', { timeout: 15000 })
    socket.write(packet('8168000008', '5130.6000'))
    await expect.poll(countLocations, { timeout: 20000 }).toBe(before + 2)
    await expect(card).toContainText('51.51000, -0.10000', { timeout: 15000 })
    // Unknown and malformed devices cannot create saved locations. A registered
    // foreign tracker resolves its own tenant; it never appears in this map.
    socket.write(
      packet('8168000007', 'NaN') +
        packet('8168000006', '5130.0000') +
        packet('8168000009', '5131.0000'),
    )
    await expect.poll(countLocations, { timeout: 20000 }).toBe(before + 3)
    expect((await page.request.get('/api/_wuu/vehicles/telemetry-foreign')).status()).toBe(404)
    await page.reload()
    await expect(card).toContainText('51.51000, -0.10000')
    await expect(page.locator('.tracking-vehicle-card')).toHaveCount(1)
    expect(countLocations()).toBe(before + 3)
    await expect(page.getByRole('button', { name: /simulation/i })).toHaveCount(0)
    if (process.env.TRACKING_TEST_HISTORY) {
      await page.goto('/trips/recorded-native')
      const replay = page.getByRole('region', { name: 'Recorded GPS replay' })
      await expect(replay).toContainText('3 recorded fixes')
      await expect(replay).toContainText('51.48333, -0.10000')
      const slider = replay.getByRole('slider', { name: 'Recorded replay timeline' })
      await slider.fill('600')
      await expect(replay).toContainText('Recording gap')
      await expect(replay.locator('.geo-vehicle-marker')).toHaveCount(0)
      await slider.fill((await slider.getAttribute('max'))!)
      await expect(replay).toContainText('51.50500, -0.10000')
      await page.reload()
      await expect(replay).toContainText('3 recorded fixes')
      await page.goto('/trips/empty-native')
      await expect(page.getByRole('region', { name: 'Recorded GPS replay' })).toContainText(
        'No recorded GPS fixes in this time window',
      )
      expect((await page.request.get('/api/_wuu/trips/foreign-native/history')).status()).toBe(404)
      expect(
        (await page.request.get('/api/_wuu/trips/recorded-native/history?limit=0')).status(),
      ).toBe(400)
      const partial = await page.request.get('/api/_wuu/trips/recorded-native/history?limit=1')
      expect(partial.status()).toBe(200)
      expect(await partial.json()).toMatchObject({
        truncated: true,
        points: [expect.objectContaining({ latitude: expect.any(Number) })],
      })
    }
    if (process.env.TRACKING_TEST_API_V1) {
      // Actual browser -> centralized BFF registry -> canonical Java alert/geofence routes.
      await page.goto('/geofences/new')
      await page.getByLabel('Geofence name', { exact: true }).fill('Canonical native depot')
      await page.getByRole('button', { name: 'Set circle using coordinates', exact: true }).click()
      await page.getByLabel('Center latitude', { exact: true }).fill('51.5')
      await page.getByLabel('Center longitude', { exact: true }).fill('-0.1')
      await page.getByLabel('Radius (meters)', { exact: true }).fill('250')
      await page.getByLabel('Assign LIVE MAP 01', { exact: true }).check()
      await page.getByRole('button', { name: 'Create geofence', exact: true }).click()
      await expect(page).toHaveURL(/\/geofences\/\d+$/)
      await page.reload()
      await expect(
        page.getByRole('heading', { name: 'Canonical native depot', exact: true }),
      ).toBeVisible()
      await page.getByRole('button', { name: 'Add geofence rule', exact: true }).click()
      await page.getByLabel('Rule key', { exact: true }).fill('native_route_rule')
      await page.getByLabel('Rule name', { exact: true }).fill('Canonical native rule')
      await page.getByRole('button', { name: 'Save rule', exact: true }).click()
      const rule = page.getByRole('article', { name: 'Rule native_route_rule', exact: true })
      await expect(rule).toContainText('Enabled')
      await page.reload()
      await rule.getByRole('button', { name: 'Disable Canonical native rule', exact: true }).click()
      await expect(rule).toContainText('Disabled')
      await page.reload()
      await expect(rule).toContainText('Disabled')

      const alertsResponse = await page.request.get('/api/_wuu/alerts')
      expect(alertsResponse.status()).toBe(200)
      const alerts = alertPageSchema.parse(normalizeWireKeys(await alertsResponse.json()))
      const alert = alerts.content[0]
      expect(alert).toBeDefined()
      const headers = { origin: new URL(page.url()).origin }
      const acknowledged = await page.request.post(
        `/api/_wuu/alerts/${alert!.alertId}/acknowledge`,
        { headers, data: {} },
      )
      expect(acknowledged.status()).toBe(200)
      expect(alertDtoSchema.parse(normalizeWireKeys(await acknowledged.json())).acknowledged).toBe(
        true,
      )
      const resolved = await page.request.post(`/api/_wuu/alerts/${alert!.alertId}/resolve`, {
        headers,
        data: { resolutionNotes: 'Verified canonical route persistence' },
      })
      expect(resolved.status()).toBe(200)
      const saved = await page.request.get(`/api/_wuu/alerts/${alert!.alertId}`)
      expect(saved.status()).toBe(200)
      expect(alertDtoSchema.parse(normalizeWireKeys(await saved.json()))).toMatchObject({
        resolved: true,
        resolutionNotes: 'Verified canonical route persistence',
      })
      expect((await page.request.get('/api/_wuu/notifications')).status()).toBe(200)
    }
  } finally {
    socket.destroy()
  }
})
