import { BellRingingIcon, CheckCircleIcon } from '@phosphor-icons/react'
import { useState, type FormEvent } from 'react'
import { store } from '../api/store'
import { useAuth } from '../auth/AuthContext'
import { ErrorBanner } from './common'

/** "Báo khi có hàng" for a sold-out size: one email when it is back in stock. */
export function StockAlertForm({ variantId, label }: { variantId: string; label: string }) {
  const { user } = useAuth()
  const [email, setEmail] = useState(user?.email ?? '')
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState<unknown>()

  if (done) {
    return (
      <p className="stock-alert done" role="status">
        <CheckCircleIcon size={18} weight="fill" aria-hidden /> Đã ghi nhận. LemonadeX sẽ email cho {email.trim()} khi {label} có hàng.
      </p>
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      await store.stockAlert(variantId, email.trim())
      setDone(true)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="stock-alert" onSubmit={submit}>
      <p>
        <BellRingingIcon size={18} aria-hidden /> <strong>{label}</strong> đã hết. Để lại email, LemonadeX báo bạn ngay khi có hàng.
      </p>
      <div className="stock-alert-row">
        <label htmlFor={`alert-${variantId}`} className="sr-only">
          Email nhận thông báo
        </label>
        <input id={`alert-${variantId}`} type="email" required maxLength={254} autoComplete="email" placeholder="email@cua-ban.vn" value={email}
          onChange={(e) => setEmail(e.target.value)} />
        <button className="btn btn-outline" disabled={busy}>
          {busy ? 'Đang gửi...' : 'Báo cho tôi'}
        </button>
      </div>
      <ErrorBanner error={error} />
    </form>
  )
}
