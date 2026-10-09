import type { ApiResponse, Problem } from './types'

const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '') + '/api/v1'
const TOKEN_KEY = 'lemonadex.admin.token'
let memoryToken: string | null = null
let storageWriteFailed = false
let unauthorizedHandler: (() => void) | null = null

export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
  return () => {
    if (unauthorizedHandler === handler) unauthorizedHandler = null
  }
}

export function getToken(): string | null {
  if (storageWriteFailed) return memoryToken
  try {
    const token = localStorage.getItem(TOKEN_KEY)
    if (token === 'undefined' || token === 'null' || token?.trim() === '') {
      localStorage.removeItem(TOKEN_KEY)
      return null
    }
    return token
  } catch {
    return memoryToken
  }
}

export function setToken(token: string | null) {
  memoryToken = token
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
    storageWriteFailed = false
  } catch {
    storageWriteFailed = true
    // The session remains in memory when browser storage is unavailable.
  }
}

/** Error carrying the backend's ApiResponse error body. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  readonly requestId?: string

  constructor(status: number, problem: Partial<Problem>) {
    super(problem.message ?? `Lỗi HTTP ${status}`)
    this.status = status
    this.code = problem.code ?? 'UNKNOWN'
    this.fieldErrors = problem.errors ?? {}
    this.requestId = problem.requestId
  }
}

type Query = Record<string, string | number | boolean | undefined | null>

interface RequestOptions {
  body?: unknown
  query?: Query
  headers?: Record<string, string>
  signal?: AbortSignal
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(BASE_URL + path, window.location.origin)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  }
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  const publicAuth = method === 'POST' && (path === '/auth/login' || path === '/auth/register')
  const token = publicAuth ? null : getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  // FormData sets its own multipart Content-Type (with the boundary), so only JSON bodies get a header.
  const multipart = options.body instanceof FormData
  if (options.body !== undefined && !multipart) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(url, {
      method,
      headers,
      body: options.body === undefined ? undefined : multipart ? (options.body as FormData) : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiError(0, { message: 'Không kết nối được máy chủ. Kiểm tra backend đang chạy.', code: 'NETWORK_ERROR' })
  }

  if (!response.ok) {
    let problem: Partial<Problem> = {}
    try {
      problem = await response.json()
    } catch {
      // Non-JSON error body (e.g. proxy error page).
    }
    if (response.status === 401 && token && token === getToken() && !options.signal?.aborted) unauthorizedHandler?.()
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) return undefined as T
  let payload: ApiResponse<T>
  try {
    payload = await response.json()
  } catch {
    throw new ApiError(response.status, { code: 'INVALID_RESPONSE', message: 'Máy chủ trả về dữ liệu không hợp lệ.' })
  }
  if (!payload || payload.success !== true || !Object.hasOwn(payload, 'data')) {
    throw new ApiError(response.status, { code: 'INVALID_RESPONSE', message: 'Máy chủ trả về dữ liệu không hợp lệ.' })
  }
  return payload.data
}

export const api = {
  get: <T>(path: string, query?: Query, signal?: AbortSignal) => request<T>('GET', path, { query, signal }),
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
