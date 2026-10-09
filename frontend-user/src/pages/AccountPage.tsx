import { BellIcon, HeartIcon, MapPinIcon, PackageIcon, PencilSimpleIcon, PlusIcon, StarIcon, TrashIcon } from '@phosphor-icons/react'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AddressSearch } from '../address/AddressSearch'
import { ApiError } from '../api/client'
import { me } from '../api/store'
import type { AddressRequest, CustomerGender, Profile, SavedAddress } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner, Field, Spinner, StatePill, useTitle } from '../components/common'
import { RETURN_STATUS, formatAddress, formatDate } from '../format'
import { useAsync } from '../hooks'

const TABS = [
  ['profile', 'Hồ sơ'],
  ['addresses', 'Sổ địa chỉ'],
  ['returns', 'Đổi/trả'],
  ['password', 'Mật khẩu'],
] as const
type Tab = (typeof TABS)[number][0]

const PHONE = /^\+?[0-9]{8,15}$/

/** The customer's own page: profile, saved addresses and password, with shortcuts to orders, wishlist and inbox. */
export default function AccountPage() {
  useTitle('Tài khoản của tôi')
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const tab = (TABS.find(([key]) => key === params.get('tab'))?.[0] ?? 'profile') as Tab

  return (
    <div className="container account-page">
      <header className="account-head">
        <div>
          <h1 className="page-title">Tài khoản của tôi</h1>
          <p className="muted">{user?.email}</p>
        </div>
        <nav className="account-shortcuts" aria-label="Lối tắt">
          <Link to="/shop/orders">
            <PackageIcon size={18} aria-hidden /> Đơn hàng
          </Link>
          <Link to="/shop/wishlist">
            <HeartIcon size={18} aria-hidden /> Yêu thích
          </Link>
          <Link to="/shop/notifications">
            <BellIcon size={18} aria-hidden /> Thông báo
          </Link>
          <Link to="/shop/orders?status=DELIVERED">
            <StarIcon size={18} aria-hidden /> Đánh giá sản phẩm
          </Link>
        </nav>
      </header>

      <div className="tabs" role="tablist" aria-label="Mục tài khoản">
        {TABS.map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={tab === key} aria-pressed={tab === key} onClick={() => setParams({ tab: key })}>
            {label}
          </button>
        ))}
      </div>

      <section className="account-panel" role="tabpanel">
        {tab === 'profile' && <ProfileSection />}
        {tab === 'addresses' && <AddressBook />}
        {tab === 'returns' && <ReturnsSection />}
        {tab === 'password' && <PasswordSection />}
      </section>
    </div>
  )
}

function ProfileSection() {
  const { refresh } = useAuth()
  const profile = useAsync(() => me.profile(), [])
  if (profile.error) return <ErrorBanner error={profile.error} onRetry={profile.reload} />
  if (!profile.data) return <Spinner />
  return <ProfileForm key={profile.data.accountId} initial={profile.data} onSaved={refresh} />
}

function ProfileForm({ initial, onSaved }: { initial: Profile; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({
    fullName: initial.fullName,
    phone: initial.phone ?? '',
    gender: initial.gender ?? '',
    dateOfBirth: initial.dateOfBirth ?? '',
  })
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<unknown>()
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}
  const phoneInvalid = form.phone.trim() !== '' && !PHONE.test(form.phone.replace(/[\s.]/g, ''))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (phoneInvalid) return
    setBusy(true)
    setSaved(false)
    setError(undefined)
    try {
      await me.updateProfile({
        fullName: form.fullName.trim(),
        phone: form.phone.trim() ? form.phone.replace(/[\s.]/g, '') : null,
        gender: form.gender || null,
        dateOfBirth: form.dateOfBirth || null,
      })
      await onSaved()
      setSaved(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form-grid account-form" onSubmit={submit} noValidate>
      <Field label="Họ tên" error={fieldErrors.fullName}>
        <input required maxLength={150} autoComplete="name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
      </Field>
      <Field label="Email" hint="Email đăng nhập không đổi được.">
        <input value={initial.email} disabled readOnly />
      </Field>
      <Field label="Số điện thoại" error={phoneInvalid ? 'Số điện thoại gồm 8-15 chữ số.' : fieldErrors.phone}>
        <input type="tel" autoComplete="tel" maxLength={20} value={form.phone} aria-invalid={phoneInvalid}
          onChange={(e) => setForm({ ...form, phone: e.target.value })} />
      </Field>
      <Field label="Giới tính">
        <select value={form.gender} onChange={(e) => setForm({ ...form, gender: e.target.value as CustomerGender | '' })}>
          <option value="">Không muốn nêu</option>
          <option value="FEMALE">Nữ</option>
          <option value="MALE">Nam</option>
          <option value="OTHER">Khác</option>
        </select>
      </Field>
      <Field label="Ngày sinh" error={fieldErrors.dateOfBirth}>
        <input type="date" max={new Date().toISOString().slice(0, 10)} value={form.dateOfBirth}
          onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })} />
      </Field>
      <div className="form-grid-full form-actions">
        <ErrorBanner error={error} />
        {saved && <p className="form-saved" role="status">Đã lưu thông tin.</p>}
        <button className="btn btn-primary" disabled={busy || !form.fullName.trim()}>
          {busy ? 'Đang lưu...' : 'Lưu thay đổi'}
        </button>
      </div>
    </form>
  )
}

