import { expect, test } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test('backend accounts select an email, clear stale passwords, and still require authentication', async ({
  page,
  context,
}) => {
  await page.goto('/sign-in?next=/vehicles')
  const operator = page.getByRole('button', { name: 'Fleet Operator operator@example.com' })
  await expect(operator).toBeVisible()
  await page.getByLabel('Password', { exact: true }).fill('stale-password')
  await operator.click()
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('operator@example.com')
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('Password', { exact: true })).toBeFocused()
  expect((await context.cookies()).some((c) => c.name === 'wuu_session')).toBe(false)
  await page.getByLabel('Password', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Sign-in failed')
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/vehicles')
})

test('new backend users appear after refreshing without frontend roster changes', async ({
  page,
  request,
}) => {
  await page.goto('/sign-in')
  await expect(page.getByRole('heading', { name: 'Choose a local account' })).toBeVisible()
  expect(
    (
      await request.post('http://127.0.0.1:18080/__test/accounts', {
        data: { email: 'additional@example.com', display_name: 'Additional Account' },
      })
    ).status(),
  ).toBe(201)
  await page.getByRole('button', { name: 'Refresh accounts' }).click()
  await page.getByRole('button', { name: 'Additional Account additional@example.com' }).click()
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('additional@example.com')
  const response = await request.get('/api/auth/accounts')
  expect(response.headers()['cache-control']).toBe('no-store')
  expect(await response.text()).not.toMatch(/password|token|roles|organization_id/)
})

test('directory failure does not block manual sign-in; nonlocal hosts cannot list accounts', async ({
  page,
  request,
}) => {
  await page.route('**/api/auth/accounts', (route) =>
    route.fulfill({ status: 502, json: { message: 'Local accounts could not be loaded.' } }),
  )
  await page.goto('/sign-in')
  await expect(page.getByRole('alert')).toBeVisible()
  await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/')
  const nonlocal = await request.get('/api/auth/accounts', { headers: { host: 'wuu.example' } })
  expect(await nonlocal.json()).toEqual({ enabled: false, accounts: [] })
})

test('local account buttons are accessible on mobile in dark mode', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.addInitScript(() => localStorage.setItem('nuxt-color-mode', 'dark'))
  await page.goto('/sign-in')
  await expect(page.getByRole('heading', { name: 'Choose a local account' })).toBeVisible()
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
})
