import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner, Field } from '../components/common'

export default function LoginPage() {
  const { user, login, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const target = (location.state as { from?: string } | null)?.from ?? '/'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()

  if (user?.role === 'ADMIN') return <Navigate to={target} replace />

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      const signedIn = await login({ email: email.trim(), password })
      if (signedIn.role !== 'ADMIN') {
        logout()
        throw new Error('Tài khoản này không có quyền quản trị.')
      }
      navigate(target, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={submit}>
        <div className="brand login-brand">
          <span className="brand-mark" aria-hidden>
            L
          </span>
          <span className="brand-name">LemonadeX Admin</span>
        </div>
        <h1>Đăng nhập quản trị</h1>
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
        <ErrorBanner error={error} />
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Đang đăng nhập...' : 'Đăng nhập'}
        </button>
        <p className="hint">Tài khoản cần được cấp role ADMIN trong database (xem README).</p>
      </form>
    </div>
  )
}
