import type { Gender, OrderPaymentStatus, OrderStatus, PaymentStatus, ReturnStatus, ShippingStatus } from './api/types'

const vnd = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' })
const dateOnly = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })

export const formatMoney = (amount: number) => vnd.format(amount)
export const formatDate = (iso: string) => dateTime.format(new Date(iso))
export const formatDay = (iso: string) => dateOnly.format(new Date(iso))

/** "199.000 ₫" or "199.000 ₫ - 249.000 ₫" for a product with several prices. */
export function priceRange(min: number, max: number) {
  return min === max ? formatMoney(min) : `${formatMoney(min)} - ${formatMoney(max)}`
}

export type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'

export const ORDER_STATUS: Record<OrderStatus, [string, Tone]> = {
  PLACED: ['Chờ xác nhận', 'warning'],
  CONFIRMED: ['Đã xác nhận', 'info'],
  SHIPPED: ['Đang giao', 'info'],
  DELIVERED: ['Đã giao', 'success'],
  COMPLETED: ['Hoàn tất', 'success'],
  CANCELLED: ['Đã hủy', 'danger'],
}

export const ORDER_PAYMENT_STATUS: Record<OrderPaymentStatus, [string, Tone]> = {
  UNPAID: ['Chưa thanh toán', 'neutral'],
  PARTIALLY_PAID: ['Đã trả một phần', 'warning'],
  PAID: ['Đã thanh toán', 'success'],
  PARTIALLY_REFUNDED: ['Đã hoàn một phần', 'warning'],
  REFUNDED: ['Đã hoàn tiền', 'neutral'],
}

export const SHIPPING_STATUS: Record<ShippingStatus, [string, Tone]> = {
  NOT_SHIPPED: ['Chưa giao', 'neutral'],
  PENDING: ['Chờ lấy hàng', 'warning'],
  SHIPPED: ['Đã gửi hàng', 'info'],
  IN_TRANSIT: ['Đang vận chuyển', 'info'],
  DELIVERED: ['Đã giao', 'success'],
  FAILED: ['Giao không thành công', 'danger'],
  CANCELLED: ['Đã hủy', 'neutral'],
  RETURNED: ['Đã hoàn về', 'danger'],
}

export const PAYMENT_STATUS: Record<PaymentStatus, [string, Tone]> = {
  PENDING: ['Chờ thanh toán', 'warning'],
  PAID: ['Đã thanh toán', 'success'],
  FAILED: ['Không thành công', 'danger'],
  VOID: ['Đã hủy', 'neutral'],
}

export const RETURN_STATUS: Record<ReturnStatus, [string, Tone]> = {
  REQUESTED: ['Chờ cửa hàng duyệt', 'warning'],
  APPROVED: ['Đã duyệt, chờ nhận hàng', 'info'],
  REJECTED: ['Không được duyệt', 'danger'],
  COMPLETED: ['Hoàn tất', 'success'],
  CANCELLED: ['Đã rút yêu cầu', 'neutral'],
}

export const GENDER_LABEL: Record<Gender, string> = { WOMEN: 'Nữ', MEN: 'Nam', UNISEX: 'Unisex', KIDS: 'Trẻ em' }

export const POLICY_LABEL: Record<string, string> = {
  SHIPPING: 'Chính sách giao hàng',
  RETURN_EXCHANGE: 'Đổi trả',
  PAYMENT: 'Thanh toán',
  PRIVACY: 'Bảo mật',
  TERMS_OF_SERVICE: 'Điều khoản dịch vụ',
  WARRANTY: 'Bảo hành',
}

/** One-line address: street, ward, district (when there is one), province. */
export function formatAddress(a: { addressLine: string; ward: string; district: string | null; province: string }) {
  return [a.addressLine, a.ward, a.district, a.province].filter(Boolean).join(', ')
}
