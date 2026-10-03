import { apiFetch } from './client'

export type BillingMode = 'MONTHLY' | 'LESSON_COUNT'

export interface TeachingUnit {
  id: number
  name: string
  billingMode: BillingMode
  teacherId: number
  teacherName: string
  teacherPhone: string
  lessonDurationMinutes: number | null
  pricePerLesson: number | null
  active: boolean
}

export function fetchTeachingUnits(billingMode?: BillingMode): Promise<TeachingUnit[]> {
  const query = billingMode ? `?billingMode=${billingMode}` : ''
  return apiFetch<TeachingUnit[]>(`/admin/teaching-units${query}`)
}

export function createTeachingUnit(params: {
  billingMode: BillingMode
  name: string
  teacherId: number
  lessonDurationMinutes?: number
  pricePerLesson?: number | null
}): Promise<TeachingUnit> {
  return apiFetch<TeachingUnit>('/admin/teaching-units', {
    method: 'POST',
    body: JSON.stringify(params),
  })
}

export function updateTeachingUnit(
  id: number,
  updates: { name?: string; teacherId?: number; pricePerLesson?: number; active?: boolean },
): Promise<TeachingUnit> {
  return apiFetch<TeachingUnit>(`/admin/teaching-units/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(updates),
  })
}

export interface TeachingUnitDeletionImpact {
  studentCount: number
}

export function fetchTeachingUnitDeletionImpact(id: number): Promise<TeachingUnitDeletionImpact> {
  return apiFetch<TeachingUnitDeletionImpact>(`/admin/teaching-units/${id}/deletion-impact`)
}

export function deleteTeachingUnit(id: number): Promise<void> {
  return apiFetch<void>(`/admin/teaching-units/${id}`, { method: 'DELETE' })
}
