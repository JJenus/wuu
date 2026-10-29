import { expect, test, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

async function signIn(page: Page, email = 'operator@example.com') {
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
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
  expect(await page.content()).not.toMatch(/private-access|private-refresh/)
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

test('automatic renewal rotates before short access expiry without losing the session', async ({
  page,
}) => {
  await page.goto('/vehicles')
  const renewal = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/auth/refresh') && response.request().method() === 'POST',
    { timeout: 15_000 },
  )
  await signIn(page, 'renew@example.com')
  const response = await renewal
  expect(response.status()).toBe(200)
  expect((await response.json()).expires_at).toBeGreaterThan(Date.now() + 60_000)
  await expect(page).toHaveURL('/vehicles')
  await expect(page.getByText('WUU LIVE 01').first()).toBeVisible()
  await expect(page).toHaveURL('/vehicles')
  expect((await page.request.get('/api/_wuu/vehicles')).status()).toBe(200)
})

test('invalid credentials and rate limiting stay errors without preview fallback', async ({
  page,
}) => {
  await page.goto('/sign-in')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
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

test('failed identity verification never issues a usable session', async ({ page, context }) => {
  await page.goto('/sign-in')
  await signIn(page, 'unavailable@example.com')
  await expect(page.getByRole('alert')).toContainText('unavailable')
  expect((await context.cookies()).some((cookie) => cookie.name === 'wuu_session')).toBe(false)
})

test('failed backend revocation still signs out locally and reports the limitation', async ({
  page,
  context,
}) => {
  await page.goto('/sign-in?next=/vehicles')
  await signIn(page, 'logout-failure@example.com')
  await expect(page).toHaveURL('/vehicles')
  await page.getByRole('button', { name: 'Sign out all sessions' }).click()
  await expect(page).toHaveURL('/sign-in?revocation=failed')
  await expect(page.getByRole('alert')).toContainText('could not confirm revocation')
  expect((await context.cookies()).some((cookie) => cookie.name === 'wuu_session')).toBe(false)
  expect((await page.request.get('/api/_wuu/vehicles')).status()).toBe(401)
})

test('tampered cookies and oversized or malformed login requests fail closed', async ({
  page,
  context,
}) => {
  await page.goto('/sign-in')
  await context.addCookies([
    { name: 'wuu_session', value: 'not-a-real-sealed-session', url: 'http://localhost:3001' },
  ])
  expect((await page.request.get('/api/auth/session')).status()).toBe(401)
  const headers = { origin: 'http://localhost:3001' }
  expect(
    (
      await page.request.post('/api/auth/login', { headers, data: { email: 'bad', password: '' } })
    ).status(),
  ).toBe(400)
  expect(
    (
      await page.request.post('/api/auth/login', {
        headers,
        data: { email: 'operator@example.com', password: 'x'.repeat(5000) },
      })
    ).status(),
  ).toBe(413)
  expect(
    (
      await page.request.post('/api/auth/login', {
        headers: { ...headers, 'content-type': 'text/plain' },
        data: 'not-json',
      })
    ).status(),
  ).toBe(415)
})

test('an expired active read returns to sign-in instead of showing sample data', async ({
  page,
}) => {
  await page.goto('/sign-in?next=/vehicles')
  await page.route('**/api/_wuu/vehicles*', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: '{"code":"UNAUTHENTICATED"}',
    }),
  )
  await signIn(page, 'expired@example.com')
  await expect(page).toHaveURL('/sign-in?expired=1')
  await expect(page.getByText('Your session expired. Sign in again to continue.')).toBeVisible()
})

test('sign-in fits mobile, supports dark mode, and has no automated accessibility violations', async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 })
  const response = await page.goto('/sign-in')
  expect(response?.headers()['cache-control']).toContain('no-store')
  await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
    'data-workspace-ready',
    'true',
  )
  for (const dark of [false, true]) {
    await page.evaluate((value) => document.documentElement.classList.toggle('dark', value), dark)
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
})
