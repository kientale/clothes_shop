import { ArrowSquareOutIcon, LockSimpleIcon, MapPinIcon, PencilSimpleIcon, TShirtIcon, UserIcon } from '@phosphor-icons/react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { AddressSearch } from '../address/AddressSearch'
import { googleMapsLink } from '../address/geocoder'
import { ApiError } from '../api/client'
import { me, myOrders, store } from '../api/store'
import type { SavedAddress, ShippingArea, ShippingQuote } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useCart } from '../cart/CartContext'
import { ErrorBanner, Field, Spinner, useTitle } from '../components/common'
import { ONLINE_KINDS } from '../components/OnlinePayButton'
import { formatAddress, formatMoney } from '../format'
import { useAsync } from '../hooks'
import { trackPurchase } from '../monitoring'
import { useSeo } from '../seo'
import { lineIssue, useCartCheck } from './CartPage'

const PHONE = /^\+?[0-9]{8,15}$/
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function CheckoutPage() {
  useTitle('Thanh toán')
  useSeo({ noIndex: true })
  const { user, loading } = useAuth()
  if (loading) return <div className="container"><Spinner /></div>
  return <Checkout key={user?.id ?? 'guest'} />
}

function Checkout() {
  const { user } = useAuth()
  const isCustomer = !!user?.roles.includes('CUSTOMER')
  const location = useLocation()
  const saved = useAsync(() => (isCustomer ? me.addresses() : Promise.resolve([] as SavedAddress[])), [isCustomer])
  const { lines, subtotal, clear } = useCart()
  const navigate = useNavigate()
  const config = useAsync(() => store.configuration(), [])
  const check = useCartCheck(lines)
  const [form, setForm] = useState({
    recipientName: user?.customer?.fullName ?? '',
    recipientPhone: user?.customer?.phone ?? '',
    email: '',
    address: '',
    ward: '',
    city: '',
    note: '',
    coupon: '',
    shippingMethod: '',
    paymentMethod: '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<unknown>()
  const [busy, setBusy] = useState(false)
  // Which address: a saved one (its id) or 'new'. Until the customer chooses, the default saved address is used.
  const [choice, setChoice] = useState<string | null>(null)
  // The street/ward/province fields stay folded away until needed: after a search pick they show as a summary.
  const [manual, setManual] = useState(false)
  const [saveAddress, setSaveAddress] = useState<boolean | null>(null)
  const [quote, setQuote] = useState<ShippingQuote | null>(null)

  const methods = config.data?.shippingMethods ?? []
  const payments = config.data?.paymentMethods ?? []
  const shippingMethod = form.shippingMethod || methods[0]?.code || ''
  const paymentMethod = form.paymentMethod || payments[0]?.code || ''
  const payment = payments.find((m) => m.code === paymentMethod)
  const shipping = methods.find((m) => m.code === shippingMethod)
  const closed = config.data && (!config.data.ordersEnabled || config.data.general?.maintenanceMode)
  const issues = lines.some((line) => lineIssue(line, check.data))

  const addresses = saved.data ?? []
  const selected = choice ?? (addresses.find((a) => a.isDefault) ?? addresses[0])?.id ?? 'new'
  const savedAddress = addresses.find((a) => a.id === selected) ?? null
  const shouldSave = saveAddress ?? addresses.length === 0
  const fullAddress = savedAddress
    ? formatAddress(savedAddress)
    : [form.address, form.ward, form.city].map((part) => part.trim()).filter(Boolean).join(', ')
  const area: ShippingArea | null = savedAddress
    ? { province: savedAddress.province, district: savedAddress.district, ward: savedAddress.ward, street: savedAddress.addressLine }
    : form.city.trim()
      ? { province: form.city.trim(), district: null, ward: form.ward.trim() || null, street: form.address.trim() || null }
      : null
  const areaKey = JSON.stringify(area)
  const cartKey = lines.map((line) => `${line.variantId}:${line.quantity}`).join(',')

  // The fee for this address: a live carrier price (GHTK) when available, with the free-shipping threshold applied.
  useEffect(() => {
    setQuote(null)
    if (!shippingMethod || lines.length === 0) return
    let active = true
    const timer = window.setTimeout(() => {
      store
        .shippingQuote(shippingMethod, area, lines.map((line) => ({ productVariantId: line.variantId, quantity: line.quantity })))
        .then((result) => active && setQuote(result))
        .catch(() => undefined)
    }, 400)
    return () => {
      active = false
      window.clearTimeout(timer)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shippingMethod, areaKey, cartKey])

  if (lines.length === 0 && !busy) return <Navigate to="/shop/cart" replace />

  const shippingFee = quote?.shippingFee ?? shipping?.baseFee ?? 0
  const set = (key: keyof typeof form, value: string) => {
    setForm((f) => ({ ...f, [key]: value }))
    setErrors((e) => {
      const { [key]: _gone, ...rest } = e
      return rest
    })
  }
  const fillAddress = (parts: { line: string; ward: string; province: string }) => {
    setForm((f) => ({ ...f, address: parts.line || f.address, ward: parts.ward, city: parts.province || f.city }))
    setErrors(({ address: _a, city: _c, ...rest }) => rest)
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!savedAddress) {
      if (!form.recipientName.trim()) next.recipientName = 'Nhập họ tên người nhận.'
      if (!PHONE.test(form.recipientPhone.replace(/[\s.]/g, ''))) next.recipientPhone = 'Số điện thoại gồm 8-15 chữ số.'
      if (!form.address.trim()) next.address = 'Nhập số nhà, tên đường.'
      if (!form.city.trim()) next.city = 'Nhập tỉnh/thành phố.'
    }
    if (!isCustomer && !EMAIL.test(form.email.trim())) next.email = 'Nhập email để nhận xác nhận đơn hàng.'
    setErrors(next)
    setError(undefined)
    if (next.address || next.city) setManual(true)
    if (Object.keys(next).length) return
    setBusy(true)
    const recipientName = savedAddress?.recipientName ?? form.recipientName.trim()
    const recipientPhone = savedAddress?.phone ?? form.recipientPhone.replace(/[\s.]/g, '')
    const request = {
      recipientName,
      recipientPhone,
      shippingAddress: fullAddress,
      note: form.note.trim() || null,
      items: lines.map((line) => ({ productVariantId: line.variantId, quantity: line.quantity })),
      shippingMethodCode: shippingMethod || null,
      paymentMethod: paymentMethod || null,
      couponCode: form.coupon.trim() || null,
      email: isCustomer ? null : form.email.trim(),
      area,
    }
    const online = !!payment?.kind && ONLINE_KINDS.has(payment.kind)
    try {
      if (isCustomer) {
        const result = await myOrders.checkout(request)
        // Saving the address is a convenience: a failure here must not hide the placed order.
        if (!savedAddress && shouldSave) {
          await me
            .addAddress({
              recipientName,
              phone: recipientPhone,
              addressLine: form.address.trim(),
              ward: form.ward.trim() || form.city.trim(),
              district: null,
              province: form.city.trim(),
              makeDefault: addresses.length === 0,
            })
            .catch(() => undefined)
        }
        track(result.order.orderCode, result.order.totalAmount, result.order.items)
        clear()
        if (online && result.order.totalAmount > 0) {
          // The order is placed; if the gateway refuses, the order page offers to pay again.
          const redirect = await myOrders.pay(result.order.id, paymentMethod).catch(() => null)
          if (redirect) return window.location.assign(redirect.payUrl)
        }
        navigate(`/shop/orders/${result.order.id}?placed=1`, { replace: true })
      } else {
        const result = await store.guestCheckout(request)
        trackPurchase(result.orderCode, result.totalAmount, [])
        clear()
        if (online && result.totalAmount > 0) {
          const redirect = await store.guestPay(result.orderCode, recipientPhone, paymentMethod).catch(() => null)
          if (redirect) return window.location.assign(redirect.payUrl)
        }
        navigate(`/shop/track?code=${encodeURIComponent(result.orderCode)}&phone=${encodeURIComponent(recipientPhone)}&placed=1`, { replace: true })
      }
    } catch (err) {
      if (err instanceof ApiError && err.code === 'VALIDATION_FAILED') {
        const fields = err.fieldErrors
        setErrors({
          ...(fields.recipientName ? { recipientName: 'Họ tên không hợp lệ.' } : {}),
          ...(fields.recipientPhone ? { recipientPhone: 'Số điện thoại gồm 8-15 chữ số.' } : {}),
          ...(fields.shippingAddress ? { address: 'Địa chỉ quá dài hoặc trống.' } : {}),
          ...(fields.email ? { email: 'Email không hợp lệ.' } : {}),
        })
      }
      setError(err)
      setBusy(false)
    }
  }

  return (
    <div className="container checkout-page">
      <h1 className="page-title">Thanh toán</h1>
      {closed && (
        <div className="alert alert-warning" role="status">
          {config.data!.general?.maintenanceMode ? config.data!.general.maintenanceMessage ?? 'Cửa hàng đang bảo trì.' : 'Cửa hàng đang tạm ngừng nhận đơn.'}
        </div>
      )}
      {!user && (
        <div className="guest-hint">
          <UserIcon size={18} aria-hidden />
          <span>
            Đã có tài khoản?{' '}
            <Link to="/shop/login" state={{ from: location.pathname }}>
              Đăng nhập
            </Link>{' '}
            để dùng địa chỉ đã lưu và theo dõi đơn dễ hơn. Hoặc tiếp tục đặt hàng không cần tài khoản.
          </span>
        </div>
      )}
      <form className="checkout-layout" onSubmit={submit} noValidate>
        <div className="checkout-main">
          <section className="form-section">
            <h2>Thông tin nhận hàng</h2>
            {addresses.length > 0 && (
              <div className="option-cards saved-addresses" role="radiogroup" aria-label="Địa chỉ đã lưu">
                {addresses.map((a) => (
                  <label key={a.id} className="option-card">
                    <input type="radio" name="address" checked={selected === a.id} onChange={() => setChoice(a.id)} />
                    <span className="option-card-body">
                      <strong>
                        {a.recipientName} <span className="muted">| {a.phone}</span>
                      </strong>
                      <span className="muted small">{formatAddress(a)}</span>
                    </span>
                    {a.isDefault && <span className="tag-pill tag-pill-brand">Mặc định</span>}
                  </label>
                ))}
                <label className="option-card">
                  <input type="radio" name="address" checked={selected === 'new'} onChange={() => setChoice('new')} />
                  <span className="option-card-body">
                    <strong>Giao đến địa chỉ khác</strong>
                  </span>
                </label>
              </div>
            )}
            {!savedAddress && (
              <div className="form-grid">
                <Field label="Họ tên người nhận" error={errors.recipientName}>
                  <input autoComplete="name" maxLength={150} value={form.recipientName} onChange={(e) => set('recipientName', e.target.value)} aria-invalid={!!errors.recipientName} />
                </Field>
                <Field label="Số điện thoại" error={errors.recipientPhone} hint={!isCustomer ? 'Dùng cùng mã đơn để tra cứu đơn hàng' : undefined}>
                  <input type="tel" autoComplete="tel" maxLength={20} value={form.recipientPhone} onChange={(e) => set('recipientPhone', e.target.value)} aria-invalid={!!errors.recipientPhone} />
                </Field>
                {!isCustomer && (
                  <Field label="Email" error={errors.email} hint="Nhận xác nhận và cập nhật đơn hàng">
                    <input type="email" autoComplete="email" maxLength={254} value={form.email} onChange={(e) => set('email', e.target.value)} aria-invalid={!!errors.email} />
                  </Field>
                )}
                {!manual && form.address.trim() ? (
                  <div className="form-grid-full address-picked">
                    <MapPinIcon size={20} aria-hidden />
                    <span>
                      <strong>{form.address}</strong>
                      <span className="muted small">{[form.ward, form.city].filter(Boolean).join(', ')}</span>
                    </span>
                    <button type="button" className="btn btn-outline btn-sm" onClick={() => setManual(true)}>
                      <PencilSimpleIcon size={14} aria-hidden /> Sửa
                    </button>
                  </div>
                ) : (
                  <div className="form-grid-full">
                    <AddressSearch onPick={fillAddress} />
                    {!manual && (
                      <button type="button" className="text-link small manual-toggle" onClick={() => setManual(true)}>
                        Không tìm thấy? Nhập địa chỉ thủ công
                      </button>
                    )}
                  </div>
                )}
                {manual && (
                  <>
                    <Field label="Số nhà, tên đường" error={errors.address} hint="Thêm số căn hộ, tầng, hẻm nếu có">
                      <input autoComplete="address-line1" maxLength={300} value={form.address} onChange={(e) => set('address', e.target.value)} aria-invalid={!!errors.address} />
                    </Field>
                    <Field label="Phường/xã, quận/huyện">
                      <input autoComplete="address-level3" maxLength={120} value={form.ward} onChange={(e) => set('ward', e.target.value)} />
                    </Field>
                    <Field label="Tỉnh/thành phố" error={errors.city}>
                      <input autoComplete="address-level1" maxLength={60} value={form.city} onChange={(e) => set('city', e.target.value)} aria-invalid={!!errors.city} />
                    </Field>
                  </>
                )}
                {isCustomer && (
                  <label className="check form-grid-full">
                    <input type="checkbox" checked={shouldSave} onChange={(e) => setSaveAddress(e.target.checked)} />
                    Lưu địa chỉ này cho lần sau
                  </label>
                )}
              </div>
            )}
            {isCustomer && savedAddress && <p className="muted small">Xác nhận đơn sẽ gửi tới {user?.email}.</p>}
            <div className="form-grid checkout-note">
              <Field label="Ghi chú cho cửa hàng" hint="Không bắt buộc">
                <input maxLength={500} value={form.note} onChange={(e) => set('note', e.target.value)} placeholder="Ví dụ: giao giờ hành chính" />
              </Field>
              {fullAddress && (savedAddress || form.city.trim()) && (
                <a className="map-check" href={googleMapsLink(fullAddress)} target="_blank" rel="noreferrer">
                  <ArrowSquareOutIcon size={14} aria-hidden /> Kiểm tra địa chỉ trên Google Maps
                </a>
              )}
            </div>
          </section>

          <section className="form-section">
            <h2>Giao hàng</h2>
            {methods.length === 0 ? (
              <p className="muted">Cửa hàng sẽ báo phí giao hàng khi xác nhận đơn.</p>
            ) : (
              <div className="option-cards">
                {methods.map((m) => {
                  const live = m.code === shippingMethod && quote
                  return (
                    <label key={m.code} className="option-card">
                      <input type="radio" name="shipping" checked={shippingMethod === m.code} onChange={() => set('shippingMethod', m.code)} />
                      <span className="option-card-body">
                        <strong>{m.name}</strong>
                        <span className="muted small">
                          {m.provider}
                          {m.estimatedDays !== null && `, khoảng ${m.estimatedDays} ngày`}
                          {m.provider === 'GHTK' && !area && ', phí tính theo địa chỉ'}
                        </span>
                      </span>
                      <span className="option-card-price">
                        {live ? (quote.shippingFee === 0 ? 'Miễn phí' : formatMoney(quote.shippingFee)) : m.baseFee === 0 ? 'Miễn phí' : formatMoney(m.baseFee)}
                      </span>
                    </label>
                  )
                })}
              </div>
            )}
          </section>

          <section className="form-section">
            <h2>Thanh toán</h2>
            {payments.length === 0 ? (
              <p className="muted">Cửa hàng sẽ liên hệ để hướng dẫn thanh toán.</p>
            ) : (
              <div className="option-cards">
                {payments.map((m) => (
                  <label key={m.code} className="option-card">
                    <input type="radio" name="payment" checked={paymentMethod === m.code} onChange={() => set('paymentMethod', m.code)} />
                    <span className="option-card-body">
                      <strong>{m.name}</strong>
                      {m.bankDetails && (
                        <span className="muted small">{m.bankDetails.bankName}, sau khi đặt hàng sẽ có mã QR để quét chuyển khoản.</span>
                      )}
                      {m.kind && ONLINE_KINDS.has(m.kind) && (
                        <span className="muted small">Sau khi đặt hàng, bạn được chuyển tới {m.kind === 'VNPAY' ? 'VNPay' : 'MoMo'} để thanh toán an toàn.</span>
                      )}
                    </span>
                  </label>
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="summary">
          <h2>Đơn hàng ({lines.reduce((s, l) => s + l.quantity, 0)} sản phẩm)</h2>
          <ul className="summary-lines">
            {lines.map((line) => (
              <li key={line.variantId}>
                <span className="summary-thumb">
                  {line.imageUrl ? <img src={line.imageUrl} alt="" /> : <TShirtIcon size={20} aria-hidden />}
                  <span className="summary-qty">{line.quantity}</span>
                </span>
                <span className="summary-name">
                  {line.name}
                  <span className="muted small">
                    {line.colorName} / {line.sizeName}
                  </span>
                </span>
                <span>{formatMoney(line.price * line.quantity)}</span>
              </li>
            ))}
          </ul>
          <Field label="Mã giảm giá" hint="Kiểm tra khi đặt hàng">
            <input value={form.coupon} maxLength={80} onChange={(e) => set('coupon', e.target.value.toUpperCase())} className="mono" />
          </Field>
          <dl className="summary-rows">
            <div>
              <dt>Tạm tính</dt>
              <dd>{formatMoney(subtotal)}</dd>
            </div>
            <div>
              <dt>Phí giao hàng</dt>
              <dd>{shipping ? (shippingFee === 0 ? 'Miễn phí' : formatMoney(shippingFee)) : 'Cửa hàng báo sau'}</dd>
            </div>
            <div className="summary-total">
              <dt>Tổng tạm tính</dt>
              <dd>{formatMoney(subtotal + (shipping ? shippingFee : 0))}</dd>
            </div>
          </dl>
          <p className="muted small">Giá cuối cùng gồm khuyến mãi, mã giảm giá và ưu đãi phí giao hàng sẽ hiển thị ngay sau khi đặt.</p>
          <ErrorBanner error={error} />
          {issues && (
            <p className="line-issue">
              Một số sản phẩm đã hết hoặc không đủ hàng. <Link to="/shop/cart">Cập nhật giỏ hàng</Link>
            </p>
          )}
          <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={busy || Boolean(closed) || issues}>
            <LockSimpleIcon size={16} /> {busy ? 'Đang đặt hàng...' : payment?.kind && ONLINE_KINDS.has(payment.kind) ? 'Đặt hàng và thanh toán' : 'Đặt hàng'}
          </button>
        </aside>
      </form>
    </div>
  )
}

function track(orderCode: string, total: number, items: { sku: string; productName: string; quantity: number; unitPrice: number }[]) {
  trackPurchase(orderCode, total, items.map((i) => ({ id: i.sku, name: i.productName, quantity: i.quantity, price: i.unitPrice })))
}
