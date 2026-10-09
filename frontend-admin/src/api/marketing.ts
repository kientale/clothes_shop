import { api } from './client'
import type { Tone } from './commerce'
import type { Page } from './types'

// Mirrors /api/v1/admin coupons, promotions, flash sales, banners, notifications, product reviews
// (docs/customer-marketing.md) and articles, store policies (docs/content-reports.md).

type Query = Record<string, string | number | boolean | undefined | null>
type Paged = { page: number; size: number }

export type MarketingStatus = 'ACTIVE' | 'INACTIVE'
export type DiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT'
export type PromotionType = 'ALL_PRODUCTS' | 'PRODUCT_DISCOUNT' | 'CATEGORY_DISCOUNT'
export type NotificationType = 'GENERAL' | 'ORDER' | 'PROMOTION'
export type NotificationTarget = 'ALL' | 'SELECTED'
export type NotificationStatus = 'DRAFT' | 'PUBLISHED'
export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type ArticleType = 'ARTICLE' | 'LOOKBOOK'
export type ArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type PolicyType = 'SHIPPING' | 'RETURN_EXCHANGE' | 'PAYMENT' | 'PRIVACY' | 'TERMS_OF_SERVICE' | 'WARRANTY'
export type PolicyStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'

interface Timestamps {
  createdAt: string
  updatedAt: string
}

export interface Coupon extends Timestamps {
  id: string
  code: string
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscount: number | null
  minimumOrderValue: number | null
  usageLimit: number | null
  usageLimitPerCustomer: number
  usedCount: number
  startAt: string
  endAt: string
  status: MarketingStatus
}

export interface CouponRequest {
  code: string
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscount: number | null
  minimumOrderValue: number | null
  usageLimit: number | null
  usageLimitPerCustomer: number | null
  startAt: string
  endAt: string
  status: MarketingStatus
}

export interface CouponUsage {
  id: string
  couponId: string
  customerId: string
  orderId: string
  discountAmount: number
  usedAt: string
  released: boolean
  releasedAt: string | null
}

export interface Promotion extends Timestamps {
  id: string
  name: string
  description: string | null
  promotionType: PromotionType
  discountType: DiscountType
  discountValue: number
  startAt: string
  endAt: string
  priority: number
  status: MarketingStatus
  productIds: string[]
  categoryIds: string[]
}

export type PromotionRequest = Omit<Promotion, 'id' | 'createdAt' | 'updatedAt'>

export interface FlashItem {
  id: string
  productVariantId: string
  flashPrice: number
  quantityLimit: number
  soldQuantity: number
  remainingQuantity: number
}

export interface FlashSale extends Timestamps {
  id: string
  name: string
  startAt: string
  endAt: string
  status: MarketingStatus
  items: FlashItem[]
}

export interface FlashSaleRequest {
  name: string
  startAt: string
  endAt: string
  status: MarketingStatus
  items: { productVariantId: string; flashPrice: number; quantityLimit: number }[]
}

export interface Banner extends Timestamps {
  id: string
  title: string
  imageUrl: string
  linkUrl: string | null
  position: string
  sortOrder: number
  startAt: string | null
  endAt: string | null
  status: MarketingStatus
}

export type BannerRequest = Omit<Banner, 'id' | 'createdAt' | 'updatedAt'>

export interface Notification extends Timestamps {
  id: string
  title: string
  content: string
  notificationType: NotificationType
  targetType: NotificationTarget
  status: NotificationStatus
  customerIds: string[]
  createdBy: string | null
  publishedAt: string | null
  recipientCount: number
  readCount: number
}

export interface NotificationRequest {
  title: string
  content: string
  notificationType: NotificationType
  targetType: NotificationTarget
  customerIds: string[]
}

export interface Review extends Timestamps {
  id: string
  productId: string
  customerId: string
  orderItemId: string
  rating: number
  comment: string | null
  status: ReviewStatus
  images: string[]
  moderationNote: string | null
  moderatedBy: string | null
  moderatedAt: string | null
}

export interface Article extends Timestamps {
  id: string
  title: string
  slug: string
  thumbnailUrl: string | null
  content: string
  articleType: ArticleType
  authorId: string | null
  status: ArticleStatus
  images: string[]
  publishedAt: string | null
}

export interface ArticleRequest {
  title: string
  slug: string | null
  thumbnailUrl: string | null
  content: string
  articleType: ArticleType
  images: string[]
}

export interface Policy extends Timestamps {
  id: string
  policyType: PolicyType
  title: string
  content: string
  version: number
  status: PolicyStatus
  updatedBy: string | null
  activatedAt: string | null
}

