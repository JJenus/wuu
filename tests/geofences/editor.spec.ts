import { expect, test, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

async function signIn(page: Page, permission = 'write', path = '/geofences/9/edit') {
  await page.goto(`/sign-in?next=${path}`)
  await page
    .getByLabel('Email', { exact: true })
    .fill(`geo-ui-${permission}-${randomUUID()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL(path)
}

test('authorized user creates a complete polygon from the live list and reloads every field', async ({
  page,
}) => {
  await signIn(page, 'write', '/geofences')
  await page.getByRole('link', { name: 'Create geofence', exact: true }).click()
  await expect(page).toHaveURL('/geofences/new')
  await page.getByLabel('Geofence name', { exact: true }).fill('Created polygon')
  await page.getByLabel('Description', { exact: true }).fill('All three points and two vehicles')
  await page.getByRole('button', { name: 'Draw polygon', exact: true }).click()
  const points = [
    { lat: 51, lng: 0 },
    { lat: 51, lng: 0.01 },
    { lat: 51.01, lng: 0.01 },
  ]
  for (const [index, point] of points.entries()) {
    await page.getByRole('button', { name: 'Add vertex using coordinates', exact: true }).click()
    await page.getByLabel(`Vertex ${index + 1} latitude`, { exact: true }).fill(String(point.lat))
    await page.getByLabel(`Vertex ${index + 1} longitude`, { exact: true }).fill(String(point.lng))
  }
  await page.getByLabel('Assign LONDON-1', { exact: true }).check()
  await page.getByLabel('Assign LONDON-2', { exact: true }).check()
  let writes = 0
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/api/_wuu/geofences')) writes++
  })
  await page.getByRole('button', { name: 'Create geofence', exact: true }).click()
  await expect(page).toHaveURL('/geofences/9')
  expect(writes).toBe(1)
  await page.reload()
  await expect(page.getByText('2 assigned', { exact: true })).toBeVisible()
  await expect(page.getByText('All three points and two vehicles', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Edit geofence', exact: true })).toBeEnabled()
  await page.getByRole('link', { name: 'Edit geofence', exact: true }).click()
  for (const [index, point] of points.entries()) {
    await expect(page.getByLabel(`Vertex ${index + 1} latitude`, { exact: true })).toHaveValue(
      String(point.lat),
    )
    await expect(page.getByLabel(`Vertex ${index + 1} longitude`, { exact: true })).toHaveValue(
      String(point.lng),
    )
  }
})

test('authorized live editor persists description, geometry and explicit unlink-all', async ({
  page,
}) => {
  await signIn(page)
  await expect(page.getByLabel('Geofence name', { exact: true })).toHaveValue('London depot')
  await expect(page.getByLabel('Center latitude', { exact: true })).toHaveValue('51.5')
  await expect(page.getByLabel('Preview event setting')).toHaveCount(0)
  await expect(page.getByLabel('Location label', { exact: true })).toHaveCount(0)
  await page.getByLabel('Description', { exact: true }).fill('Saved from authorized editor')
  await page.getByLabel('Radius (meters)', { exact: true }).fill('325')
  await page.getByLabel('Assign LONDON-1', { exact: true }).uncheck()
  await page.getByLabel('Assign LONDON-2', { exact: true }).uncheck()
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(page).toHaveURL('/geofences/9')
  await page.reload()
  await expect(page.getByText('Saved from authorized editor', { exact: true })).toBeVisible()
  await expect(page.getByText('0 assigned', { exact: true })).toBeVisible()
  await page.goto('/geofences/9/edit')
  await expect(page.getByLabel('Radius (meters)', { exact: true })).toHaveValue('325')
  await expect(page.getByLabel('Assign LONDON-1', { exact: true })).not.toBeChecked()
  await expect(page.getByLabel('Assign LONDON-2', { exact: true })).not.toBeChecked()
})

test('denied permission and unavailable current access never expose a live save control', async ({
  page,
}) => {
  await signIn(page, 'denied')
  await expect(page.getByText('You do not have permission to edit geofences.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toHaveCount(0)
  await page.route('**/api/_wuu/access', (route) => route.fulfill({ status: 503, json: {} }))
  await page.reload()
  await expect(page.getByText('Geofence permissions could not be checked.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toHaveCount(0)
})

test('permission recheck failure preserves the draft and sends no mutation until explicit retry', async ({
  page,
}) => {
  await signIn(page)
  const description = page.getByLabel('Description', { exact: true })
  await description.fill('Keep this unsaved draft')
  let writes = 0
  page.on('request', (request) => {
    if (request.method() === 'PUT' && request.url().includes('/api/_wuu/geofences/')) writes++
  })
  await page.route('**/api/_wuu/access', (route) => route.fulfill({ status: 503, json: {} }))
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(page.getByText('Geofence permissions could not be checked.')).toBeVisible()
  await expect(description).toHaveValue('Keep this unsaved draft')
  expect(writes).toBe(0)
  await page.unroute('**/api/_wuu/access')
  await page.getByRole('button', { name: 'Check permissions again', exact: true }).click()
  await expect(description).toHaveValue('Keep this unsaved draft')
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(page).toHaveURL('/geofences/9')
  expect(writes).toBe(1)
})

test('live maps disclose persistence and preserve large radius through numeric-only editing', async ({
  page,
}) => {
  await signIn(page)
  await expect(page.getByText('Geographic boundary · Live data', { exact: true })).toBeVisible()
  await expect(
    page.getByText('Real map · Boundary changes are session-only', { exact: true }),
  ).toHaveCount(0)
  await page.getByLabel('Radius (meters)', { exact: true }).fill('10000')
  await expect(page.getByRole('button', { name: 'Resize circle', exact: true })).toHaveCount(0)
  await expect(
    page.getByText(
      'Use numeric coordinates for this boundary; bounded map editing is unavailable.',
    ),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(page).toHaveURL('/geofences/9')
  await page.goto('/geofences/9/edit')
  await expect(page.getByLabel('Radius (meters)', { exact: true })).toHaveValue('10000')
})

test('saving freezes map handles and fields throughout a delayed permission check', async ({
  page,
}) => {
  await signIn(page)
  await page.getByLabel('Description', { exact: true }).fill('Immutable save snapshot')
  let release!: () => void
  let seen!: () => void
  const ready = new Promise<void>((resolve) => {
    release = resolve
  })
  const received = new Promise<void>((resolve) => {
    seen = resolve
  })
  await page.route('**/api/_wuu/access', async (route) => {
    seen()
    await ready
    await route.continue()
  })
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await received
  try {
    await expect(page.getByRole('button', { name: 'Resize circle', exact: true })).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Move circle center', exact: true })).toHaveCount(
      0,
    )
    await expect(page.getByLabel('Radius (meters)', { exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: 'Save changes', exact: true })).toBeDisabled()
  } finally {
    release()
  }
  await expect(page).toHaveURL('/geofences/9')
})

test('a failed create retains the boundary and draft without automatic replay', async ({
  page,
}) => {
  await signIn(page, 'write', '/geofences/new')
  await page.getByLabel('Geofence name', { exact: true }).fill('Keep my new depot')
  await page.getByRole('button', { name: 'Set circle using coordinates', exact: true }).click()
  await page.getByLabel('Radius (meters)', { exact: true }).fill('400')
  let writes = 0
  await page.route('**/api/_wuu/geofences', async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    writes++
    return route.fulfill({ status: 503, json: { message: 'Temporary save failure' } })
  })
  await page.getByRole('button', { name: 'Create geofence', exact: true }).click()
  await expect(page.getByRole('alert').filter({ hasText: 'Temporary save failure' })).toBeVisible()
  await expect(page.getByLabel('Geofence name', { exact: true })).toHaveValue('Keep my new depot')
  await expect(page.getByLabel('Radius (meters)', { exact: true })).toHaveValue('400')
  expect(writes).toBe(1)
})

test('a role name without write permission cannot enter the live creation form', async ({
  page,
}) => {
  await signIn(page, 'denied', '/geofences')
  await expect(page.getByRole('link', { name: 'Create geofence', exact: true })).toBeDisabled()
  await page.goto('/geofences/new')
  await expect(
    page.getByText('You do not have permission to edit geofences.', { exact: true }),
  ).toBeVisible()
  await expect(page.getByLabel('Geofence name', { exact: true })).toHaveCount(0)
})

test('clearing a numeric coordinate does not silently save zero', async ({ page }) => {
  await signIn(page)
  let writes = 0
  page.on('request', (request) => {
    if (request.method() === 'PUT' && request.url().includes('/api/_wuu/geofences/')) writes++
  })
  await page.getByLabel('Center latitude', { exact: true }).fill('')
  await expect(page.getByLabel('Center latitude', { exact: true })).toHaveValue('')
  await page.getByRole('button', { name: 'Save changes', exact: true }).click()
  await expect(page.locator('#geofence-errors')).toContainText('valid')
  expect(writes).toBe(0)
})
