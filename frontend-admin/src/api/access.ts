import { ApiError, api } from './client'
import { COMMERCE_CODE_MESSAGE, COMMERCE_MODULE_LABEL, COMMERCE_PERMISSION_LABEL } from './messages'
import type {
  AccountStatus,
  AdminAccountCreateRequest,
  AdminAccountResponse,
  AdminAccountUpdateRequest,
  Page,
  PermissionResponse,
  RoleCreateRequest,
  RoleResponse,
  RoleUpdateRequest,
} from './types'

/** Role codes the backend refuses to edit or delete. */
export const SYSTEM_ROLES = ['ADMIN', 'CUSTOMER']

export const adminAccounts = {
  list: (query: { status?: AccountStatus; search?: string; page: number; size: number }) =>
    api.get<Page<AdminAccountResponse>>('/admin/admin-accounts', query),
  get: (id: string, signal?: AbortSignal) =>
    api.get<AdminAccountResponse>(`/admin/admin-accounts/${id}`, undefined, signal),
  create: (body: AdminAccountCreateRequest) => api.post<AdminAccountResponse>('/admin/admin-accounts', body),
  update: (id: string, body: AdminAccountUpdateRequest) => api.put<AdminAccountResponse>(`/admin/admin-accounts/${id}`, body),
  resetPassword: (id: string, password: string) => api.put<void>(`/admin/admin-accounts/${id}/password`, { password }),
  remove: (id: string) => api.delete(`/admin/admin-accounts/${id}`),
}

export const roles = {
  list: (query: { search?: string; page: number; size: number }) => api.get<Page<RoleResponse>>('/admin/roles', query),
  /** Every role, for pickers; the backend caps a page at 50. */
  all: async () => {
    const first = await roles.list({ page: 0, size: 50 })
    const rest = await Promise.all(
      Array.from({ length: Math.max(0, first.totalPages - 1) }, (_, i) => roles.list({ page: i + 1, size: 50 })),
    )
    return [first, ...rest].flatMap((page) => page.content)
  },
  create: (body: RoleCreateRequest) => api.post<RoleResponse>('/admin/roles', body),
  update: (id: string, body: RoleUpdateRequest) => api.put<RoleResponse>(`/admin/roles/${id}`, body),
  remove: (id: string) => api.delete(`/admin/roles/${id}`),
  permissions: () => api.get<PermissionResponse[]>('/admin/permissions'),
}

export const STATUS_LABEL: Record<AccountStatus, string> = {
  ACTIVE: 'Đang hoạt động',
  INACTIVE: 'Ngừng hoạt động',
  LOCKED: 'Đã khóa',
  SUSPENDED: 'Tạm đình chỉ',
}

export const MODULE_LABEL: Record<string, string> = {
  AUTH: 'Xác thực',
  ACCOUNT: 'Tài khoản',
  ROLE: 'Vai trò & quyền',
  CUSTOMER: 'Khách hàng',
  ...COMMERCE_MODULE_LABEL,
}

/** Vietnamese labels for the seeded permission codes; unknown codes fall back to the server name. */
export const PERMISSION_LABEL: Record<string, string> = {
  AUTH_PROFILE_READ: 'Xem hồ sơ của chính mình',
  ACCOUNT_READ: 'Xem danh sách tài khoản',
  ACCOUNT_WRITE: 'Quản lý tài khoản admin',
  ROLE_READ: 'Xem vai trò và quyền',
  ROLE_WRITE: 'Quản lý vai trò',
  CUSTOMER_READ: 'Xem khách hàng',
  CUSTOMER_WRITE: 'Quản lý khách hàng',
  ...COMMERCE_PERMISSION_LABEL,
}

