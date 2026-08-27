import { apiFetch } from './client'

export interface StudentArrival {
  studentId: number
  arrivedAt: string
}

export function fetchArrivals(classId: number, date: string): Promise<StudentArrival[]> {
  return apiFetch<StudentArrival[]>(`/classes/${classId}/arrivals?date=${date}`)
}

export function setArrival(studentId: number, date: string, arrivedAt: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/arrival`, {
    method: 'PATCH',
    body: JSON.stringify({ date, arrivedAt }),
  })
}

export function clearArrival(studentId: number, date: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/arrival?date=${date}`, {
    method: 'DELETE',
  })
}
