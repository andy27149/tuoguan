import { apiFetch } from './client'

export interface QuickBillCourse {
  courseId: number
  name: string
  // 这个学生这门课"账单实际结算的那个月"已经有的消课记录数（不论是这个工具自己写的还是
  // 老师真实点名消课写的）。>0 时这门课不支持再提交——重新提交会被后端拒绝，前端要把
  // 这一格锁成只读，不要让运营人员改了之后点保存才发现失败。
  consumptionCount: number
}

export interface QuickBillStudent {
  studentId: number
  name: string
  // 这个学生"账单实际结算的那个月"已经保存过的用餐/请假天数——用来预填进输入框，
  // 运营人员分几次陆续录入时，在这个基础上改成新的整月累计数，而不是凭记忆口算。
  mealDays: number
  leaveDays: number
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

export function fetchQuickBillStudents(classId: number, month: string): Promise<QuickBillStudent[]> {
  return apiFetch<QuickBillStudent[]>(
    `/admin/monthly-quick-bill/classes/${classId}/students?month=${month}`,
  )
}

export function submitQuickBill(studentId: number, request: QuickBillSubmitRequest): Promise<void> {
  return apiFetch<void>(`/admin/monthly-quick-bill/students/${studentId}/submit`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
