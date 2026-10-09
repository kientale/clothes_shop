import { api } from './client'
import type { Page } from './types'

// Mirrors /api/v1/admin warehouses, inventory, orders, payments, shipments, returns and refunds
// (see docs/inventory-orders.md). Money arrives as JSON numbers; timestamps are UTC ISO strings.

type Query = Record<string, string | number | boolean | undefined | null>
type Paged = { page: number; size: number }

export type WarehouseStatus = 'ACTIVE' | 'INACTIVE'
export type MovementType = 'RECEIPT' | 'ISSUE' | 'ADJUSTMENT' | 'RESERVE' | 'RELEASE' | 'SHIPMENT' | 'RETURN' | 'EXCHANGE'
export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'COMPLETED' | 'CANCELLED'
export type OrderPaymentStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'PARTIALLY_REFUNDED' | 'REFUNDED'
export type ShippingStatus = 'NOT_SHIPPED' | 'PENDING' | 'SHIPPED' | 'IN_TRANSIT' | 'DELIVERED' | 'FAILED' | 'CANCELLED' | 'RETURNED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'VOID'
export type ShipmentStatus = Exclude<ShippingStatus, 'NOT_SHIPPED'>
export type ReturnType = 'RETURN' | 'EXCHANGE'
export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED'
export type RefundStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export interface Warehouse {
  id: string
  name: string
  address: string
  status: WarehouseStatus
  createdAt: string
  updatedAt: string
}

export interface Stock {
  id: string
  warehouseId: string
  warehouseName: string
  productVariantId: string
  sku: string
  productName: string
  quantityOnHand: number
  quantityReserved: number
  quantityAvailable: number
  updatedAt: string
}

export interface Movement {
  id: string
  warehouseId: string
  productVariantId: string
  transactionType: MovementType
  quantity: number
  quantityBefore: number
  quantityAfter: number
  reservedBefore: number
  reservedAfter: number
  referenceType: 'MANUAL' | 'ORDER' | 'RETURN' | string
  referenceId: string | null
  note: string | null
  createdBy: string | null
  createdAt: string
}

