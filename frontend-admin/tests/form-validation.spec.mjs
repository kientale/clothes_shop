import { test, expect } from '@playwright/test'
import { ADMIN, CUSTOMER, TOKEN, envelope, mockApi } from './auth-fixtures.mjs'

const SHOP = 'http://127.0.0.1:3300'
const writes = (requests) => requests.filter(({ method }) => ['POST', 'PUT', 'PATCH'].includes(method))

async function admin(page, permissions) {
  await page.addInitScript((token) => localStorage.setItem('trankien.admin.token', token), TOKEN)
  return mockApi(page, { account: { ...ADMIN, permissions } })
}

test('registration rejects whitespace, malformed email and short password with inline accessible errors', async ({ page }) => {
  const requests = await mockApi(page, { account: CUSTOMER })
  await page.goto(`${SHOP}/shop/register`)
  await page.getByLabel('Họ tên', { exact: true }).fill('   ')
  await page.getByLabel('Email', { exact: true }).fill('an@')
  await page.getByLabel('Mật khẩu (8-72 ký tự)', { exact: true }).fill('short')
  await page.getByRole('button', { name: 'Đăng ký', exact: true }).click()
  await expect(page.getByLabel('Họ tên', { exact: true })).toBeFocused()
  for (const name of ['Họ tên', 'Email', 'Mật khẩu (8-72 ký tự)', 'Xác nhận mật khẩu']) {
    const input = page.getByLabel(name, { exact: true })
    await expect(input).toHaveAttribute('aria-invalid', 'true')
    await expect(input).toHaveAttribute('aria-describedby', /error/)
  }
  expect(writes(requests)).toEqual([])
  await page.getByLabel('Họ tên', { exact: true }).fill('Nguyễn An')
  await expect(page.getByLabel('Họ tên', { exact: true })).not.toHaveAttribute('aria-invalid', 'true')
})

test('password validation counts UTF-8 bytes before login, including a revealed admin password', async ({ page }) => {
  const requests = await mockApi(page)
  await page.goto('/login')
  await page.getByLabel('Email hoặc tên đăng nhập', { exact: true }).fill('admin')
  await page.getByLabel('Mật khẩu', { exact: true }).fill('ắ'.repeat(25))
  await page.getByRole('button', { name: 'Hiện ký tự đã nhập', exact: true }).click()
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('72 byte UTF-8')
  expect(writes(requests)).toEqual([])
})

