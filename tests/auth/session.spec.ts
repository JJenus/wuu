import { expect, test, type Page } from '@playwright/test'

async function signIn(page: Page, email = 'operator@example.com') {
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
}

test('anonymous live routes redirect, API rejects ambient and legacy credentials, CSRF rejects cross-site requests', async ({
  page,
  request,
}) => {
  await page.goto('/vehicles')
  await expect(page).toHaveURL(/sign-in\?next=/)
  expect(
    (
      await request.get('/api/_wuu/vehicles', { headers: { cookie: 'wuu_access_token=forged' } })
    ).status(),
  ).toBe(401)
  expect(
    (
      await request.post('/api/auth/login', {
        headers: { origin: 'https://evil.test' },
        data: { email: 'operator@example.com', password: 'test-only-password' },
      })
    ).status(),
  ).toBe(403)
  expect((await request.post('/api/auth/logout')).status()).toBe(403)
  await expect(page.getByText('Northstar Logistics')).toHaveCount(0)
})

test('sign-in preserves safe deep links, hides credentials, verifies identity, guards admin, signs out', async ({
  page,
  context,
}) => {
  await page.goto('/vehicles')
  let loginBody = ''
  await page.route('**/api/auth/login', async (route) => {
    const response = await route.fetch()
    loginBody = await response.text()
    await route.fulfill({ response })
  })
  await signIn(page)
  await expect(page).toHaveURL('/vehicles')
  expect(loginBody).not.toMatch(/access_token|refresh_token|private-access|private-refresh/)
  await expect(page.getByText('WUU LIVE 01').first()).toBeVisible()
  await expect(page.locator('.sidebar-user')).toContainText('Fleet Operator')
  await expect(page.getByText('Northstar Logistics')).toHaveCount(0)
  const cookie = (await context.cookies()).find((item) => item.name === 'wuu_session')
  expect(cookie).toMatchObject({ httpOnly: true, sameSite: 'Strict' })
  expect(cookie?.value).not.toContain('private-access')
  expect(await page.evaluate(() => document.cookie)).not.toContain('wuu_session')
  expect(await page.evaluate(() => JSON.stringify(localStorage))).not.toContain('private-access')
  await page.reload()
  await expect(page.getByText('WUU LIVE 01').first()).toBeVisible()
  await page.goto('/admin')
  await expect(page).toHaveURL('/access-denied')
  await expect(page.getByRole('heading', { name: 'Platform access is unavailable' })).toBeVisible()
  const write = await page.request.post('/api/_wuu/vehicles', {
    headers: { origin: 'http://localhost:3001' },
    data: {},
  })
  expect(write.status()).toBe(405)
  await page.getByRole('button', { name: 'Sign out all sessions' }).click()
  await expect(page).toHaveURL('/sign-in')
  expect((await page.request.get('/api/_wuu/vehicles')).status()).toBe(401)
})

test('invalid credentials and rate limiting stay errors without preview fallback', async ({
  page,
}) => {
  await page.goto('/sign-in')
  await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
  await page.getByLabel('Password', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Sign-in failed')
  await signIn(page, 'limited@example.com')
  await expect(page.getByRole('alert')).toContainText('Too many')
  await expect(page).toHaveURL('/sign-in')
})

test('a denied trip permission does not hide a permitted fleet view', async ({ page }) => {
  await page.goto('/sign-in?next=/vehicles')
  await signIn(page, 'fleet-only@example.com')
  await expect(page).toHaveURL('/vehicles')
  await expect(page.getByText('WUU LIVE 01').first()).toBeVisible()
})

test('upstream forbidden reads never become preview records', async ({ page }) => {
  await page.goto('/sign-in?next=/vehicles')
  await signIn(page, 'forbidden@example.com')
  await expect(page).toHaveURL('/vehicles')
  await expect(page.getByText('Your fleet could not load')).toBeVisible()
  await expect(page.getByText('Transit 01', { exact: true })).toHaveCount(0)
})

test('revoked backend sessions force reauthentication rather than using cached identity', async ({
  page,
  context,
  browser,
}) => {
  await page.goto('/sign-in?next=/vehicles')
  await signIn(page, 'revoked@example.com')
  await expect(page).toHaveURL('/vehicles')
  const second = await browser.newContext({ storageState: await context.storageState() })
  const other = await second.newPage()
  await other.goto('http://localhost:3001/vehicles')
  await other.getByRole('button', { name: 'Sign out all sessions' }).click()
  await expect(other).toHaveURL('http://localhost:3001/sign-in')
  await page.reload()
  await expect(page).toHaveURL(/sign-in/)
  await second.close()
})
