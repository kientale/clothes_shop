import { Link, useSearchParams } from 'react-router-dom'
import { api } from '../api/client'
import type { OrderResponse, Page } from '../api/types'
import { ErrorBanner, Pagination, Spinner, StatusBadge } from '../components/common'
import { formatDate, formatMoney, shortId } from '../format'
import { useAsync } from '../hooks'

export default function OrdersPage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? 0)
  const orders = useAsync(() => api.get<Page<OrderResponse>>('/orders', { page, size: 10 }), [page])

  return (
    <>
      <h1>Đơn hàng của tôi</h1>
      <ErrorBanner error={orders.error} />
      {orders.loading && !orders.data ? (
        <Spinner />
      ) : orders.data?.content.length === 0 ? (
        <div className="empty">
          <p>Bạn chưa có đơn hàng nào.</p>
          <Link to="/" className="btn btn-primary">
            Mua sắm ngay
          </Link>
        </div>
      ) : (
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>Mã đơn</th>
                <th>Ngày đặt</th>
                <th>Sản phẩm</th>
                <th>Tổng tiền</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {orders.data?.content.map((order) => (
                <tr key={order.id}>
                  <td>
                    <Link to={`/orders/${order.id}`}>#{shortId(order.id)}</Link>
                  </td>
                  <td>{formatDate(order.createdAt)}</td>
                  <td>{order.items.reduce((sum, item) => sum + item.quantity, 0)} sản phẩm</td>
                  <td>{formatMoney(order.total)}</td>
                  <td>
                    <StatusBadge status={order.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {orders.data && (
        <Pagination page={orders.data.page} totalPages={orders.data.totalPages} onChange={(next) => setParams({ page: String(next) })} />
      )}
    </>
  )
}
