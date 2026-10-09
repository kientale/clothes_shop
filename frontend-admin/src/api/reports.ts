import { api } from './client'
import type { Page } from './types'

// Mirrors /api/v1/admin/reports (docs/content-reports.md). `from` is inclusive, `to` exclusive.

export type ReportBucket = 'DAY' | 'WEEK' | 'MONTH'
export type CampaignType = 'COUPON' | 'PROMOTION' | 'FLASH_SALE'

export interface ReportPeriod {
  from: string
  to: string
  timezone: string
}

export interface RevenuePoint {
  bucketStart: string
  paidAmount: number
  refundedAmount: number
  netReceivedAmount: number
}

export interface RevenueReport {
  period: ReportPeriod
  groupBy: ReportBucket
  paidAmount: number
  refundedAmount: number
  netReceivedAmount: number
  series: RevenuePoint[]
}

export interface StatusCount {
  status: string
  count: number
  amount: number
}

export interface OrderPoint {
  bucketStart: string
  orderCount: number
  cancelledCount: number
  orderAmount: number
}

export interface OrderReport {
  period: ReportPeriod
  groupBy: ReportBucket
  totalOrders: number
  cancelledOrders: number
  orderAmount: number
  cancelledAmount: number
  averageOrderValue: number
  statuses: StatusCount[]
  series: OrderPoint[]
}

export interface BestsellerRow {
  productId: string
  productName: string
  deleted: boolean
  orderCount: number
  grossQuantity: number
  returnedQuantity: number
  netQuantity: number
  merchandiseAmount: number
}

export interface StockRow {
  inventoryId: string
  warehouseId: string
  warehouseName: string
  warehouseDeleted: boolean
  productVariantId: string
  productId: string
  productName: string
  sku: string
  productDeleted: boolean
  variantDeleted: boolean
  quantityOnHand: number
  quantityReserved: number
  quantityAvailable: number
  unitRetailPrice: number
  retailValue: number
}

export interface InventoryReport {
  asOf: string
  stockRecords: number
  quantityOnHand: number
  quantityReserved: number
  quantityAvailable: number
  retailValue: number
  stocks: Page<StockRow>
}

export interface CustomerReportRow {
  customerId: string
  fullName: string
  status: string
  deleted: boolean
  deliveredOrders: number
  orderAmount: number
  refundedAmount: number
  netOrderAmount: number
}

export interface CustomerReport {
  period: ReportPeriod
  newCustomers: number
  totalCustomers: number
  activeCustomers: number
  purchasingCustomers: number
  repeatCustomers: number
  customers: Page<CustomerReportRow>
}

export interface ReturnReportRow {
  returnRequestId: string
  orderId: string
  orderCode: string
  customerId: string
  requestType: string
  status: string
  reason: string
  requestedAt: string
  completedAt: string | null
  quantity: number
  refundedAmount: number
}

export interface ReturnReport {
  period: ReportPeriod
  totalRequests: number
  returnRequests: number
  exchangeRequests: number
  requestedQuantity: number
  completedQuantity: number
  refundedAmount: number
  statuses: StatusCount[]
  requests: Page<ReturnReportRow>
}

export interface CampaignRow {
  campaignType: CampaignType
  campaignId: string
  name: string
  code: string | null
  deleted: boolean
  orderCount: number
  releasedOrderCount: number
  quantity: number
  discountAmount: number
  attributedOrderAmount: number
}

export interface PromotionReport {
  period: ReportPeriod
  campaigns: Page<CampaignRow>
}

type Query = Record<string, string | number | boolean | undefined | null>

export const reports = {
  revenue: (query: Query) => api.get<RevenueReport>('/admin/reports/revenue', query),
  orders: (query: Query) => api.get<OrderReport>('/admin/reports/orders', query),
  bestsellers: (query: Query) => api.get<{ period: ReportPeriod; products: Page<BestsellerRow> }>('/admin/reports/bestsellers', query),
  inventory: (query: Query) => api.get<InventoryReport>('/admin/reports/inventory', query),
  customers: (query: Query) => api.get<CustomerReport>('/admin/reports/customers', query),
  returns: (query: Query) => api.get<ReturnReport>('/admin/reports/returns', query),
  promotions: (query: Query) => api.get<PromotionReport>('/admin/reports/promotions', query),
}

export const BUCKET_LABEL: Record<ReportBucket, string> = { DAY: 'Theo ngày', WEEK: 'Theo tuần', MONTH: 'Theo tháng' }
export const CAMPAIGN_TYPE_LABEL: Record<CampaignType, string> = { COUPON: 'Mã giảm giá', PROMOTION: 'Khuyến mãi', FLASH_SALE: 'Flash Sale' }
