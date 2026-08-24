import { apiFetch } from './client'

export type Role = 'ADMIN' | 'TEACHER' | 'PLATFORM_ADMIN'

export interface LoginResult {
  token: string
  mustChangePassword: boolean
}

export interface TeacherMe {
  id: number
  phone: string
  institutionId: number | null
  role: Role
}

export function login(phone: string, password: string): Promise<LoginResult> {
  return apiFetch<LoginResult>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ phone, password }),
  })
}

export function changePassword(oldPassword: string, newPassword: string): Promise<void> {
  return apiFetch<void>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}

export function fetchMe(): Promise<TeacherMe> {
  return apiFetch<TeacherMe>('/teachers/me')
}
