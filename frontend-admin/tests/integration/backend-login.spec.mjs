import { test, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { adminLogin, assertNoToken } from '../auth-fixtures.mjs'

test.beforeEach(async ({ page }) => {
  await page.route('https://picsum.photos/**', (route) => route.abort())
  await page.route('https://api.getlayers.ai/**', (route) => route.abort())
})

test('real admin signs in with username and email, reloads and signs out', async ({ page }) => {
  await page.goto('/login')
  await adminLogin(page)
  await expect(page.locator('.account-name')).toHaveText('admin@example.com')
  const token = await page.evaluate(() => localStorage.getItem('lemonadex.admin.token'))
  expect(token.split('.')).toHaveLength(3)
  const profile = page.waitForResponse((response) => response.url().endsWith('/api/v1/auth/me'))
  await page.reload()
  expect((await profile).status()).toBe(200)
  await expect(page.locator('.account-name')).toHaveText('admin@example.com')
  await page.locator('.account-btn').click()
  await page.getByRole('menuitem', { name: 'Đăng xuất' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await assertNoToken(page)
  await adminLogin(page, 'admin@example.com')
  await expect(page.locator('.account-name')).toHaveText('admin@example.com')
})

test('real backend rejects the wrong admin password', async ({ page }) => {
  await page.goto('/login')
  const response = page.waitForResponse((response) => response.url().endsWith('/api/v1/auth/login'))
  await adminLogin(page, 'admin', 'wrong-password')
  expect((await response).status()).toBe(401)
  await expect(page.getByRole('alert')).toBeVisible()
  await assertNoToken(page)
  await expect(page).toHaveURL(/\/login$/)
})

test('real customer registers, reloads, signs in and cannot enter admin', async ({ page }) => {
  const email = `browser-${randomUUID()}@example.com`
  const password = 'Password-1234'
  await page.goto('http://127.0.0.1:3400/shop/orders')
  await expect(page).toHaveURL(/\/shop\/login$/)
  await page.locator('.auth-form').getByRole('link', { name: 'Tạo tài khoản', exact: true }).click()
  await page.getByLabel('Họ tên', { exact: true }).fill('Nguyễn An')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Mật khẩu (8-72 ký tự)', { exact: true }).fill(password)
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(password)
  const registration = page.waitForResponse((response) => response.url().endsWith('/api/v1/auth/register'))
  await page.getByRole('button', { name: 'Đăng ký', exact: true }).click()
  expect((await registration).status()).toBe(201)
  await expect(page).toHaveURL(/\/shop\/orders$/)
  await expect(page.locator('.user-name')).toHaveText('Nguyễn An')
  await page.reload()
  await expect(page.locator('.user-name')).toHaveText('Nguyễn An')
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await assertNoToken(page, 'lemonadex.token')
  await page.goto('http://127.0.0.1:3400/shop/login')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.locator('.user-name')).toHaveText('Nguyễn An')
  await page.goto('/login')
  await adminLogin(page, email, password)
  await expect(page.getByRole('alert')).toContainText('Tài khoản này không có quyền quản trị.')
  await assertNoToken(page)
})
