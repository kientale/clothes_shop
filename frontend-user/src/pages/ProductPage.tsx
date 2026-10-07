import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { ProductResponse } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { MAX_LINE_QUANTITY, useCart } from '../cart/CartContext'
import { ErrorBanner, Spinner } from '../components/common'
import { formatMoney } from '../format'
import { useAsync } from '../hooks'

export default function ProductPage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const { refresh, setQuantity } = useCart()
  const navigate = useNavigate()
  const location = useLocation()
  const product = useAsync(() => api.get<ProductResponse>(`/products/${id}`), [id])

  const [size, setSize] = useState<string>()
  const [color, setColor] = useState<string>()
  const [quantity, setQty] = useState(1)
  const [imageIndex, setImageIndex] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const [added, setAdded] = useState(false)

  const variants = useMemo(() => product.data?.variants ?? [], [product.data])
  const sizes = useMemo(() => [...new Set(variants.map((v) => v.size))], [variants])
  const colors = useMemo(() => [...new Set(variants.map((v) => v.color))], [variants])
  const selected = variants.find((v) => v.size === size && v.color === color)

  if (product.loading) return <Spinner />
  if (product.error || !product.data) {
    return (
      <>
        <ErrorBanner error={product.error ?? new Error('Không tìm thấy sản phẩm.')} />
        <Link to="/">← Quay lại cửa hàng</Link>
      </>
    )
  }
  const p = product.data
  const available = (predicate: (v: (typeof variants)[number]) => boolean) =>
    variants.some((v) => predicate(v) && v.availableQuantity > 0)

  const addToCart = async () => {
    if (!user) {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    if (!selected) return
    setBusy(true)
    setError(undefined)
    setAdded(false)
    try {
      // PUT sets the absolute quantity, so add on top of what is already in the cart.
      const cart = await refresh()
      const existing = cart?.items.find((item) => item.variantId === selected.id)?.quantity ?? 0
      const target = Math.min(existing + quantity, MAX_LINE_QUANTITY, Math.max(selected.availableQuantity, existing))
      await setQuantity(selected.id, target)
      setAdded(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="product-detail">
      <div className="gallery">
        <div className="thumb large">
          {p.images[imageIndex] ? <img src={p.images[imageIndex]} alt={p.name} /> : <span>{p.brand}</span>}
        </div>
        {p.images.length > 1 && (
          <div className="thumbs">
            {p.images.map((src, index) => (
              <button key={src} className={index === imageIndex ? 'active' : ''} onClick={() => setImageIndex(index)}>
                <img src={src} alt="" />
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="info">
        <Link to="/" className="muted small">
          ← Cửa hàng
        </Link>
        <span className="muted">{p.brand}</span>
        <h1>{p.name}</h1>
        <p className="price large">
          {selected ? formatMoney(selected.price) : formatMoney(Math.min(...variants.map((v) => v.price)))}
        </p>
        {p.description && <p className="description">{p.description}</p>}

        <div className="option-group">
          <span className="field-label">Size</span>
          <div className="chips">
            {sizes.map((s) => (
              <button
                key={s}
                className={`chip ${size === s ? 'active' : ''}`}
                disabled={!available((v) => v.size === s && (!color || v.color === color))}
                onClick={() => setSize(s)}
              >
                {s}
              </button>
            ))}
          </div>
        </div>

        <div className="option-group">
          <span className="field-label">Màu</span>
          <div className="chips">
            {colors.map((c) => (
              <button
                key={c}
                className={`chip ${color === c ? 'active' : ''}`}
                disabled={!available((v) => v.color === c && (!size || v.size === size))}
                onClick={() => setColor(c)}
              >
                {c}
              </button>
            ))}
          </div>
        </div>

        {selected && (
          <p className="muted small">
            SKU {selected.sku} · {selected.availableQuantity > 0 ? `Còn ${selected.availableQuantity} sản phẩm` : 'Hết hàng'}
          </p>
        )}

        <div className="buy-row">
          <input
            type="number"
            min={1}
            max={Math.min(MAX_LINE_QUANTITY, selected?.availableQuantity ?? MAX_LINE_QUANTITY)}
            value={quantity}
            onChange={(event) => setQty(Math.max(1, Math.min(MAX_LINE_QUANTITY, Number(event.target.value) || 1)))}
            aria-label="Số lượng"
          />
          <button
            className="btn btn-primary"
            disabled={busy || !selected || selected.availableQuantity === 0}
            onClick={addToCart}
          >
            {!selected ? 'Chọn size và màu' : busy ? 'Đang thêm…' : 'Thêm vào giỏ'}
          </button>
        </div>
        <ErrorBanner error={error} />
        {added && (
          <div className="alert alert-success">
            Đã thêm vào giỏ. <Link to="/cart">Xem giỏ hàng →</Link>
          </div>
        )}
      </div>
    </div>
  )
}
