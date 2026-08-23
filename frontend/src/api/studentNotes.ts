import { apiFetch } from './client'

export interface StudentDailyNote {
  studentId: number
  rating: number
  comment: string
}

export function fetchStudentNotes(classId: number, date: string): Promise<StudentDailyNote[]> {
  return apiFetch<StudentDailyNote[]>(`/classes/${classId}/student-notes?date=${date}`)
}

export function setRating(studentId: number, date: string, rating: number): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/rating`, {
    method: 'PATCH',
    body: JSON.stringify({ date, rating }),
  })
}

export function setComment(studentId: number, date: string, comment: string): Promise<void> {
  return apiFetch<void>(`/students/${studentId}/comment`, {
    method: 'PATCH',
    body: JSON.stringify({ date, comment }),
  })
}
