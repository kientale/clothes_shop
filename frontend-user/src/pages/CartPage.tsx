import { useState } from 'react'
import { Link } from 'react-router-dom'
import { MAX_LINE_QUANTITY, useCart } from '../cart/CartContext'
import { ErrorBanner, Spinner } from '../components/common'
import { formatMoney } from '../format'

export default function CartPage() {
  const { cart, setQuantity, remove, clear } = useCart()
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<unknown>()

  const run = async (key: string, action: () => Promise<void>) => {
    setPending(key)
    setError(undefined)
    try {
      await action()
    } catch (err) {
      setError(err)
    } finally {
      setPending(null)
    }
  }

  if (!cart) return <Spinner />
  if (cart.items.length === 0) {
    return (
      <div className="empty">
        <h1>Giỏ hàng trống</h1>
        <Link to="/" className="btn btn-primary">
          Tiếp tục mua sắm
        </Link>
      </div>
    )
  }

  const hasInactive = cart.items.some((item) => !item.active)

  return (
    <>
      <h1>Giỏ hàng</h1>
      <ErrorBanner error={error} />
      {hasInactive && (
        <div className="alert alert-warning">Một số sản phẩm đã ngừng bán. Hãy xóa chúng trước khi đặt hàng.</div>
      )}
      <div className="cart-layout">
        <div className="card">
          <table className="table">
            <thead>
              <tr>
                <th>Sản phẩm</th>
                <th>Đơn giá</th>
                <th>Số lượng</th>
                <th>Thành tiền</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {cart.items.map((item) => (
                <tr key={item.variantId} className={item.active ? '' : 'inactive'}>
                  <td>
                    <strong>{item.productName}</strong>
                    <div className="muted small">
                      {item.size} · {item.color} · {item.sku}
                      {!item.active && <span className="badge badge-cancelled">Ngừng bán</span>}
                    </div>
                  </td>
                  <td>{formatMoney(item.unitPrice)}</td>
                  <td>
                    <div className="stepper">
                      <button
                        disabled={pending !== null || item.quantity <= 1}
                        onClick={() => run(item.variantId, () => setQuantity(item.variantId, item.quantity - 1))}
                        aria-label="Giảm"
                      >
                        −
                      </button>
                      <span>{item.quantity}</span>
                      <button
                        disabled={pending !== null || item.quantity >= MAX_LINE_QUANTITY || !item.active}
                        onClick={() => run(item.variantId, () => setQuantity(item.variantId, item.quantity + 1))}
                        aria-label="Tăng"
                      >
                        +
                      </button>
                    </div>
                  </td>
                  <td>{formatMoney(item.lineTotal)}</td>
                  <td>
                    <button
                      className="btn btn-link danger"
                      disabled={pending !== null}
                      onClick={() => run(item.variantId, () => remove(item.variantId))}
                    >
                      Xóa
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button className="btn btn-ghost" disabled={pending !== null} onClick={() => run('clear', clear)}>
            Xóa toàn bộ giỏ
          </button>
        </div>

        <aside className="card summary">
          <h2>Tóm tắt</h2>
          <div className="row">
            <span>Tạm tính</span>
            <strong>{formatMoney(cart.subtotal)}</strong>
          </div>
          <div className="row muted">
            <span>Phí vận chuyển</span>
            <span>Miễn phí</span>
          </div>
          <p className="muted small">Giá được tính theo giá hiện tại và chốt khi đặt hàng.</p>
          {hasInactive ? (
            <button className="btn btn-primary block" disabled>
              Đặt hàng
            </button>
          ) : (
            <Link to="/checkout" className="btn btn-primary block">
              Đặt hàng
            </Link>
          )}
        </aside>
      </div>
    </>
  )
}
