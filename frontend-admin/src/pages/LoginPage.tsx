import { ArrowRightIcon, EyeIcon, EyeSlashIcon, WarningIcon } from '@phosphor-icons/react'
import { useEffect, useId, useRef, useState, type CSSProperties, type FormEvent, type KeyboardEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner } from '../components/common'
import ThemeToggle from '../components/ThemeToggle'

// Same court photograph as the storefront homepage hero, so both apps open on one visual.
const HERO_IMAGE = 'https://api.getlayers.ai/storage/v1/object/public/public/assets/baseline-88535e4000/hero/hero-court.webp'
const DISPLAY_LINES = ['Vận hành', 'cửa hàng']

const timeFormat = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', hour12: false })
const dateFormat = new Intl.DateTimeFormat('vi-VN', { weekday: 'long', day: 'numeric', month: 'long' })

/** Clock card on the hero; isolated so the per-second tick only re-renders this leaf. */
function HeroClock() {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  const date = dateFormat.format(now)
  return (
    <div className="signin-clock">
      <time className="signin-time" dateTime={now.toISOString()}>
        {timeFormat.format(now)}
      </time>
      <p className="signin-date">{date.charAt(0).toUpperCase() + date.slice(1)}</p>
    </div>
  )
}

/** Staggered rise-in order for the form's children (read by CSS as --i). */
const order = (i: number) => ({ '--i': i }) as CSSProperties

export default function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const target = (location.state as { from?: string } | null)?.from ?? '/'
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [reveal, setReveal] = useState(false)
  const [capsLock, setCapsLock] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const formRef = useRef<HTMLFormElement>(null)
  const usernameId = useId()
  const passwordId = useId()
  const capsId = useId()

  // A short shake on failure, like a rejected password. Skipped under reduced motion.
  useEffect(() => {
    if (!error || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    formRef.current?.animate(
      [0, -8, 7, -5, 3, 0].map((x) => ({ transform: `translateX(${x}px)` })),
      { duration: 380, easing: 'cubic-bezier(0.16, 1, 0.3, 1)' },
    )
  }, [error])

  if (loading) return <div className="boot" aria-busy="true" />
  if (user?.roles.includes('ADMIN')) return <Navigate to={target} replace />

  const trackCapsLock = (event: KeyboardEvent<HTMLInputElement>) => setCapsLock(event.getModifierState('CapsLock'))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await login({ email: username.trim(), password })
      navigate(target, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="signin">
      <section className="signin-hero">
        <div className="signin-plate" aria-hidden>
          <img src={HERO_IMAGE} alt="" fetchPriority="high" decoding="async" />
        </div>

        <header className="signin-brand">
          <span>LemonadeX</span>
        </header>

        <div className="signin-hero-foot">
          <p className="signin-display">
            {DISPLAY_LINES.map((line, i) => (
              <span key={line} className="signin-clip">
                <span className="signin-line" style={{ animationDelay: `${200 + i * 140}ms` }}>
                  {line}
                </span>
              </span>
            ))}
          </p>
          <HeroClock />
        </div>
      </section>

      <main className="signin-panel">
        <div className="signin-panel-top">
          <span className="signin-panel-name">LemonadeX Admin</span>
          <ThemeToggle className="signin-theme" />
        </div>

        <form ref={formRef} className="signin-form" onSubmit={submit} aria-labelledby="signin-title">
          <h1 id="signin-title" className="signin-title" style={order(0)}>
            Đăng nhập
            <br />
            quản trị
          </h1>
          <p className="signin-lede" style={order(1)}>
            Quản lý đơn hàng, sản phẩm và khách hàng.
          </p>

          <div className="signin-field" style={order(2)}>
            <label htmlFor={usernameId}>Email hoặc tên đăng nhập</label>
            <input
              id={usernameId}
              type="text"
              required
              maxLength={254}
              autoComplete="username"
              autoFocus
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              onKeyUp={trackCapsLock}
            />
          </div>

          <div className="signin-field" style={order(3)}>
            <label htmlFor={passwordId}>Mật khẩu</label>
            <div className="signin-input">
              <input
                id={passwordId}
                type={reveal ? 'text' : 'password'}
                required
                minLength={8}
                maxLength={72}
                autoComplete="current-password"
                aria-describedby={capsLock ? capsId : undefined}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyUp={trackCapsLock}
                onKeyDown={trackCapsLock}
              />
              <button
                type="button"
                className="signin-reveal"
                onClick={() => setReveal((shown) => !shown)}
                aria-pressed={reveal}
                aria-label={reveal ? 'Ẩn ký tự đã nhập' : 'Hiện ký tự đã nhập'}
                title={reveal ? 'Ẩn ký tự đã nhập' : 'Hiện ký tự đã nhập'}
              >
                {reveal ? <EyeSlashIcon size={18} aria-hidden /> : <EyeIcon size={18} aria-hidden />}
              </button>
            </div>
            {capsLock && (
              <p id={capsId} className="signin-caps">
                <WarningIcon size={14} weight="bold" aria-hidden /> Caps Lock đang bật
              </p>
            )}
          </div>

          <ErrorBanner error={error} />

          <div className="signin-actions" style={order(4)}>
            <button className="signin-submit" disabled={busy}>
              {busy ? 'Đang đăng nhập...' : 'Đăng nhập'}
              {!busy && (
                <span className="signin-arrow" aria-hidden>
                  <ArrowRightIcon size={16} weight="bold" />
                </span>
              )}
            </button>
            <p className="signin-hint">Đăng nhập bằng tài khoản được cấp quyền quản trị.</p>
          </div>
        </form>

        <footer className="signin-foot">© 2026 LemonadeX</footer>
      </main>
    </div>
  )
}
