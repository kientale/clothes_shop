import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { account, passwordReset } from '../api/store'
import { useAuth } from '../auth/AuthContext'
import type { ReactNode } from 'react'
import { ErrorBanner, Field, Spinner, useTitle } from '../components/common'
import { SocialButtons } from '../components/SocialButtons'
import { useAsync } from '../hooks'

function useRedirectTarget() {
  const location = useLocation()
  return (location.state as { from?: string } | null)?.from ?? '/shop'
}

/** Two-panel sign-in shell: brand panel on the left (hidden on phones), form on the right. */
function AuthShell({ title, lede, children }: { title: string; lede: string; children: ReactNode }) {
  useTitle(title)
  return (
    <div className="container auth-page">
      <div className="auth-shell">
        <aside className="auth-brand" aria-hidden>
          <span className="wordmark wordmark-lg">LemonadeX</span>
          <p>Theo dõi đơn hàng, lưu thông tin giao hàng và đặt mua nhanh hơn ở lần sau.</p>
        </aside>
        <div className="auth-form">
          <h1 className="page-title">{title}</h1>
          <p className="muted">{lede}</p>
          {children}
        </div>
      </div>
    </div>
  )
}

export function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()

  if (loading) return <Spinner />
  if (user) return <Navigate to={target} replace />

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await login({ email: email.trim(), password })
      navigate(target, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Đăng nhập" lede="Chào mừng bạn quay lại LemonadeX.">
    <form className="form-stack" onSubmit={submit}>
      <Field label="Email">
        <input type="email" required maxLength={254} autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </Field>
      <Field label="Mật khẩu">
        <input
          type="password"
          required
          minLength={8}
          maxLength={72}
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </Field>
      <Link to="/shop/forgot-password" className="text-link small forgot-link">
        Quên mật khẩu?
      </Link>
      <ErrorBanner error={error} />
      <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
        {busy ? 'Đang đăng nhập...' : 'Đăng nhập'}
      </button>
      <SocialButtons onSignedIn={() => navigate(target, { replace: true })} />
      <p className="muted small center">
        Chưa có tài khoản?{' '}
        <Link to="/shop/register" state={{ from: target }}>
          Tạo tài khoản
        </Link>
      </p>
    </form>
    </AuthShell>
  )
}

