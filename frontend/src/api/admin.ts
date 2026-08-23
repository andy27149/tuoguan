import { apiFetch } from './client'

export interface Teacher {
  id: number
  phone: string
  role: 'ADMIN' | 'TEACHER'
  mustChangePassword: boolean
}

export interface ClassSummary {
  classRoomId: number
  className: string
  studentCount: number
  completedStudentCount: number
}

export interface AdminDashboard {
  date: string
  classes: ClassSummary[]
}

export function fetchTeachers(): Promise<Teacher[]> {
  return apiFetch<Teacher[]>('/admin/teachers')
}

export function createTeacher(phone: string, initialPassword: string): Promise<Teacher> {
  return apiFetch<Teacher>('/admin/teachers', {
    method: 'POST',
    body: JSON.stringify({ phone, initialPassword }),
  })
}

export function fetchAdminDashboard(date?: string): Promise<AdminDashboard> {
  const query = date ? `?date=${date}` : ''
  return apiFetch<AdminDashboard>(`/admin/dashboard${query}`)
}
