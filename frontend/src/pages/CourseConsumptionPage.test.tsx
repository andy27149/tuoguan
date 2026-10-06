import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { CourseConsumptionPage } from './CourseConsumptionPage'
import * as courseApi from '../api/course'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'
import { ApiError } from '../api/client'

vi.mock('../api/course')
vi.mock('../api/classes')
vi.mock('../api/students')

const COURSES: courseApi.Course[] = [
  { id: 1, name: '书法课', pricePerLesson: 50, lessonDurationMinutes: 60, active: true },
]

const ROSTER: courseApi.CourseRosterEntry[] = [
  { studentId: 100, name: '小明', schoolClassName: '三年级一班', offCampusOnly: false, balance: null },
  { studentId: 200, name: '小红', schoolClassName: null, offCampusOnly: true, balance: 3 },
]

function setup() {
  const onBack = vi.fn()
  render(<CourseConsumptionPage onBack={onBack} />)
  return { onBack }
}

describe('CourseConsumptionPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue(COURSES)
    vi.mocked(courseApi.fetchCourseRoster).mockResolvedValue(ROSTER)
    vi.mocked(courseApi.fetchOffCampusCandidates).mockResolvedValue([])
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([])
  })

  it('shows a message to contact the admin when there are no assigned courses', async () => {
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([])
    setup()
    expect(await screen.findByText('暂无课外课，请联系管理员分配')).toBeInTheDocument()
  })

  it('renders the roster with billing-mode badges explaining the actual financial treatment', async () => {
    setup()

    expect(await screen.findByText('小明')).toBeInTheDocument()
    expect(screen.getByText('托管（计入月度账单）')).toBeInTheDocument()
    expect(screen.getByText('纯课外（扣课时余额）')).toBeInTheDocument()
  })

  it('shows the off-campus balance, highlighted red when depleted or negative', async () => {
    vi.mocked(courseApi.fetchCourseRoster).mockResolvedValue([
      { studentId: 100, name: '小明', schoolClassName: '三年级一班', offCampusOnly: false, balance: null },
      { studentId: 200, name: '小红', schoolClassName: null, offCampusOnly: true, balance: 3 },
      { studentId: 300, name: '小刚', schoolClassName: null, offCampusOnly: true, balance: -1 },
    ])
    setup()
    await screen.findByText('小明')

    expect(screen.getByText('余额：3 课时')).toHaveClass('text-gray-600')
    expect(screen.getByText('余额：-1 课时')).toHaveClass('text-red-700')
  })

  it('shows a hint to contact the admin instead of a self-create-student form', async () => {
    setup()
    await screen.findByText('小明')

    expect(screen.getByText('新增课外学生请联系管理员添加')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '新增并报名' })).not.toBeInTheDocument()
  })

  it('enrolls an existing student from one of the teacher own classes', async () => {
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([{ id: 5, name: '一班', teacherId: 1 } as classesApi.ClassRoom])
    vi.mocked(studentsApi.fetchStudents).mockResolvedValue([
      { id: 300, name: '小李' } as studentsApi.Student,
    ])
    vi.mocked(courseApi.enrollExistingStudent).mockResolvedValue(undefined)
    setup()
    await screen.findByText('小明')

    fireEvent.change(screen.getByText('选择班级').closest('select')!, { target: { value: '5' } })
    await waitFor(() => expect(studentsApi.fetchStudents).toHaveBeenCalledWith(5))
    fireEvent.change(screen.getAllByText('选择学生')[0].closest('select')!, { target: { value: '300' } })
    fireEvent.click(screen.getAllByRole('button', { name: '添加到花名册' })[0])

    await waitFor(() => expect(courseApi.enrollExistingStudent).toHaveBeenCalledWith(1, 300))
  })

  it('shows off-campus candidates and enrolls one into the roster', async () => {
    vi.mocked(courseApi.fetchOffCampusCandidates).mockResolvedValue([
      { studentId: 400, name: '小芳', schoolClassName: '四年级二班' },
    ])
    vi.mocked(courseApi.enrollExistingStudent).mockResolvedValue(undefined)
    setup()
    await screen.findByText('小明')

    expect(await screen.findByText('小芳 · 四年级二班')).toBeInTheDocument()
    fireEvent.change(screen.getAllByText('选择学生')[1].closest('select')!, { target: { value: '400' } })
    fireEvent.click(screen.getAllByRole('button', { name: '添加到花名册' })[1])

    await waitFor(() => expect(courseApi.enrollExistingStudent).toHaveBeenCalledWith(1, 400))
  })

  it('shows a hint when there are no off-campus candidates to add', async () => {
    setup()
    await screen.findByText('小明')

    expect(screen.getByText('暂无可添加的纯课外课学生')).toBeInTheDocument()
  })

  it('asks for confirmation before unenrolling a student, and does nothing on cancel', async () => {
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getAllByRole('button', { name: '移出' })[0])
    expect(await screen.findByText(/从本课程花名册移出吗/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(courseApi.unenrollStudent).not.toHaveBeenCalled()
  })

  it('unenrolls a student from the roster after confirming', async () => {
    vi.mocked(courseApi.unenrollStudent).mockResolvedValue(undefined)
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getAllByRole('button', { name: '移出' })[0])
    fireEvent.click(await screen.findByRole('button', { name: '确认移出' }))

    await waitFor(() => expect(courseApi.unenrollStudent).toHaveBeenCalledWith(1, 100))
  })

  it('defaults all roster students to present and submits a roll call for everyone', async () => {
    vi.mocked(courseApi.recordBatchConsumption).mockResolvedValue([
      {
        id: 1,
        studentId: 100,
        courseId: 1,
        courseName: null,
        consumptionDate: '2026-09-30',
        priceSnapshot: 50,
        teacherName: null,
      },
      {
        id: 2,
        studentId: 200,
        courseId: 1,
        courseName: null,
        consumptionDate: '2026-09-30',
        priceSnapshot: 50,
        teacherName: null,
      },
    ])
    setup()
    await screen.findByText('小明')

    expect(screen.getByRole('checkbox', { name: /小明/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /小红/ })).toBeChecked()

    fireEvent.click(screen.getByRole('button', { name: '确认消课（2人）' }))

    await waitFor(() =>
      expect(courseApi.recordBatchConsumption).toHaveBeenCalledWith(1, expect.any(String), [100, 200]),
    )
  })

  it('rejects submitting a roll call for a future date', async () => {
    setup()
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('消课日期'), { target: { value: '2099-01-01' } })
    fireEvent.click(screen.getByRole('button', { name: '确认消课（2人）' }))

    expect(await screen.findByText('无法对未来日期进行消课处理！')).toBeInTheDocument()
    expect(courseApi.recordBatchConsumption).not.toHaveBeenCalled()
  })

  it('unchecking a student excludes them from the submitted roll call', async () => {
    vi.mocked(courseApi.recordBatchConsumption).mockResolvedValue([])
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('checkbox', { name: /小红/ }))
    expect(screen.getByRole('button', { name: '确认消课（1人）' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '确认消课（1人）' }))

    await waitFor(() =>
      expect(courseApi.recordBatchConsumption).toHaveBeenCalledWith(1, expect.any(String), [100]),
    )
  })

  it('shows a friendly error when the roll call submission fails on an unpriced course', async () => {
    vi.mocked(courseApi.recordBatchConsumption).mockRejectedValue(new ApiError(400, 'unpriced'))
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '确认消课（2人）' }))

    expect(await screen.findByText('该课程尚未配置单价，请联系管理员配置')).toBeInTheDocument()
  })

  it('shows an unpriced-course warning and disables roll call when the course has no price', async () => {
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([{ ...COURSES[0], pricePerLesson: null }])
    setup()
    await screen.findByText('小明')

    expect(screen.getByText('该课程尚未配置单价，请联系管理员配置后再消课')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /小明/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /确认消课/ })).toBeDisabled()
  })
})
