import type { Problem } from './types'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '') + '/api/v1'
const TOKEN_KEY = 'lemonadex.admin.token'

/** Error carrying the backend's RFC 9457 Problem Details body. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  readonly requestId?: string

  constructor(status: number, problem: Partial<Problem>) {
    super(problem.detail ?? problem.title ?? `Lỗi HTTP ${status}`)
    this.status = status
    this.code = problem.code ?? 'UNKNOWN'
    this.fieldErrors = problem.errors ?? {}
    this.requestId = problem.requestId
  }
}

let unauthorizedHandler: (() => void) | null = null

/** Called whenever an authenticated request comes back 401 (expired or revoked token). */
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Storage unavailable (private mode): the session just won't survive a reload.
  }
}

type Query = Record<string, string | number | undefined | null>

interface RequestOptions {
  body?: unknown
  query?: Query
  headers?: Record<string, string>
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(BASE_URL + path, window.location.origin)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  }
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(url, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, { detail: 'Không kết nối được máy chủ. Kiểm tra backend đang chạy.', code: 'NETWORK_ERROR' })
  }

  if (!response.ok) {
    let problem: Partial<Problem> = {}
    try {
      problem = await response.json()
    } catch {
      // Non-JSON error body (e.g. proxy error page).
    }
    if (response.status === 401 && token) unauthorizedHandler?.()
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string, query?: Query) => request<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>('POST', path, { body, headers }),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, { body }),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body }),
  delete: <T = void>(path: string) => request<T>('DELETE', path),
}

/** Human-readable message for any thrown value. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return 'Đã xảy ra lỗi không xác định.'
}
