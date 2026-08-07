import { apiFetch } from './client'

export function dismissClass(classId: number, date: string): Promise<void> {
  return apiFetch<void>(`/classes/${classId}/dismissal`, {
    method: 'POST',
    body: JSON.stringify({ date }),
  })
}

export function undoDismissClass(classId: number, date: string): Promise<void> {
  return apiFetch<void>(`/classes/${classId}/dismissal?date=${date}`, { method: 'DELETE' })
}

export function fetchDismissalStatus(classId: number, date: string): Promise<{ dismissed: boolean }> {
  return apiFetch<{ dismissed: boolean }>(`/classes/${classId}/dismissal?date=${date}`)
}
