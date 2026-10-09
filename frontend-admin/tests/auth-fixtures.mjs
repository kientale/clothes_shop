import { expect } from '@playwright/test'

export const TOKEN = 'test.jwt.token'
export const ADMIN = {
  id: '00000000-0000-4000-8000-000000000001', email: 'admin@example.com', status: 'ACTIVE',
  roles: ['ADMIN'], permissions: ['ACCOUNT_READ', 'AUTH_PROFILE_READ'], customer: null,
  lastLoginAt: null, createdAt: '2026-10-08T00:00:00Z', updatedAt: '2026-10-08T00:00:00Z',
}
export const CUSTOMER = {
  ...ADMIN, email: 'customer@example.com', roles: ['CUSTOMER'], permissions: ['AUTH_PROFILE_READ'],
  customer: { id: '00000000-0000-4000-8000-000000000002', fullName: 'Nguyễn An', phone: null, dateOfBirth: null },
}
export const envelope = (data) => ({ success: true, code: 'SUCCESS', message: 'Success', data,
  timestamp: '2026-10-08T00:00:00Z', requestId: 'test-request' })
export const authResponse = (account = ADMIN) => envelope({ accessToken: TOKEN, tokenType: 'Bearer', expiresIn: 900, account })
export const failure = (code, message) => ({ success: false, code, message, data: null, requestId: 'failed-request' })
const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }

export async function mockApi(page, { account = ADMIN, login, profile, register, resourceStatus } = {}) {
  const requests = []
  await page.route('https://picsum.photos/**', (route) => route.abort())
  await page.route('https://api.getlayers.ai/**', (route) => route.abort())
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    requests.push({ path, method: request.method(), headers: request.headers(), body: request.postDataJSON() })
    if (path === '/api/v1/auth/login') await route.fulfill(login ?? { json: authResponse(account) })
    else if (path === '/api/v1/auth/register') await route.fulfill(register ?? { json: authResponse(account) })
    else if (path === '/api/v1/auth/me') await route.fulfill(profile ?? { json: envelope(account) })
    else if (path === '/api/v1/auth/providers') await route.fulfill({ json: envelope({ googleClientId: null, facebookAppId: null }) })
    else if (resourceStatus) await route.fulfill({ status: resourceStatus, json: failure('DENIED', 'Request denied') })
    else if (path === '/api/v1/categories') await route.fulfill({ json: envelope([]) })
    else if (path === '/api/v1/cart') await route.fulfill({ json: envelope({ items: [], subtotal: 0, currency: 'VND' }) })
    else if (path === '/api/v1/me/cart') await route.fulfill({ json: envelope([]) })
    else await route.fulfill({ json: envelope(emptyPage) })
  })
  return requests
}

export async function adminLogin(page, username = 'admin', password = 'admin123') {
  await page.getByLabel('Email hoặc tên đăng nhập', { exact: true }).fill(username)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
}

export async function assertNoToken(page, key = 'lemonadex.admin.token') {
  await expect.poll(() => page.evaluate((key) => localStorage.getItem(key), key)).toBeNull()
}
