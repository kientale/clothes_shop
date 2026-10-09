// Auth types mirror docs/openapi.json; commerce types describe the existing UI.

export type Role = 'CUSTOMER' | 'ADMIN'
export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'VOID'
export type PaymentMethod = 'COD'

export interface Problem {
  success: false
  message: string
  code: string
  requestId: string
  errors?: Record<string, string>
}

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data: T
  timestamp: string
  requestId: string
  errors?: Record<string, string>
}

export interface AccountResponse {
  id: string
  email: string
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'SUSPENDED'
  roles: string[]
  permissions: string[]
  customer: {
    id: string
    fullName: string
    phone: string | null
    dateOfBirth: string | null
  } | null
  avatarUrl?: string | null
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

/** Account data with a display label for the UI; roles remain the server's full list. */
export interface UserResponse extends AccountResponse {
  displayName: string
  avatarUrl?: string | null
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  account: AccountResponse
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  fullName: string
  confirmPassword: string
  phone?: string | null
  dateOfBirth?: string | null
}

export interface CategoryRequest {
  name: string
  slug: string
}

export interface CategoryResponse extends CategoryRequest {
  id: string
}

export interface VariantRequest {
  sku: string
  size: string
  color: string
  price: number
  initialStock: number
}

export interface ProductRequest {
  name: string
  slug: string
  description: string | null
  brand: string
  categoryId: string
  images: string[]
  variants: VariantRequest[]
}

export interface VariantResponse {
  id: string
  sku: string
  size: string
  color: string
  price: number
  availableQuantity: number
}

export interface ProductResponse {
  id: string
  name: string
  slug: string
  description: string | null
  brand: string
  categoryId: string
  active: boolean
  currency: 'VND'
  images: string[]
  variants: VariantResponse[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface StockResponse {
  variantId: string
  quantity: number
}

export interface Adjustment {
  delta: number
  reason: string
}

export interface CartItemResponse {
  variantId: string
  productName: string
  sku: string
  size: string
  color: string
  quantity: number
  unitPrice: number
  lineTotal: number
  active: boolean
}

export interface CartResponse {
  items: CartItemResponse[]
  subtotal: number
  currency: 'VND'
}

export interface ShippingAddress {
  recipientName: string
  phone: string
  line1: string
  city: string
  district: string
  ward: string
}

export interface CheckoutRequest {
  shippingAddress: ShippingAddress
  paymentMethod: PaymentMethod
  customerNote: string | null
}

export interface OrderItemResponse {
  variantId: string
  productName: string
  sku: string
  size: string
  color: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

export interface OrderResponse {
  id: string
  status: OrderStatus
  paymentMethod: PaymentMethod
  paymentStatus: PaymentStatus
  currency: 'VND'
  subtotal: number
  shippingFee: number
  total: number
  shippingAddress: ShippingAddress
  customerNote: string | null
  items: OrderItemResponse[]
  createdAt: string
}

// Administrator and role management (mirrors /api/v1/admin/admin-accounts, /roles, /permissions).

export type AccountStatus = AccountResponse['status']

export interface AdminAccountResponse {
  id: string
  email: string
  status: AccountStatus
  fullName: string | null
  phone: string | null
  avatarUrl: string | null
  /** Role codes, sorted by code. */
  roles: string[]
  roleIds: string[]
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface AdminAccountCreateRequest {
  email: string
  password: string
  fullName: string
  phone: string | null
  avatarUrl: string | null
  roleIds: string[]
}

export interface AdminAccountUpdateRequest {
  email: string
  fullName: string
  phone: string | null
  avatarUrl: string | null
  status: AccountStatus
  roleIds: string[]
}

export interface PermissionResponse {
  id: string
  name: string
  code: string
  module: string
}

export interface RoleResponse {
  id: string
  name: string
  code: string
  permissions: PermissionResponse[]
  createdAt: string
  updatedAt: string
}

export interface RoleCreateRequest {
  name: string
  code: string
  permissionIds: string[]
}

export interface RoleUpdateRequest {
  name: string
  permissionIds: string[]
}

// Customer management (mirrors /api/v1/admin/customers).

export type CustomerStatus = 'ACTIVE' | 'INACTIVE' | 'BLOCKED'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER'

export interface CustomerResponse {
  id: string
  accountId: string | null
  /** Login email of the linked account; null for guests or a soft-deleted account. */
  email: string | null
  fullName: string
  phone: string | null
  gender: Gender | null
  /** yyyy-MM-dd */
  dateOfBirth: string | null
  avatarUrl: string | null
  status: CustomerStatus
  createdAt: string
  updatedAt: string
}

export interface CustomerUpdateRequest {
  fullName: string
  phone: string | null
  gender: Gender | null
  dateOfBirth: string | null
  avatarUrl: string | null
  status: CustomerStatus
}

export interface CustomerCreateRequest extends CustomerUpdateRequest {
  accountId: string | null
}

export interface CustomerSummaryResponse {
  total: number
  active: number
  inactive: number
  blocked: number
}

export interface LinkableAccountResponse {
  id: string
  email: string
  createdAt: string
}
