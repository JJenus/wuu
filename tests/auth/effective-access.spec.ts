import { expect, test } from '@playwright/test'

test('settings show effective server access without enabling role-template platform access', async ({
  page,
}) => {
  await page.goto('/sign-in?next=/settings')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  const panel = page.getByRole('region', { name: 'Current server access' })
  await expect(panel.getByText('vehicles.read', { exact: true })).toBeVisible()
  await expect(panel.getByText('system.admin', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Preferences are unavailable', { exact: true })).toBeVisible()
  await page.route('**/api/_wuu/access', (route) =>
    route.fulfill({
      json: {
        user_id: 7,
        organization_id: 12,
        permissions: [],
        access_expires_at: '2099-01-01T00:00:00Z',
      },
    }),
  )
  await panel.getByRole('button', { name: 'Recheck access', exact: true }).click()
  await expect(
    panel.getByText('No effective permissions in the current context.', { exact: true }),
  ).toBeVisible()
  await page.goto('/admin')
  await expect(page).toHaveURL('/access-denied')
})
