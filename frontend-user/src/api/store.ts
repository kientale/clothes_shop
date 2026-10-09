import { api } from './client'
import type {
  ActivePolicy,
  Article,
  ExchangeOption,
  PaymentRedirect,
  PaymentResult,
  ReturnRequest,
  ReturnType,
  ServerCartLine,
  ShippingArea,
  ShippingQuote,
  SocialProviders,
  Suggestions,
  TokenResponse,
  AddressRequest,
  CollectionDetail,
  CollectionSummary,
  InboxItem,
  MyReview,
  Profile,
  SavedAddress,
  TrackedOrder,
  ArticleSummary,
  CheckoutRequest,
  CustomerOrder,
  Order,
  OrderStatus,
  Page,
  ProductCard,
  ProductDetail,
  StoreBanner,
  StoreCatalog,
  StoreConfiguration,
} from './types'

export interface ProductFilters {
  search?: string
  categoryId?: string
  brandId?: string
  gender?: string
  colorId?: string
  sizeId?: string
  minPrice?: number
  maxPrice?: number
  inStock?: boolean
  sort?: 'newest' | 'price_asc' | 'price_desc' | 'name'
  page?: number
  size?: number
}

/** Public storefront reads; no sign-in needed. */
export const store = {
  configuration: () => api.get<StoreConfiguration>('/store/configuration'),
  catalog: () => api.get<StoreCatalog>('/store/catalog'),
  banners: (position?: string) => api.get<StoreBanner[]>('/store/banners', { position }),
  products: (filters: ProductFilters) =>
    api.get<Page<ProductCard>>('/store/products', { ...filters, inStock: filters.inStock ? 'true' : undefined }),
  product: (key: string) => api.get<ProductDetail>(`/store/products/${encodeURIComponent(key)}`),
  articles: (articleType?: 'ARTICLE' | 'LOOKBOOK', size = 6, page = 0, search?: string) =>
    api.get<Page<ArticleSummary>>('/content/articles', { articleType, page, size, search }),
  article: (slug: string) => api.get<Article>(`/content/articles/${encodeURIComponent(slug)}`),
  suggest: (q: string, signal?: AbortSignal) => api.get<Suggestions>('/store/search/suggest', { q }, signal),
  /** Checkout without an account; answers the tracking view of the new order. */
  guestCheckout: (body: CheckoutRequest) => api.post<TrackedOrder>('/store/orders', body),
  guestPay: (orderCode: string, phone: string, method: string) => api.post<PaymentRedirect>('/store/orders/pay', { orderCode, phone, method }),
  shippingQuote: (shippingMethodCode: string, area: ShippingArea | null, items: { productVariantId: string; quantity: number }[]) =>
    api.post<ShippingQuote>('/store/shipping/quote', { shippingMethodCode, area, items }),
  /** "Email me when it is back" for a sold-out size. */
  stockAlert: (productVariantId: string, email: string) => api.post<null>('/store/stock-alerts', { productVariantId, email }),
  /** The signed result the gateway appended to the return address, checked (and settled) by the backend. */
  paymentReturn: (gateway: string, params: Record<string, string>) => api.post<PaymentResult>(`/payments/${gateway}/return`, params),
  policy: (policyType: string) => api.get<ActivePolicy>(`/content/store-policies/${policyType}`),
  collections: () => api.get<CollectionSummary[]>('/store/collections'),
  collection: (slug: string, page = 0, size = 24) =>
    api.get<CollectionDetail>(`/store/collections/${encodeURIComponent(slug)}`, { page, size }),
  /** Order tracking without signing in: order code plus the phone on the order. */
  trackOrder: (orderCode: string, phone: string) => api.post<TrackedOrder>('/store/orders/lookup', { orderCode, phone }),
}

/** Email verification and Google/Facebook sign-in. */
export const account = {
  providers: () => api.get<SocialProviders>('/auth/providers'),
  verifyEmail: (token: string) => api.post<null>('/auth/email/verify', { token }),
  resendVerification: () => api.post<null>('/auth/email/resend'),
  google: (credential: string) => api.post<TokenResponse>('/auth/google', { credential }),
  facebook: (accessToken: string) => api.post<TokenResponse>('/auth/facebook', { accessToken }),
}

