import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { adminAccounts } from '../api/access'
import { api, getToken, onUnauthorized, setToken } from '../api/client'
import type { AccountResponse, LoginRequest, TokenResponse, UserResponse } from '../api/types'
import { toUser } from './account'

interface AuthState {
  user: UserResponse | null
  /** True while validating the stored session against /auth/me. */
  loading: boolean
  login: (request: LoginRequest) => Promise<UserResponse>
  logout: () => void
  updateAvatar: (avatarUrl: string | null) => void
}

const AuthContext = createContext<AuthState | null>(null)

function authenticatedAccount(account: AccountResponse): UserResponse {
  const user = toUser(account)
  if (!user.roles.includes('ADMIN')) throw new Error('Tài khoản này không có quyền quản trị.')
  return user
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [loading, setLoading] = useState(() => getToken() !== null)

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
    setLoading(false)
  }, [])

  useEffect(() => {
    try { localStorage.removeItem('lemonadex.admin.mock-session') } catch { /* Storage unavailable. */ }
    const unsubscribe = onUnauthorized(logout)
    const token = getToken()
    if (!token) {
      setLoading(false)
      return unsubscribe
    }
    const controller = new AbortController()
    let active = true
    api.get<AccountResponse>('/auth/me', undefined, controller.signal)
      .then((account) => {
        if (!active || getToken() !== token) return
        const authenticated = authenticatedAccount(account)
        setUser(authenticated)
        if (!authenticated.avatarUrl && authenticated.id) {
          adminAccounts.get(authenticated.id, controller.signal)
            .then((admin) => {
              if (active && admin?.avatarUrl) {
                setUser((prev) => (prev && prev.id === authenticated.id ? { ...prev, avatarUrl: admin.avatarUrl } : prev))
              }
            })
            .catch(() => {})
        }
      })
      .catch(() => {
        if (active && getToken() === token) logout()
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
      controller.abort()
      unsubscribe()
    }
  }, [logout])

  const accept = useCallback((response: TokenResponse) => {
    if (!response || typeof response.accessToken !== 'string' || !response.accessToken.trim()
        || response.tokenType !== 'Bearer' || !(response.expiresIn > 0)) {
      throw new Error('Phản hồi đăng nhập từ máy chủ không hợp lệ.')
    }
    const account = authenticatedAccount(response.account)
    setToken(response.accessToken)
    setUser(account)
    if (!account.avatarUrl && account.id) {
      adminAccounts.get(account.id)
        .then((admin) => {
          if (admin?.avatarUrl) {
            setUser((prev) => (prev && prev.id === account.id ? { ...prev, avatarUrl: admin.avatarUrl } : prev))
          }
        })
        .catch(() => {})
    }
    return account
  }, [])

  const login = useCallback(async (request: LoginRequest) => {
    logout()
    return accept(await api.post<TokenResponse>('/auth/login', request))
  }, [accept, logout])

  const updateAvatar = useCallback((avatarUrl: string | null) => {
    setUser((prev) => (prev ? { ...prev, avatarUrl } : null))
  }, [])

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    login,
    logout,
    updateAvatar,
  }), [user, loading, login, logout, updateAvatar])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
