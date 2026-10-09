import { EnvelopeSimpleIcon } from '@phosphor-icons/react'
import { useState } from 'react'
import { errorMessage } from '../api/client'
import { account } from '../api/store'
import { useAuth } from '../auth/AuthContext'

/** Reminds a customer who has not confirmed their email yet, with a "send again" button. */
export function EmailVerifyBanner() {
  const { user } = useAuth()
  const [state, setState] = useState<'idle' | 'busy' | 'sent'>('idle')
  const [error, setError] = useState<string>()
  if (!user || user.emailVerified !== false || !user.roles.includes('CUSTOMER')) return null

  const resend = async () => {
    setState('busy')
    setError(undefined)
    try {
      await account.resendVerification()
      setState('sent')
    } catch (err) {
      setError(errorMessage(err))
      setState('idle')
    }
  }

  return (
    <div className="notice notice-soft" role="status">
      <EnvelopeSimpleIcon size={18} aria-hidden />
      <span>
        {state === 'sent'
          ? `Đã gửi liên kết mới tới ${user.email}. Kiểm tra hộp thư (cả mục Spam).`
          : `Xác nhận email ${user.email} để nhận thông báo đơn hàng và lấy lại mật khẩu khi cần.`}
        {error && <span className="field-error"> {error}</span>}
      </span>
      {state !== 'sent' && (
        <button type="button" className="link-btn" onClick={resend} disabled={state === 'busy'}>
          {state === 'busy' ? 'Đang gửi...' : 'Gửi lại email'}
        </button>
      )}
    </div>
  )
}
