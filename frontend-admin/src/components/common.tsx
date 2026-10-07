import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { ApiError, errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function ErrorBanner({ error }: { error: unknown }) {
  if (!error) return null
  const requestId = error instanceof ApiError ? error.requestId : undefined
  return (
    <div className="banner banner-error" role="alert">
      {errorMessage(error)}
      {requestId && <small> (mã yêu cầu: {requestId})</small>}
    </div>
  )
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  )
}

/** Only signed-in ADMIN users get through; everyone else goes to /login. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="boot" aria-busy="true" />
  if (!user || user.role !== 'ADMIN') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <>{children}</>
}