function PasswordSection() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [busy, setBusy] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<unknown>()

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaved(false)
    if (form.newPassword !== form.confirmPassword) {
      setError(new Error('Mật khẩu xác nhận không khớp.'))
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      await me.changePassword(form)
      setForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
      setSaved(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form-stack account-narrow" onSubmit={submit}>
      <Field label="Mật khẩu hiện tại">
        <input type="password" required maxLength={72} autoComplete="current-password" value={form.currentPassword}
          onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
      </Field>
      <Field label="Mật khẩu mới (8-72 ký tự)">
        <input type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={form.newPassword}
          onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
      </Field>
      <Field label="Xác nhận mật khẩu mới">
        <input type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={form.confirmPassword}
          onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} />
      </Field>
      <ErrorBanner error={error} />
      {saved && <p className="form-saved" role="status">Đã đổi mật khẩu.</p>}
      <div>
        <button className="btn btn-primary" disabled={busy}>
          {busy ? 'Đang lưu...' : 'Đổi mật khẩu'}
        </button>
      </div>
      <p className="muted small">
        Quên mật khẩu hiện tại? <Link to="/shop/forgot-password">Đặt lại qua email</Link>
      </p>
    </form>
  )
}

function AddressBook() {
  const list = useAsync(() => me.addresses(), [])
  const [editing, setEditing] = useState<SavedAddress | 'new' | null>(null)
  const [error, setError] = useState<unknown>()

  if (list.error) return <ErrorBanner error={list.error} onRetry={list.reload} />
  if (!list.data) return <Spinner />
  const addresses = list.data

  const act = async (work: () => Promise<unknown>) => {
    setError(undefined)
    try {
      await work()
      list.reload()
    } catch (err) {
      setError(err)
    }
  }

  return (
    <div className="address-book">
      <ErrorBanner error={error} />
      {editing ? (
        <AddressForm
          initial={editing === 'new' ? null : editing}
          first={addresses.length === 0}
          onCancel={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            list.reload()
          }}
        />
      ) : (
        <>
          {addresses.length === 0 ? (
            <div className="empty-state">
              <MapPinIcon size={40} weight="thin" aria-hidden />
              <h2>Chưa có địa chỉ nào</h2>
              <p className="muted">Lưu địa chỉ để lần sau thanh toán chỉ cần một lần chọn.</p>
            </div>
          ) : (
            <ul className="address-cards">
              {addresses.map((a) => (
                <li key={a.id} className={a.isDefault ? 'address-card is-default' : 'address-card'}>
                  <div className="address-card-body">
                    <strong>
                      {a.recipientName} <span className="muted">| {a.phone}</span>
                    </strong>
                    <span>{formatAddress(a)}</span>
                    {a.isDefault && <span className="tag-pill tag-pill-brand">Mặc định</span>}
                  </div>
                  <div className="address-card-actions">
                    {!a.isDefault && (
                      <button type="button" className="text-link" onClick={() => void act(() => me.makeDefault(a.id))}>
                        Đặt mặc định
                      </button>
                    )}
                    <button type="button" className="icon-btn" aria-label={`Sửa địa chỉ ${a.addressLine}`} onClick={() => setEditing(a)}>
                      <PencilSimpleIcon size={16} />
                    </button>
                    <button
                      type="button"
                      className="icon-btn danger"
                      aria-label={`Xóa địa chỉ ${a.addressLine}`}
                      onClick={() => {
                        if (window.confirm('Xóa địa chỉ này?')) void act(() => me.deleteAddress(a.id))
                      }}
                    >
                      <TrashIcon size={16} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {addresses.length < 10 && (
            <button type="button" className="btn btn-outline" onClick={() => setEditing('new')}>
              <PlusIcon size={16} /> Thêm địa chỉ
            </button>
          )}
        </>
      )}
    </div>
  )
}

function AddressForm({ initial, first, onCancel, onSaved }: { initial: SavedAddress | null; first: boolean; onCancel: () => void; onSaved: () => void }) {
  const { user } = useAuth()
  const [form, setForm] = useState<AddressRequest>({
    recipientName: initial?.recipientName ?? user?.customer?.fullName ?? '',
    phone: initial?.phone ?? user?.customer?.phone ?? '',
    addressLine: initial?.addressLine ?? '',
    ward: initial?.ward ?? '',
    district: initial?.district ?? '',
    province: initial?.province ?? '',
    makeDefault: initial?.isDefault ?? first,
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const set = (key: keyof AddressRequest, value: string | boolean) => setForm((f) => ({ ...f, [key]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const next: Record<string, string> = {}
    if (!form.recipientName.trim()) next.recipientName = 'Nhập tên người nhận.'
    if (!PHONE.test(form.phone.replace(/[\s.]/g, ''))) next.phone = 'Số điện thoại gồm 8-15 chữ số.'
    if (!form.addressLine.trim()) next.addressLine = 'Nhập số nhà, tên đường.'
    if (!form.ward.trim()) next.ward = 'Nhập phường/xã.'
    if (!form.province.trim()) next.province = 'Nhập tỉnh/thành phố.'
    setErrors(next)
    if (Object.keys(next).length) return
    setBusy(true)
    setError(undefined)
    const body = { ...form, phone: form.phone.replace(/[\s.]/g, ''), district: form.district?.trim() || null }
    try {
      if (initial) await me.updateAddress(initial.id, body)
      else await me.addAddress(body)
      onSaved()
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  return (
    <form className="form-grid address-form" onSubmit={submit} noValidate>
      <h2 className="form-grid-full section-title">{initial ? 'Sửa địa chỉ' : 'Địa chỉ mới'}</h2>
      <div className="form-grid-full">
        <AddressSearch onPick={(p) => setForm((f) => ({ ...f, addressLine: p.line || f.addressLine, ward: p.ward, district: '', province: p.province || f.province }))} />
      </div>
      <Field label="Người nhận" error={errors.recipientName}>
        <input maxLength={150} autoComplete="name" value={form.recipientName} onChange={(e) => set('recipientName', e.target.value)} />
      </Field>
      <Field label="Số điện thoại" error={errors.phone}>
        <input type="tel" maxLength={20} autoComplete="tel" value={form.phone} onChange={(e) => set('phone', e.target.value)} />
      </Field>
      <Field label="Số nhà, tên đường" error={errors.addressLine}>
        <input maxLength={500} autoComplete="address-line1" value={form.addressLine} onChange={(e) => set('addressLine', e.target.value)} />
      </Field>
      <Field label="Phường/xã" error={errors.ward}>
        <input maxLength={100} value={form.ward} onChange={(e) => set('ward', e.target.value)} />
      </Field>
      <Field label="Quận/huyện" hint="Bỏ trống nếu địa chỉ mới không còn quận/huyện">
        <input maxLength={100} value={form.district ?? ''} onChange={(e) => set('district', e.target.value)} />
      </Field>
      <Field label="Tỉnh/thành phố" error={errors.province}>
        <input maxLength={100} autoComplete="address-level1" value={form.province} onChange={(e) => set('province', e.target.value)} />
      </Field>
      <label className="check form-grid-full">
        <input type="checkbox" checked={form.makeDefault} disabled={first || initial?.isDefault} onChange={(e) => set('makeDefault', e.target.checked)} />
        Đặt làm địa chỉ mặc định
      </label>
      <div className="form-grid-full form-actions">
        <ErrorBanner error={error} />
        <div className="row-gap">
          <button className="btn btn-primary" disabled={busy}>
            {busy ? 'Đang lưu...' : 'Lưu địa chỉ'}
          </button>
          <button type="button" className="btn btn-outline" onClick={onCancel}>
            Hủy
          </button>
        </div>
      </div>
    </form>
  )
}

/** Every return or exchange the customer has asked for, newest first; each links to its order. */
function ReturnsSection() {
  const returns = useAsync(() => me.returns({ page: 0, size: 50 }), [])
  if (returns.error) return <ErrorBanner error={returns.error} onRetry={returns.reload} />
  if (!returns.data) return <Spinner />
  if (returns.data.content.length === 0) {
    return (
      <div className="empty-state">
        <h2>Chưa có yêu cầu đổi/trả</h2>
        <p className="muted">Muốn đổi size hay trả hàng? Mở đơn đã giao và chọn "Yêu cầu đổi/trả".</p>
        <Link to="/shop/orders?status=DELIVERED" className="btn btn-outline">
          Xem đơn đã giao
        </Link>
      </div>
    )
  }
  return (
    <ul className="return-list">
      {returns.data.content.map((r) => (
        <li key={r.id}>
          <div>
            <strong>{r.requestType === 'EXCHANGE' ? 'Đổi size/màu' : 'Trả hàng'}</strong>
            <span className="muted small"> gửi lúc {formatDate(r.requestedAt)}</span>
            <p className="small">{r.reason}</p>
            {r.note && <p className="muted small">Cửa hàng: {r.note}</p>}
          </div>
          <div className="return-list-side">
            <StatePill map={RETURN_STATUS} value={r.status} />
            <Link to={`/shop/orders/${r.orderId}`} className="text-link small">
              Xem đơn
            </Link>
          </div>
        </li>
      ))}
    </ul>
  )
}
