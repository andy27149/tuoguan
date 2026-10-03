import { apiFetch } from './client'

export interface Course {
  id: number
  name: string
  pricePerLesson: number | null
  lessonDurationMinutes: number
  active: boolean
}

export interface CourseRosterEntry {
  studentId: number
  name: string
  schoolClassName: string | null
  offCampusOnly: boolean
  // 仅纯课外课学生（offCampusOnly=true）有意义：该生在本课程下的预充值课时余额。
  // 托管班学生消课记入月度账单，没有余额概念，恒为 null。
  balance: number | null
}

export interface ConsumptionRecord {
  id: number
  studentId: number
  courseId: number
  courseName: string | null
  consumptionDate: string
  priceSnapshot: number
  teacherName: string | null
}

export interface RechargeRecord {
  id: number
  studentId: number
  courseId: number
  courseName: string | null
  lessonCount: number
  note: string | null
  createdAt: string
}

export interface CourseBalance {
  courseId: number
  courseName: string | null
  lessonsRecharged: number
  lessonsConsumed: number
  balance: number
}

export interface StudentCourseStatement {
  balances: CourseBalance[]
  recharges: RechargeRecord[]
  consumptions: ConsumptionRecord[]
}

export interface AdminStudent {
  id: number
  name: string
  schoolClassName: string | null
  classRoomId: number | null
  classRoomName: string | null
  offCampusOnly: boolean
  enrolledCourseNames: string[]
}

export interface CourseConsumptionSummaryRow {
  courseId: number
  courseName: string
  pricePerLesson: number | null
  lessonCount: number
  amount: number
}

// 教师端

export function fetchMyCourses(): Promise<Course[]> {
  return apiFetch<Course[]>('/courses')
}

export function enrollExistingStudent(courseId: number, studentId: number): Promise<void> {
  return apiFetch<void>(`/courses/${courseId}/enrollments`, {
    method: 'POST',
    body: JSON.stringify({ studentId }),
  })
}

export function unenrollStudent(courseId: number, studentId: number): Promise<void> {
  return apiFetch<void>(`/courses/${courseId}/enrollments/${studentId}`, { method: 'DELETE' })
}

export function fetchCourseRoster(courseId: number): Promise<CourseRosterEntry[]> {
  return apiFetch<CourseRosterEntry[]>(`/courses/${courseId}/roster`)
}

export function recordConsumption(
  courseId: number,
  studentId: number,
  date: string,
  confirm: boolean,
): Promise<ConsumptionRecord> {
  return apiFetch<ConsumptionRecord>(`/courses/${courseId}/consumption`, {
    method: 'POST',
    body: JSON.stringify({ studentId, date, confirm }),
  })
}

export function recordBatchConsumption(
  courseId: number,
  date: string,
  presentStudentIds: number[],
): Promise<ConsumptionRecord[]> {
  return apiFetch<ConsumptionRecord[]>(`/courses/${courseId}/consumption/batch`, {
    method: 'POST',
    body: JSON.stringify({ date, presentStudentIds }),
  })
}

// 管理员端
// 课程（教学单元）的增删改已统一收归 api/unit.ts 的 teaching-units 接口；这里只保留
// 课外课预充值账户相关的管理员操作（对账单、充值、消课统计），维持独立不并入月度账单。

export function fetchAdminStudents(): Promise<AdminStudent[]> {
  return apiFetch<AdminStudent[]>('/admin/students')
}

export function createAdminStudent(
  name: string,
  schoolClassName: string | null,
  teachingUnitId: number | null,
): Promise<AdminStudent> {
  return apiFetch<AdminStudent>('/admin/students', {
    method: 'POST',
    body: JSON.stringify({ name, schoolClassName, teachingUnitId }),
  })
}

export function rechargeStudentAccount(
  studentId: number,
  courseId: number,
  lessonCount: number,
  note: string | null,
): Promise<RechargeRecord> {
  return apiFetch<RechargeRecord>(`/admin/students/${studentId}/recharges`, {
    method: 'POST',
    body: JSON.stringify({ courseId, lessonCount, note }),
  })
}

export function fetchStudentCourseStatement(studentId: number): Promise<StudentCourseStatement> {
  return apiFetch<StudentCourseStatement>(`/admin/students/${studentId}/course-statement`)
}

export function fetchStudentCourseConsumption(
  studentId: number,
  month: string,
): Promise<CourseConsumptionSummaryRow[]> {
  return apiFetch<CourseConsumptionSummaryRow[]>(`/admin/students/${studentId}/course-consumption?month=${month}`)
}