export interface OrderItem {
  id: string
  productVariantId: string
  inventoryId: string
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
  customerId: string
  warehouseId: string
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
  /** Set for guest orders: where the confirmation went. */
  contactEmail: string | null
  items: OrderItem[]
  placedAt: string
  confirmedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface OrderHistory {
  id: string
  orderId: string
  fromStatus: OrderStatus | null
  toStatus: OrderStatus
  note: string | null
  changedBy: string | null
  createdAt: string
}

export interface Payment {
  id: string
  orderId: string
  paymentMethod: string
  amount: number
  status: PaymentStatus
  paidAt: string | null
  createdAt: string
}

export interface PaymentTransaction {
  id: string
  paymentId: string
  transactionCode: string
  provider: string
  providerTransactionId: string | null
  amount: number
  status: PaymentStatus
  createdAt: string
}

export interface PaymentMethodOption {
  code: string
  name: string
  provider: string
}

export interface Shipment {
  id: string
  orderId: string
  shippingProvider: string
  trackingCode: string | null
  shippingFee: number
  status: ShipmentStatus
  shippedAt: string | null
  deliveredAt: string | null
  createdAt: string
}

export interface ShipmentHistory {
  id: string
  shipmentId: string
  status: ShipmentStatus
  description: string | null
  createdAt: string
}

export interface ReturnItem {
  id: string
  orderItemId: string
  quantity: number
  reason: string | null
  conditionNote: string | null
  resolution: ReturnType
  replacementVariantId: string | null
  replacementInventoryId: string | null
}

export interface ReturnRequest {
  id: string
  orderId: string
  customerId: string
  requestType: ReturnType
  reason: string
  status: ReturnStatus
  note: string | null
  items: ReturnItem[]
  images: string[]
  refundableAmount: number
  requestedAt: string
  approvedAt: string | null
  rejectedAt: string | null
  completedAt: string | null
}

export interface Refund {
  id: string
  returnRequestId: string
  paymentId: string
  amount: number
  refundMethod: string
  status: RefundStatus
  reason: string | null
  processedBy: string | null
  processedAt: string | null
  createdAt: string
}

export interface CreateOrderRequest {
  customerId: string
  warehouseId: string
  recipientName: string
  recipientPhone: string
  shippingAddress: string
  note: string | null
  discountAmount: number
  shippingFee: number
  items: { productVariantId: string; quantity: number; discountAmount: number }[]
  couponCode: string | null
  applyMarketing: boolean | null
  shippingMethodCode: string | null
}

export const warehouses = {
  list: (query: Query & Paged) => api.get<Page<Warehouse>>('/admin/warehouses', query),
  /** Every warehouse, for pickers. */
  all: async () => (await warehouses.list({ page: 0, size: 50 })).content,
  create: (body: { name: string; address: string; status: WarehouseStatus }) => api.post<Warehouse>('/admin/warehouses', body),
  update: (id: string, body: { name: string; address: string; status: WarehouseStatus }) => api.put<Warehouse>(`/admin/warehouses/${id}`, body),
  remove: (id: string) => api.delete(`/admin/warehouses/${id}`),
}

export const inventory = {
  list: (query: Query & Paged) => api.get<Page<Stock>>('/admin/inventory', query),
  create: (body: { warehouseId: string; productVariantId: string }) => api.post<Stock>('/admin/inventory', body),
  adjust: (id: string, body: { quantityOnHand: number; reason: string }) => api.post<Stock>(`/admin/inventory/${id}/adjustments`, body),
  transactions: (query: Query & Paged) => api.get<Page<Movement>>('/admin/inventory/transactions', query),
  move: (body: { warehouseId: string; productVariantId: string; transactionType: 'RECEIPT' | 'ISSUE'; quantity: number; note: string | null }) =>
    api.post<Movement>('/admin/inventory/transactions', body),
  history: (query: Query & Paged) => api.get<Page<Movement>>('/admin/inventory/history', query),
}

export const orders = {
  list: (query: Query & Paged) => api.get<Page<Order>>('/admin/orders', query),
  get: (id: string) => api.get<Order>(`/admin/orders/${id}`),
  create: (body: CreateOrderRequest) => api.post<Order>('/admin/orders', body),
  update: (id: string, body: { recipientName: string; recipientPhone: string; shippingAddress: string; note: string | null }) =>
    api.put<Order>(`/admin/orders/${id}`, body),
  setStatus: (id: string, status: OrderStatus, note: string | null) => api.put<Order>(`/admin/orders/${id}/status`, { status, note }),
  history: (id: string, query: Paged) => api.get<Page<OrderHistory>>(`/admin/orders/${id}/history`, query),
}

export const payments = {
  list: (query: Query & Paged) => api.get<Page<Payment>>('/admin/payments', query),
  methods: () => api.get<PaymentMethodOption[]>('/admin/payments/methods'),
  create: (body: { orderId: string; paymentMethod: string; amount: number }) => api.post<Payment>('/admin/payments', body),
  setStatus: (id: string, status: PaymentStatus, providerTransactionId: string | null) =>
    api.put<Payment>(`/admin/payments/${id}/status`, { status, providerTransactionId }),
  transactions: (id: string, query: Paged) => api.get<Page<PaymentTransaction>>(`/admin/payments/${id}/transactions`, query),
}

export const shipments = {
  list: (query: Query & Paged) => api.get<Page<Shipment>>('/admin/shipments', query),
  /** GHTK: whether the token and pick-up address are configured. */
  ghtkStatus: () => api.get<{ carrier: string; configured: boolean }>('/admin/carriers/ghtk'),
  /** Books the confirmed order at GHTK and records the shipment with GHTK's tracking label. */
  shipWithGhtk: (orderId: string) => api.post<Shipment>('/admin/carriers/ghtk/shipments', { orderId }),
  create: (body: { orderId: string; shippingProvider: string; trackingCode: string | null; shippingFee: number }) =>
    api.post<Shipment>('/admin/shipments', body),
  update: (id: string, body: { shippingProvider: string; trackingCode: string | null }) => api.put<Shipment>(`/admin/shipments/${id}`, body),
  setStatus: (id: string, status: ShipmentStatus, description: string | null) =>
    api.put<Shipment>(`/admin/shipments/${id}/status`, { status, description }),
  history: (query: Query & Paged) => api.get<Page<ShipmentHistory>>('/admin/shipments/history', query),
}

export const returns = {
  list: (query: Query & Paged) => api.get<Page<ReturnRequest>>('/admin/returns', query),
  create: (body: {
    orderId: string
    requestType: ReturnType
    reason: string
    note: string | null
    images: string[]
    items: { orderItemId: string; quantity: number; reason: string | null; conditionNote: string | null; replacementVariantId: string | null }[]
  }) => api.post<ReturnRequest>('/admin/returns', body),
  setStatus: (id: string, status: ReturnStatus, note: string | null) => api.put<ReturnRequest>(`/admin/returns/${id}/status`, { status, note }),
}

export const refunds = {
  list: (query: Query & Paged) => api.get<Page<Refund>>('/admin/refunds', query),
  create: (body: { returnRequestId: string; paymentId: string; amount: number; refundMethod: string; reason: string | null }) =>
    api.post<Refund>('/admin/refunds', body),
  setStatus: (id: string, status: RefundStatus) => api.put<Refund>(`/admin/refunds/${id}/status`, { status }),
}

// Labels and tones. Tone names map to the .status-* pills in styles.css.

export type Tone = 'active' | 'inactive' | 'info' | 'warn' | 'blocked'

export const WAREHOUSE_STATUS_LABEL: Record<WarehouseStatus, string> = { ACTIVE: 'Đang dùng', INACTIVE: 'Ngừng dùng' }

export const MOVEMENT_LABEL: Record<MovementType, string> = {
  RECEIPT: 'Nhập kho',
  ISSUE: 'Xuất kho',
  ADJUSTMENT: 'Điều chỉnh',
  RESERVE: 'Giữ hàng',
  RELEASE: 'Trả hàng giữ',
  SHIPMENT: 'Xuất giao',
  RETURN: 'Nhập trả',
  EXCHANGE: 'Xuất đổi',
}

export const ORDER_STATUS: Record<OrderStatus, [string, Tone]> = {
  PLACED: ['Chờ xác nhận', 'warn'],
  CONFIRMED: ['Đã xác nhận', 'info'],
  SHIPPED: ['Đang giao', 'info'],
  DELIVERED: ['Đã giao', 'active'],
  COMPLETED: ['Hoàn tất', 'active'],
  CANCELLED: ['Đã hủy', 'blocked'],
}

export const ORDER_PAYMENT_STATUS: Record<OrderPaymentStatus, [string, Tone]> = {
  UNPAID: ['Chưa thanh toán', 'inactive'],
  PARTIALLY_PAID: ['Thanh toán một phần', 'warn'],
  PAID: ['Đã thanh toán', 'active'],
  PARTIALLY_REFUNDED: ['Hoàn một phần', 'warn'],
  REFUNDED: ['Đã hoàn tiền', 'blocked'],
}

export const SHIPPING_STATUS: Record<ShippingStatus, [string, Tone]> = {
  NOT_SHIPPED: ['Chưa tạo vận đơn', 'inactive'],
  PENDING: ['Chờ lấy hàng', 'warn'],
  SHIPPED: ['Đã xuất kho', 'info'],
  IN_TRANSIT: ['Đang vận chuyển', 'info'],
  DELIVERED: ['Đã giao', 'active'],
  FAILED: ['Giao thất bại', 'blocked'],
  CANCELLED: ['Đã hủy', 'inactive'],
  RETURNED: ['Đã hoàn về kho', 'blocked'],
}

export const PAYMENT_STATUS: Record<PaymentStatus, [string, Tone]> = {
  PENDING: ['Chờ xác nhận', 'warn'],
  PAID: ['Đã thu', 'active'],
  FAILED: ['Thất bại', 'blocked'],
  VOID: ['Đã hủy', 'inactive'],
}

export const RETURN_TYPE_LABEL: Record<ReturnType, string> = { RETURN: 'Trả hàng', EXCHANGE: 'Đổi hàng' }

export const RETURN_STATUS: Record<ReturnStatus, [string, Tone]> = {
  REQUESTED: ['Chờ duyệt', 'warn'],
  APPROVED: ['Đã duyệt', 'info'],
  REJECTED: ['Từ chối', 'blocked'],
  COMPLETED: ['Hoàn tất', 'active'],
  CANCELLED: ['Đã hủy', 'inactive'],
}

export const REFUND_STATUS: Record<RefundStatus, [string, Tone]> = {
  PENDING: ['Chờ xử lý', 'warn'],
  SUCCEEDED: ['Thành công', 'active'],
  FAILED: ['Thất bại', 'blocked'],
  CANCELLED: ['Đã hủy', 'inactive'],
}

/** Allowed next states, mirroring the backend state machines. */
export const ORDER_NEXT: Record<OrderStatus, OrderStatus[]> = {
  PLACED: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['CANCELLED'],
  SHIPPED: [],
  DELIVERED: ['COMPLETED'],
  COMPLETED: [],
  CANCELLED: [],
}

export const PAYMENT_NEXT: Record<PaymentStatus, PaymentStatus[]> = { PENDING: ['PAID', 'FAILED', 'VOID'], PAID: [], FAILED: [], VOID: [] }

export const SHIPMENT_NEXT: Record<ShipmentStatus, ShipmentStatus[]> = {
  PENDING: ['SHIPPED', 'CANCELLED'],
  SHIPPED: ['IN_TRANSIT', 'DELIVERED', 'FAILED', 'RETURNED'],
  IN_TRANSIT: ['IN_TRANSIT', 'DELIVERED', 'FAILED', 'RETURNED'],
  FAILED: ['IN_TRANSIT', 'DELIVERED', 'RETURNED'],
  DELIVERED: [],
  CANCELLED: [],
  RETURNED: [],
}

export const RETURN_NEXT: Record<ReturnStatus, ReturnStatus[]> = {
  REQUESTED: ['APPROVED', 'REJECTED', 'CANCELLED'],
  APPROVED: ['COMPLETED', 'CANCELLED'],
  REJECTED: [],
  COMPLETED: [],
  CANCELLED: [],
}

export const REFUND_NEXT: Record<RefundStatus, RefundStatus[]> = { PENDING: ['SUCCEEDED', 'FAILED', 'CANCELLED'], SUCCEEDED: [], FAILED: [], CANCELLED: [] }

export function labelOf<K extends string>(map: Record<K, [string, Tone]>, key: K) {
  return map[key]?.[0] ?? key
}

/** Open "email me when it is back" requests, grouped by variant. */
export interface StockAlertSummary {
  productVariantId: string
  productId: string
  productName: string
  sku: string
  colorName: string
  sizeName: string
  waiting: number
  available: number
  latestAt: string
}

export const stockAlerts = {
  list: () => api.get<StockAlertSummary[]>('/admin/stock-alerts', { limit: 200 }),
}
