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
  const cancellations: string[] = []
  await page.route('**/api/_wuu/subscriptions/*/cancel', async (route) => {
    cancellations.push(route.request().url())
    if (cancellations.length === 1)
      return route.fulfill({ status: 502, json: { message: 'Cancellation unavailable' } })
    return route.continue()
  })
  await page.getByRole('button', { name: 'Confirm cancellation', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Confirm cancellation', exact: true }),
  ).toBeVisible()
  await expect(page.getByText('Status: PENDING', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Confirm cancellation', exact: true }).click()
  await expect(page.getByText('Status: CANCELED', { exact: true })).toBeVisible()
  expect(cancellations).toEqual([
    `http://localhost:3001${path}/cancel`,
    `http://localhost:3001${path}/cancel`,
  ])
  await expect(page.getByRole('alert')).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('Status: CANCELED', { exact: true })).toBeVisible()
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('mounted subscription snapshot refreshes grants and expiry without another enrollment', async ({
  page,
}) => {
  await page.goto('/sign-in?next=/subscriptions')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await page.getByLabel('Email', { exact: true }).fill(`snapshot-${Date.now()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'No enrollments', exact: true })).toBeVisible()
  let posts = 0
  let snapshot: Record<string, unknown>[] | undefined
  let reads = 0
  await page.route('**/api/_wuu/subscriptions', async (route) => {
    if (route.request().method() === 'POST') {
      posts++
      return route.continue()
    }
    reads++
    if (snapshot) return route.fulfill({ json: snapshot })
    return route.continue()
  })
  await page.getByRole('button', { name: 'Request pending enrollment' }).click()
  await expect(page.getByText('Status: PENDING', { exact: true })).toBeVisible()
  const owned = await (await page.request.get('/api/_wuu/subscriptions')).json()
  snapshot = [{ ...owned[0], status: 'TRIALING', remaining_units: 75 }]
  await expect(page.getByText('Spendable units: 0', { exact: true })).toBeVisible()
  await expect(page.getByText(/Last-checked snapshot/)).toBeVisible()
  const refresh = page.getByRole('button', { name: 'Refresh subscriptions', exact: true })
  const beforeGrant = reads
  await refresh.click()
  await expect(page.getByText('Status: TRIALING', { exact: true })).toBeVisible()
  await expect(page.getByText('Spendable units: 75', { exact: true })).toBeVisible()
  expect(reads).toBeGreaterThan(beforeGrant)
  // Java retains the lifecycle status but reports zero spendable units after grant expiry.
  snapshot = [{ ...owned[0], status: 'TRIALING', remaining_units: 0 }]
  const beforeExpiry = reads
  await refresh.click()
  await expect(page.getByText('Status: TRIALING', { exact: true })).toBeVisible()
  await expect(page.getByText('Spendable units: 0', { exact: true })).toBeVisible()
  expect(reads).toBeGreaterThan(beforeExpiry)
  expect(posts).toBe(1)
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
