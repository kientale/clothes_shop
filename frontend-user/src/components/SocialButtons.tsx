import { FacebookLogoIcon } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { account } from '../api/store'
import { useAuth } from '../auth/AuthContext'
import { useAsync } from '../hooks'
import { ErrorBanner } from './common'

// Minimal typings of the two vendor SDKs, loaded on demand from their CDNs.
interface GoogleId {
  initialize: (options: { client_id: string; callback: (response: { credential: string }) => void; ux_mode?: 'popup' }) => void
  renderButton: (element: HTMLElement, options: Record<string, unknown>) => void
}
interface FacebookSdk {
  init: (options: { appId: string; version: string; cookie?: boolean; xfbml?: boolean }) => void
  login: (callback: (response: { authResponse?: { accessToken: string } }) => void, options: { scope: string }) => void
}
declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } }
    FB?: FacebookSdk
  }
}

const loaded = new Map<string, Promise<void>>()

function script(src: string) {
  if (!loaded.has(src)) {
    loaded.set(
      src,
      new Promise<void>((resolve, reject) => {
        const element = Object.assign(document.createElement('script'), { src, async: true, defer: true })
        element.onload = () => resolve()
        element.onerror = () => {
          loaded.delete(src)
          reject(new Error('Không tải được nút đăng nhập. Kiểm tra kết nối mạng.'))
        }
        document.head.appendChild(element)
      }),
    )
  }
  return loaded.get(src)!
}

/**
 * "Tiếp tục với Google / Facebook". Each button appears only when the shop has configured that provider; the
 * provider's token is checked by the backend, which signs in, links or creates the customer account.
 */
export function SocialButtons({ onSignedIn }: { onSignedIn: () => void }) {
  const providers = useAsync(() => account.providers().catch(() => null), [])
  const { socialLogin } = useAuth()
  const googleBox = useRef<HTMLDivElement>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>()
  const googleId = providers.data?.googleClientId
  const facebookId = providers.data?.facebookAppId

  const finish = async (provider: 'google' | 'facebook', token: string) => {
    setBusy(true)
    setError(undefined)
    try {
      await socialLogin(provider, token)
      onSignedIn()
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  useEffect(() => {
    if (!googleId || !googleBox.current) return
    let active = true
    script('https://accounts.google.com/gsi/client')
      .then(() => {
        if (!active || !window.google || !googleBox.current) return
        window.google.accounts.id.initialize({ client_id: googleId, callback: (response) => void finish('google', response.credential), ux_mode: 'popup' })
        window.google.accounts.id.renderButton(googleBox.current, {
          theme: document.documentElement.dataset.theme === 'dark' ? 'filled_black' : 'outline',
          size: 'large',
          shape: 'pill',
          text: 'continue_with',
          locale: 'vi',
          width: googleBox.current.offsetWidth || 320,
        })
      })
      .catch(setError)
    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [googleId])

  const facebook = async () => {
    setError(undefined)
    try {
      await script('https://connect.facebook.net/vi_VN/sdk.js')
      window.FB!.init({ appId: facebookId!, version: 'v21.0' })
      window.FB!.login((response) => {
        if (response.authResponse) void finish('facebook', response.authResponse.accessToken)
      }, { scope: 'email,public_profile' })
    } catch (err) {
      setError(err)
    }
  }

  if (!googleId && !facebookId) return null
  return (
    <div className="social-login">
      <div className="divider-text">
        <span>hoặc</span>
      </div>
      {googleId && <div ref={googleBox} className="google-button" aria-busy={busy} />}
      {facebookId && (
        <button type="button" className="btn btn-outline btn-block facebook-button" onClick={facebook} disabled={busy}>
          <FacebookLogoIcon size={20} weight="fill" aria-hidden /> Tiếp tục với Facebook
        </button>
      )}
      <ErrorBanner error={error} />
    </div>
  )
}
