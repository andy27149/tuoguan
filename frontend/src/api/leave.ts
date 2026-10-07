import { apiFetch } from './client'

export interface StudentLeaveRecord {
  studentId: number
  reason: string | null
}

export function fetchLeaves(classId: number, date: string): Promise<StudentLeaveRecord[]> {
  return apiFetch<StudentLeaveRecord[]>(`/classes/${classId}/leaves?date=${date}`)
}

export function setLeave(studentId: number, date: string, reason: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/leave`, {
    method: 'PATCH',
    body: JSON.stringify({ date, reason }),
  })
}

export function clearLeave(studentId: number, date: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/leave?date=${date}`, {
    method: 'DELETE',
  })
}
