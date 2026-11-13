import { apiFetch } from './client'

export interface QuickBillCourse {
  courseId: number
  name: string
}

export interface QuickBillStudent {
  studentId: number
  name: string
  courses: QuickBillCourse[]
}

export interface QuickBillCourseConsumptionInput {
  courseId: number
  count: number
}

export interface QuickBillSubmitRequest {
  month: string
  mealDays: number
  leaveDays: number
  courseConsumptions: QuickBillCourseConsumptionInput[]
}

export function fetchQuickBillStudents(classId: number): Promise<QuickBillStudent[]> {
  return apiFetch<QuickBillStudent[]>(`/admin/monthly-quick-bill/classes/${classId}/students`)
}

export function submitQuickBill(studentId: number, request: QuickBillSubmitRequest): Promise<void> {
  return apiFetch<void>(`/admin/monthly-quick-bill/students/${studentId}/submit`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
