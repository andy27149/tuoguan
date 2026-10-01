import { apiFetch } from './client'
import type { MonthlyStats } from './monthlyStats'
import type { StudentCourseStatement } from './course'

export interface PublicShare {
  studentName: string
  schoolClassName: string | null
  avatarUrl: string | null
  stats: MonthlyStats | null
  courseStatement: StudentCourseStatement | null
}

export function fetchPublicShare(token: string, month?: string): Promise<PublicShare> {
  const query = month ? `?month=${month}` : ''
  return apiFetch<PublicShare>(`/public/share/${token}${query}`)
}
