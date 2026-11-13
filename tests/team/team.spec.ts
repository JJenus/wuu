import { expect, test, type Page } from '@playwright/test'
const permissions = ['users.read', 'users.write', 'users.assign_roles', 'users.delete', 'roles.read']
async function setup(page: Page) {
  let grants = [...permissions]
  let writes = 0
  let member = { id: 7, email: 'member@example.test', first_name: 'Initial', last_name: 'Member', roles: ['OPERATOR'], organization_id: 10, enabled: true, locked: false, created_at: '2026-11-13T00:00:00Z' }
  let present = true
  const roles = [{ id: 1, name: 'OPERATOR', description: 'Operator', permissions: ['users.read'] }, { id: 2, name: 'VIEWER', description: 'Viewer', permissions: ['vehicles.read'] }]
  await page.route('**/api/_wuu/access', route => route.fulfill({ json: { user_id: 9, organization_id: 10, permissions: grants, access_expires_at: Date.now() + 600000 } }))
  await page.route('**/api/_wuu/roles?*', route => route.fulfill({ json: { content: roles, page_number: 0, page_size: 100, total_elements: 2, total_pages: 1, first: true, last: true, empty: false } }))
  await page.route('**/api/_wuu/team**', async route => {
    const request = route.request()
    if (request.method() === 'GET') return route.fulfill({ json: { content: present ? [member] : [], page_number: 0, page_size: 50, total_elements: present ? 1 : 0, total_pages: present ? 1 : 0, first: true, last: true, empty: !present } })
    writes++
    if (request.method() === 'DELETE') { present = false; return route.fulfill({ status: 204 }) }
    const body: { email?: string; firstName?: string; lastName?: string; roleIds?: number[] } = request.postDataJSON()
    if (request.url().endsWith('/roles')) member = { ...member, roles: body.roleIds?.map(id => roles.find(role => role.id === id)!.name) ?? [] }
    else member = { ...member, email: body.email!, first_name: body.firstName!, last_name: body.lastName! }
    return route.fulfill({ status: request.method() === 'POST' ? 201 : 200, json: member })
  })
  await page.goto('/sign-in?next=/team')
  await page.waitForLoadState('networkidle')
  await page.getByLabel('Email', { exact: true }).fill('operator@example.com')
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/team')
  return { revoke: () => { grants = ['users.read'] }, writes: () => writes }
}
test('live team creates, edits, assigns roles and confirms removal without sample fallback', async ({ page }) => {
  await setup(page)
  await page.getByRole('button', { name: 'Create account', exact: true }).click()
  await page.getByLabel('First name', { exact: true }).fill('Created')
  await page.getByLabel('Last name', { exact: true }).fill('Member')
  await page.getByLabel('Email', { exact: true }).fill('member@example.test')
  await page.getByLabel('Initial password', { exact: true }).fill('eight123')
  await page.getByRole('button', { name: 'Create member account', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Live team directory' })).toContainText('Created Member')
  await page.getByRole('button', { name: 'Edit member@example.test', exact: true }).click()
  await page.getByLabel('First name', { exact: true }).fill('Updated')
  await page.getByRole('button', { name: 'Save profile', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Live team directory' })).toContainText('Updated Member')
  await page.getByRole('button', { name: 'Change roles for member@example.test', exact: true }).click()
  await page.getByRole('checkbox', { name: 'Assign OPERATOR', exact: true }).uncheck()
  await page.getByRole('checkbox', { name: 'Assign VIEWER', exact: true }).check()
  await page.getByRole('button', { name: 'Save roles', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Live team directory' })).toContainText('VIEWER')
  await page.getByRole('button', { name: 'Remove member@example.test', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Remove member', exact: true })).toContainText('revokes their account sessions')
  await page.getByRole('button', { name: 'Confirm removal', exact: true }).click()
  await expect(page.getByText('No team members found on this page.', { exact: true })).toBeVisible()
})
test('permission recheck blocks revoked writes while retaining profile drafts', async ({ page }) => {
  const state = await setup(page)
  await page.getByRole('button', { name: 'Edit member@example.test', exact: true }).click()
  await page.getByLabel('First name', { exact: true }).fill('Keep this draft')
  state.revoke()
  await page.getByRole('button', { name: 'Save profile', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('do not currently have permission')
  expect(state.writes()).toBe(0)
  await expect(page.getByLabel('First name', { exact: true })).toHaveValue('Keep this draft')
})
