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

  it('records consumption for a student', async () => {
    vi.mocked(courseApi.recordConsumption).mockResolvedValue({
      id: 1,
      studentId: 100,
      courseId: 1,
      courseName: null,
      consumptionDate: '2026-09-30',
      priceSnapshot: 50,
      teacherName: null,
    })
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getAllByRole('button', { name: '消课' })[0])
    fireEvent.click(screen.getByRole('button', { name: '确认消课' }))

    await waitFor(() =>
      expect(courseApi.recordConsumption).toHaveBeenCalledWith(1, 100, expect.any(String), false),
    )
  })

  it('shows a confirmation dialog on duplicate same-day consumption and retries with confirm=true', async () => {
    vi.mocked(courseApi.recordConsumption)
      .mockRejectedValueOnce(new ApiError(409, 'dup'))
      .mockResolvedValueOnce({
        id: 2,
        studentId: 100,
        courseId: 1,
        courseName: null,
        consumptionDate: '2026-09-30',
        priceSnapshot: 50,
        teacherName: null,
      })
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getAllByRole('button', { name: '消课' })[0])
    fireEvent.click(screen.getByRole('button', { name: '确认消课' }))

    expect(await screen.findByText('该学生今日已有消课记录，是否继续再记一次消课？')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '是' }))

    await waitFor(() =>
      expect(courseApi.recordConsumption).toHaveBeenLastCalledWith(1, 100, expect.any(String), true),
    )
  })

  it('shows an unpriced-course error message and disables consume when the course has no price', async () => {
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([{ ...COURSES[0], pricePerLesson: null }])
    setup()
    await screen.findByText('小明')

    expect(screen.getByText('该课程尚未配置单价，请联系管理员配置后再消课')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: '消课' })[0]).toBeDisabled()
  })
})
