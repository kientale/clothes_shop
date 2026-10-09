import { BellRingingIcon } from '@phosphor-icons/react'
import { Link } from 'react-router-dom'
import { accessErrorMessage } from '../../api/access'
import { stockAlerts } from '../../api/commerce'
import { PageHead, Pill, countOf, whenOf } from '../../components/kit'
import { useAsync } from '../../hooks'

/**
 * Sold-out sizes shoppers asked to be told about, most wanted first. Emails go out automatically a few minutes
 * after stock comes back (receipt, return or cancelled order), once per request.
 */
export default function StockAlertsPage() {
  const list = useAsync(() => stockAlerts.list(), [])
  const rows = list.data ?? []
  return (
    <div className="page page-wide">
      <PageHead
        title="Yêu cầu báo có hàng"
        lede="Khách để lại email ở size đã hết. Nhập thêm hàng cho size được chờ nhiều nhất; hệ thống tự gửi email báo khi có hàng."
      />
      <section className="panel">
        {list.error ? (
          <div className="banner banner-error" role="alert">
            {accessErrorMessage(list.error)}{' '}
            <button type="button" className="link-btn" onClick={list.reload}>
              Thử lại
            </button>
          </div>
        ) : list.loading && !list.data ? (
          <span className="skel skel-row" />
        ) : rows.length === 0 ? (
          <div className="empty-inline">
            <BellRingingIcon size={28} aria-hidden />
            <p className="muted small">Chưa có khách nào chờ hàng.</p>
          </div>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Sản phẩm</th>
                  <th scope="col">Màu / size</th>
                  <th scope="col" className="hide-sm">SKU</th>
                  <th scope="col" className="num">Đang chờ</th>
                  <th scope="col" className="num">Khả dụng</th>
                  <th scope="col" className="hide-sm">Yêu cầu gần nhất</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.productVariantId}>
                    <td>
                      <Link to={`/products/${row.productId}`} className="text-link">
                        {row.productName}
                      </Link>
                    </td>
                    <td>
                      {row.colorName} / {row.sizeName}
                    </td>
                    <td className="hide-sm mono">{row.sku}</td>
                    <td className="num">
                      <strong>{countOf(row.waiting)}</strong>
                    </td>
                    <td className="num">
                      {row.available > 0 ? <Pill tone="active">{countOf(row.available)} - đang gửi email</Pill> : <Pill tone="inactive">Hết</Pill>}
                    </td>
                    <td className="hide-sm">{whenOf(row.latestAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
