import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, onUnauthorized, setToken } from '../api/client'
import type { AccountResponse, LoginRequest, RegisterRequest, TokenResponse, UserResponse } from '../api/types'
import { toUser } from './account'

interface AuthState {
  user: UserResponse | null
  /** True while validating the stored session against /auth/me. */
  loading: boolean
  login: (request: LoginRequest) => Promise<UserResponse>
  register: (request: RegisterRequest) => Promise<UserResponse>
  /** Signs in with the token a Google or Facebook button returned. */
  socialLogin: (provider: 'google' | 'facebook', token: string) => Promise<UserResponse>
  logout: () => void
  /** Re-reads the account (e.g. after the customer renamed themselves). */
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

function authenticatedAccount(account: AccountResponse): UserResponse {
  const user = toUser(account)
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
        if (active && getToken() === token) setUser(authenticatedAccount(account))
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
    return account
  }, [])

  const login = useCallback(async (request: LoginRequest) => {
    logout()
    return accept(await api.post<TokenResponse>('/auth/login', request))
  }, [accept, logout])

  const value = useMemo<AuthState>(() => ({
    user,
    loading,
    login,
    register: async (request) => {
      logout()
      return accept(await api.post<TokenResponse>('/auth/register', request))
    },
    socialLogin: async (provider, token) => {
      logout()
      return accept(await api.post<TokenResponse>(`/auth/${provider}`, provider === 'google' ? { credential: token } : { accessToken: token }))
    },
    logout,
    refresh: async () => {
      const account = await api.get<AccountResponse>('/auth/me')
      setUser(authenticatedAccount(account))
    },
  }), [user, loading, login, accept, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
