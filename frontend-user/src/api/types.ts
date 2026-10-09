// Mirrors docs/openapi.json: auth (/auth), the public storefront (/store, /content) and the customer's orders (/me/orders).

export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'COMPLETED' | 'CANCELLED'
export type OrderPaymentStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'PARTIALLY_REFUNDED' | 'REFUNDED'
export type ShippingStatus = 'NOT_SHIPPED' | 'PENDING' | 'SHIPPED' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED' | 'CANCELLED' | 'RETURNED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'VOID'
export type Gender = 'MEN' | 'WOMEN' | 'UNISEX' | 'KIDS'

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
  /** False until the owner opens the link emailed at registration. */
  emailVerified: boolean
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'SUSPENDED'
  roles: string[]
  permissions: string[]
  customer: {
    id: string
    fullName: string
    phone: string | null
    dateOfBirth: string | null
  } | null
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

/** Account data with a display label for the UI; roles remain the server's full list. */
export interface UserResponse extends AccountResponse {
  displayName: string
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

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// Storefront catalog

export interface StoreCatalog {
  categories: { id: string; parentId: string | null; name: string; slug: string }[]
  brands: { id: string; name: string; slug: string; logoUrl: string | null }[]
  colors: { id: string; name: string; code: string; hexCode: string | null }[]
  sizes: { id: string; name: string; code: string; sortOrder: number }[]
}

export interface StoreBanner {
  id: string
  title: string
  imageUrl: string
  linkUrl: string | null
  position: string
}

export interface ProductCard {
  id: string
  slug: string
  name: string
  brandName: string
  categoryName: string
  gender: Gender
  imageUrl: string | null
  hoverImageUrl: string | null
  minPrice: number
  maxPrice: number
  /** Set only when it is above the highest current price (a real markdown). */
  compareAtPrice: number | null
  colors: { name: string; hexCode: string | null }[]
  inStock: boolean
  createdAt: string
}

export interface VariantOption {
  id: string
  sku: string
  colorId: string
  colorName: string
  hexCode: string | null
  sizeId: string
  sizeName: string
  sizeSortOrder: number
  price: number
  compareAtPrice: number | null
  available: number
}

export interface ProductDetail {
  id: string
  slug: string
  productCode: string
  name: string
  shortDescription: string | null
  description: string | null
  material: string | null
  gender: Gender
  brand: { id: string; name: string; slug: string }
  category: { id: string; name: string; slug: string }
  images: { url: string; altText: string | null }[]
  variants: VariantOption[]
  ratingAverage: number
  ratingCount: number
  reviews: { id: string; rating: number; comment: string | null; customerName: string; images: string[]; createdAt: string }[]
  /** Measurements per size; null when the shop has not written one. */
  sizeChart: SizeChart | null
}

export interface SizeChart {
  columns: string[]
  rows: string[][]
  note: string | null
}

export interface StoreConfiguration {
  store: {
    storeName: string
    supportEmail: string | null
    supportPhone: string | null
    address: string | null
    logoUrl: string | null
    websiteUrl: string | null
    businessHours: string | null
    zaloPhone?: string | null
    messengerUrl?: string | null
  }
  general: { timezone: string; language: string; maintenanceMode: boolean; maintenanceMessage: string | null }
  ordersEnabled: boolean
  paymentMethods: PaymentOption[]
  shippingMethods: { code: string; name: string; provider: string; baseFee: number; estimatedDays: number | null }[]
}

export interface ArticleSummary {
  id: string
  title: string
  slug: string
  thumbnailUrl: string | null
  articleType: 'ARTICLE' | 'LOOKBOOK'
  publishedAt: string
}

export interface Article extends ArticleSummary {
  content: string
  images: string[]
  updatedAt: string
}

export interface ActivePolicy {
  policyType: string
  title: string
  content: string
  version: number
  activatedAt: string
  updatedAt: string
}

// Customer orders

export interface CheckoutRequest {
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  note: string | null
  items: { productVariantId: string; quantity: number }[]
  shippingMethodCode: string | null
  paymentMethod: string | null
  couponCode: string | null
  /** Required when checking out without an account; the confirmation goes here. */
  email?: string | null
  /** Parts of the address, so a carrier (GHTK) can price the delivery. */
  area?: ShippingArea | null
}

export interface ShippingArea {
  province: string
  district: string | null
  ward: string | null
  street: string | null
}

export interface ShippingQuote {
  shippingMethodCode: string | null
  shippingFee: number
  freeShipping: boolean
  /** True when the fee came from the carrier for this address. */
  live: boolean
}

export interface OrderItem {
  id: string
  productVariantId: string
  productName: string
  sku: string
  colorName: string
  sizeName: string
  unitPrice: number
  quantity: number
  discountAmount: number
  totalAmount: number
}

export interface Order {
  id: string
  orderCode: string
  orderStatus: OrderStatus
  paymentStatus: OrderPaymentStatus
  shippingStatus: ShippingStatus
  subtotal: number
  discountAmount: number
  shippingFee: number
  totalAmount: number
  paidAmount: number
  refundedAmount: number
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  note: string | null
  shippingMethodCode: string | null
  items: OrderItem[]
  placedAt: string
  confirmedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
}

export interface CustomerOrder {
  order: Order
  payments: { id: string; paymentMethod: string; amount: number; status: PaymentStatus; paidAt: string | null; createdAt: string }[]
  shipments: {
    id: string
    shippingProvider: string
    trackingCode: string | null
    status: Exclude<ShippingStatus, 'NOT_SHIPPED'>
    shippedAt: string | null
    deliveredAt: string | null
  }[]
}

export interface PaymentOption {
  code: string
  name: string
  provider: string
  kind: 'COD' | 'BANK_TRANSFER' | 'VNPAY' | 'MOMO' | null
  bankDetails: { bankName: string; accountNumber: string; accountHolder: string } | null
  instructions: string | null
}

// Customer account

export type CustomerGender = 'MALE' | 'FEMALE' | 'OTHER'

export interface Profile {
  accountId: string
  email: string
  fullName: string
  phone: string | null
  gender: CustomerGender | null
  dateOfBirth: string | null
}

export interface SavedAddress {
  id: string
  recipientName: string
  phone: string
  addressLine: string
  ward: string
  district: string | null
  province: string
  isDefault: boolean
}

export interface AddressRequest {
  recipientName: string
  phone: string
  addressLine: string
  ward: string
  district: string | null
  province: string
  makeDefault: boolean
}

export interface CollectionSummary {
  id: string
  slug: string
  name: string
  description: string | null
  imageUrl: string | null
  productCount: number
  startAt: string | null
  endAt: string | null
}

export interface CollectionDetail {
  collection: CollectionSummary
  products: Page<ProductCard>
}

export interface TrackedOrder {
  orderCode: string
  orderStatus: OrderStatus
  paymentStatus: OrderPaymentStatus
  shippingStatus: ShippingStatus
  placedAt: string
  confirmedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  recipientName: string
  shippingArea: string
  items: { productName: string; colorName: string; sizeName: string; quantity: number; totalAmount: number }[]
  subtotal: number
  discountAmount: number
  shippingFee: number
  totalAmount: number
  paidAmount: number
  shipments: { shippingProvider: string; trackingCode: string | null; status: string; shippedAt: string | null; deliveredAt: string | null }[]
  payments: { paymentMethod: string; amount: number; status: PaymentStatus }[]
}

export interface InboxItem {
  id: string
  notificationId: string
  title: string
  content: string
  notificationType: 'GENERAL' | 'ORDER' | 'PROMOTION'
  read: boolean
  readAt: string | null
  createdAt: string
}

export interface MyReview {
  id: string
  productId: string
  orderItemId: string
  rating: number
  comment: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  images: string[]
  moderationNote: string | null
  createdAt: string
}

// Online payments

export interface PaymentRedirect {
  gateway: 'VNPAY' | 'MOMO'
  payUrl: string
  reference: string
  amount: number
}

export interface PaymentResult {
  orderId: string
  orderCode: string
  gateway: string
  status: 'PAID' | 'FAILED' | 'PENDING'
  amount: number
}

// Returns and exchanges

export type ReturnType = 'RETURN' | 'EXCHANGE'
export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED'

export interface ReturnRequest {
  id: string
  orderId: string
  requestType: ReturnType
  reason: string
  status: ReturnStatus
  note: string | null
  items: { id: string; orderItemId: string; quantity: number; reason: string | null; replacementVariantId: string | null }[]
  images: string[]
  refundableAmount: number
  requestedAt: string
  approvedAt: string | null
  rejectedAt: string | null
  completedAt: string | null
}

export interface ExchangeOption {
  variantId: string
  colorName: string
  sizeName: string
  available: number
}

// Saved cart, search, sign-in

export interface ServerCartLine {
  variantId: string
  productId: string
  slug: string
  name: string
  imageUrl: string | null
  colorName: string
  sizeName: string
  sku: string
  price: number
  quantity: number
  available: number
  sellable: boolean
}

export interface Suggestions {
  products: ProductCard[]
  categories: StoreCatalog['categories']
  brands: StoreCatalog['brands']
}

export interface SocialProviders {
  googleClientId: string | null
  facebookAppId: string | null
}
