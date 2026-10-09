import { CaretLeftIcon, CaretRightIcon } from '@phosphor-icons/react'
import { useEffect, useRef, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { Tone } from '../format'

export function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null
  const requestId = error instanceof ApiError ? error.requestId : undefined
  return (
    <div className="alert alert-error" role="alert">
      <span>
        {errorMessage(error)}
        {requestId && <small className="muted"> (mã yêu cầu: {requestId})</small>}
      </span>
      {onRetry && (
        <button type="button" className="link-btn" onClick={onRetry}>
          Thử lại
        </button>
      )}
    </div>
  )
}

/** Page-level loading placeholder shaped like a heading and two lines of text. */
export function Spinner({ label = 'Đang tải' }: { label?: string }) {
  return (
    <div className="page-skeleton" role="status" aria-label={label}>
      <span className="skel" style={{ width: '40%', height: 28 }} />
      <span className="skel" style={{ width: '70%' }} />
      <span className="skel" style={{ width: '55%' }} />
    </div>
  )
}

export function Pill({ tone, children }: { tone: Tone; children: ReactNode }) {
  return <span className={`pill-status tone-${tone}`}>{children}</span>
}

export function StatePill<K extends string>({ map, value }: { map: Record<K, [string, Tone]>; value: K }) {
  const [label, tone] = map[value] ?? [value, 'neutral']
  return <Pill tone={tone}>{label}</Pill>
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button type="button" className="icon-btn" disabled={page === 0} onClick={() => onChange(page - 1)} aria-label="Trang trước">
        <CaretLeftIcon size={16} />
      </button>
      <span className="pagination-label">
        Trang <strong>{page + 1}</strong> / {totalPages}
      </span>
      <button type="button" className="icon-btn" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)} aria-label="Trang sau">
        <CaretRightIcon size={16} />
      </button>
    </nav>
  )
}

interface FieldProps {
  label: string
  error?: string
  hint?: ReactNode
  children: ReactNode
}

/** Label above the control, then the error or the hint below it. */
export function Field({ label, error, hint, children }: FieldProps) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  )
}

/** Redirects to the sign-in page (remembering the target) when no user is signed in. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Spinner />
  if (!user) return <Navigate to="/shop/login" replace state={{ from: location.pathname + location.search }} />
  return <>{children}</>
}

/**
 * Fades its children up once they scroll into view, to pace long pages. IntersectionObserver
 * only (no scroll listeners); reduced-motion users get the content immediately via CSS.
 */
export function Reveal({ children, className = '', delay = 0, as: Tag = 'div' }: { children: ReactNode; className?: string; delay?: number; as?: 'div' | 'section' }) {
  const ref = useRef<HTMLElement>(null)
  useEffect(() => {
    const element = ref.current
    if (!element) return
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting) {
          element.classList.add('in')
          observer.disconnect()
        }
      },
      { rootMargin: '0px 0px -10% 0px' },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return (
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    <Tag ref={ref as any} className={`reveal ${className}`} style={{ '--delay': `${delay}ms` } as React.CSSProperties}>
      {children}
    </Tag>
  )
}

/** Mirrors the page title for the browser tab. */
export function useTitle(title: string | undefined) {
  useEffect(() => {
    if (title) document.title = `${title} | LemonadeX`
  }, [title])
}
