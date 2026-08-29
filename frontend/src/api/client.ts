const TOKEN_STORAGE_KEY = 'tuoguan_token'

// import.meta.env.BASE_URL 由 Vite 的 base 配置派生，始终带尾斜杠（默认 '/'，
// 子路径部署时如 '/tuoguan/'），用它推导 API 前缀，这样切换部署路径不需要
// 再单独维护一份配置。
const API_BASE = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api`

export function getToken(): string | null {
  return window.localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setToken(token: string | null): void {
  if (token) {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, token)
  } else {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY)
  }
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const res = await fetch(`${API_BASE}${path}`, { ...init, headers })

  if (!res.ok) {
    throw new ApiError(res.status, `请求失败（${res.status}）`)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}

export async function apiUpload<T>(path: string, formData: FormData): Promise<T> {
  const headers = new Headers()
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const res = await fetch(`${API_BASE}${path}`, { method: 'POST', headers, body: formData })

  if (!res.ok) {
    throw new ApiError(res.status, `请求失败（${res.status}）`)
  }
  return (await res.json()) as T
}
