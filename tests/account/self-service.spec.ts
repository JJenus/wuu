import { randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'

const origin = 'http://localhost:3001'
async function account(page: Page) {
  const email = `self-${randomUUID()}@example.com`
  const password = process.env.ACCOUNT_TEST_JAVA_ORIGIN ? 'eight123' : 'test-only-password'
  if (process.env.ACCOUNT_TEST_JAVA_ORIGIN) {
    // Out-of-band fixture account only in the owned disposable Java database.
    const created = await page.request.post(`${process.env.ACCOUNT_TEST_JAVA_ORIGIN}/api/v1/auth/register`, {
      data: { email, password, first_name: 'Fleet', last_name: 'Operator', organization_name: 'Account acceptance' },
    })
    expect(created.status()).toBe(201)
  }
  await signIn(page, email, password)
  return { email, password }
}
async function signIn(page: Page, email: string, password: string) {
  await page.goto('/sign-in?next=/settings')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/settings')
}

test('self profile saves names, reloads and rejects identity/privilege overrides', async ({ page }) => {
  const { email } = await account(page)
  const profile = page.getByRole('region', { name: 'Your profile' })
  await profile.getByLabel('First name', { exact: true }).fill('Updated')
  await profile.getByLabel('Last name', { exact: true }).fill('Person')
  await profile.getByRole('button', { name: 'Save profile', exact: true }).click()
  await expect(profile.getByRole('status')).toContainText('Profile saved')
  await expect(profile).toContainText(email)
  await page.reload()
  await expect(profile.getByLabel('First name', { exact: true })).toHaveValue('Updated')
  for (const extra of [{ id: 9 }, { email: 'forged@example.com' }, { roles: ['SUPER_ADMIN'] }, { organizationId: 9 }])
    expect((await page.request.post('/api/auth/profile', { headers: { origin }, data: { firstName: 'Forged', lastName: '', ...extra } })).status()).toBe(400)
  expect((await page.request.post('/api/auth/profile?userId=9', { headers: { origin }, data: { firstName: 'Forged', lastName: '' } })).status()).toBe(400)
  expect((await page.request.post('/api/auth/profile', { headers: { origin: 'https://other.example' }, data: { firstName: 'Forged', lastName: '' } })).status()).toBe(403)
  expect((await page.request.post('/api/auth/profile', { headers: { origin }, data: { firstName: 'x'.repeat(5000), lastName: '' } })).status()).toBe(413)
  await page.reload()
  await expect(profile.getByLabel('First name', { exact: true })).toHaveValue('Updated')
})

test('wrong password preserves access; successful change revokes sessions and requires new-password sign-in', async ({ page, browser }) => {
  const { email, password } = await account(page)
  const second = await browser.newContext()
  try {
    const other = await second.newPage(); await signIn(other, email, password)
    const form = page.getByRole('region', { name: 'Change password' })
    await form.getByLabel('Current password', { exact: true }).fill('wrong-current-password')
    await form.getByLabel('New password', { exact: true }).fill(' new123 ')
    await form.getByLabel('Confirm new password', { exact: true }).fill(' new123 ')
    await form.getByRole('button', { name: 'Change password', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText('Current password is incorrect')
    await expect(page).toHaveURL('/settings')
    await expect(form.getByLabel('Current password', { exact: true })).toHaveValue('')
    await expect(form.getByLabel('New password', { exact: true })).toHaveValue('')
    await form.getByLabel('Current password', { exact: true }).fill(password)
    await form.getByLabel('New password', { exact: true }).fill(' new123 ')
    await form.getByLabel('Confirm new password', { exact: true }).fill(' new123 ')
    await form.getByRole('button', { name: 'Change password', exact: true }).click()
    await expect(page).toHaveURL('/sign-in?password=changed')
    await expect(page.getByRole('status')).toContainText('All account sessions have ended')
    expect((await other.request.get('/api/auth/session')).status()).toBe(401)
    expect((await page.request.post('/api/auth/login', { headers: { origin }, data: { email, password } })).status()).toBe(401)
    await signIn(page, email, ' new123 ')
    await expect(page.getByRole('region', { name: 'Your profile' })).toBeVisible()
    expect(await page.evaluate(() => JSON.stringify([localStorage, sessionStorage]))).not.toContain('new123')
  } finally { await second.close() }
})

test('lost password-change reply is not retried or presented as confirmed success', async ({ page }) => {
  const { email, password } = await account(page)
  let writes = 0
  await page.route('**/api/auth/change-password', async route => {
    writes++
    await route.fetch()
    await route.abort('failed')
  })
  const form = page.getByRole('region', { name: 'Change password' })
  await form.getByLabel('Current password', { exact: true }).fill(password)
  await form.getByLabel('New password', { exact: true }).fill('newpass1')
  await form.getByLabel('Confirm new password', { exact: true }).fill('newpass1')
  await form.getByRole('button', { name: 'Change password', exact: true }).click()
  await expect(page).toHaveURL('/sign-in?password=uncertain')
  await expect(page.getByRole('alert')).toContainText('could not be confirmed')
  expect(writes).toBe(1)
  await page.unroute('**/api/auth/change-password')
  await signIn(page, email, 'newpass1')
})

test('mobile password validation blocks mismatches, short and oversized UTF-8 passwords', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const { password } = await account(page)
  let writes = 0
  page.on('request', request => { if (request.url().endsWith('/api/auth/change-password')) writes++ })
  const form = page.getByRole('region', { name: 'Change password' })
  await form.getByLabel('Current password', { exact: true }).fill(password)
  for (const [next, confirmation, message] of [['seven12', 'seven12', 'at least 8'], ['newpass1', 'different', 'do not match'], ['é'.repeat(37), 'é'.repeat(37), '72 UTF-8 bytes']]) {
    await form.getByLabel('New password', { exact: true }).fill(next!)
    await form.getByLabel('Confirm new password', { exact: true }).fill(confirmation!)
    await form.getByRole('button', { name: 'Change password', exact: true }).click()
    await expect(form).toContainText(message!)
  }
  expect(writes).toBe(0)
})
