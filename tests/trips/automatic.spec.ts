import { createConnection } from 'node:net'
import { execFileSync } from 'node:child_process'
import { expect, test, type Page } from '@playwright/test'
import {
  tripPageSchema,
  tripDtoSchema,
  tripHistorySchema,
} from '../../app/features/trips/api/contracts'
import { normalizeWireKeys } from '../../app/features/core/api/wire-format'

async function signIn(page: Page, email: string) {
  await page.goto('/sign-in?next=/trips')
  await page.waitForLoadState('networkidle')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('eight123')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/trips')
}

test('real TCP creates, completes and restarts associated trips across backend restart without inferred routes', async ({
  page,
  browser,
}) => {
  const tcpPort = process.env.TRACKING_TEST_TCP_PORT ?? '',
    pgPort = process.env.TRACKING_TEST_POSTGRES_PORT ?? ''
  if (!/^\d+$/.test(tcpPort) || !/^\d+$/.test(pgPort))
    throw new Error('Owned native test ports are required')
  const count = () =>
    Number(
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
          '-c',
          'SELECT count(*) FROM tracker_location_data',
        ],
        { encoding: 'utf8' },
      ).trim(),
    )
  let lastSecond = 0
  async function send(speed: string, latitude: string, offset = 0) {
    if (offset === 0)
      await expect.poll(() => Math.floor(Date.now() / 1000)).toBeGreaterThan(lastSecond)
    const at = new Date(Date.now() + offset * 1000)
    if (offset === 0) lastSecond = Math.floor(at.getTime() / 1000)
    const day = `${String(at.getUTCDate()).padStart(2, '0')}${String(at.getUTCMonth() + 1).padStart(2, '0')}${String(at.getUTCFullYear()).slice(-2)}`
    const frame = `*HQ,8168000008,V1,${at.toISOString().slice(11, 19).replaceAll(':', '')},A,${latitude},N,00006.0000,W,${speed},000,${day},FBFBBFF,460,00,10342,4283,10,25,128#`
    const before = count()
    const peer = createConnection({ host: '127.0.0.1', port: Number(tcpPort) })
    try {
      await new Promise<void>((resolve, reject) => {
        peer.once('connect', resolve)
        peer.once('error', reject)
      })
      peer.write(frame + frame) // Retransmission must not create a second point or trip.
      await expect.poll(count, { timeout: 20000 }).toBe(before + 1)
    } finally {
      peer.destroy()
    }
  }
  await signIn(page, process.env.TRACKING_TEST_EMAIL!)
  async function trips() {
    return tripPageSchema.parse(
      normalizeWireKeys(await (await page.request.get('/api/_wuu/trips')).json()),
    ).content
  }
  const initial = (await trips()).find(
    (trip) => trip.vehicleId === 'tracking-map-vehicle' && trip.isActive,
  )
  expect(initial).toBeDefined()
  expect(initial!.startReason).toBe('AUTO_DETECTED')
  const history = async (id: string) =>
    tripHistorySchema.parse(
      normalizeWireKeys(await (await page.request.get(`/api/_wuu/trips/${id}/history`)).json()),
    )
  const before = await history(initial!.tripId)
  expect(before.source).toBe('TRIP_ASSOCIATED')
  expect(before.points).toHaveLength(3)
  expect(before.points.every((point) => point.latitude > 51.49)).toBe(true) // Old received history was not silently backfilled.
  await page.goto(`/trips/${initial!.tripId}`)
  await expect(page.getByRole('region', { name: 'Recorded GPS replay' })).toContainText(
    'Recorded fixes associated with this trip',
  )
  await send('000.00', '5130.3000') // Confirms the stop begun before the runner restarted Java.
  await expect
    .poll(
      async () =>
        tripDtoSchema.parse(
          normalizeWireKeys(
            await (await page.request.get(`/api/_wuu/trips/${initial!.tripId}`)).json(),
          ),
        ).isActive,
    )
    .toBe(false)
  const completed = await history(initial!.tripId)
  expect(completed.points).toHaveLength(4)
  await page.reload()
  await expect(page.getByRole('region', { name: 'Recorded GPS replay' })).toContainText(
    '4 recorded fixes',
  )
  await send('012.96', '5130.6000')
  const active = (await trips()).filter((trip) => trip.isActive)
  expect(active).toHaveLength(1)
  expect(active[0]!.tripId).not.toBe(initial!.tripId)
  expect((await history(active[0]!.tripId)).points).toHaveLength(1)
  await send('012.96', '5129.4000', -3600)
  await send('012.96', '5500.0000', 3600)
  expect((await history(active[0]!.tripId)).points).toHaveLength(1)
  expect((await history(initial!.tripId)).points).toEqual(completed.points)
  await send('012.96', '5130.9000')
  expect((await history(active[0]!.tripId)).points).toHaveLength(2)
  const context = await browser.newContext()
  try {
    const foreign = await context.newPage()
    await signIn(foreign, 'foreign@telemetry-test.example')
    expect((await foreign.request.get(`/api/_wuu/trips/${initial!.tripId}`)).status()).toBe(404)
    expect((await foreign.request.get(`/api/_wuu/trips/${initial!.tripId}/history`)).status()).toBe(
      404,
    )
  } finally {
    await context.close()
  }
})
