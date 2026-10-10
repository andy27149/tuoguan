import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminCourseStatementModal } from './AdminCourseStatementModal'
import * as courseApi from '../api/course'
import * as unitApi from '../api/unit'

vi.mock('../api/course')
vi.mock('../api/unit')

function unit(id: number, name: string): unitApi.TeachingUnit {
  return {
    id,
    name,
    billingMode: 'LESSON_COUNT',
    teacherId: 900,
    teacherName: '王老师',
    teacherPhone: '13900000000',
    lessonDurationMinutes: 60,
    pricePerLesson: 50,
    active: true,
  }
}

const COURSES: unitApi.TeachingUnit[] = [unit(10, '数学'), unit(11, '书法'), unit(12, '语文')]

function setup(enrolledCourseIds: number[] = [10, 11]) {
  const onClose = vi.fn()
  render(
    <AdminCourseStatementModal
      studentId={1}
      studentName="小明"
      enrolledCourseIds={enrolledCourseIds}
      onClose={onClose}
    />,
  )
  return { onClose }
}

describe('AdminCourseStatementModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(courseApi.fetchStudentCourseStatement).mockResolvedValue({
      balances: [],
      recharges: [],
      consumptions: [],
    })
    vi.mocked(unitApi.fetchTeachingUnits).mockResolvedValue(COURSES)
  })

  it('shows an enrollment error and does not call the API when recharging a course the student has not enrolled in', async () => {
    const { onClose } = setup([10, 11])
    await screen.findByText('课外账户对账单 - 小明')

    fireEvent.change(screen.getByLabelText('充值课程'), { target: { value: '12' } })
    fireEvent.change(screen.getByLabelText('充值课时数'), { target: { value: '5' } })
    fireEvent.click(screen.getByRole('button', { name: '充值' }))

    expect(await screen.findByText('学生尚未报名该课程，请报名后再来充值！')).toBeInTheDocument()
    expect(courseApi.rechargeStudentAccount).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('recharges normally when the selected course is in the student\'s enrolled courses', async () => {
    vi.mocked(courseApi.rechargeStudentAccount).mockResolvedValue({
      id: 1,
      studentId: 1,
      courseId: 10,
      courseName: '数学',
      lessonCount: 5,
      note: null,
      createdAt: '2026-10-10T00:00:00Z',
    })
    setup([10, 11])
    await screen.findByText('课外账户对账单 - 小明')

    fireEvent.change(screen.getByLabelText('充值课程'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('充值课时数'), { target: { value: '5' } })
    fireEvent.click(screen.getByRole('button', { name: '充值' }))

    await waitFor(() => expect(courseApi.rechargeStudentAccount).toHaveBeenCalledWith(1, 10, 5, null))
    expect(screen.queryByText('学生尚未报名该课程，请报名后再来充值！')).not.toBeInTheDocument()
  })
})
