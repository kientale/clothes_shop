import { CaretRightIcon, PackageIcon } from '@phosphor-icons/react'
import { Link, useSearchParams } from 'react-router-dom'
import { myOrders } from '../api/store'
import type { OrderStatus } from '../api/types'
import { ErrorBanner, Pagination, StatePill, useTitle } from '../components/common'
import { ORDER_STATUS, formatDate, formatMoney } from '../format'
import { useAsync } from '../hooks'

const TABS: [OrderStatus | undefined, string][] = [
  [undefined, 'Tất cả'],
  ['PLACED', 'Chờ xác nhận'],
  ['SHIPPED', 'Đang giao'],
  ['DELIVERED', 'Đã giao'],
  ['CANCELLED', 'Đã hủy'],
]

export default function OrdersPage() {
  useTitle('Đơn hàng của tôi')
  const [params, setParams] = useSearchParams()
  const status = (TABS.find(([key]) => key === params.get('status'))?.[0] ?? undefined) as OrderStatus | undefined
  const page = Math.max(0, Number(params.get('page') ?? 1) - 1)
  const orders = useAsync(() => myOrders.list({ status, page, size: 10 }), [status, page])

  return (
    <div className="container narrow orders-page">
      <h1 className="page-title">Đơn hàng của tôi</h1>
      <div className="tabs" role="group" aria-label="Lọc theo trạng thái">
        {TABS.map(([key, label]) => (
          <button key={label} type="button" aria-pressed={status === key} onClick={() => setParams(key ? { status: key } : {})}>
            {label}
          </button>
        ))}
      </div>
      <ErrorBanner error={orders.error} onRetry={orders.reload} />
      {orders.loading && !orders.data ? (
        <div className="order-list">
          {Array.from({ length: 3 }, (_, i) => (
            <span key={i} className="skel order-skel" />
          ))}
        </div>
      ) : orders.data && orders.data.content.length > 0 ? (
        <ul className="order-list">
          {orders.data.content.map((order) => (
            <li key={order.id}>
              <Link to={`/shop/orders/${order.id}`} className="order-row">
                <span className="order-row-main">
                  <span className="order-code">{order.orderCode}</span>
                  <span className="muted small">
                    {formatDate(order.placedAt)} | {order.items.reduce((sum, item) => sum + item.quantity, 0)} sản phẩm
                  </span>
                </span>
                <StatePill map={ORDER_STATUS} value={order.orderStatus} />
                <strong className="order-row-total">{formatMoney(order.totalAmount)}</strong>
                <CaretRightIcon size={16} aria-hidden className="muted" />
              </Link>
            </li>
          ))}
        </ul>
      ) : orders.data ? (
        <div className="empty-state">
          <PackageIcon size={44} weight="thin" aria-hidden />
          <h2>{status ? 'Không có đơn nào ở trạng thái này' : 'Bạn chưa có đơn hàng nào'}</h2>
          <Link to="/shop/products" className="btn btn-primary">
            Bắt đầu mua sắm
          </Link>
        </div>
      ) : null}
      {orders.data && (
        <Pagination
          page={orders.data.page}
          totalPages={orders.data.totalPages}
          onChange={(next) => setParams((current) => {
            const p = new URLSearchParams(current)
            if (next > 0) p.set('page', String(next + 1))
            else p.delete('page')
            return p
          })}
        />
      )}
    </div>
  )
}
