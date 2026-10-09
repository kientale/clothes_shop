import { api } from './client'
import type {
  CustomerCreateRequest,
  CustomerResponse,
  CustomerStatus,
  CustomerSummaryResponse,
  CustomerUpdateRequest,
  Gender,
  LinkableAccountResponse,
  Page,
} from './types'

export const customers = {
  list: (query: { status?: CustomerStatus; search?: string; page: number; size: number }) =>
    api.get<Page<CustomerResponse>>('/admin/customers', query),
  summary: () => api.get<CustomerSummaryResponse>('/admin/customers/summary'),
  linkableAccounts: (search: string) =>
    api.get<LinkableAccountResponse[]>('/admin/customers/linkable-accounts', { search, size: 10 }),
  create: (body: CustomerCreateRequest) => api.post<CustomerResponse>('/admin/customers', body),
  update: (id: string, body: CustomerUpdateRequest) => api.put<CustomerResponse>(`/admin/customers/${id}`, body),
  remove: (id: string) => api.delete(`/admin/customers/${id}`),
}

export const CUSTOMER_STATUS_LABEL: Record<CustomerStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  BLOCKED: 'Đã chặn',
}

export const GENDER_LABEL: Record<Gender, string> = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
}

const date = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })

/** Formats a yyyy-MM-dd date without shifting it through a timezone. */
export function formatBirthDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return date.format(new Date(year!, month! - 1, day!))
}
