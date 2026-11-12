import { execFileSync } from 'node:child_process'
import { expect, test } from '@playwright/test'

test('real Java snapshot refreshes committed PostgreSQL positions without simulation @java', async ({
  page,
}) => {
  const port = process.env.TRACKING_TEST_POSTGRES_PORT ?? ''
  if (!/^[0-9]+$/.test(port))
    throw new Error('An owned loopback tracking_map_test database is required')
  function sql(statement: string) {
    execFileSync('/usr/lib/postgresql/18/bin/psql', [
      '-h',
      '127.0.0.1',
      '-p',
      port,
      '-U',
      'tracking_map_test',
      '-d',
      'tracking_map_test',
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      statement,
    ])
  }
  // Test-only database fixture, not a telemetry ingestion API or a device substitute.
  sql(`WITH point AS (
    INSERT INTO tracker_location_data(tracker_id, latitude, longitude, speed_kmh, recorded_at)
    VALUES ('tracking-map-tracker', 51.5, -0.1, 24, CURRENT_TIMESTAMP) RETURNING location_id
  ) UPDATE vehicles SET current_location_id=(SELECT location_id FROM point), last_telemetry_time=CURRENT_TIMESTAMP
    WHERE vehicle_id='tracking-map-vehicle'`)
  await page.goto('/sign-in?next=/map')
  await page.getByLabel('Email', { exact: true }).fill(process.env.TRACKING_TEST_EMAIL!)
  await page.getByLabel('Password', { exact: true }).fill(process.env.TRACKING_TEST_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/map')
  const card = page.locator('.tracking-vehicle-card').filter({ hasText: 'LIVE MAP 01' })
  await expect(card).toContainText('51.50000, -0.10000')
  await expect(page.getByRole('button', { name: /simulation/i })).toHaveCount(0)
  sql(`WITH point AS (
    INSERT INTO tracker_location_data(tracker_id, latitude, longitude, speed_kmh, recorded_at)
    VALUES ('tracking-map-tracker', 51.51, -0.1, 32, CURRENT_TIMESTAMP) RETURNING location_id
  ) UPDATE vehicles SET current_location_id=(SELECT location_id FROM point), last_telemetry_time=CURRENT_TIMESTAMP
    WHERE vehicle_id='tracking-map-vehicle'`)
  await expect(card).toContainText('51.51000, -0.10000', { timeout: 15000 })
  await expect(card).toContainText('32 km/h')
  await page.reload()
  await expect(card).toContainText('51.51000, -0.10000')
})
