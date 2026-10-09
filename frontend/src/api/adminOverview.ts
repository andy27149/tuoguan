import { apiFetch } from './client'

export interface LowBalanceRow {
  studentId: number
  studentName: string
  courseId: number
  courseName: string
  balance: number
}

export interface UnpaidBillRow {
  studentId: number
  studentName: string
  className: string
  yearMonth: string
  totalAmount: number
}

export interface UnpaidBillSummary {
  count: number
  totalAmount: number
  rows: UnpaidBillRow[]
}

export interface TeacherStudentCount {
  teacherName: string
  studentCount: number
}

export interface EnrollmentSummary {
  totalCount: number
  custodyCount: number
  offCampusOnlyCount: number
  byTeacher: TeacherStudentCount[]
}

export interface TodaySnapshot {
  arrivedCount: number
  mealCount: number
  leaveCount: number
  totalCustodyStudentCount: number
}

export interface RevenueSnapshot {
  tuitionMonth: string
  tuitionBilled: number
  tuitionCollected: number
  consumptionMonth: string
  offCampusConsumptionCount: number
}

export function fetchLowBalance(): Promise<LowBalanceRow[]> {
  return apiFetch<LowBalanceRow[]>('/admin/overview/low-balance')
}

export function fetchUnpaidBills(): Promise<UnpaidBillSummary> {
  return apiFetch<UnpaidBillSummary>('/admin/overview/unpaid-bills')
}

export function fetchEnrollmentSummary(): Promise<EnrollmentSummary> {
  return apiFetch<EnrollmentSummary>('/admin/overview/enrollment')
}

export function fetchTodaySnapshot(): Promise<TodaySnapshot> {
  return apiFetch<TodaySnapshot>('/admin/overview/today')
}

export function fetchRevenueSnapshot(): Promise<RevenueSnapshot> {
  return apiFetch<RevenueSnapshot>('/admin/overview/revenue')
}
