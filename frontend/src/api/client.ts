const TOKEN_STORAGE_KEY = 'tuoguan_token'

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

  const res = await fetch(`/api${path}`, { ...init, headers })

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

  const res = await fetch(`/api${path}`, { method: 'POST', headers, body: formData })

  if (!res.ok) {
    throw new ApiError(res.status, `请求失败（${res.status}）`)
  }
  return (await res.json()) as T
}