function resource<T, Req>(path: string) {
  return {
    list: (query: Query & Paged) => api.get<Page<T>>(`/admin/${path}`, query),
    get: (id: string) => api.get<T>(`/admin/${path}/${id}`),
    create: (body: Req) => api.post<T>(`/admin/${path}`, body),
    update: (id: string, body: Req) => api.put<T>(`/admin/${path}/${id}`, body),
    remove: (id: string) => api.delete(`/admin/${path}/${id}`),
  }
}

export const marketing = {
  coupons: {
    ...resource<Coupon, CouponRequest>('coupons'),
    usages: (id: string, query: Paged) => api.get<Page<CouponUsage>>(`/admin/coupons/${id}/usages`, query),
  },
  promotions: resource<Promotion, PromotionRequest>('promotions'),
  flashSales: resource<FlashSale, FlashSaleRequest>('flash-sales'),
  banners: resource<Banner, BannerRequest>('banners'),
  notifications: {
    ...resource<Notification, NotificationRequest>('notifications'),
    publish: (id: string) => api.post<Notification>(`/admin/notifications/${id}/publish`),
  },
}

export const reviews = {
  list: (query: Query & Paged) => api.get<Page<Review>>('/admin/product-reviews', query),
  moderate: (id: string, status: ReviewStatus, note: string) => api.put<Review>(`/admin/product-reviews/${id}/moderation`, { status, note }),
  remove: (id: string) => api.delete(`/admin/product-reviews/${id}`),
}

export const content = {
  articles: {
    ...resource<Article, ArticleRequest>('articles'),
    setStatus: (id: string, status: ArticleStatus) => api.put<Article>(`/admin/articles/${id}/status`, { status }),
  },
  policies: {
    ...resource<Policy, { policyType: PolicyType; title: string; content: string }>('store-policies'),
    setStatus: (id: string, status: PolicyStatus) => api.put<Policy>(`/admin/store-policies/${id}/status`, { status }),
  },
}

export const MARKETING_STATUS_LABEL: Record<MarketingStatus, string> = { ACTIVE: 'Đang bật', INACTIVE: 'Đang tắt' }
export const DISCOUNT_TYPE_LABEL: Record<DiscountType, string> = { PERCENTAGE: 'Phần trăm', FIXED_AMOUNT: 'Số tiền cố định' }
export const PROMOTION_TYPE_LABEL: Record<PromotionType, string> = {
  ALL_PRODUCTS: 'Toàn bộ sản phẩm',
  PRODUCT_DISCOUNT: 'Theo sản phẩm',
  CATEGORY_DISCOUNT: 'Theo danh mục',
}
export const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = { GENERAL: 'Chung', ORDER: 'Đơn hàng', PROMOTION: 'Khuyến mãi' }
export const NOTIFICATION_STATUS_LABEL: Record<NotificationStatus, string> = { DRAFT: 'Bản nháp', PUBLISHED: 'Đã phát hành' }
export const REVIEW_STATUS: Record<ReviewStatus, [string, Tone]> = {
  PENDING: ['Chờ duyệt', 'warn'],
  APPROVED: ['Đã duyệt', 'active'],
  REJECTED: ['Đã từ chối', 'blocked'],
}
export const ARTICLE_TYPE_LABEL: Record<ArticleType, string> = { ARTICLE: 'Bài viết', LOOKBOOK: 'Lookbook' }
export const ARTICLE_STATUS: Record<ArticleStatus, [string, Tone]> = {
  DRAFT: ['Bản nháp', 'inactive'],
  PUBLISHED: ['Đã xuất bản', 'active'],
  ARCHIVED: ['Lưu trữ', 'blocked'],
}
export const POLICY_TYPE_LABEL: Record<PolicyType, string> = {
  SHIPPING: 'Giao hàng',
  RETURN_EXCHANGE: 'Đổi trả',
  PAYMENT: 'Thanh toán',
  PRIVACY: 'Bảo mật',
  TERMS_OF_SERVICE: 'Điều khoản dịch vụ',
  WARRANTY: 'Bảo hành',
}
export const POLICY_STATUS: Record<PolicyStatus, [string, Tone]> = {
  DRAFT: ['Bản nháp', 'inactive'],
  ACTIVE: ['Đang áp dụng', 'active'],
  ARCHIVED: ['Đã thay thế', 'blocked'],
}

/** "Đang chạy", "Sắp diễn ra" or "Đã kết thúc" for a campaign window. */
export function campaignPhase(startAt: string | null, endAt: string | null, status: MarketingStatus): [string, Tone] {
  if (status === 'INACTIVE') return ['Đang tắt', 'inactive']
  const now = Date.now()
  if (startAt && new Date(startAt).getTime() > now) return ['Sắp diễn ra', 'info']
  if (endAt && new Date(endAt).getTime() <= now) return ['Đã kết thúc', 'blocked']
  return ['Đang chạy', 'active']
}
