import { CheckCircleIcon, MagnifyingGlassIcon, PackageIcon } from '@phosphor-icons/react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { store } from '../api/store'
import type { TrackedOrder } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { BankTransferQr } from '../components/BankTransferQr'
import { ErrorBanner, Field, StatePill, useTitle } from '../components/common'
import { ONLINE_KINDS, OnlinePayButton } from '../components/OnlinePayButton'
import { ORDER_PAYMENT_STATUS, ORDER_STATUS, PAYMENT_STATUS, SHIPPING_STATUS, formatDate, formatMoney } from '../format'
import { useAsync } from '../hooks'

/** Order tracking without an account: the order code from the confirmation plus the phone on the order. */
export default function TrackOrderPage() {
  useTitle('Tra cứu đơn hàng')
  const { user } = useAuth()
  const [params] = useSearchParams()
  const [form, setForm] = useState({ orderCode: params.get('code') ?? '', phone: params.get('phone') ?? '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [order, setOrder] = useState<TrackedOrder | null>(null)
  const config = useAsync(() => store.configuration(), [])
  const placed = params.get('placed') === '1'

  // Links from the confirmation email (and guest checkout) carry both the code and the phone: look it up at once.
  useEffect(() => {
    if (form.orderCode && form.phone) void lookup()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    await lookup()
  }

  const lookup = async () => {
    setBusy(true)
    setError(undefined)
    setOrder(null)
    try {
      setOrder(await store.trackOrder(form.orderCode.trim(), form.phone.trim()))
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 404
          ? new Error('Không tìm thấy đơn khớp mã và số điện thoại. Kiểm tra lại mã đơn trong email xác nhận.')
          : err,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container narrow track-page">
      <h1 className="page-title">Tra cứu đơn hàng</h1>
      <p className="muted page-lede">
        Nhập mã đơn (ví dụ LX-...) và số điện thoại người nhận.
        {user && (
          <>
            {' '}
            Bạn đang đăng nhập, có thể xem <Link to="/shop/orders">tất cả đơn của mình</Link>.
          </>
        )}
      </p>
      <form className="track-form" onSubmit={submit}>
        <Field label="Mã đơn hàng">
          <input required maxLength={80} value={form.orderCode} onChange={(e) => setForm({ ...form, orderCode: e.target.value })} autoCapitalize="characters" />
        </Field>
        <Field label="Số điện thoại người nhận">
          <input required type="tel" maxLength={20} autoComplete="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        </Field>
        <button className="btn btn-primary btn-lg" disabled={busy}>
          <MagnifyingGlassIcon size={18} aria-hidden /> {busy ? 'Đang tìm...' : 'Tra cứu'}
        </button>
      </form>
      <ErrorBanner error={error} />

      {order && placed && order.orderStatus !== 'CANCELLED' && (
        <div className="alert alert-success" role="status">
          <CheckCircleIcon size={20} weight="fill" aria-hidden />
          <span>
            Đặt hàng thành công. Mã đơn <strong>{order.orderCode}</strong> đã được gửi kèm email xác nhận; dùng mã này và số điện thoại để theo dõi đơn.
          </span>
        </div>
      )}

      {order && (
        <section className="track-result" aria-live="polite">
          <header className="track-head">
            <div>
              <span className="order-code">{order.orderCode}</span>
              <span className="muted small">Đặt lúc {formatDate(order.placedAt)}</span>
            </div>
            <StatePill map={ORDER_STATUS} value={order.orderStatus} />
          </header>
          <dl className="track-facts">
            <div>
              <dt>Thanh toán</dt>
              <dd>
                <StatePill map={ORDER_PAYMENT_STATUS} value={order.paymentStatus} />
              </dd>
            </div>
            <div>
              <dt>Giao hàng</dt>
              <dd>
                <StatePill map={SHIPPING_STATUS} value={order.shippingStatus} />
              </dd>
            </div>
            <div>
              <dt>Người nhận</dt>
              <dd>{order.recipientName}</dd>
            </div>
            <div>
              <dt>Khu vực</dt>
              <dd>{order.shippingArea}</dd>
            </div>
          </dl>
          {order.shipments.length > 0 && (
            <ul className="track-shipments">
              {order.shipments.map((s, i) => (
                <li key={i}>
                  <PackageIcon size={18} aria-hidden />
                  <span>
                    {s.shippingProvider}
                    {s.trackingCode && (
                      <>
                        {' '}
                        - mã vận đơn <strong>{s.trackingCode}</strong>
                      </>
                    )}
                  </span>
                  <span className="muted small">{s.deliveredAt ? `Đã giao ${formatDate(s.deliveredAt)}` : s.shippedAt ? `Gửi đi ${formatDate(s.shippedAt)}` : ''}</span>
                </li>
              ))}
            </ul>
          )}
          <ul className="track-lines">
            {order.items.map((item, i) => (
              <li key={i}>
                <span>
                  {item.productName}
                  <span className="muted small">
                    {' '}
                    {item.colorName} / {item.sizeName} x {item.quantity}
                  </span>
                </span>
                <span>{formatMoney(item.totalAmount)}</span>
              </li>
            ))}
            <li className="track-total">
              <span>Tổng cộng</span>
              <strong>{formatMoney(order.totalAmount)}</strong>
            </li>
          </ul>
          {order.payments.length > 0 && (
            <ul className="track-payments">
              {order.payments.map((p, i) => (
                <li key={i}>
                  <span>{config.data?.paymentMethods.find((m) => m.code === p.paymentMethod)?.name ?? p.paymentMethod}</span>
                  <span>{formatMoney(p.amount)}</span>
                  <StatePill map={PAYMENT_STATUS} value={p.status} />
                </li>
              ))}
            </ul>
          )}
          <PayNow order={order} phone={form.phone.trim()} />
        </section>
      )}
    </div>
  )
}

/** Unpaid guest order: the bank transfer QR, or a button back to VNPay/MoMo. */
function PayNow({ order, phone }: { order: TrackedOrder; phone: string }) {
  const config = useAsync(() => store.configuration(), [])
  if (order.orderStatus === 'CANCELLED' || order.paymentStatus === 'PAID' || order.totalAmount <= order.paidAmount) return null
  const pending = order.payments.find((p) => p.status === 'PENDING')
  const method = config.data?.paymentMethods.find((m) => m.code === pending?.paymentMethod)
  if (!pending || !method) return null
  if (method.bankDetails) return <BankTransferQr method={method} amount={pending.amount} orderCode={order.orderCode} />
  if (method.kind && ONLINE_KINDS.has(method.kind))
    return <OnlinePayButton method={method.kind} start={() => store.guestPay(order.orderCode, phone, method.code)} />
  return null
}
