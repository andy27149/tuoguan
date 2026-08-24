import { apiFetch } from './client'
import type { MonthlyStats } from './monthlyStats'

export interface PublicShare {
  studentName: string
  schoolClassName: string
  avatarUrl: string | null
  stats: MonthlyStats
}

export function fetchPublicShare(token: string, month?: string): Promise<PublicShare> {
  const query = month ? `?month=${month}` : ''
  return apiFetch<PublicShare>(`/public/share/${token}${query}`)
}
