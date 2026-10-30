import { expect, test } from '@playwright/test'

const origin = 'http://localhost:3001'
test('alert gateway admits exact transitions without caller-selected actor', async ({
  request,
}) => {
  expect(
    (
      await request.post('/api/_wuu/alerts/1/acknowledge', { headers: { origin }, data: {} })
    ).status(),
  ).toBe(401)
  expect(
    (
      await request.post('/api/auth/login', {
        headers: { origin },
        data: { email: 'operator@example.com', password: 'test-only-password' },
      })
    ).status(),
  ).toBe(200)
  const acknowledge = await request.post('/api/_wuu/alerts/1/acknowledge', {
    headers: { origin },
    data: {},
  })
  expect(acknowledge.status()).toBe(200)
  expect(await acknowledge.json()).toMatchObject({ acknowledged: true, acknowledgedBy: '7' })
  const resolved = await request.post('/api/_wuu/alerts/1/resolve', {
    headers: { origin },
    data: { resolutionNotes: '  Reviewed and confirmed safe  ' },
  })
  expect(resolved.status()).toBe(200)
  expect(await resolved.json()).toMatchObject({
    resolved: true,
    resolvedBy: '7',
    resolutionNotes: 'Reviewed and confirmed safe',
  })
  for (const action of ['acknowledge', 'resolve']) {
    const path = `/api/_wuu/alerts/1/${action}`
    expect(
      (await request.post(path, { headers: { origin: 'https://evil.test' }, data: {} })).status(),
    ).toBe(403)
    expect(
      (await request.post(`${path}?orgId=99`, { headers: { origin }, data: {} })).status(),
    ).toBe(400)
    expect(
      (
        await request.post(path, {
          headers: { origin },
          data: { acknowledgedBy: 'forged', resolvedBy: 'forged' },
        })
      ).status(),
    ).toBe(400)
    expect(
      (
        await request.post(path, { headers: { origin, 'content-type': 'text/plain' }, data: '{}' })
      ).status(),
    ).toBe(415)
    expect(
      (
        await request.post(path, {
          headers: { origin, 'content-type': 'application/json' },
          data: 'x'.repeat(4097),
        })
      ).status(),
    ).toBe(413)
  }
  for (const path of [
    'alerts/bulk/resolve',
    'alerts/0/resolve',
    'alerts/1/reopen',
    'geofences/1/resolve',
    'alerts/1/resolve/extra',
  ])
    expect(
      (await request.post(`/api/_wuu/${path}`, { headers: { origin }, data: {} })).status(),
    ).toBe(405)
  expect(
    (
      await request.post('/api/_wuu/alerts/2/acknowledge', { headers: { origin }, data: {} })
    ).status(),
  ).toBe(404)
  expect(
    (
      await request.post('/api/auth/login', {
        headers: { origin },
        data: { email: 'alert-denied@example.com', password: 'test-only-password' },
      })
    ).status(),
  ).toBe(200)
  expect(
    (
      await request.post('/api/_wuu/alerts/1/acknowledge', { headers: { origin }, data: {} })
    ).status(),
  ).toBe(403)
})
