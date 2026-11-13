import { expect, test, type Page } from '@playwright/test'
import { z } from 'zod'

async function signIn(page: Page, email: string, password = 'eight123') {
  await page.goto('/sign-in?next=/team')
  await page.waitForLoadState('networkidle')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/team')
}
test('real Java team administration persists and enforces membership, roles, sessions and last admin', async ({
  page,
  browser,
}) => {
  test.setTimeout(150000)
  await signIn(page, process.env.TRACKING_TEST_EMAIL!)
  const headers = { origin: new URL(page.url()).origin }
  const directory = page.getByRole('region', { name: 'Live team directory' })
  await expect(directory).toContainText(process.env.TRACKING_TEST_EMAIL!)
  await page
    .getByRole('button', { name: `Remove ${process.env.TRACKING_TEST_EMAIL!}`, exact: true })
    .click()
  await page.getByRole('button', { name: 'Confirm removal', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('last tenant administrator')
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page
    .getByRole('button', {
      name: `Change roles for ${process.env.TRACKING_TEST_EMAIL!}`,
      exact: true,
    })
    .click()
  await page.getByRole('checkbox', { name: 'Assign TENANT_ADMIN', exact: true }).uncheck()
  await page.getByRole('checkbox', { name: 'Assign OPERATOR', exact: true }).check()
  await page.getByRole('button', { name: 'Save roles', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('last tenant administrator')
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()

  const email = 'team-member@native-test.example'
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await page.getByLabel('First name', { exact: true }).fill('Team')
  await page.getByLabel('Last name', { exact: true }).fill('Member')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Initial password', { exact: true }).fill('eight123')
  await page.getByRole('button', { name: 'Create member account', exact: true }).click()
  const row = directory.getByRole('row').filter({ hasText: email })
  await expect(row).toContainText('OPERATOR')
  await page.reload()
  await expect(row).toContainText('Team Member')
  const id = (await row.getAttribute('data-member-id'))!
  await page.getByRole('button', { name: `Edit ${email}`, exact: true }).click()
  await page.getByLabel('First name', { exact: true }).fill('Updated')
  await page.getByRole('button', { name: 'Save profile', exact: true }).click()
  await page.reload()
  await expect(row).toContainText('Updated Member')

  const memberContext = await browser.newContext()
  const foreignContext = await browser.newContext()
  try {
    const member = await memberContext.newPage()
    await signIn(member, email)
    await expect(member.getByRole('region', { name: 'Live team directory' })).toContainText(email)
    await expect(member.getByRole('button', { name: 'Create account', exact: true })).toHaveCount(0)
    expect(
      (
        await member.request.post('/api/_wuu/team', {
          headers,
          data: {
            email: 'denied@example.test',
            firstName: 'Denied',
            lastName: 'User',
            password: 'eight123',
          },
        })
      ).status(),
    ).toBe(403)

    const foreign = await foreignContext.newPage()
    await signIn(foreign, 'foreign@telemetry-test.example')
    const foreignId = await foreign
      .getByRole('row')
      .filter({ hasText: 'foreign@telemetry-test.example' })
      .getAttribute('data-member-id')
    expect((await page.request.get(`/api/_wuu/team/${foreignId}`)).status()).toBe(404)
    expect(
      (
        await page.request.put(`/api/_wuu/team/${foreignId}`, {
          headers,
          data: { email: 'no-change@example.test', firstName: 'No', lastName: 'Change' },
        })
      ).status(),
    ).toBe(404)
    expect(
      (await page.request.delete(`/api/_wuu/team/${foreignId}`, { headers, data: {} })).status(),
    ).toBe(404)
    const rolesResponse = await foreign.request.get('/api/_wuu/roles?page=0&size=100')
    const roles = z
      .object({ content: z.array(z.object({ id: z.number().int().positive() })) })
      .parse(await rolesResponse.json())
    expect(
      (
        await page.request.put(`/api/_wuu/team/${id}/roles`, {
          headers,
          data: { roleIds: [roles.content[0]!.id] },
        })
      ).status(),
    ).toBe(400)
    expect(
      (await page.request.post('/api/_wuu/team?orgId=999', { headers, data: {} })).status(),
    ).toBe(400)
    expect(
      (
        await page.request.put(`/api/_wuu/team/${id}/roles`, {
          headers,
          data: { roleIds: [], permissions: ['system.admin'] },
        })
      ).status(),
    ).toBe(400)
    expect((await page.request.get('/api/_wuu/team/me')).status()).toBe(404)
    expect(
      (
        await page.request.post('/api/_wuu/team', {
          headers: { origin: 'https://evil.example' },
          data: {},
        })
      ).status(),
    ).toBe(403)

    await page.getByRole('button', { name: `Change roles for ${email}`, exact: true }).click()
    await page.getByRole('checkbox', { name: 'Assign OPERATOR', exact: true }).uncheck()
    await page.getByRole('checkbox', { name: 'Assign VIEWER', exact: true }).check()
    await page.getByRole('button', { name: 'Save roles', exact: true }).click()
    await page.reload()
    await expect(row).toContainText('VIEWER')
    expect((await member.request.get('/api/_wuu/team')).status()).toBe(403)
    await page.getByRole('button', { name: `Remove ${email}`, exact: true }).click()
    await page.getByRole('button', { name: 'Confirm removal', exact: true }).click()
    await expect(row).toHaveCount(0)
    expect((await member.request.get('/api/_wuu/access')).status()).toBe(401)
    await page.reload()
    await expect(directory).not.toContainText(email)
  } finally {
    await memberContext.close()
    await foreignContext.close()
  }
})
