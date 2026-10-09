import { CreditCardIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import type { PaymentRedirect } from '../api/types'
import { ErrorBanner } from './common'

export const ONLINE_KINDS = new Set(['VNPAY', 'MOMO'])

const LABEL: Record<string, string> = { VNPAY: 'Thanh toán qua VNPay', MOMO: 'Thanh toán bằng ví MoMo' }

/** Sends the browser to the gateway page; the shopper comes back to /shop/payment/{gateway}. */
export function OnlinePayButton({ method, start, className = 'btn btn-primary btn-block' }: {
  method: string
  start: () => Promise<PaymentRedirect>
  className?: string
}) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const pay = async () => {
    setBusy(true)
    setError(undefined)
    try {
      window.location.assign((await start()).payUrl)
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }
  return (
    <div className="online-pay">
      <button type="button" className={className} onClick={pay} disabled={busy}>
        <CreditCardIcon size={18} aria-hidden /> {busy ? 'Đang chuyển tới cổng thanh toán...' : LABEL[method] ?? 'Thanh toán online'}
      </button>
      <ErrorBanner error={error} />
    </div>
  )
}
