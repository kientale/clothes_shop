import { CheckCircleIcon, WarningCircleIcon, XIcon } from '@phosphor-icons/react'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'

type ToastKind = 'success' | 'error'

interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

interface ToastApi {
  success: (message: string) => void
  error: (message: string) => void
}

const ToastContext = createContext<ToastApi | null>(null)
const DURATION_MS = 4000
/** Older toasts are dropped beyond this, so a burst of actions cannot fill the screen. */
const MAX_TOASTS = 4

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const nextId = useRef(0)

  const dismiss = useCallback((id: number) => setToasts((list) => list.filter((toast) => toast.id !== id)), [])
  const push = useCallback((kind: ToastKind, message: string) => {
    const id = ++nextId.current
    setToasts((list) => [...list, { id, kind, message }].slice(-MAX_TOASTS))
  }, [])

  const api = useMemo<ToastApi>(
    () => ({ success: (message) => push('success', message), error: (message) => push('error', message) }),
    [push],
  )

  return (
    <ToastContext.Provider value={api}>
      {children}
      {/* Always mounted so screen readers announce toasts added to it. */}
      <div className="toast-stack" aria-live="polite" aria-relevant="additions">
        {toasts.map((toast) => (
          <Toast key={toast.id} toast={toast} onDismiss={dismiss} />
        ))}
      </div>
    </ToastContext.Provider>
  )
}

function Toast({ toast, onDismiss }: { toast: ToastItem; onDismiss: (id: number) => void }) {
  const [paused, setPaused] = useState(false)
  const remaining = useRef(DURATION_MS)

  // Count down only while the pointer or focus is elsewhere, so a toast being read stays put.
  useEffect(() => {
    if (paused) return
    const started = Date.now()
    const timer = window.setTimeout(() => onDismiss(toast.id), remaining.current)
    return () => {
      window.clearTimeout(timer)
      remaining.current -= Date.now() - started
    }
  }, [paused, toast.id, onDismiss])

  const Icon = toast.kind === 'success' ? CheckCircleIcon : WarningCircleIcon
  return (
    <div
      className={`toast toast-${toast.kind}${paused ? ' paused' : ''}`}
      role={toast.kind === 'error' ? 'alert' : 'status'}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      style={{ '--toast-duration': `${DURATION_MS}ms` } as CSSProperties}
    >
      <Icon size={20} weight="fill" className="toast-icon" aria-hidden />
      <p className="toast-message">{toast.message}</p>
      <button type="button" className="toast-close" onClick={() => onDismiss(toast.id)} aria-label="Đóng thông báo">
        <XIcon size={14} />
      </button>
      <span className="toast-timer" aria-hidden />
    </div>
  )
}

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used inside ToastProvider')
  return context
}
