import { apiFetch } from './client'

export interface DailyRate {
  date: string
  rate: number
}

export interface MonthlyStats {
  completedDays: number
  incompleteDays: number
  dailyRates: DailyRate[]
  averageRating: number
}

export function fetchMonthlyStats(studentId: number, month?: string): Promise<MonthlyStats> {
  const query = month ? `?month=${month}` : ''
  return apiFetch<MonthlyStats>(`/students/${studentId}/monthly-stats${query}`)
}
