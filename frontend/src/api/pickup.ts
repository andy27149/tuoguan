import { apiFetch } from './client'

export interface StudentPickup {
  studentId: number
  pickedUpBy: string
  pickedUpAt: string
}

export function fetchPickups(classId: number, date: string): Promise<StudentPickup[]> {
  return apiFetch<StudentPickup[]>(`/classes/${classId}/pickups?date=${date}`)
}

export function setPickup(studentId: number, date: string, pickedUpBy: string, pickedUpAt: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/pickup`, {
    method: 'PATCH',
    body: JSON.stringify({ date, pickedUpBy, pickedUpAt }),
  })
}
