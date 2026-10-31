import { expect, test, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'

async function signIn(page: Page, permission = 'write') {
  await page.goto('/sign-in?next=/geofences/9/edit')
  await page
    .getByLabel('Email', { exact: true })
    .fill(`geo-ui-${permission}-${randomUUID()}@example.com`)
  await page.getByLabel('Password', { exact: true }).fill('test-only-password')
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page).toHaveURL('/geofences/9/edit')
}

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
