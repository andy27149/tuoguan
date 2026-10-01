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
  { studentId: 100, name: '小明', schoolClassName: '三年级一班', offCampusOnly: false },
  { studentId: 200, name: '小红', schoolClassName: null, offCampusOnly: true },
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
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([])
  })

  it('creates a new course', async () => {
    const created: courseApi.Course = { id: 2, name: '绘画课', pricePerLesson: null, lessonDurationMinutes: 45, active: true }
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValueOnce([]).mockResolvedValueOnce(COURSES)
    vi.mocked(courseApi.createCourse).mockResolvedValue(created)
    setup()
    await screen.findByText('暂无课外课，先在上方创建一门吧')

    fireEvent.change(screen.getByPlaceholderText('课程名称'), { target: { value: '绘画课' } })
    fireEvent.change(screen.getByPlaceholderText('时长（分钟）'), { target: { value: '45' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(courseApi.createCourse).toHaveBeenCalledWith('绘画课', 45))
    expect(await screen.findByText('绘画课')).toBeInTheDocument()
  })

  it('shows a friendly error when creating a duplicate-named course', async () => {
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([])
    vi.mocked(courseApi.createCourse).mockRejectedValue(new ApiError(409, 'dup'))
    setup()
    await screen.findByText('暂无课外课，先在上方创建一门吧')

    fireEvent.change(screen.getByPlaceholderText('课程名称'), { target: { value: '书法课' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该课程名称已存在')).toBeInTheDocument()
  })

  it('renders the roster with 托管/课外 badges', async () => {
    setup()

    expect(await screen.findByText('小明')).toBeInTheDocument()
    expect(screen.getByText('托管')).toBeInTheDocument()
    expect(screen.getByText('纯课外')).toBeInTheDocument()
  })

  it('adds a new off-campus student and re-enrolls to the roster', async () => {
    vi.mocked(courseApi.createCourseStudent).mockResolvedValue(ROSTER[1])
    setup()
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小刚' } })
    fireEvent.click(screen.getByRole('button', { name: '新增并报名' }))

    await waitFor(() =>
      expect(courseApi.createCourseStudent).toHaveBeenCalledWith(1, '小刚', null),
    )
    await waitFor(() => expect(courseApi.fetchCourseRoster).toHaveBeenCalledTimes(2))
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
    fireEvent.change(screen.getByText('选择学生').closest('select')!, { target: { value: '300' } })
    fireEvent.click(screen.getByRole('button', { name: '添加到花名册' }))

    await waitFor(() => expect(courseApi.enrollExistingStudent).toHaveBeenCalledWith(1, 300))
  })

  it('unenrolls a student from the roster', async () => {
    vi.mocked(courseApi.unenrollStudent).mockResolvedValue(undefined)
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getAllByRole('button', { name: '移出' })[0])

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