const CODE_MESSAGE: Record<string, string> = {
  ...COMMERCE_CODE_MESSAGE,
  SELF_ADMIN_MODIFICATION: 'Bạn không thể khóa, ngừng hoạt động hoặc xóa chính tài khoản của mình.',
  LAST_ACTIVE_ADMIN: 'Phải còn ít nhất một tài khoản admin đang hoạt động.',
  EMAIL_ALREADY_REGISTERED: 'Email này đã được dùng cho tài khoản khác.',
  SYSTEM_ROLE_PROTECTED: 'Vai trò hệ thống (ADMIN, CUSTOMER) không thể sửa hoặc xóa.',
  ROLE_IN_USE: 'Vai trò đang được gán cho tài khoản, hãy gỡ vai trò khỏi các tài khoản trước khi xóa.',
  ROLE_CODE_EXISTS: 'Mã vai trò đã tồn tại.',
  FORBIDDEN: 'Tài khoản của bạn không có quyền thực hiện thao tác này.',
  RESOURCE_NOT_FOUND: 'Không tìm thấy dữ liệu, có thể đã bị xóa. Hãy tải lại trang.',
  VALIDATION_FAILED: 'Một số trường chưa hợp lệ.',
  CUSTOMER_ACCOUNT_ALREADY_LINKED: 'Tài khoản này đã có hồ sơ khách hàng (kể cả hồ sơ đã xóa).',
  EMPTY_FILE: 'File ảnh đang trống.',
  UNSUPPORTED_FILE_TYPE: 'Chỉ nhận ảnh JPEG, PNG, WebP hoặc GIF.',
  FILE_TOO_LARGE: 'Ảnh vượt quá dung lượng cho phép.',
  SLUG_EXISTS: 'Slug này đã được dùng (kể cả mục đã xóa). Hãy đổi slug khác.',
  CODE_EXISTS: 'Mã này đã được dùng (kể cả mục đã xóa).',
  PRODUCT_CODE_EXISTS: 'Mã sản phẩm đã được dùng (kể cả sản phẩm đã xóa).',
  SKU_EXISTS: 'SKU đã được dùng cho biến thể khác.',
  VARIANT_EXISTS: 'Sản phẩm đã có biến thể với màu và size này.',
  INVALID_COMPARE_PRICE: 'Giá gốc (giá so sánh) không được thấp hơn giá bán.',
  INVALID_PERIOD: 'Thời gian kết thúc phải sau thời gian bắt đầu.',
  DUPLICATE_PRODUCTS: 'Mỗi sản phẩm chỉ có mặt một lần trong bộ sưu tập.',
  INVALID_SLUG: 'Slug phải có ít nhất một chữ cái hoặc chữ số.',
  CATEGORY_CYCLE: 'Không thể đặt danh mục vào chính nó hoặc danh mục con của nó.',
  CATEGORY_HAS_CHILDREN: 'Danh mục còn danh mục con. Hãy chuyển hoặc xóa danh mục con trước.',
  CATEGORY_IN_USE: 'Danh mục còn sản phẩm. Hãy chuyển sản phẩm sang danh mục khác trước.',
  BRAND_IN_USE: 'Thương hiệu còn sản phẩm. Hãy chuyển sản phẩm sang thương hiệu khác trước.',
  COLOR_IN_USE: 'Màu đang được dùng cho biến thể sản phẩm.',
  SIZE_IN_USE: 'Size đang được dùng cho biến thể sản phẩm.',
}

const ARGUMENT_MESSAGE: Record<string, string> = {
  'An administrator must retain the ADMIN role': 'Tài khoản admin phải giữ vai trò ADMIN.',
  'Password must contain 8-72 characters and at most 72 UTF-8 bytes': 'Mật khẩu phải có từ 8 đến 72 ký tự.',
  'Customer profiles can only link to customer accounts': 'Chỉ liên kết được với tài khoản khách hàng (không phải admin).',
}

/** Translates backend error codes into messages an operator can act on. */
export function accessErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'INVALID_ARGUMENT') return ARGUMENT_MESSAGE[error.message] ?? error.message
    return CODE_MESSAGE[error.code] ?? error.message
  }
  return error instanceof Error ? error.message : 'Đã xảy ra lỗi không xác định.'
}

/** Field-level validation messages from a 400 VALIDATION_FAILED response. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || error.code !== 'VALIDATION_FAILED') return {}
  // Bean Validation messages arrive in English; show a Vietnamese rule per field instead.
  return Object.fromEntries(
    Object.keys(error.fieldErrors).map((field) => [field, FIELD_MESSAGE[field] ?? 'Giá trị chưa hợp lệ.']),
  )
}

const FIELD_MESSAGE: Record<string, string> = {
  email: 'Nhập email hợp lệ, tối đa 254 ký tự.',
  fullName: 'Nhập họ tên, tối đa 150 ký tự.',
  name: 'Nhập tên, tối đa 100 ký tự.',
  code: 'Mã gồm 2-50 ký tự IN HOA, số hoặc dấu gạch dưới, bắt đầu bằng chữ cái.',
  phone: 'Số điện thoại gồm 8-15 chữ số, có thể bắt đầu bằng +.',
  avatarUrl: 'Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://, tối đa 2048 ký tự.',
  password: 'Mật khẩu phải có từ 8 đến 72 ký tự.',
  dateOfBirth: 'Ngày sinh phải ở trong quá khứ.',
  gender: 'Giới tính không hợp lệ.',
  status: 'Chọn trạng thái.',
  roleIds: 'Chọn ít nhất một vai trò.',
  permissionIds: 'Danh sách quyền không hợp lệ.',
  slug: 'Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang.',
  productCode: 'Mã gồm chữ, số, gạch ngang hoặc gạch dưới, tối đa 80 ký tự.',
  sku: 'SKU gồm chữ, số, gạch ngang hoặc gạch dưới, tối đa 100 ký tự.',
  hexCode: 'Mã màu dạng #RRGGBB.',
  sortOrder: 'Thứ tự là số nguyên từ 0.',
  price: 'Giá phải từ 0, tối đa 2 chữ số thập phân.',
  basePrice: 'Giá phải từ 0, tối đa 2 chữ số thập phân.',
  compareAtPrice: 'Giá so sánh phải từ 0.',
  logoUrl: 'Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.',
  imageUrl: 'Đường dẫn ảnh phải bắt đầu bằng http:// hoặc https://.',
  images: 'Tối đa 10 ảnh, mỗi ảnh là đường dẫn http(s).',
  description: 'Mô tả quá dài.',
  productIds: 'Danh sách sản phẩm không hợp lệ.',
}
