import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

async function signIn(page: import('@playwright/test').Page, email: string) {
  await page.goto('/sign-in?next=/settings')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
}

test('tenant role catalog is a read-only, tenant-selected collection', async ({ page }) => {
  await signIn(page, 'roles@example.com')
  const panel = page.getByRole('region', { name: 'Tenant roles' })
  await expect(panel).toBeVisible()
  await expect(panel.getByRole('heading', { name: 'TENANT_ADMIN', exact: true })).toBeVisible()
  await expect(panel.getByText('roles.read', { exact: true })).toBeVisible()
  await expect(panel.getByRole('heading', { name: 'OPERATOR', exact: true })).toBeVisible()
  await expect(panel.getByText('No description provided.', { exact: true })).toBeVisible()
  await expect(panel.getByText('Showing 1–2 of 2 · page 1 of 1', { exact: true })).toBeVisible()

  // Query overrides are ignored by Java; nested collection paths and writes never pass the BFF.
  const ignored = await page.request.get('/api/_wuu/roles?orgId=999999')
  expect(ignored.status()).toBe(200)
  expect((await ignored.json()).content.map((role: { id: number }) => role.id)).toEqual([31, 32])
  expect((await page.request.get('/api/_wuu/roles/31')).status()).toBe(404)
  expect(
    (
      await page.request.post('/api/_wuu/roles', {
        headers: { origin: 'http://localhost:3001' },
        data: { name: 'SUPER_ADMIN' },
      })
    ).status(),
  ).toBe(405)
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('a user without roles.read sees no tenant role catalog', async ({ page }) => {
  await signIn(page, 'operator@example.com')
  await expect(page.getByRole('region', { name: 'Current server access' })).toBeVisible()
  await expect(page.getByRole('region', { name: 'Tenant roles' })).toHaveCount(0)
})
