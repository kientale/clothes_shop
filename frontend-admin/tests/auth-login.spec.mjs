import { test, expect } from '@playwright/test'
import { ADMIN, CUSTOMER, TOKEN, envelope, failure, mockApi, adminLogin, assertNoToken } from './auth-fixtures.mjs'

const KEY = 'lemonadex.admin.token'

test('backend admin login persists JWT, restores profile and logs out', async ({ page }) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  const requests = await mockApi(page, { account: { ...ADMIN, roles: ['CUSTOMER', 'ADMIN'] } })
  await page.goto('/login')
  await adminLogin(page)
  await expect(page.locator('.account-name')).toHaveText(ADMIN.email)
  expect(requests.find(({ path }) => path === '/api/v1/auth/login')).toMatchObject({ body: { email: 'admin', password: 'admin123' } })
  expect(requests.find(({ path }) => path === '/api/v1/auth/login').headers.authorization).toBeUndefined()
  expect(await page.evaluate((key) => localStorage.getItem(key), KEY)).toBe(TOKEN)
  await page.reload()
  await expect(page.locator('.account-name')).toHaveText(ADMIN.email)
  expect(requests.some(({ path, headers }) => path === '/api/v1/auth/me' && headers.authorization === `Bearer ${TOKEN}`)).toBe(true)
  expect(requests.filter(({ path }) => !path.startsWith('/api/v1/auth/')).every(({ headers }) => headers.authorization === `Bearer ${TOKEN}`)).toBe(true)
  await page.locator('.account-btn').click()
  await page.getByRole('menuitem', { name: 'Đăng xuất' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await assertNoToken(page)
  await page.reload()
  await expect(page).toHaveURL(/\/login$/)
  expect(errors).toEqual([])
})

test('obsolete mock session does not grant access', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('lemonadex.admin.mock-session', 'admin'))
  const requests = await mockApi(page)
  await page.goto('/')
  await expect(page).toHaveURL(/\/login$/)
  expect(requests).toEqual([])
  expect(await page.evaluate(() => localStorage.getItem('lemonadex.admin.mock-session'))).toBeNull()
})

test('storage write failure preserves JWT in memory until logout or reload', async ({ page }) => {
  await page.addInitScript(() => {
    Storage.prototype.setItem = () => { throw new DOMException('Quota exceeded', 'QuotaExceededError') }
  })
  const requests = await mockApi(page)
  await page.goto('/login')
  await adminLogin(page)
  await expect(page.locator('.account-name')).toHaveText(ADMIN.email)
  await expect.poll(() => requests.filter(({ path }) => !path.startsWith('/api/v1/auth/')).length).toBeGreaterThan(0)
  expect(requests.filter(({ path }) => !path.startsWith('/api/v1/auth/')).every(({ headers }) => headers.authorization === `Bearer ${TOKEN}`)).toBe(true)
  await page.reload()
  await expect(page).toHaveURL(/\/login$/)
  await assertNoToken(page)
})

test('invalid credentials display the backend error without saving JWT', async ({ page }) => {
  await mockApi(page, { login: { status: 401, json: failure('INVALID_CREDENTIALS', 'Invalid email or password') } })
  await page.goto('/login')
  await adminLogin(page, 'admin@example.com', 'wrong-password')
  await expect(page.getByRole('alert')).toContainText('Invalid email or password')
  await assertNoToken(page)
})

test('customer credentials cannot enter admin or save an admin token', async ({ page }) => {
  await mockApi(page, { account: CUSTOMER })
  await page.goto('/login')
  await adminLogin(page, CUSTOMER.email, 'Password-1234')
  await expect(page.getByRole('alert')).toContainText('Tài khoản này không có quyền quản trị.')
  await expect(page).toHaveURL(/\/login$/)
  await assertNoToken(page)
})

for (const [name, profile] of [
  ['expired JWT', { status: 401, json: failure('UNAUTHORIZED', 'Token expired') }],
  ['removed admin role', { json: envelope(CUSTOMER) }],
]) {
  test(`${name} clears the stored admin session`, async ({ page }) => {
    await page.addInitScript(({ key, token }) => localStorage.setItem(key, token), { key: KEY, token: TOKEN })
    await mockApi(page, { profile })
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
    await assertNoToken(page)
  })
}

for (const [name, json] of [
  ['missing account', envelope({ accessToken: TOKEN, tokenType: 'Bearer', expiresIn: 900 })],
  ['missing token', envelope({ tokenType: 'Bearer', expiresIn: 900, account: ADMIN })],
  ['legacy response', { accessToken: TOKEN, user: ADMIN }],
]) {
  test(`rejects ${name} from the backend`, async ({ page }) => {
    await mockApi(page, { login: { json } })
    await page.goto('/login')
    await adminLogin(page)
    await expect(page.getByRole('alert')).toBeVisible()
    await assertNoToken(page)
    await expect(page).toHaveURL(/\/login$/)
  })
}

test('network failure cannot fall back to mock login', async ({ page }) => {
  await mockApi(page)
  await page.route('**/api/v1/auth/login', (route) => route.abort('connectionrefused'))
  await page.goto('/login')
  await adminLogin(page)
  await expect(page.getByRole('alert')).toContainText('Không kết nối được máy chủ.')
  await assertNoToken(page)
})

for (const status of [401, 403]) {
  test(`protected API ${status} ${status === 401 ? 'clears' : 'preserves'} the session`, async ({ page }) => {
    await mockApi(page, { resourceStatus: status })
    await page.goto('/login')
    await adminLogin(page)
    if (status === 401) {
      await expect(page).toHaveURL(/\/login$/)
      await assertNoToken(page)
    } else {
      await expect(page.locator('.account-name')).toHaveText(ADMIN.email)
      await expect(page.getByRole('alert')).toBeVisible()
      expect(await page.evaluate((key) => localStorage.getItem(key), KEY)).toBe(TOKEN)
    }
  })
}

test('admin navbar displays user avatar when avatarUrl is present, and falls back to initials when absent', async ({ page }) => {
  const avatarUrl = 'https://example.com/admin-avatar.jpg'
  await mockApi(page, { account: { ...ADMIN, avatarUrl } })
  await page.goto('/login')
  await adminLogin(page)
  const avatarImg = page.locator('.account-btn img.avatar')
  await expect(avatarImg).toBeVisible()
  await expect(avatarImg).toHaveAttribute('src', avatarUrl)

  // Sign out and sign in with an account having no avatarUrl
  await page.locator('.account-btn').click()
  await page.getByRole('menuitem', { name: 'Đăng xuất' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await mockApi(page, { account: { ...ADMIN, avatarUrl: null } })
  await adminLogin(page)
  await expect(page.locator('.account-btn span.avatar')).toBeVisible()
  await expect(page.locator('.account-btn span.avatar')).toHaveText('A')
})
