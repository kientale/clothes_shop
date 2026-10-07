import type { OrderStatus, PaymentStatus } from './api/types'

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

export const formatMoney = (amount: number) => vnd.format(amount)
export const formatDate = (iso: string) => dateTime.format(new Date(iso))

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PLACED: 'Đã đặt',
  CONFIRMED: 'Đã xác nhận',
  SHIPPED: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
}

export const PAYMENT_STATUS_LABEL: Record<PaymentStatus, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  VOID: 'Đã hủy',
}

export function shortId(id: string) {
  return id.slice(0, 8).toUpperCase()
}
