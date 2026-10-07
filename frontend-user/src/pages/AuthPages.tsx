import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner, Field } from '../components/common'

function useRedirectTarget() {
  const location = useLocation()
  return (location.state as { from?: string } | null)?.from ?? '/'
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()

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
    <form className="card auth-card" onSubmit={submit}>
      <h1>Đăng nhập</h1>
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
      <button className="btn btn-primary block" disabled={busy}>
        {busy ? 'Đang đăng nhập…' : 'Đăng nhập'}
      </button>
      <p className="muted small">
        Chưa có tài khoản?{' '}
        <Link to="/register" state={{ from: target }}>
          Đăng ký
        </Link>
      </p>
    </form>
  )
}

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const target = useRedirectTarget()
  const [form, setForm] = useState({ displayName: '', email: '', password: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()

  if (user) return <Navigate to={target} replace />
  const fieldErrors = error instanceof ApiError ? error.fieldErrors : {}

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (new TextEncoder().encode(form.password).length > 72) {
      setError(new Error('Mật khẩu không được vượt quá 72 byte UTF-8.'))
      return
    }
    setBusy(true)
    setError(undefined)
    try {
      await register({ displayName: form.displayName.trim(), email: form.email.trim(), password: form.password })
      navigate(target, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="card auth-card" onSubmit={submit}>
      <h1>Tạo tài khoản</h1>
      <Field label="Họ tên" error={fieldErrors.displayName}>
        <input
          required
          maxLength={120}
          autoComplete="name"
          value={form.displayName}
          onChange={(e) => setForm({ ...form, displayName: e.target.value })}
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
      <Field label="Mật khẩu (8–72 ký tự)" error={fieldErrors.password}>
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
      <ErrorBanner error={error} />
      <button className="btn btn-primary block" disabled={busy}>
        {busy ? 'Đang tạo…' : 'Đăng ký'}
      </button>
      <p className="muted small">
        Đã có tài khoản?{' '}
        <Link to="/login" state={{ from: target }}>
          Đăng nhập
        </Link>
      </p>
    </form>
  )
}