/** Password reset by email; both calls work without signing in. */
export const passwordReset = {
  request: (email: string) => api.post<null>('/auth/password/forgot', { email }),
  reset: (token: string, password: string, confirmPassword: string) =>
    api.post<null>('/auth/password/reset', { token, password, confirmPassword }),
}

/** The signed-in customer's profile, password, addresses, wishlist, inbox and reviews. */
export const me = {
  profile: () => api.get<Profile>('/me/profile'),
  updateProfile: (body: { fullName: string; phone: string | null; gender: string | null; dateOfBirth: string | null }) =>
    api.put<Profile>('/me/profile', body),
  changePassword: (body: { currentPassword: string; newPassword: string; confirmPassword: string }) => api.put<null>('/me/password', body),
  addresses: () => api.get<SavedAddress[]>('/me/addresses'),
  addAddress: (body: AddressRequest) => api.post<SavedAddress>('/me/addresses', body),
  updateAddress: (id: string, body: AddressRequest) => api.put<SavedAddress>(`/me/addresses/${id}`, body),
  makeDefault: (id: string) => api.post<SavedAddress>(`/me/addresses/${id}/default`),
  deleteAddress: (id: string) => api.delete(`/me/addresses/${id}`),
  wishlist: (page = 0, size = 24) => api.get<Page<ProductCard>>('/me/wishlist', { page, size }),
  wishlistIds: () => api.get<string[]>('/me/wishlist/ids'),
  like: (productId: string) => api.put<string[]>(`/me/wishlist/${productId}`),
  unlike: (productId: string) => api.delete<string[]>(`/me/wishlist/${productId}`),
  inbox: (query: { read?: boolean; page: number; size: number }) => api.get<Page<InboxItem>>('/me/notifications', query),
  markRead: (id: string) => api.put<InboxItem>(`/me/notifications/${id}/read`),
  reviews: (page = 0, size = 50) => api.get<Page<MyReview>>('/me/product-reviews', { page, size }),
  submitReview: (body: { orderItemId: string; rating: number; comment: string | null; images: string[] }) =>
    api.post<MyReview>('/me/product-reviews', body),
  /** Photo for a review or a return request; answers its public URL. */
  uploadImage: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<{ id: string; url: string }>('/me/uploads/images', form)
  },
  cart: () => api.get<ServerCartLine[]>('/me/cart'),
  saveCart: (items: { productVariantId: string; quantity: number }[]) => api.put<ServerCartLine[]>('/me/cart', { items }),
  returns: (query: { orderId?: string; page: number; size: number }) => api.get<Page<ReturnRequest>>('/me/returns', query),
  requestReturn: (body: {
    orderId: string
    requestType: ReturnType
    reason: string
    items: { orderItemId: string; quantity: number; reason: string | null; replacementVariantId: string | null }[]
    images: string[]
  }) => api.post<ReturnRequest>('/me/returns', body),
  cancelReturn: (id: string) => api.post<ReturnRequest>(`/me/returns/${id}/cancel`),
  exchangeOptions: (orderId: string, itemId: string) => api.get<ExchangeOption[]>(`/me/orders/${orderId}/items/${itemId}/exchange-options`),
}

/** The signed-in customer's orders. */
export const myOrders = {
  list: (query: { status?: OrderStatus; page: number; size: number }) => api.get<Page<Order>>('/me/orders', query),
  get: (id: string) => api.get<CustomerOrder>(`/me/orders/${id}`),
  checkout: (body: CheckoutRequest) => api.post<CustomerOrder>('/me/orders', body),
  cancel: (id: string) => api.post<CustomerOrder>(`/me/orders/${id}/cancel`),
  pay: (id: string, method: string) => api.post<PaymentRedirect>(`/me/orders/${id}/pay`, { method }),
}
