import { apiFetch } from './client'
import type { Student } from './students'
import type { DailyTask } from './dailyTasks'
import type { StudentDailyNote } from './studentNotes'
import type { StudentArrival } from './arrival'
import type { MonthlyStats } from './monthlyStats'

export function fetchStudents(classId: number): Promise<Student[]> {
  return apiFetch<Student[]>(`/admin/classes/${classId}/students`)
}

export function listDailyTasksForClass(classId: number, date: string): Promise<DailyTask[]> {
  return apiFetch<DailyTask[]>(`/admin/classes/${classId}/daily-tasks?date=${date}`)
}

export function fetchDismissalStatus(classId: number, date: string): Promise<{ dismissed: boolean }> {
  return apiFetch<{ dismissed: boolean }>(`/admin/classes/${classId}/dismissal?date=${date}`)
}

export function fetchStudentNotes(classId: number, date: string): Promise<StudentDailyNote[]> {
  return apiFetch<StudentDailyNote[]>(`/admin/classes/${classId}/student-notes?date=${date}`)
}

export function fetchArrivals(classId: number, date: string): Promise<StudentArrival[]> {
  return apiFetch<StudentArrival[]>(`/admin/classes/${classId}/arrivals?date=${date}`)
}

export function fetchShareLink(studentId: number): Promise<{ token: string }> {
  return apiFetch<{ token: string }>(`/admin/students/${studentId}/share-link`)
}

export function fetchMonthlyStats(studentId: number, month?: string): Promise<MonthlyStats> {
  const query = month ? `?month=${month}` : ''
  return apiFetch<MonthlyStats>(`/admin/students/${studentId}/monthly-stats${query}`)
}
