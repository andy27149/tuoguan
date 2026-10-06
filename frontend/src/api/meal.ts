import { apiFetch } from './client'

export interface StudentMealRecord {
  studentId: number
}

export function fetchMeals(classId: number, date: string): Promise<StudentMealRecord[]> {
  return apiFetch<StudentMealRecord[]>(`/classes/${classId}/meals?date=${date}`)
}

export function setMeal(studentId: number, date: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/meal`, {
    method: 'PATCH',
    body: JSON.stringify({ date }),
  })
}

export function clearMeal(studentId: number, date: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/meal?date=${date}`, {
    method: 'DELETE',
  })
}
