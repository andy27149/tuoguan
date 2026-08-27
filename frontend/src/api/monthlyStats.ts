import { apiFetch } from './client'

export interface DailyRate {
  date: string
  rate: number
}

export interface DailyRating {
  date: string
  rating: number
}

export interface DayTask {
  id: number
  subject: string
  name: string
  completed: boolean
}

export interface DayDetail {
  date: string
  tasks: DayTask[]
  rating: number
  comment: string
  arrivedAt: string | null
}

export interface MonthlyStats {
  completedDays: number
  incompleteDays: number
  dailyRates: DailyRate[]
  averageRating: number
  dailyRatings: DailyRating[]
  days: DayDetail[]
}

export function fetchMonthlyStats(studentId: number, month?: string): Promise<MonthlyStats> {
  const query = month ? `?month=${month}` : ''
  return apiFetch<MonthlyStats>(`/students/${studentId}/monthly-stats${query}`)
}
