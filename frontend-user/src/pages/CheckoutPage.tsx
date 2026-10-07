import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { CheckoutRequest, OrderResponse, ShippingAddress } from '../api/types'
import { useCart } from '../cart/CartContext'
import { ErrorBanner, Field, Spinner } from '../components/common'
import { formatMoney } from '../format'

const EMPTY_ADDRESS: ShippingAddress = { recipientName: '', phone: '', line1: '', ward: '', district: '', city: '' }

const ADDRESS_FIELDS: { key: keyof ShippingAddress; label: string; maxLength: number; type?: string }[] = [
  { key: 'recipientName', label: 'Người nhận', maxLength: 120 },
  { key: 'phone', label: 'Số điện thoại', maxLength: 30, type: 'tel' },
  { key: 'line1', label: 'Địa chỉ (số nhà, đường)', maxLength: 255 },
  { key: 'ward', label: 'Phường/Xã', maxLength: 100 },
  { key: 'district', label: 'Quận/Huyện', maxLength: 100 },
  { key: 'city', label: 'Tỉnh/Thành phố', maxLength: 100 },
]

export default function CheckoutPage() {
  const { cart, reset } = useCart()
  const navigate = useNavigate()
  const [address, setAddress] = useState(EMPTY_ADDRESS)
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  // One Idempotency-Key per checkout attempt: retries of the same payload reuse it so a
  // timed-out request can never create a second order; editing the form starts a new attempt.
  const attempt = useRef<{ key: string; payload: string } | null>(null)

  if (!cart) return <Spinner />
  if (cart.items.length === 0) return <Navigate to="/cart" replace />

  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const request: CheckoutRequest = {
      shippingAddress: Object.fromEntries(
        Object.entries(address).map(([key, value]) => [key, value.trim()]),
      ) as unknown as ShippingAddress,
      paymentMethod: 'COD',
      customerNote: note.trim() || null,
    }
    const payload = JSON.stringify(request)
    if (attempt.current?.payload !== payload) attempt.current = { key: crypto.randomUUID(), payload }

    setBusy(true)
    setError(undefined)
    try {
      const order = await api.post<OrderResponse>('/orders', request, { 'Idempotency-Key': attempt.current.key })
      attempt.current = null
      reset()
      navigate(`/orders/${order.id}`, { replace: true, state: { justPlaced: true } })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <h1>Thanh toán</h1>
      <form className="cart-layout" onSubmit={submit}>
        <div className="card">
          <h2>Địa chỉ giao hàng</h2>
          <div className="form-grid">
            {ADDRESS_FIELDS.map(({ key, label, maxLength, type }) => (
              <Field key={key} label={label} error={fieldErrors[`shippingAddress.${key}`] ?? fieldErrors[key]}>
                <input
                  type={type ?? 'text'}
                  required
                  maxLength={maxLength}
                  pattern={key === 'phone' ? '[+0-9][0-9 ()\\-]{7,24}' : undefined}
                  value={address[key]}
                  onChange={(event) => setAddress({ ...address, [key]: event.target.value })}
                />
              </Field>
            ))}
          </div>
          <Field label="Ghi chú cho đơn hàng (không bắt buộc)" error={fieldErrors.customerNote}>
            <textarea rows={3} maxLength={500} value={note} onChange={(event) => setNote(event.target.value)} />
          </Field>
          <h2>Phương thức thanh toán</h2>
          <label className="radio">
            <input type="radio" checked readOnly /> Thanh toán khi nhận hàng (COD)
          </label>
        </div>

        <aside className="card summary">
          <h2>Đơn hàng</h2>
          {cart.items.map((item) => (
            <div key={item.variantId} className="row small">
              <span>
                {item.productName} ({item.size}/{item.color}) × {item.quantity}
              </span>
              <span>{formatMoney(item.lineTotal)}</span>
            </div>
          ))}
          <hr />
          <div className="row">
            <span>Tổng cộng</span>
            <strong className="price">{formatMoney(cart.subtotal)}</strong>
          </div>
          <ErrorBanner error={error} />
          <button className="btn btn-primary block" disabled={busy}>
            {busy ? 'Đang đặt hàng…' : 'Xác nhận đặt hàng'}
          </button>
          <Link to="/cart" className="btn btn-ghost block">
            ← Quay lại giỏ hàng
          </Link>
        </aside>
      </form>
    </>
  )
}
