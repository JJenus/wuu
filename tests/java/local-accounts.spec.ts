import { readFileSync } from 'node:fs'
import { expect, test } from '@playwright/test'
import { z } from 'zod'

const credentialsSchema = z.object({
  user: z.object({ email: z.email(), password: z.string().min(12) }),
  admin: z.object({ email: z.email(), password: z.string().min(12) }),
  additional: z.object({ email: z.email(), password: z.string().min(12) }),
})

test('real Java seeds require passwords and a newly created user appears without frontend changes', async ({
  page,
  request,
}) => {
  const accounts = credentialsSchema.parse(
    JSON.parse(readFileSync('.data/local-accounts/credentials.json', 'utf8')),
  )
  await expect
    .poll(
      async () => {
        try {
          return (await request.get('http://127.0.0.1:8080/api/v1/auth/local-accounts')).status()
        } catch {
          return 0
        }
      },
      { timeout: 60000 },
    )
    .toBe(200)
  for (const [key, role] of [
    ['user', 'VIEWER'],
    ['admin', 'TENANT_ADMIN'],
  ] as const) {
    const account = accounts[key]
    await page.goto('/sign-in?next=/vehicles')
    const button = page.getByRole('button', {
      name: new RegExp(account.email.replaceAll('.', '\\.')),
    })
    await expect(button).toBeVisible()
    await button.click()
    await expect(page.getByLabel('Email', { exact: true })).toHaveValue(account.email)
    await expect(page.getByLabel('Password', { exact: true })).toHaveValue('')
    expect((await page.request.get('/api/auth/session')).status()).toBe(401)
    await page.getByLabel('Password', { exact: true }).fill(account.password)
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page).toHaveURL('/vehicles')
    const session = await page.request.get('/api/auth/session')
    expect(session.status()).toBe(200)
    expect((await session.json()).user.roles).toContain(role)
    await page.getByRole('button', { name: 'Sign out all sessions' }).click()
    await expect(page).toHaveURL('/sign-in')
  }

  // Provision through the real, permission-checked user API, never an unauthenticated seed route.
  const login = await request.post('http://127.0.0.1:8080/api/v1/auth/login', {
    data: accounts.admin,
  })
  expect(login.status()).toBe(200)
  const headers = { authorization: `Bearer ${(await login.json()).access_token}` }
  const directory = await request.get('http://127.0.0.1:8080/api/v1/auth/local-accounts')
  expect(directory.status()).toBe(200)
  if (
    !(await directory.json()).accounts.some(
      (a: { email: string }) => a.email === accounts.additional.email,
    )
  ) {
    const created = await request.post('http://127.0.0.1:8080/api/v1/users', {
      headers,
      data: { ...accounts.additional, first_name: 'Additional', last_name: 'Local user' },
    })
    expect(created.status()).toBe(201)
  }
  expect(
    (await request.post('http://127.0.0.1:8080/api/v1/auth/logout', { headers })).status(),
  ).toBe(204)
  await page.reload()
  await page.getByRole('button', { name: /Additional Local user additional@wuu.local/ }).click()
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue(accounts.additional.email)
  await page.getByLabel('Password', { exact: true }).fill(accounts.additional.password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/')
  await page.getByRole('button', { name: 'Sign out all sessions' }).click()
  await expect(page).toHaveURL('/sign-in')
})
