import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test('owner enrollment/cancel, stable retry key, CSRF and administrative gateway denial', async ({
  page,
}) => {
  await page.goto('/sign-in?next=/subscriptions')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await page.getByLabel('Email', { exact: true }).fill(`subscriptions-${Date.now()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Subscriptions', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Usage tokens', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'No enrollments', exact: true })).toBeVisible()
  const keys: string[] = []
  await page.route('**/api/_wuu/subscriptions', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    keys.push(route.request().postDataJSON().operation_key)
    const response = await route.fetch()
    // Simulate an ambiguous network outcome AFTER the server committed enrollment.
    if (keys.length === 1)
      return route.fulfill({ status: 502, json: { message: 'Ambiguous outcome' } })
    return route.fulfill({ response })
  })
  await page.getByRole('button', { name: 'Request pending enrollment' }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page.reload()
  await expect(page.getByText('Status: PENDING', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Request pending enrollment' }).click()
  await expect(page.getByText('Status: PENDING', { exact: true })).toBeVisible()
  expect(keys).toHaveLength(2)
  expect(keys[0]).toBe(keys[1])
  await expect(page.getByText('Spendable units: 0', { exact: true })).toBeVisible()
  const owned = await (await page.request.get('/api/_wuu/subscriptions')).json()
  expect(owned).toHaveLength(1)
  const path = `/api/_wuu/subscriptions/${owned[0].id}`
  expect(
    (
      await page.request.post(`${path}/cancel`, {
        headers: { origin: 'https://evil.test' },
        data: {},
      })
    ).status(),
  ).toBe(403)
  expect(
    (
      await page.request.post(`${path}/grants`, {
        headers: { origin: 'http://localhost:3001' },
        data: {},
      })
    ).status(),
  ).toBe(405)
  expect(
    (
      await page.request.post('/api/_wuu/subscriptions/catalog', {
        headers: { origin: 'http://localhost:3001' },
        data: {},
      })
    ).status(),
  ).toBe(405)
  expect(
    (
      await page.request.post('/api/_wuu/subscriptions', {
        headers: { origin: 'http://localhost:3001' },
        data: { organization_id: 99 },
      })
    ).status(),
  ).toBe(400)
  expect(
    (
      await page.request.post('/api/_wuu/subscriptions', {
        headers: { origin: 'http://localhost:3001', 'content-type': 'application/json' },
        data: 'x'.repeat(5000),
      })
    ).status(),
  ).toBe(413)
  await page.getByRole('button', { name: 'Cancel enrollment', exact: true }).click()
  await page.getByRole('button', { name: 'Confirm cancellation', exact: true }).click()
  await expect(page.getByText('Status: CANCELED', { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByText('Status: CANCELED', { exact: true })).toBeVisible()
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('subscription denial remains an error and does not show samples', async ({ page }) => {
  await page.goto('/sign-in?next=/subscriptions')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await page.getByLabel('Email', { exact: true }).fill('subscription-denied@example.com')
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByText('Plans could not load', { exact: true })).toBeVisible()
  await expect(page.getByText('Enrollments could not load', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Request pending enrollment' })).toHaveCount(0)
})
