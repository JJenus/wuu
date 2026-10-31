import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'

const origin = 'http://localhost:3001'
const path = '/api/_wuu/geofences/9'

test('geofence edit gateway enforces authentication, origin, strict JSON and narrow paths', async ({
  request,
}) => {
  expect((await request.put(path, { headers: { origin }, data: { name: 'Depot' } })).status()).toBe(
    401,
  )
  const login = await request.post('/api/auth/login', {
    headers: { origin },
    data: { email: `geo-ui-write-${randomUUID()}@example.com`, password: 'test-only-password' },
  })
  expect(login.status()).toBe(200)
  const saved = await request.put(path, {
    headers: { origin },
    data: { name: 'Renamed', description: '', active: false, vehicleIds: [] },
  })
  expect(saved.status()).toBe(200)
  expect(await saved.json()).toMatchObject({
    name: 'Renamed',
    description: '',
    active: false,
    vehicle_ids: [],
  })
  expect(
    (
      await request.put(path, { headers: { origin: 'https://evil.test' }, data: { name: 'Bad' } })
    ).status(),
  ).toBe(403)
  expect((await request.put(path, { data: { name: 'Bad' } })).status()).toBe(403)
  expect(
    (await request.put(`${path}?orgId=3`, { headers: { origin }, data: { name: 'Bad' } })).status(),
  ).toBe(400)
  for (const data of [
    { createdBy: 'forged' },
    { vehicle_ids: [] },
    { organizationId: 3 },
    { points: [null] },
  ])
    expect((await request.put(path, { headers: { origin }, data })).status()).toBe(400)
  expect(
    (
      await request.put(path, { headers: { origin, 'content-type': 'text/plain' }, data: '{}' })
    ).status(),
  ).toBe(415)
  expect(
    (
      await request.put(path, {
        headers: { origin, 'content-type': 'application/json' },
        data: 'x'.repeat(16385),
      })
    ).status(),
  ).toBe(413)
  for (const target of [
    'geofences/0',
    'geofences/01',
    'geofences/9223372036854775808',
    'geofences/9/vehicles',
    'geofences/check-violations',
    'alerts/rules/key',
  ])
    expect(
      (
        await request.put(`/api/_wuu/${target}`, { headers: { origin }, data: { name: 'Bad' } })
      ).status(),
    ).toBe(405)
  expect((await request.delete(path, { headers: { origin }, data: {} })).status()).toBe(405)
  expect((await request.get(path)).status()).toBe(200)
  expect(await (await request.get(path)).json()).toMatchObject({ name: 'Renamed', vehicle_ids: [] })
  expect(
    (
      await request.put('/api/_wuu/geofences/10', {
        headers: { origin },
        data: { name: 'Foreign' },
      })
    ).status(),
  ).toBe(404)
  await request.post('/api/auth/login', {
    headers: { origin },
    data: { email: `geo-ui-denied-${randomUUID()}@example.com`, password: 'test-only-password' },
  })
  expect(
    (await request.put(path, { headers: { origin }, data: { name: 'Denied' } })).status(),
  ).toBe(403)
})