export function RegisterPage() {
  const { user, loading, register } = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  const [form, setForm] = useState({ fullName: '', email: '', password: '', confirmPassword: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()

  if (loading) return <Spinner />
  if (user) return <Navigate to={target} replace />
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (form.password !== form.confirmPassword) {
      setError(new Error('Mật khẩu xác nhận không khớp.'))
      return
    }
    if (new TextEncoder().encode(form.password).length > 72) {
      setError(new Error('Mật khẩu không được vượt quá 72 byte UTF-8.'))
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      await register({ fullName: form.fullName.trim(), email: form.email.trim(), password: form.password, confirmPassword: form.confirmPassword })
      navigate(target, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Tạo tài khoản" lede="Chỉ mất một phút để bắt đầu mua sắm.">
    <form className="form-stack" onSubmit={submit}>
      <Field label="Họ tên" error={fieldErrors.fullName}>
        <input
          required
          maxLength={150}
          autoComplete="name"
          value={form.fullName}
          onChange={(e) => setForm({ ...form, fullName: e.target.value })}
        />
      </Field>
      <Field label="Email" error={fieldErrors.email}>
        <input
          type="email"
          required
          maxLength={254}
          autoComplete="email"
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
        />
      </Field>
      <Field label="Mật khẩu (8-72 ký tự)" error={fieldErrors.password}>
        <input
          type="password"
          required
          minLength={8}
          maxLength={72}
          autoComplete="new-password"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
        />
      </Field>
      <Field label="Xác nhận mật khẩu" error={fieldErrors.confirmPassword}>
        <input type="password" required minLength={8} maxLength={72} autoComplete="new-password"
          value={form.confirmPassword} onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} />
      </Field>
      <ErrorBanner error={error} />
      <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
        {busy ? 'Đang tạo...' : 'Đăng ký'}
      </button>
      <SocialButtons onSignedIn={() => navigate(target, { replace: true })} />
      <p className="muted small center">
        Đã có tài khoản?{' '}
        <Link to="/shop/login" state={{ from: target }}>
          Đăng nhập
        </Link>
      </p>
    </form>
    </AuthShell>
  )
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<unknown>()

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await passwordReset.request(email.trim())
      setSent(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Quên mật khẩu" lede="Nhập email đã đăng ký, LemonadeX sẽ gửi liên kết để bạn đặt mật khẩu mới.">
      {sent ? (
        <div className="form-stack">
          <div className="alert alert-success" role="status">
            Nếu <strong>{email.trim()}</strong> đã có tài khoản, email đặt lại mật khẩu đang trên đường tới. Liên kết dùng được trong 30 phút.
          </div>
          <p className="muted small">Không thấy email? Hãy xem thư mục Spam hoặc thử lại sau vài phút.</p>
          <Link to="/shop/login" className="btn btn-outline btn-block">
            Quay lại đăng nhập
          </Link>
        </div>
      ) : (
        <form className="form-stack" onSubmit={submit}>
          <Field label="Email">
            <input type="email" required maxLength={254} autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </Field>
          <ErrorBanner error={error} />
          <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
            {busy ? 'Đang gửi...' : 'Gửi liên kết'}
          </button>
          <p className="muted small center">
            Nhớ ra rồi? <Link to="/shop/login">Đăng nhập</Link>
          </p>
        </form>
      )}
    </AuthShell>
  )
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [form, setForm] = useState({ password: '', confirmPassword: '' })
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<unknown>()

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (form.password !== form.confirmPassword) {
      setError(new Error('Mật khẩu xác nhận không khớp.'))
      return
    }
    if (new TextEncoder().encode(form.password).length > 72) {
      setError(new Error('Mật khẩu không được vượt quá 72 byte UTF-8.'))
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      await passwordReset.reset(token, form.password, form.confirmPassword)
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Đặt mật khẩu mới" lede="Chọn mật khẩu mới cho tài khoản LemonadeX của bạn.">
      {!token ? (
        <div className="form-stack">
          <div className="alert alert-warning" role="alert">
            Liên kết thiếu mã đặt lại. Hãy mở lại liên kết trong email hoặc yêu cầu liên kết mới.
          </div>
          <Link to="/shop/forgot-password" className="btn btn-primary btn-block">
            Yêu cầu liên kết mới
          </Link>
        </div>
      ) : done ? (
        <div className="form-stack">
          <div className="alert alert-success" role="status">
            Đã đổi mật khẩu. Bạn có thể đăng nhập bằng mật khẩu mới.
          </div>
          <Link to="/shop/login" className="btn btn-primary btn-block btn-lg">
            Đăng nhập
          </Link>
        </div>
      ) : (
        <form className="form-stack" onSubmit={submit}>
          <Field label="Mật khẩu mới (8-72 ký tự)">
            <input type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </Field>
          <Field label="Xác nhận mật khẩu mới">
            <input type="password" required minLength={8} maxLength={72} autoComplete="new-password" value={form.confirmPassword}
              onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })} />
          </Field>
          <ErrorBanner error={error} />
          {error instanceof ApiError && error.code === 'INVALID_RESET_TOKEN' && (
            <Link to="/shop/forgot-password" className="text-link small">
              Yêu cầu liên kết mới
            </Link>
          )}
          <button className="btn btn-primary btn-block btn-lg" disabled={busy}>
            {busy ? 'Đang lưu...' : 'Đặt mật khẩu mới'}
          </button>
        </form>
      )}
    </AuthShell>
  )
}

// The link works once, so a repeated effect (React strict mode) must reuse the first attempt.
const verifications = new Map<string, Promise<null>>()

/** Opened from the link emailed at registration; confirms the address once. */
export function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const { user, refresh } = useAuth()
  const result = useAsync(async () => {
    if (!token) throw new Error('Liên kết thiếu mã xác nhận. Hãy mở lại liên kết trong email.')
    if (!verifications.has(token)) verifications.set(token, account.verifyEmail(token))
    await verifications.get(token)
    // A signed-in customer sees the reminder banner disappear right away.
    if (user) await refresh().catch(() => undefined)
    return true
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  return (
    <AuthShell title="Xác nhận email" lede="Một bước nhỏ để bảo vệ tài khoản và nhận thông báo đơn hàng.">
      <div className="form-stack">
        {result.loading ? (
          <Spinner label="Đang xác nhận" />
        ) : result.data ? (
          <div className="alert alert-success" role="status">
            Email đã được xác nhận. Cảm ơn bạn!
          </div>
        ) : (
          <>
            <ErrorBanner error={result.error} />
            {user && <p className="muted small">Bạn có thể gửi lại liên kết từ thông báo ở đầu trang.</p>}
          </>
        )}
        <Link to={user ? '/shop/account' : '/shop/login'} className="btn btn-primary btn-block">
          {user ? 'Về tài khoản' : 'Đăng nhập'}
        </Link>
      </div>
    </AuthShell>
  )
}
