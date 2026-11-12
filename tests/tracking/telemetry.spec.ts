import { createConnection } from 'node:net'
import { execFileSync } from 'node:child_process'
import { expect, test } from '@playwright/test'

test('real TCP telemetry crosses Artemis into persisted positions and automatic map refresh @java', async ({
  page,
}) => {
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
  await expect(card).toContainText('No GPS location')
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
  } finally {
    socket.destroy()
  }
})
