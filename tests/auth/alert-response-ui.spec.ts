import { expect, test, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import AxeBuilder from '@axe-core/playwright'

async function signIn(page: Page, kind = 'write') {
  await page.goto('/sign-in?next=/alerts')
  await page.getByLabel('Email', { exact: true }).fill(`alert-ui-${kind}-${randomUUID()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/alerts')
  await expect(page.getByRole('heading', { name: /^Overspeed/ })).toBeVisible()
}

test('live operator acknowledges from list and resolves detail with validated notes and persisted refresh', async ({ page }) => {
  await signIn(page)
  await page.getByRole('button', { name: 'Acknowledge', exact: true }).click()
  await page.getByRole('button', { name: /^Acknowledged/ }).click()
  await expect(page.getByRole('heading', { name: /^Overspeed/ })).toBeVisible()
  await page.getByRole('link', { name: 'View alert 1' }).click()
  await expect(page.getByTestId('alert-state')).toContainText('Acknowledged')
  await page.getByRole('button', { name: 'Resolve', exact: true }).click()
  await page.getByLabel('Resolution notes').fill('short')
  await expect(page.getByRole('button', { name: 'Confirm resolution' })).toBeDisabled()
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await expect(page.getByLabel('Resolution notes')).toHaveCount(0)
  await page.getByRole('button', { name: 'Resolve', exact: true }).click()
  await page.getByLabel('Resolution notes').fill('  Reviewed and confirmed safe  ')
  let attempts = 0
  const bodies: unknown[] = []
  await page.route('**/api/_wuu/alerts/1/resolve', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    attempts++
    bodies.push(route.request().postDataJSON())
    if (attempts === 1) return route.fulfill({ status: 503, json: { message: 'Temporary failure' } })
    return route.continue()
  })
  await page.getByRole('button', { name: 'Confirm resolution' }).click()
  await expect(page.getByRole('alert')).toContainText('Temporary failure')
  expect(attempts).toBe(1)
  await expect(page.getByLabel('Resolution notes')).toHaveValue('  Reviewed and confirmed safe  ')
  await page.getByRole('button', { name: 'Confirm resolution' }).click()
  await expect(page.getByTestId('alert-state')).toContainText('Resolved')
  expect(bodies).toEqual([{ resolutionNotes: 'Reviewed and confirmed safe' }, { resolutionNotes: 'Reviewed and confirmed safe' }])
  await page.reload()
  await expect(page.getByTestId('alert-state')).toContainText('Resolved')
  await page.getByRole('link', { name: 'Back to alerts', exact: true }).click()
  await page.getByRole('button', { name: /^Resolved/ }).click()
  await expect(page.getByRole('heading', { name: /^Overspeed/ })).toBeVisible()
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('missing write permission and access-check failures keep controls unavailable', async ({ page }) => {
  await signIn(page, 'denied')
  await expect(page.getByText('You do not have permission to change alerts.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Acknowledge', exact: true })).toHaveCount(0)
  await page.route('**/api/_wuu/access', (route) => route.fulfill({ status: 503, json: { message: 'Access unavailable' } }))
  await page.reload()
  await expect(page.getByText('Alert permissions could not be checked.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Resolve', exact: true })).toHaveCount(0)
})

test('permission revocation is rechecked before submission and does not send a mutation', async ({ page }) => {
  await signIn(page)
  await expect(page.getByRole('button', { name: 'Acknowledge', exact: true })).toBeVisible()
  let mutations = 0
  page.on('request', (request) => { if (request.method() === 'POST' && request.url().includes('/api/_wuu/alerts/')) mutations++ })
  await page.route('**/api/_wuu/access', (route) => route.fulfill({ json: { user_id: 7, organization_id: 12, permissions: ['alerts.read'], access_expires_at: '2099-01-01T00:00:00Z' } }))
  await page.getByRole('button', { name: 'Acknowledge', exact: true }).click()
  await expect(page.getByText('You do not currently have permission to change alerts.')).toBeVisible()
  expect(mutations).toBe(0)
})