test('order lookup validates the recipient phone before calling the API', async ({ page }) => {
  const requests = await mockApi(page)
  await page.goto(`${SHOP}/shop/track`)
  await page.getByLabel('Mã đơn hàng', { exact: true }).fill('LX-123')
  await page.getByLabel('Số điện thoại người nhận', { exact: true }).fill('090abc123')
  await page.getByRole('button', { name: /Tra cứu/ }).click()
  await expect(page.getByLabel('Số điện thoại người nhận', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  expect(writes(requests)).toEqual([])
})

test('forgot password shows errors on blur and clears them when corrected', async ({ page }) => {
  const requests = await mockApi(page)
  await page.goto(`${SHOP}/shop/forgot-password`)
  const email = page.getByLabel('Email', { exact: true })
  await email.fill('invalid')
  await email.blur()
  await expect(email).toHaveAttribute('aria-invalid', 'true')
  await email.fill(CUSTOMER.email)
  await expect(email).not.toHaveAttribute('aria-invalid', 'true')
  await page.getByRole('button', { name: 'Gửi liên kết', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('email đặt lại mật khẩu')
  expect(writes(requests)).toHaveLength(1)
})

test('catalog dialog validates external submit buttons, optional fields and image URLs', async ({ page }) => {
  const requests = await admin(page, ['PRODUCT_READ', 'PRODUCT_WRITE'])
  await page.goto('/brands')
  await page.getByRole('button', { name: 'Thêm thương hiệu', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Thêm thương hiệu', exact: true }).click()
  await expect(dialog.locator('input').first()).toHaveAttribute('aria-invalid', 'true')
  expect(writes(requests)).toEqual([])
  await dialog.getByLabel('Tên thương hiệu', { exact: true }).fill('TranKien')
  await dialog.getByRole('button', { name: 'Dán đường dẫn ảnh', exact: true }).click()
  await dialog.getByLabel('Đường dẫn ảnh đại diện', { exact: true }).fill('javascript:alert(1)')
  await dialog.getByRole('button', { name: 'Thêm thương hiệu', exact: true }).click()
  await expect(dialog.getByLabel('Đường dẫn ảnh đại diện', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  expect(writes(requests)).toEqual([])
  await dialog.getByLabel('Đường dẫn ảnh đại diện', { exact: true }).fill('')
  await dialog.getByRole('button', { name: 'Thêm thương hiệu', exact: true }).click()
  await expect.poll(() => writes(requests).length).toBe(1)
  expect(writes(requests)[0].body).toMatchObject({ name: 'TranKien', logoUrl: null })
})

test('order settings reject missing values, zero, fractions and values outside backend limits', async ({ page }) => {
  await admin(page, ['SETTINGS_ORDER_READ', 'SETTINGS_ORDER_WRITE'])
  const writes = []
  const configuration = { ordersEnabled: true, autoConfirm: false, orderCodePrefix: 'LX', minimumOrderAmount: 0, maxItems: 100,
    maxQuantityPerItem: 1000000, enableMarketingByDefault: true, allowCancellation: true, allowReturns: true, returnWindowDays: 30 }
  await page.route('**/api/v1/admin/settings/order', async (route) => {
    if (route.request().method() === 'PUT') writes.push(route.request().postDataJSON())
    await route.fulfill({ json: envelope({ configuration, revision: 0, updatedAt: null }) })
  })
  await page.goto('/settings/orders')
  const count = page.getByLabel('Số dòng tối đa mỗi đơn', { exact: true })
  for (const value of ['', '0', '1.5', '101', 'Infinity']) {
    await count.fill(value)
    await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
    await expect(count).toHaveAttribute('aria-invalid', 'true')
    expect(writes).toEqual([])
  }
  await page.getByRole('button', { name: 'Hoàn tác', exact: true }).click()
  await expect(count).toHaveValue('100')
  await expect(count).not.toHaveAttribute('aria-invalid', 'true')
  await count.fill('50')
  await page.getByLabel('Giá trị đơn tối thiểu', { exact: true }).fill('100.00')
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
  await expect(page.getByLabel('Giá trị đơn tối thiểu', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  expect(writes).toEqual([])
  await page.getByLabel('Giá trị đơn tối thiểu', { exact: true }).fill('100.000')
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
  await expect.poll(() => writes.length).toBe(1)
  expect(writes[0].configuration).toMatchObject({ maxItems: 50, minimumOrderAmount: 100000 })
})

test('coupon percentages preserve decimals and reject values over 100', async ({ page }) => {
  const requests = await admin(page, ['COUPON_READ', 'COUPON_WRITE'])
  await page.goto('/coupons')
  await page.getByRole('button', { name: 'Tạo mã giảm giá', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Mã', { exact: true }).fill('WELCOME')
  await dialog.getByLabel('Tên hiển thị', { exact: true }).fill('Chào bạn mới')
  await dialog.getByLabel('Kiểu giảm', { exact: true }).selectOption('PERCENTAGE')
  await dialog.getByLabel('Phần trăm giảm', { exact: true }).fill('101')
  await dialog.getByLabel('Bắt đầu', { exact: true }).fill('2026-10-15T09:00')
  await dialog.getByLabel('Kết thúc', { exact: true }).fill('2026-10-16T09:00')
  await dialog.getByRole('button', { name: 'Tạo mã', exact: true }).click()
  await expect(dialog.getByLabel('Phần trăm giảm', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  expect(writes(requests)).toEqual([])
  await dialog.getByLabel('Phần trăm giảm', { exact: true }).fill('12.5')
  await dialog.getByLabel('Kết thúc', { exact: true }).fill('2026-10-14T09:00')
  await dialog.getByRole('button', { name: 'Tạo mã', exact: true }).click()
  await expect(dialog.getByLabel('Kết thúc', { exact: true })).toHaveAttribute('aria-invalid', 'true')
  expect(writes(requests)).toEqual([])
  await dialog.getByLabel('Kết thúc', { exact: true }).fill('2026-10-16T09:00')
  await dialog.getByRole('button', { name: 'Tạo mã', exact: true }).click()
  await expect.poll(() => writes(requests).length).toBe(1)
  expect(writes(requests)[0].body.discountValue).toBe(12.5)
})
