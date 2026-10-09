import { HandbagIcon, MinusIcon, PlusIcon, TrashIcon, TShirtIcon, WarningCircleIcon } from '@phosphor-icons/react'
import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { store } from '../api/store'
import type { ProductDetail } from '../api/types'
import { MAX_LINE_QUANTITY, useCart, type CartLine } from '../cart/CartContext'
import { useTitle } from '../components/common'
import { formatMoney } from '../format'
import { useAsync } from '../hooks'

/** Current product data for every product in the cart, keyed by product id (null when no longer on sale). */
export function useCartCheck(lines: CartLine[]) {
  const key = [...new Set(lines.map((line) => line.slug))].sort().join(',')
  return useAsync(async () => {
    const slugs = key ? key.split(',') : []
    const entries = await Promise.all(
      slugs.map(async (slug) => {
        try {
          return [slug, await store.product(slug)] as const
        } catch {
          return [slug, null] as const
        }
      }),
    )
    return Object.fromEntries(entries) as Record<string, ProductDetail | null>
  }, [key])
}

/** Problem with a cart line, judged against current stock. */
export function lineIssue(line: CartLine, products: Record<string, ProductDetail | null> | undefined) {
  if (!products || !(line.slug in products)) return null
  const variant = products[line.slug]?.variants.find((v) => v.id === line.variantId)
  if (!variant) return { kind: 'gone' as const, text: 'Sản phẩm đã ngừng bán' }
  if (variant.available === 0) return { kind: 'gone' as const, text: 'Đã hết hàng' }
  if (variant.available < line.quantity) return { kind: 'short' as const, text: `Chỉ còn ${variant.available} sản phẩm`, available: variant.available }
  return null
}

export default function CartPage() {
  useTitle('Giỏ hàng')
  const { lines, subtotal, itemCount, setQuantity, remove, update } = useCart()
  const check = useCartCheck(lines)

  // Refresh prices shown in the cart from the live catalog.
  useEffect(() => {
    if (!check.data) return
    lines.forEach((line) => {
      const variant = check.data![line.slug]?.variants.find((v) => v.id === line.variantId)
      if (variant && variant.price !== line.price) update(line.variantId, { price: variant.price })
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [check.data])

  const blocked = lines.some((line) => lineIssue(line, check.data))

  if (lines.length === 0) {
    return (
      <div className="container narrow">
        <div className="empty-state large">
          <HandbagIcon size={48} weight="thin" aria-hidden />
          <h1>Giỏ hàng đang trống</h1>
          <p className="muted">Chọn vài món bạn thích rồi quay lại đây để đặt hàng.</p>
          <Link to="/shop/products" className="btn btn-primary">
            Mua sắm ngay
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="container cart-page">
      <h1 className="page-title">
        Giỏ hàng <span className="muted">({itemCount})</span>
      </h1>
      <div className="checkout-layout">
        <ul className="cart-lines">
          {lines.map((line) => {
            const issue = lineIssue(line, check.data)
            return (
              <li key={line.variantId} className={`cart-line${issue?.kind === 'gone' ? ' is-gone' : ''}`}>
                <Link to={`/shop/products/${line.slug}`} className="cart-thumb">
                  {line.imageUrl ? <img src={line.imageUrl} alt="" loading="lazy" /> : <TShirtIcon size={28} weight="thin" aria-hidden />}
                </Link>
                <div className="cart-line-info">
                  <Link to={`/shop/products/${line.slug}`} className="cart-line-name">
                    {line.name}
                  </Link>
                  <span className="muted small">
                    {line.colorName} / Size {line.sizeName}
                  </span>
                  <span className="cart-line-price">{formatMoney(line.price)}</span>
                  {issue && (
                    <span className="line-issue">
                      <WarningCircleIcon size={14} aria-hidden /> {issue.text}
                      {issue.kind === 'short' && (
                        <button type="button" className="link-btn" onClick={() => setQuantity(line.variantId, issue.available)}>
                          Cập nhật số lượng
                        </button>
                      )}
                    </span>
                  )}
                </div>
                <div className="stepper small" role="group" aria-label={`Số lượng ${line.name}`}>
                  <button type="button" onClick={() => setQuantity(line.variantId, line.quantity - 1)} disabled={line.quantity <= 1} aria-label="Giảm">
                    <MinusIcon size={12} />
                  </button>
                  <output>{line.quantity}</output>
                  <button type="button" onClick={() => setQuantity(line.variantId, line.quantity + 1)} disabled={line.quantity >= MAX_LINE_QUANTITY} aria-label="Tăng">
                    <PlusIcon size={12} />
                  </button>
                </div>
                <strong className="cart-line-total">{formatMoney(line.price * line.quantity)}</strong>
                <button type="button" className="icon-btn" onClick={() => remove(line.variantId)} aria-label={`Xóa ${line.name}`} title="Xóa">
                  <TrashIcon size={18} />
                </button>
              </li>
            )
          })}
        </ul>

        <aside className="summary">
          <h2>Tóm tắt</h2>
          <dl className="summary-rows">
            <div>
              <dt>Tạm tính</dt>
              <dd>{formatMoney(subtotal)}</dd>
            </div>
            <div>
              <dt>Phí giao hàng</dt>
              <dd className="muted">Tính ở bước sau</dd>
            </div>
          </dl>
          <p className="muted small">Khuyến mãi đang chạy và mã giảm giá được áp dụng khi đặt hàng.</p>
          {blocked ? (
            <p className="line-issue">Vui lòng xử lý các sản phẩm hết hàng trước khi thanh toán.</p>
          ) : null}
          {blocked ? (
            <button type="button" className="btn btn-primary btn-block btn-lg" disabled>
              Thanh toán
            </button>
          ) : (
            <Link to="/shop/checkout" className="btn btn-primary btn-block btn-lg">
              Thanh toán
            </Link>
          )}
          <Link to="/shop" className="text-link center">
            Tiếp tục mua sắm
          </Link>
        </aside>
      </div>
    </div>
  )
}
