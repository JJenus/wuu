import { expect, test, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

async function signIn(page: Page, writer = true) {
  await page.goto('/sign-in?next=/geofences/9')
  await page
    .getByLabel('Email', { exact: true })
    .fill(`geo-ui-${writer ? 'write' : 'denied'}-${randomUUID()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/geofences/9')
}

test('create, reload and enable/disable a configured geofence rule', async ({ page }) => {
  await signIn(page)
  await page.getByRole('button', { name: 'Add geofence rule', exact: true }).click()
  await page.getByLabel('Rule key', { exact: true }).fill('depot_exits')
  await page.getByLabel('Rule name', { exact: true }).fill('Depot exits')
  await page.getByRole('combobox', { name: /^Trigger/ }).click()
  await page.getByRole('option', { name: 'EXIT', exact: true }).click()
  await page.getByLabel('Priority', { exact: true }).fill('3')
  await page.getByLabel('Apply rule to LONDON-2', { exact: true }).uncheck()
  await page.getByRole('button', { name: 'Save rule', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const rule = page.getByRole('article', { name: 'Rule depot_exits', exact: true })
  await expect(rule).toContainText('EXIT')
  await expect(rule).toContainText('VH-1')
  await expect(rule).not.toContainText('VH-2')
  await page.reload()
  await expect(rule).toContainText('Enabled')
  await rule.getByRole('button', { name: 'Disable Depot exits', exact: true }).click()
  await expect(rule).toContainText('Disabled')
  await page.reload()
  await expect(rule).toContainText('Disabled')
  await rule.getByRole('button', { name: 'Enable Depot exits', exact: true }).click()
  await expect(rule).toContainText('Enabled')
})

test('permission denial and strict rule gateway prevent unauthorized or generic writes', async ({
  page,
  request,
}) => {
  const data = {
    ruleKey: 'denied_rule',
    ruleName: 'Denied rule',
    geofenceId: '9',
    action: 'BOTH',
    vehicleIds: ['VH-1'],
    enabled: true,
    priority: 2,
  }
  const path = '/api/_wuu/geofence-rules/templates/geofence'
  const headers = { origin: 'http://localhost:3001' }
  expect((await request.post(path, { headers, data })).status()).toBe(401)
  expect(
    (await request.patch('/api/_wuu/geofence-rules/key/disable', { headers, data: {} })).status(),
  ).toBe(401)
  await signIn(page, false)
  await expect(page.getByRole('button', { name: 'Add geofence rule', exact: true })).toHaveCount(0)
  expect((await page.request.post(path, { headers, data })).status()).toBe(403)
  expect(
    (
      await page.request.patch('/api/_wuu/geofence-rules/key/disable', { headers, data: {} })
    ).status(),
  ).toBe(403)
  expect(
    (
      await page.request.patch('/api/_wuu/geofence-rules/key/disable', {
        headers,
        data: { enabled: false },
      })
    ).status(),
  ).toBe(400)
  expect(
    (await page.request.post(path, { headers, data: { ...data, createdBy: 'spoofed' } })).status(),
  ).toBe(400)
  expect((await page.request.post(path + '?orgId=2', { headers, data })).status()).toBe(400)
  expect(
    (await page.request.post(path, { headers: { origin: 'https://evil.test' }, data })).status(),
  ).toBe(403)
  expect(
    (await page.request.post('/api/_wuu/alerts/rules/batch', { headers, data: [] })).status(),
  ).toBe(405)
  expect(
    (await page.request.put('/api/_wuu/alerts/rules/key', { headers, data: {} })).status(),
  ).toBe(405)
})

test('revoked permission and failed saves retain rule drafts without replay', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await signIn(page)
  await page.getByRole('button', { name: 'Add geofence rule', exact: true }).click()
  await page.getByLabel('Rule key', { exact: true }).fill('draft_rule')
  await page.getByLabel('Rule name', { exact: true }).fill('Keep rule draft')
  let writes = 0
  await page.route('**/api/_wuu/geofence-rules/templates/geofence', (route) => {
    writes++
    return route.fulfill({ status: 503, json: { message: 'Temporary rule save failure' } })
  })
  await page.getByRole('button', { name: 'Save rule', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('It may have been saved')
  await expect(page.getByLabel('Rule name', { exact: true })).toHaveValue('Keep rule draft')
  expect(writes).toBe(1)
  await page.route('**/api/_wuu/access', (route) =>
    route.fulfill({
      json: {
        user_id: 7,
        organization_id: 12,
        permissions: ['alerts.read', 'vehicles.read'],
        access_expires_at: '2099-01-01T00:00:00Z',
      },
    }),
  )
  await page.getByRole('button', { name: 'Save rule', exact: true }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText(
    'do not currently have permission',
  )
  expect(writes).toBe(1)
})
