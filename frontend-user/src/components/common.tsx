import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import type { OrderStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ORDER_STATUS_LABEL } from '../format'

export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null
  const requestId = error instanceof ApiError ? error.requestId : undefined
  return (
    <div className="alert alert-error" role="alert">
      {errorMessage(error)}
      {requestId && <small className="muted"> (mã yêu cầu: {requestId})</small>}
    </div>
  )
}

export function Spinner({ label = 'Đang tải…' }: { label?: string }) {
  return <p className="muted">{label}</p>
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav className="pagination" aria-label="Phân trang">
      <button className="btn btn-ghost" disabled={page === 0} onClick={() => onChange(page - 1)}>
        ← Trước
      </button>
      <span>
        Trang {page + 1} / {totalPages}
      </span>
      <button className="btn btn-ghost" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Sau →
      </button>
    </nav>
  )
}

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{ORDER_STATUS_LABEL[status]}</span>
}

interface FieldProps {
  label: string
  error?: string
  children: ReactNode
}

export function Field({ label, error, children }: FieldProps) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  )
}

/** Redirects to /login (remembering the target) when no user is signed in. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <Spinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <>{children}</>
}
