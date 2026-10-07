import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, getToken, onUnauthorized, setToken } from '../api/client'
import type { LoginRequest, RegisterRequest, TokenResponse, UserResponse } from '../api/types'

interface AuthState {
  user: UserResponse | null
  /** True until the stored token has been checked against /me. */
  loading: boolean
  login: (request: LoginRequest) => Promise<UserResponse>
  register: (request: RegisterRequest) => Promise<UserResponse>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null)
  const [loading, setLoading] = useState(() => getToken() !== null)

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  useEffect(() => {
    onUnauthorized(logout)
    if (!getToken()) return
    api
      .get<UserResponse>('/me')
      .then(setUser)
      .catch(logout)
      .finally(() => setLoading(false))
  }, [logout])

  const accept = useCallback((response: TokenResponse) => {
    setToken(response.accessToken)
    setUser(response.user)
    return response.user
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user,
      loading,
      login: async (request) => accept(await api.post<TokenResponse>('/auth/login', request)),
      register: async (request) => accept(await api.post<TokenResponse>('/auth/register', request)),
      logout,
    }),
    [user, loading, accept, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
