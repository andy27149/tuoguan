import { apiFetch } from './client'
import type { MonthlyStats } from './monthlyStats'
import type { StudentCourseStatement } from './course'

export interface CourseActivityRow {
  courseId: number
  courseName: string | null
  recentConsumptionDates: string[]
}

export interface PublicShare {
  studentName: string
  schoolClassName: string | null
  avatarUrl: string | null
  stats: MonthlyStats | null
  courseStatement: StudentCourseStatement | null
  // 托管班学生同时报名课外课时的轻量展示：只列课程+最近消课日期，不含充值/余额
  // （这类学生的课外课费用走托管月度账单附加费，余额概念不成立）。见产品诊断 #06。
  courseActivity: CourseActivityRow[]
}

export function fetchPublicShare(token: string, month?: string): Promise<PublicShare> {
  const query = month ? `?month=${month}` : ''
  return apiFetch<PublicShare>(`/public/share/${token}${query}`)
}
