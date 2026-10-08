import { apiFetch } from './client'

export interface ClassBillingRate {
  id: number
  institutionId: number
  classRoomId: number
  tuitionRatePerMonth: number
  mealRatePerDay: number
  updatedAt: string
}

export interface ClassBillingRateRow {
  classRoomId: number
  className: string
  tuitionRatePerMonth: number | null
  mealRatePerDay: number | null
}

export interface MonthlyBillExtraFeeLine {
  id: number
  monthlyBillId: number
  name: string
  pricePerLesson: number
  lessonCount: number
  amount: number
}

export interface MonthlyBillLeaveLine {
  id: number
  monthlyBillId: number
  leaveDate: string
  reason: string | null
}

export interface MonthlyBill {
  id: number
  institutionId: number
  studentId: number
  // null：纯课外课学生，没有托管班。此前这里误写成不存在的 classRoomId 字段，
  // 后端实际返回的 JSON 字段名是 teachingUnitId（对应 MonthlyBill 领域对象）。
  teachingUnitId: number | null
  yearMonth: string
  totalWeekdays: number
  leaveDays: number
  attendanceDays: number
  tuitionAmount: number
  mealAmount: number
  extraFeeTotal: number
  totalAmount: number
  isPaid: boolean
  generatedAt: string
  extraFeeLines: MonthlyBillExtraFeeLine[]
  mealRecordDates: string[]
  leaveLines: MonthlyBillLeaveLine[]
}

export interface BillOverviewRow {
  studentId: number
  studentName: string
  // null：纯课外课学生，没有托管班。
  classRoomId: number | null
  className: string
  teacherName: string
  billId: number | null
  totalAmount: number | null
  isPaid: boolean
  yearMonth: string
}

export function fetchClassBillingRate(classId: number): Promise<ClassBillingRate | null> {
  return apiFetch<ClassBillingRate | null>(`/admin/classes/${classId}/billing-rate`)
}

export function upsertClassBillingRate(
  classId: number,
  tuitionRatePerMonth: number,
  mealRatePerDay: number,
): Promise<ClassBillingRate> {
  return apiFetch<ClassBillingRate>(`/admin/classes/${classId}/billing-rate`, {
    method: 'PUT',
    body: JSON.stringify({ tuitionRatePerMonth, mealRatePerDay }),
  })
}

export function fetchAllClassBillingRates(): Promise<ClassBillingRateRow[]> {
  return apiFetch<ClassBillingRateRow[]>('/admin/classes/billing-rates')
}

export function bulkSetClassBillingRate(
  tuitionRatePerMonth: number,
  mealRatePerDay: number,
): Promise<ClassBillingRateRow[]> {
  return apiFetch<ClassBillingRateRow[]>('/admin/classes/billing-rates/bulk-set', {
    method: 'PUT',
    body: JSON.stringify({ tuitionRatePerMonth, mealRatePerDay }),
  })
}

export function fetchClassBills(classId: number, month: string): Promise<MonthlyBill[]> {
  return apiFetch<MonthlyBill[]>(`/admin/classes/${classId}/bills?month=${month}`)
}

export function generateClassBills(classId: number, month: string): Promise<MonthlyBill[]> {
  return apiFetch<MonthlyBill[]>(`/admin/classes/${classId}/bills/generate?month=${month}`, { method: 'POST' })
}

export function generateStudentBill(
  studentId: number,
  month: string,
  tuitionOverride?: number,
): Promise<MonthlyBill> {
  return apiFetch<MonthlyBill>(`/admin/students/${studentId}/bills/generate?month=${month}`, {
    method: 'POST',
    ...(tuitionOverride !== undefined ? { body: JSON.stringify({ tuitionOverride }) } : {}),
  })
}

export function fetchBillOverview(
  month?: string,
  classRoomId?: number,
  studentName?: string,
  offCampusOnly?: boolean,
): Promise<BillOverviewRow[]> {
  const params = new URLSearchParams()
  if (month) params.set('month', month)
  if (classRoomId !== undefined) params.set('classRoomId', String(classRoomId))
  if (studentName) params.set('studentName', studentName)
  if (offCampusOnly) params.set('offCampusOnly', 'true')
  return apiFetch<BillOverviewRow[]>(`/admin/bills?${params.toString()}`)
}

export function fetchBillDetail(billId: number): Promise<MonthlyBill> {
  return apiFetch<MonthlyBill>(`/admin/bills/${billId}`)
}

export function setBillPaid(billId: number, isPaid: boolean): Promise<MonthlyBill> {
  return apiFetch<MonthlyBill>(`/admin/bills/${billId}/paid`, {
    method: 'PATCH',
    body: JSON.stringify({ isPaid }),
  })
}
