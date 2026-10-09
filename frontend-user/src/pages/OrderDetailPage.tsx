import { ArrowLeftIcon, ArrowsClockwiseIcon, CheckCircleIcon, StarIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { me, myOrders, store } from '../api/store'
import type { MyReview, Order, ReturnRequest } from '../api/types'
import { BankTransferQr } from '../components/BankTransferQr'
import { ErrorBanner, Spinner, StatePill, useTitle } from '../components/common'
import { ONLINE_KINDS, OnlinePayButton } from '../components/OnlinePayButton'
import { ReturnRequestForm } from '../components/ReturnRequestForm'
import { ReviewForm } from '../components/ReviewForm'
import { ORDER_PAYMENT_STATUS, ORDER_STATUS, PAYMENT_STATUS, RETURN_STATUS, SHIPPING_STATUS, formatDate, formatMoney } from '../format'
import { useAsync } from '../hooks'

const STEPS = [
  { label: 'Đặt hàng', done: () => true, at: (o: Order) => o.placedAt },
  { label: 'Xác nhận', done: (o: Order) => o.confirmedAt !== null, at: (o: Order) => o.confirmedAt },
  { label: 'Đang giao', done: (o: Order) => ['SHIPPED', 'DELIVERED', 'COMPLETED'].includes(o.orderStatus), at: () => null },
  { label: 'Đã giao', done: (o: Order) => ['DELIVERED', 'COMPLETED'].includes(o.orderStatus), at: (o: Order) => o.completedAt },
]

export default function OrderDetailPage() {
  const { id = '' } = useParams()
  const [params] = useSearchParams()
  const data = useAsync(() => myOrders.get(id), [id])
  const [cancelling, setCancelling] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<unknown>()
  const config = useAsync(() => store.configuration(), [])
  const reviews = useAsync(() => me.reviews(0, 50).catch(() => null), [id])
  const [reviewing, setReviewing] = useState<string | null>(null)
  const returns = useAsync(() => me.returns({ orderId: id, page: 0, size: 50 }).catch(() => null), [id])
  const [requesting, setRequesting] = useState(false)
  useTitle(data.data ? `Đơn ${data.data.order.orderCode}` : 'Đơn hàng')

  if (data.error) {
    return (
      <div className="container narrow">
        <ErrorBanner error={data.error} onRetry={data.reload} />
        <Link to="/shop/orders" className="text-link">
          Về danh sách đơn hàng
        </Link>
      </div>
    )
  }
  if (!data.data) return <div className="container narrow"><Spinner /></div>

  const { order, payments, shipments } = data.data
  const cancelled = order.orderStatus === 'CANCELLED'
  const delivered = order.orderStatus === 'DELIVERED' || order.orderStatus === 'COMPLETED'
  const reviewOf = new Map((reviews.data?.content ?? []).map((r) => [r.orderItemId, r] as const))
  // An unpaid bank transfer gets the transfer details and a VietQR code.
  const transfer = !cancelled && order.paymentStatus !== 'PAID'
    ? payments
        .filter((p) => p.status === 'PENDING')
        .map((p) => ({ payment: p, method: config.data?.paymentMethods.find((m) => m.code === p.paymentMethod && m.bankDetails) }))
        .find((t) => t.method)
    : undefined
  // An unpaid VNPay/MoMo payment can be paid (again) from here.
  const online = !cancelled && order.paymentStatus !== 'PAID' && !transfer
    ? payments
        .filter((p) => p.status === 'PENDING')
        .map((p) => ({ payment: p, method: config.data?.paymentMethods.find((m) => m.code === p.paymentMethod && m.kind && ONLINE_KINDS.has(m.kind)) }))
        .find((t) => t.method)
    : undefined
  const requests = returns.data?.content ?? []
  // Quantity already claimed per item by requests the shop has not turned down.
  const claimed = new Map<string, number>()
  requests
    .filter((r) => r.status !== 'CANCELLED' && r.status !== 'REJECTED')
    .forEach((r) => r.items.forEach((i) => claimed.set(i.orderItemId, (claimed.get(i.orderItemId) ?? 0) + i.quantity)))
  const withdraw = async (request: ReturnRequest) => {
    setError(undefined)
    try {
      const updated = await me.cancelReturn(request.id)
      returns.setData({ ...returns.data!, content: requests.map((r) => (r.id === updated.id ? updated : r)) })
    } catch (err) {
      setError(err)
    }
  }

  const cancel = async () => {
    setCancelling(true)
    setError(undefined)
    try {
      data.setData(await myOrders.cancel(order.id))
      setConfirming(false)
    } catch (err) {
      setError(err)
    } finally {
      setCancelling(false)
    }
  }

  return (
    <div className="container narrow order-page">
      <Link to="/shop/orders" className="back-link">
        <ArrowLeftIcon size={14} /> Đơn hàng của tôi
      </Link>

      {params.get('placed') && !cancelled && (
        <div className="alert alert-success" role="status">
          <CheckCircleIcon size={20} weight="fill" aria-hidden />
          <span>
            Đặt hàng thành công. Cửa hàng sẽ xác nhận đơn <strong>{order.orderCode}</strong> sớm nhất.
          </span>
        </div>
      )}

      <header className="order-head">
        <div>
          <h1 className="page-title order-code-title">{order.orderCode}</h1>
          <p className="muted">Đặt lúc {formatDate(order.placedAt)}</p>
        </div>
        <StatePill map={ORDER_STATUS} value={order.orderStatus} />
      </header>

      {!cancelled && (
        <ol className="progress" aria-label="Tiến trình đơn hàng">
          {STEPS.map((step) => {
            const done = step.done(order)
            const at = step.at(order)
            return (
              <li key={step.label} className={done ? 'done' : undefined}>
                <span className="progress-dot" aria-hidden />
                <span className="progress-label">{step.label}</span>
                {done && at && <span className="muted small">{formatDate(at)}</span>}
              </li>
            )
          })}
        </ol>
      )}

      <section className="panel">
        <h2>Sản phẩm</h2>
        <ul className="order-items">
          {order.items.map((item) => (
            <li key={item.id}>
              <span className="order-item-name">
                {item.productName}
                <span className="muted small">
                  {item.colorName} / {item.sizeName} x {item.quantity}
                </span>
              </span>
              <span className="order-item-total">
                {item.discountAmount > 0 && <s className="muted small">{formatMoney(item.unitPrice * item.quantity)}</s>}
                <strong>{formatMoney(item.totalAmount)}</strong>
              </span>
              {delivered && reviews.data && (
                <div className="order-item-review">
                  {reviewOf.has(item.id) ? (
                    <ReviewBadge review={reviewOf.get(item.id)!} />
                  ) : reviewing === item.id ? (
                    <ReviewForm
                      orderItemId={item.id}
                      productName={item.productName}
                      onCancel={() => setReviewing(null)}
                      onDone={(review) => {
                        reviews.setData({ ...reviews.data!, content: [review, ...reviews.data!.content] })
                        setReviewing(null)
                      }}
                    />
                  ) : (
                    <button type="button" className="btn btn-outline btn-sm" onClick={() => setReviewing(item.id)}>
                      <StarIcon size={14} aria-hidden /> Đánh giá sản phẩm
                    </button>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
        <dl className="summary-rows">
          <div>
            <dt>Tạm tính</dt>
            <dd>{formatMoney(order.subtotal)}</dd>
          </div>
          {order.discountAmount > 0 && (
            <div>
              <dt>Giảm giá</dt>
              <dd className="price-sale">-{formatMoney(order.discountAmount)}</dd>
            </div>
          )}
          <div>
            <dt>Phí giao hàng</dt>
            <dd>{order.shippingFee === 0 ? 'Miễn phí' : formatMoney(order.shippingFee)}</dd>
          </div>
          <div className="summary-total">
            <dt>Tổng cộng</dt>
            <dd>{formatMoney(order.totalAmount)}</dd>
          </div>
        </dl>
      </section>

      {transfer?.method && (
        <BankTransferQr method={transfer.method} amount={transfer.payment.amount} orderCode={order.orderCode} />
      )}
      {online?.method && (
        <section className="panel">
          <h2>Thanh toán online</h2>
          <p className="muted">Đơn chưa được thanh toán {formatMoney(online.payment.amount)}. Thanh toán ngay để cửa hàng xử lý đơn sớm hơn.</p>
          <OnlinePayButton method={online.method.kind!} start={() => myOrders.pay(order.id, online.method!.code)} />
        </section>
      )}

      {delivered && returns.data && (
        <section className="panel returns-panel">
          <h2>
            <ArrowsClockwiseIcon size={20} aria-hidden /> Đổi/trả hàng
          </h2>
          {requests.length > 0 && (
            <ul className="return-list">
              {requests.map((r) => (
                <li key={r.id}>
                  <div>
                    <strong>{r.requestType === 'EXCHANGE' ? 'Đổi size/màu' : 'Trả hàng'}</strong>
                    <span className="muted small"> gửi lúc {formatDate(r.requestedAt)}</span>
                    <p className="small">
                      {r.items
                        .map((i) => {
                          const item = order.items.find((o) => o.id === i.orderItemId)
                          return (item?.productName ?? 'Sản phẩm') + ' (' + (item?.sizeName ?? '') + ') x ' + i.quantity
                        })
                        .join(', ')}
                    </p>
                    {r.note && <p className="muted small">Cửa hàng: {r.note}</p>}
                    {r.requestType === 'RETURN' && r.refundableAmount > 0 && r.status !== 'REJECTED' && r.status !== 'CANCELLED' && (
                      <p className="muted small">Số tiền hoàn dự kiến: {formatMoney(r.refundableAmount)}</p>
                    )}
                  </div>
                  <div className="return-list-side">
                    <StatePill map={RETURN_STATUS} value={r.status} />
                    {r.status === 'REQUESTED' && (
                      <button type="button" className="link-btn danger" onClick={() => void withdraw(r)}>
                        Rút yêu cầu
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
          {requesting ? (
            <ReturnRequestForm
              order={order}
              claimed={claimed}
              onCancel={() => setRequesting(false)}
              onDone={(created) => {
                returns.setData({ ...returns.data!, content: [created, ...requests] })
                setRequesting(false)
              }}
            />
          ) : (
            order.items.some((item) => item.quantity > (claimed.get(item.id) ?? 0)) && (
              <>
                <p className="muted small">
                  Sản phẩm chưa vừa ý? Gửi yêu cầu đổi size/màu hoặc trả hàng, cửa hàng sẽ phản hồi sớm. Xem{' '}
                  <Link to="/policies/RETURN_EXCHANGE">chính sách đổi trả</Link>.
                </p>
                <button type="button" className="btn btn-outline" onClick={() => setRequesting(true)}>
                  <ArrowsClockwiseIcon size={16} aria-hidden /> Yêu cầu đổi/trả
                </button>
              </>
            )
          )}
          {!requesting && <ErrorBanner error={error} />}
        </section>
      )}

      <div className="order-grid">
        <section className="panel">
          <h2>Giao đến</h2>
          <p>
            <strong>{order.recipientName}</strong>
            <br />
            {order.recipientPhone}
            <br />
            {order.shippingAddress}
          </p>
          {order.note && <p className="muted">Ghi chú: {order.note}</p>}
          {shipments.map((s) => (
            <p key={s.id} className="shipment">
              <StatePill map={SHIPPING_STATUS} value={s.status} /> {s.shippingProvider}
              {s.trackingCode && (
                <>
                  , mã vận đơn <span className="mono">{s.trackingCode}</span>
                </>
              )}
            </p>
          ))}
        </section>
        <section className="panel">
          <h2>Thanh toán</h2>
          <p>
            <StatePill map={ORDER_PAYMENT_STATUS} value={order.paymentStatus} />
          </p>
          {payments.map((p) => (
            <p key={p.id} className="payment-row">
              <span>{config.data?.paymentMethods.find((m) => m.code === p.paymentMethod)?.name ?? p.paymentMethod}</span>
              <span>{formatMoney(p.amount)}</span>
              <StatePill map={PAYMENT_STATUS} value={p.status} />
            </p>
          ))}
          {payments.length === 0 && <p className="muted small">Cửa hàng sẽ liên hệ hướng dẫn thanh toán.</p>}
        </section>
      </div>

      {order.orderStatus === 'PLACED' && (
        <section className="cancel-box">
          {confirming ? (
            <>
              <p>Hủy đơn {order.orderCode}? Sản phẩm sẽ được trả lại kho và không thể khôi phục đơn này.</p>
              <ErrorBanner error={error} />
              <div className="row-actions">
                <button type="button" className="btn btn-danger" onClick={cancel} disabled={cancelling}>
                  {cancelling ? 'Đang hủy...' : 'Xác nhận hủy'}
                </button>
                <button type="button" className="btn btn-outline" onClick={() => setConfirming(false)} disabled={cancelling}>
                  Giữ đơn
                </button>
              </div>
            </>
          ) : (
            <button type="button" className="link-btn danger" onClick={() => setConfirming(true)}>
              Hủy đơn hàng
            </button>
          )}
        </section>
      )}
    </div>
  )
}

function ReviewBadge({ review }: { review: MyReview }) {
  return (
    <span className="review-badge">
      <span className="stars" aria-label={`${review.rating} trên 5 sao`}>
        {[1, 2, 3, 4, 5].map((n) => (
          <StarIcon key={n} size={14} weight={n <= review.rating ? 'fill' : 'regular'} aria-hidden />
        ))}
      </span>
      <span className="muted small">
        {review.status === 'APPROVED' ? 'Đã đăng đánh giá' : review.status === 'PENDING' ? 'Đánh giá đang chờ duyệt' : 'Đánh giá không được duyệt'}
      </span>
    </span>
  )
}
