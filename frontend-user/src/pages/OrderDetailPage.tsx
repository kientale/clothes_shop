import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { OrderResponse } from '../api/types'
import { ErrorBanner, Spinner, StatusBadge } from '../components/common'
import { formatDate, formatMoney, PAYMENT_STATUS_LABEL, shortId } from '../format'
import { useAsync } from '../hooks'

const CANCELLABLE = new Set(['PLACED', 'CONFIRMED'])

export default function OrderDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const justPlaced = (location.state as { justPlaced?: boolean } | null)?.justPlaced
  const order = useAsync(() => api.get<OrderResponse>(`/orders/${id}`), [id])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()

  if (order.loading && !order.data) return <Spinner />
  if (!order.data) {
    return (
      <>
        <ErrorBanner error={order.error} />
        <Link to="/orders">← Đơn hàng của tôi</Link>
      </>
    )
  }
  const o = order.data

  const cancel = async () => {
    if (!window.confirm('Bạn chắc chắn muốn hủy đơn hàng này?')) return
    setBusy(true)
    setError(undefined)
    try {
      order.setData(await api.post<OrderResponse>(`/orders/${o.id}/cancel`))
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Link to="/orders" className="muted small">
        ← Đơn hàng của tôi
      </Link>
      {justPlaced && <div className="alert alert-success">Đặt hàng thành công! Cảm ơn bạn đã mua sắm.</div>}
      <div className="page-head">
        <h1>Đơn #{shortId(o.id)}</h1>
        <StatusBadge status={o.status} />
      </div>
      <p className="muted">
        Đặt lúc {formatDate(o.createdAt)} · {o.paymentMethod} · {PAYMENT_STATUS_LABEL[o.paymentStatus]}
      </p>
      <ErrorBanner error={error} />

      <div className="cart-layout">
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>Sản phẩm</th>
                <th>Đơn giá</th>
                <th>SL</th>
                <th>Thành tiền</th>
              </tr>
            </thead>
            <tbody>
              {o.items.map((item) => (
                <tr key={item.variantId}>
                  <td>
                    <strong>{item.productName}</strong>
                    <div className="muted small">
                      {item.size} · {item.color} · {item.sku}
                    </div>
                  </td>
                  <td>{formatMoney(item.unitPrice)}</td>
                  <td>{item.quantity}</td>
                  <td>{formatMoney(item.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <aside className="card summary">
          <h2>Giao đến</h2>
          <p>
            <strong>{o.shippingAddress.recipientName}</strong> · {o.shippingAddress.phone}
            <br />
            {o.shippingAddress.line1}, {o.shippingAddress.ward}, {o.shippingAddress.district}, {o.shippingAddress.city}
          </p>
          {o.customerNote && <p className="muted">Ghi chú: {o.customerNote}</p>}
          <hr />
          <div className="row">
            <span>Tạm tính</span>
            <span>{formatMoney(o.subtotal)}</span>
          </div>
          <div className="row">
            <span>Phí vận chuyển</span>
            <span>{formatMoney(o.shippingFee)}</span>
          </div>
          <div className="row">
            <span>Tổng cộng</span>
            <strong className="price">{formatMoney(o.total)}</strong>
          </div>
          {CANCELLABLE.has(o.status) && (
            <button className="btn btn-danger block" disabled={busy} onClick={cancel}>
              {busy ? 'Đang hủy…' : 'Hủy đơn hàng'}
            </button>
          )}
        </aside>
      </div>
    </>
  )
}
