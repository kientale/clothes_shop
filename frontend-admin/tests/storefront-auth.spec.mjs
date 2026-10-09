import { test, expect } from '@playwright/test'
import { CUSTOMER, TOKEN, failure, mockApi, assertNoToken } from './auth-fixtures.mjs'

test.use({ baseURL: 'http://127.0.0.1:3300' })
const KEY = 'lemonadex.token'

test('customer login returns to the protected page, restores profile and logs out', async ({ page }) => {
  const requests = await mockApi(page, { account: CUSTOMER })
  await page.goto('/shop/orders')
  await expect(page).toHaveURL(/\/shop\/login$/)
  await page.getByLabel('Email', { exact: true }).fill(CUSTOMER.email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill('Password-1234')
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page).toHaveURL(/\/shop\/orders$/)
  await expect(page.locator('.user-name')).toHaveText('Nguyễn An')
  await page.reload()
  await expect(page.locator('.user-name')).toHaveText('Nguyễn An')
  expect(requests.some(({ path, headers }) => path === '/api/v1/auth/me' && headers.authorization === `Bearer ${TOKEN}`)).toBe(true)
  expect(requests.some(({ path }) => path === '/api/v1/me')).toBe(false)
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await assertNoToken(page, KEY)
  await page.goto('/shop/orders')
  await expect(page).toHaveURL(/\/shop\/login$/)
})

test('registration sends the backend fullName and confirmPassword fields', async ({ page }) => {
  const requests = await mockApi(page, { account: CUSTOMER })
  await page.goto('/shop/register')
  await page.getByLabel('Họ tên', { exact: true }).fill('Nguyễn An')
  await page.getByLabel('Email', { exact: true }).fill(CUSTOMER.email)
  await page.getByLabel('Mật khẩu (8-72 ký tự)', { exact: true }).fill('Password-1234')
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill('Password-1234')
  await page.getByRole('button', { name: 'Đăng ký', exact: true }).click()
  await expect(page.locator('.user-name')).toHaveText('Nguyễn An')
  const request = requests.find(({ path }) => path === '/api/v1/auth/register')
  expect(request.body).toEqual({ email: CUSTOMER.email, fullName: 'Nguyễn An', password: 'Password-1234', confirmPassword: 'Password-1234' })
  expect(request.headers.authorization).toBeUndefined()
})

test('registration catches mismatched confirmation before sending a request', async ({ page }) => {
  const requests = await mockApi(page, { account: CUSTOMER })
  await page.goto('/shop/register')
  await page.getByLabel('Họ tên', { exact: true }).fill('Nguyễn An')
  await page.getByLabel('Email', { exact: true }).fill(CUSTOMER.email)
  await page.getByLabel('Mật khẩu (8-72 ký tự)', { exact: true }).fill('Password-1234')
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill('Different-1234')
  await page.getByRole('button', { name: 'Đăng ký', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Mật khẩu xác nhận không khớp.')
  // Public provider discovery may run; invalid forms must not submit authentication requests.
  expect(requests.filter(({ path, method }) => path.startsWith('/api/v1/auth') && method !== 'GET')).toEqual([])
})

test('an expired storefront session redirects to login and clears its token', async ({ page }) => {
  await page.addInitScript(({ key, token }) => localStorage.setItem(key, token), { key: KEY, token: TOKEN })
  await mockApi(page, { account: CUSTOMER, profile: { status: 401, json: failure('UNAUTHORIZED', 'Token expired') } })
  await page.goto('/shop/orders')
  await expect(page).toHaveURL(/\/shop\/login$/)
  await assertNoToken(page, KEY)
})

test('storefront displays backend credential errors and does not store a token', async ({ page }) => {
  await mockApi(page, { login: { status: 401, json: failure('INVALID_CREDENTIALS', 'Invalid email or password') } })
  await page.goto('/shop/login')
  await page.getByLabel('Email', { exact: true }).fill(CUSTOMER.email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu không đúng.')
  await assertNoToken(page, KEY)
})
