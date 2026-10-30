import { expect, test, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import AxeBuilder from '@axe-core/playwright'

async function signIn(page: Page, kind = 'write') {
  await page.goto('/sign-in?next=/alerts')
  await page
    .getByLabel('Email', { exact: true })
    .fill(`alert-ui-${kind}-${randomUUID()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/alerts')
  await expect(page.getByRole('heading', { name: /^Overspeed/ })).toBeVisible()
}

test('live operator acknowledges from list and resolves detail with validated notes and persisted refresh', async ({
  page,
}) => {
  await signIn(page)
  await page.getByRole('button', { name: 'Acknowledge', exact: true }).click()
  await page.getByRole('button', { name: /^Acknowledged/ }).click()
  await expect(page.getByRole('heading', { name: /^Overspeed/ })).toBeVisible()
  await page.getByRole('link', { name: 'View alert 1' }).click()
  await expect(page.getByTestId('alert-state')).toContainText('acknowledged')
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
    if (attempts === 1)
      return route.fulfill({ status: 503, json: { message: 'Temporary failure' } })
    return route.continue()
  })
  await page.getByRole('button', { name: 'Confirm resolution' }).click()
  await expect(page.getByRole('alert')).toContainText('Temporary failure')
  expect(attempts).toBe(1)
  await expect(page.getByLabel('Resolution notes')).toHaveValue('  Reviewed and confirmed safe  ')
  await page.getByRole('button', { name: 'Confirm resolution' }).click()
  await expect(page.getByTestId('alert-state')).toContainText('resolved')
  expect(bodies).toEqual([
    { resolutionNotes: 'Reviewed and confirmed safe' },
    { resolutionNotes: 'Reviewed and confirmed safe' },
  ])
  await page.reload()
  await expect(page.getByTestId('alert-state')).toContainText('resolved')
  await page.getByRole('link', { name: 'Back to alerts', exact: true }).click()
  await page.getByRole('button', { name: /^Resolved/ }).click()
  await expect(page.getByRole('heading', { name: /^Overspeed/ })).toBeVisible()
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('missing write permission and access-check failures keep controls unavailable', async ({
  page,
}) => {
  await signIn(page, 'denied')
  await expect(page.getByText('You do not have permission to change alerts.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Acknowledge', exact: true })).toHaveCount(0)
  await page.route('**/api/_wuu/access', (route) =>
    route.fulfill({ status: 503, json: { message: 'Access unavailable' } }),
  )
  await page.reload()
  await expect(page.getByText('Alert permissions could not be checked.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Resolve', exact: true })).toHaveCount(0)
})

test('permission revocation is rechecked before submission and does not send a mutation', async ({
  page,
}) => {
  await signIn(page)
  await expect(page.getByRole('button', { name: 'Acknowledge', exact: true })).toBeVisible()
  let mutations = 0
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().includes('/api/_wuu/alerts/')) mutations++
  })
  await page.route('**/api/_wuu/access', (route) =>
    route.fulfill({
      json: {
        user_id: 7,
        organization_id: 12,
        permissions: ['alerts.read'],
        access_expires_at: '2099-01-01T00:00:00Z',
      },
    }),
  )
  await page.getByRole('button', { name: 'Acknowledge', exact: true }).click()
  await expect(
    page.getByText('You do not currently have permission to change alerts.'),
  ).toBeVisible()
  expect(mutations).toBe(0)
})

test('mobile resolution supports keyboard submission and disables duplicate writes while pending', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await signIn(page)
  await page.getByRole('link', { name: 'View alert 1' }).click()
  await page.getByRole('button', { name: 'Resolve', exact: true }).click()
  await page.getByLabel('Resolution notes', { exact: true }).fill('Reviewed mobile fleet event')
  let release!: () => void
  const ready = new Promise<void>((resolve) => {
    release = resolve
  })
  let seen!: () => void
  const received = new Promise<void>((resolve) => {
    seen = resolve
  })
  let writes = 0
  await page.route('**/api/_wuu/alerts/1/resolve', async (route) => {
    writes++
    seen()
    await ready
    await route.continue()
  })
  const confirm = page.getByRole('button', { name: 'Confirm resolution' })
  await confirm.focus()
  await page.keyboard.press('Enter')
  await received
  try {
    await expect(confirm).toBeDisabled()
    await expect(page.getByLabel('Resolution notes', { exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: 'Cancel', exact: true })).toBeDisabled()
    expect(writes).toBe(1)
  } finally {
    release()
  }
  await expect(page.getByTestId('alert-state')).toContainText('resolved')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})
