import { expect, test } from '@playwright/test'

test('public preview does not simulate subscriptions, payment or entitlement', async ({ page }) => {
  const calls: string[] = []
  page.on('request', (request) => {
    if (request.url().includes('/api/_wuu/subscriptions')) calls.push(request.url())
  })
  await page.goto('/subscriptions')
  await expect(
    page.getByRole('heading', { name: 'Subscriptions require a live workspace', exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Request pending enrollment' })).toHaveCount(0)
  expect(calls).toEqual([])
})
