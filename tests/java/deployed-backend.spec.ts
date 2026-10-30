import { randomBytes, randomUUID } from 'node:crypto'
import { expect, test, type Page } from '@playwright/test'

test.skip(!process.env.WUU_DEPLOYED_FIXTURE, 'Requires the owned disposable Java fixture')

test('actual Java: tenant fleet, sealed sessions and account-wide revocation', async ({
  browser,
  request,
}) => {
  const backend = process.env.WUU_DEPLOYED_API!
  const frontend = process.env.PLAYWRIGHT_BASE_URL!
  const run = randomUUID().slice(0, 8)
  const owners = ['alpha', 'beta'].map((tenant) => ({
    email: `${tenant}-${run}@example.test`,
    password: randomBytes(24).toString('hex'),
    first_name: tenant,
    last_name: 'Acceptance',
    organization_name: `Acceptance ${tenant} ${run}`,
    vehicle_id: `${tenant}-${run}`,
    license_plate: `${tenant.toUpperCase()}-${run}`,
    token: '',
    organization: 0,
    user: 0,
    alert: 0,
  }))
  for (const owner of owners) {
    const registration = await request.post(`${backend}/api/v1/auth/register`, { data: owner })
    expect(registration.status(), 'disposable tenant owner registration').toBe(201)
    const login = await request.post(`${backend}/api/v1/auth/login`, {
      data: { email: owner.email, password: owner.password },
    })
    expect(login.status()).toBe(200)
    owner.token = (await login.json()).access_token
    const headers = { authorization: `Bearer ${owner.token}` }
    const identity = await request.get(`${backend}/api/v1/users/me`, { headers })
    expect(identity.status()).toBe(200)
    const user = await identity.json()
    expect(user.roles).toContain('TENANT_ADMIN')
    owner.organization = user.organization_id
    owner.user = user.id
    const vehicle = await request.post(`${backend}/api/v1/vehicles`, {
      headers,
      data: {
        vehicle_id: owner.vehicle_id,
        license_plate: owner.license_plate,
        model: 'Acceptance',
      },
    })
    expect(vehicle.status(), 'API creates real persisted vehicle').toBe(201)
    const alert = await request.post(`${backend}/alerts`, {
      headers,
      data: {
        vehicleId: owner.vehicle_id,
        trackerId: `acceptance-${run}`,
        alertType: 'OVERSPEED',
        severity: 'WARNING',
        message: 'Disposable acceptance alert',
      },
    })
    expect(alert.status(), 'API creates tenant-scoped acceptance alert').toBe(201)
    owner.alert = (await alert.json()).alertId
  }
  expect(owners[0]!.organization).not.toBe(owners[1]!.organization)
  const catalogFor = (owner: (typeof owners)[number], override?: number) =>
    request.get(`${backend}/api/v1/roles?page=0&size=100${override ? `&orgId=${override}` : ''}`, {
      headers: { authorization: `Bearer ${owner.token}` },
    })
  const alphaCatalog = await catalogFor(owners[0]!, owners[1]!.organization)
  const betaCatalog = await catalogFor(owners[1]!)
  expect(alphaCatalog.status()).toBe(200)
  expect(betaCatalog.status()).toBe(200)
  const alphaRoles = (await alphaCatalog.json()).content as Array<{
    id: number
    name: string
    permissions: string[]
  }>
  const betaRoles = (await betaCatalog.json()).content as Array<{
    id: number
    name: string
    permissions: string[]
  }>
  expect(alphaRoles.map((role) => role.name)).toContain('TENANT_ADMIN')
  expect(alphaRoles.find((role) => role.name === 'TENANT_ADMIN')?.permissions).toContain(
    'roles.read',
  )
  expect(alphaRoles.map((role) => role.id)).not.toEqual(
    expect.arrayContaining(betaRoles.map((role) => role.id)),
  )
  expect((await catalogFor(owners[0]!, undefined)).status()).toBe(200)

  async function signIn(page: Page, owner: (typeof owners)[number]) {
    let browserBearer = false
    page.on('request', (request) => {
      if (request.headers().authorization) browserBearer = true
    })
    await page.goto('/sign-in?next=/vehicles')
    await expect(page.locator('[data-workspace-ready]')).toHaveAttribute(
      'data-workspace-ready',
      'true',
    )
    await page.getByLabel('Email', { exact: true }).fill(owner.email)
    await page.getByLabel('Password', { exact: true }).fill(owner.password)
    const response = page.waitForResponse(
      (r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST',
    )
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    const login = await response
    expect(login.status()).toBe(200)
    await expect(page).toHaveURL('/vehicles')
    // Read the same real BFF contract via its HTTP client: full navigation can discard
    // Chromium's DevTools response body before response.text() reads it.
    const contract = await page.request.post('/api/auth/login', {
      headers: { origin: frontend },
      data: { email: owner.email, password: owner.password },
    })
    expect(contract.status()).toBe(200)
    expect(/access_token|refresh_token|accessToken|refreshToken/.test(await contract.text())).toBe(
      false,
    )
    await expect(page.getByText('1 vehicles · Live fleet')).toBeVisible()
    await expect(
      page.getByRole('link', { name: `View ${owner.license_plate}`, exact: true }),
    ).toBeVisible()
    const cookie = (await page.context().cookies()).find((c) => c.name === 'wuu_session')
    expect(Boolean(cookie?.httpOnly && cookie.sameSite === 'Strict')).toBe(true)
    expect(cookie?.expires).toBeGreaterThan(Date.now() / 1000 + 3_600)
    expect(
      Boolean(cookie && !cookie.value.includes(owner.token) && !cookie.value.includes('.')),
    ).toBe(true)
    const refreshed = await page.request.post('/api/auth/refresh', {
      headers: { origin: frontend },
    })
    expect(refreshed.status(), 'BFF rotates the real Java refresh token').toBe(200)
    expect(refreshed.headers()['set-cookie']).toContain('wuu_session=')
    const refreshedBody = await refreshed.json()
    expect(refreshedBody.user.email).toBe(owner.email)
    expect(refreshedBody.expires_at).toBeGreaterThan(Date.now())
    expect(
      /access_token|refresh_token|accessToken|refreshToken/.test(JSON.stringify(refreshedBody)),
    ).toBe(false)
    expect((await page.request.get('/api/_wuu/vehicles')).status()).toBe(200)
    const visibleState = await page.evaluate(() => ({
      cookie: document.cookie,
      local: JSON.stringify(localStorage),
      session: JSON.stringify(sessionStorage),
      nuxt: JSON.stringify(window.__NUXT__),
    }))
    expect(JSON.stringify(visibleState).includes(owner.token)).toBe(false)
    expect(
      /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(JSON.stringify(visibleState)),
    ).toBe(false)
    expect(visibleState.cookie.includes('wuu_session')).toBe(false)
    expect(browserBearer).toBe(false)
  }

  const contexts = await Promise.all([
    browser.newContext(),
    browser.newContext(),
    browser.newContext(),
  ])
  try {
    const [alpha, beta, secondAlpha] = await Promise.all(contexts.map((c) => c.newPage()))
    await signIn(alpha!, owners[0]!)
    await signIn(beta!, owners[1]!)
    await signIn(secondAlpha!, owners[0]!)
    console.log('PASS three browser logins, two owners, cookie and bearer secrecy')
    const alertPath = `/api/_wuu/alerts/${owners[0]!.alert}`
    expect(
      (
        await beta!.request.post(`${alertPath}/acknowledge`, {
          headers: { origin: frontend },
          data: {},
        })
      ).status(),
    ).toBe(404)
    expect(
      (
        await alpha!.request.post(`${alertPath}/acknowledge`, {
          headers: { origin: 'https://evil.test' },
          data: {},
        })
      ).status(),
    ).toBe(403)
    expect(
      (
        await alpha!.request.post(`${alertPath}/acknowledge`, {
          headers: { origin: frontend },
          data: { acknowledgedBy: 'forged' },
        })
      ).status(),
    ).toBe(400)
    const acknowledged = await alpha!.request.post(`${alertPath}/acknowledge`, {
      headers: { origin: frontend },
      data: {},
    })
    expect(acknowledged.status()).toBe(200)
    expect(await acknowledged.json()).toMatchObject({
      acknowledged: true,
      acknowledgedBy: String(owners[0]!.user),
    })
    const resolved = await alpha!.request.post(`${alertPath}/resolve`, {
      headers: { origin: frontend },
      data: { resolutionNotes: 'Reviewed during disposable acceptance' },
    })
    expect(resolved.status()).toBe(200)
    expect(await resolved.json()).toMatchObject({
      resolved: true,
      resolvedBy: String(owners[0]!.user),
      resolutionNotes: 'Reviewed during disposable acceptance',
    })
    expect(await (await alpha!.request.get(alertPath)).json()).toMatchObject({
      acknowledged: true,
      resolved: true,
    })
    console.log(
      'PASS real alert acknowledge/resolve, authenticated actor persistence, cross-tenant and Origin denial',
    )
    for (const [page, own, foreign] of [
      [alpha!, owners[0]!, owners[1]!],
      [beta!, owners[1]!, owners[0]!],
    ] as const) {
      const fleet = await page.request.get('/api/_wuu/vehicles')
      expect(fleet.status()).toBe(200)
      const payload = await fleet.text()
      expect(payload.includes(own.vehicle_id)).toBe(true)
      expect(payload.includes(foreign.vehicle_id)).toBe(false)
      expect((await page.request.get(`/api/_wuu/vehicles/${foreign.vehicle_id}`)).status()).toBe(
        404,
      )
      await page.getByRole('link', { name: `View ${own.license_plate}`, exact: true }).click()
      await expect(
        page.getByRole('heading', { name: own.license_plate, exact: true }),
      ).toBeVisible()
      await expect(
        page
          .locator('section')
          .filter({
            has: page.getByRole('heading', { name: 'Vehicle information', exact: true }),
          })
          .getByText('Live data', { exact: true }),
      ).toBeVisible()
      await page.goto(`/vehicles/${foreign.vehicle_id}`)
      await expect(
        page.getByRole('heading', { name: foreign.license_plate, exact: true }),
      ).toHaveCount(0)
      await expect(
        page.getByRole('heading', { name: 'Vehicle not found', exact: true }),
      ).toBeVisible()
      await expect(
        page.getByText('This vehicle is not part of the current workspace.'),
      ).toBeVisible()
      await page.goto('/vehicles')
      await expect(
        page.getByRole('link', { name: `View ${own.license_plate}`, exact: true }),
      ).toBeVisible()
    }
    console.log(
      'PASS own live fleet/detail and bidirectional foreign isolation; no preview fallback',
    )
    await alpha!.goto('/subscriptions')
    for (const name of ['Usage tokens', 'Monthly plan', 'Yearly plan']) {
      await expect(alpha!.getByRole('heading', { name, exact: true })).toBeVisible()
    }
    await alpha!.getByRole('button', { name: 'Request pending enrollment' }).first().click()
    await expect(alpha!.getByText('Status: PENDING', { exact: true })).toBeVisible()
    await expect(alpha!.getByText('Spendable units: 0', { exact: true })).toBeVisible()
    const subscriptions = await (await alpha!.request.get('/api/_wuu/subscriptions')).json()
    expect(subscriptions).toHaveLength(1)
    const subscriptionPath = `/api/_wuu/subscriptions/${subscriptions[0].id}`
    // Same tenant, different user: Java ownership is user-scoped, not tenant-wide.
    const colleagueEmail = `alpha-colleague-${run}@example.test`
    const colleaguePassword = randomBytes(24).toString('hex')
    const colleague = await request.post(`${backend}/api/v1/users`, {
      headers: { authorization: `Bearer ${owners[0]!.token}` },
      data: {
        email: colleagueEmail,
        password: colleaguePassword,
        first_name: 'Same',
        last_name: 'Tenant',
      },
    })
    expect(colleague.status(), 'same-tenant colleague creation').toBe(201)
    const colleagueLogin = await request.post(`${backend}/api/v1/auth/login`, {
      data: { email: colleagueEmail, password: colleaguePassword },
    })
    expect(colleagueLogin.status()).toBe(200)
    const colleagueHeaders = {
      authorization: `Bearer ${(await colleagueLogin.json()).access_token as string}`,
    }
    expect(
      (await request.get(`${backend}/api/v1/roles`, { headers: colleagueHeaders })).status(),
      'same-tenant OPERATOR lacks roles.read',
    ).toBe(403)
    const directSubscription = `${backend}/api/v1/subscriptions/${subscriptions[0].id}`
    expect((await request.get(directSubscription, { headers: colleagueHeaders })).status()).toBe(
      404,
    )
    expect(
      (
        await request.post(`${directSubscription}/cancel`, {
          headers: colleagueHeaders,
          data: {},
        })
      ).status(),
    ).toBe(404)
    expect(
      (await request.get(`${backend}/api/v1/subscriptions`, { headers: colleagueHeaders })).json(),
    ).resolves.toEqual([])
    expect((await beta!.request.get(subscriptionPath)).status()).toBe(404)
    expect(
      (
        await beta!.request.post(`${subscriptionPath}/cancel`, {
          headers: { origin: frontend },
          data: {},
        })
      ).status(),
    ).toBe(404)
    expect(
      (
        await alpha!.request.post(`${subscriptionPath}/grants`, {
          headers: { origin: frontend },
          data: {},
        })
      ).status(),
    ).toBe(405)
    expect(
      (
        await alpha!.request.post(`${subscriptionPath}/cancel`, {
          headers: { origin: 'https://evil.test' },
          data: {},
        })
      ).status(),
    ).toBe(403)
    await alpha!.reload()
    await expect(alpha!.getByText('Status: PENDING', { exact: true })).toBeVisible()
    await alpha!.getByRole('button', { name: 'Cancel enrollment', exact: true }).click()
    await alpha!.getByRole('button', { name: 'Confirm cancellation', exact: true }).click()
    await expect(alpha!.getByText('Status: CANCELED', { exact: true })).toBeVisible()
    await alpha!.reload()
    await expect(alpha!.getByText('Status: CANCELED', { exact: true })).toBeVisible()
    await beta!.goto('/subscriptions')
    await expect(beta!.getByRole('heading', { name: 'No enrollments', exact: true })).toBeVisible()
    await alpha!.goto('/vehicles')
    console.log(
      'PASS actual Java subscription catalog, pending enrollment, persisted cancellation, foreign ownership and CSRF/admin denials',
    )
    await alpha!.getByRole('button', { name: 'Sign out all sessions' }).click()
    await expect(alpha!).toHaveURL('/sign-in')
    expect((await alpha!.request.get('/api/auth/session')).status()).toBe(401)
    expect((await secondAlpha!.request.get('/api/auth/session')).status()).toBe(401)
    expect((await secondAlpha!.request.get('/api/_wuu/vehicles')).status()).toBe(401)
    expect(
      (
        await request.get(`${backend}/api/v1/users/me`, {
          headers: { authorization: `Bearer ${owners[0]!.token}` },
        })
      ).status(),
    ).toBe(401)
    expect((await beta!.request.get('/api/_wuu/vehicles')).status()).toBe(200)
    console.log(
      'PASS account-wide revocation across two browser sessions and direct Java token; other tenant unaffected',
    )
    expect((await contexts[0]!.cookies()).some((c) => c.name === 'wuu_session')).toBe(false)
    await contexts[0]!.addCookies([
      { name: 'wuu_session', value: 'forged-tenant-admin-cookie', url: frontend },
    ])
    expect((await alpha!.request.get('/api/_wuu/vehicles')).status()).toBe(401)
    await alpha!.goto('/vehicles')
    await expect(alpha!).toHaveURL(/\/sign-in/)
    console.log('PASS forged cookie denied API and protected HTML')
    // Current Java seeds grant TENANT_ADMIN trips.read but not system.admin.
    // Exercise a genuinely denied domain through the same authenticated BFF.
    expect((await beta!.request.get('/api/_wuu/trips')).status()).toBe(200)
    expect(
      (await beta!.request.get('/api/_wuu/notifications/templates')).status(),
      'Tenant administration never grants system.admin template access',
    ).toBe(403)
    expect((await beta!.request.get('/api/_wuu/vehicles')).status()).toBe(200)
    await beta!.goto('/admin')
    await expect(
      beta!.getByRole('heading', { name: 'Platform access is unavailable' }),
    ).toBeVisible()
    await beta!.goto('/vehicles')
    await expect(
      beta!.getByRole('link', { name: `View ${owners[1]!.license_plate}`, exact: true }),
    ).toBeVisible()
    console.log(
      'PASS real system-admin API denial and platform guard preserve permitted fleet/trip access',
    )
    await beta!.goto('/settings')
    const accessPanel = beta!.getByRole('region', { name: 'Current server access' })
    await expect(accessPanel.getByText('vehicles.read', { exact: true })).toBeVisible()
    await expect(accessPanel.getByText('system.admin', { exact: true })).toHaveCount(0)
    const accessResponse = await beta!.request.get('/api/_wuu/access')
    expect(accessResponse.status()).toBe(200)
    const access = await accessResponse.json()
    expect(access.organization_id).toBe(owners[1]!.organization)
    expect(access.permissions).toContain('vehicles.read')
    expect(access.permissions).not.toContain('system.admin')
    expect(Object.keys(access)).not.toContain('session_id')
    await accessPanel.getByRole('button', { name: 'Recheck access', exact: true }).click()
    await expect(accessPanel.getByText('vehicles.read', { exact: true })).toBeVisible()
    console.log(
      'PASS actual Java current-tenant effective access, no role-name platform elevation or session identifier leak',
    )
  } finally {
    await Promise.all(contexts.map((c) => c.close()))
  }
})
