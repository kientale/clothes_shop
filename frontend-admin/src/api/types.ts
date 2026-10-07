// Mirrors the schemas in docs/openapi.json. Money is a decimal VND amount.

export type Role = 'CUSTOMER' | 'ADMIN'
export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'VOID'
export type PaymentMethod = 'COD'

export interface Problem {
  type?: string
  title?: string
  status: number
  detail: string
  instance?: string
  code: string
  requestId: string
  errors?: Record<string, string>
}

export interface UserResponse {
  id: string
  email: string
  displayName: string
  role: Role
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserResponse
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  displayName: string
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
