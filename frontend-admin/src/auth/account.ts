import type { AccountResponse, UserResponse } from '../api/types'

export function toUser(account: AccountResponse): UserResponse {
  if (!account || typeof account.id !== 'string' || !account.id
    || typeof account.email !== 'string' || !account.email.trim() || account.status !== 'ACTIVE'
    || !Array.isArray(account.roles) || !account.roles.every((role) => typeof role === 'string')
    || !Array.isArray(account.permissions) || !account.permissions.every((permission) => typeof permission === 'string')) {
    throw new Error('Thông tin tài khoản từ máy chủ không hợp lệ.')
  }
  return {
    ...account,
    displayName: (typeof account.customer?.fullName === 'string' ? account.customer.fullName.trim() : '') || account.email,
    avatarUrl: (typeof account.avatarUrl === 'string' && account.avatarUrl.trim()) ? account.avatarUrl.trim() : null,
  }
}
