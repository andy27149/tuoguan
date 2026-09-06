import { apiFetch } from './client'

export interface Teacher {
  id: number
  phone: string
  name: string
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

export interface AdminClassRoom {
  id: number
  name: string
  teacherId: number
  teacherPhone: string
}

export function fetchTeachers(): Promise<Teacher[]> {
  return apiFetch<Teacher[]>('/admin/teachers')
}

export function createTeacher(phone: string, name: string, initialPassword: string): Promise<Teacher> {
  return apiFetch<Teacher>('/admin/teachers', {
    method: 'POST',
    body: JSON.stringify({ phone, name, initialPassword }),
  })
}

export function fetchAdminDashboard(date?: string): Promise<AdminDashboard> {
  const query = date ? `?date=${date}` : ''
  return apiFetch<AdminDashboard>(`/admin/dashboard${query}`)
}

export function fetchAdminClasses(): Promise<AdminClassRoom[]> {
  return apiFetch<AdminClassRoom[]>('/admin/classes')
}

export interface TeacherDeletionImpact {
  classCount: number
  studentCount: number
  templateCount: number
  hasStudents: boolean
}

export function fetchTeacherDeletionImpact(teacherId: number): Promise<TeacherDeletionImpact> {
  return apiFetch<TeacherDeletionImpact>(`/admin/teachers/${teacherId}/deletion-impact`)
}

export function deleteTeacher(teacherId: number, mode: 'DELETE_ALL' | 'TRANSFER', targetTeacherId?: number): Promise<void> {
  const params = new URLSearchParams({ mode })
  if (targetTeacherId) params.set('targetTeacherId', String(targetTeacherId))
  return apiFetch<void>(`/admin/teachers/${teacherId}?${params.toString()}`, { method: 'DELETE' })
}
