import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminCoursesModule } from './AdminCoursesModule'
import * as courseApi from '../api/course'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'

vi.mock('../api/course')
vi.mock('../api/admin')

const TEACHERS = [
  { id: 1, phone: '13800000001', name: '张校长', role: 'ADMIN' as const, mustChangePassword: false },
  { id: 2, phone: '13800000002', name: '李老师', role: 'TEACHER' as const, mustChangePassword: false },
  { id: 3, phone: '13800000003', name: '王老师', role: 'TEACHER' as const, mustChangePassword: false },
]

const COURSES: courseApi.AdminCourse[] = [
  {
    id: 10,
    name: '书法课',
    teacherId: 2,
    teacherName: '李老师',
    teacherPhone: '13800000002',
    pricePerLesson: 50,
    lessonDurationMinutes: 60,
    active: true,
  },
]

describe('AdminCoursesModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(courseApi.fetchAdminCourses).mockResolvedValue(COURSES)
    vi.mocked(adminApi.fetchTeachers).mockResolvedValue(TEACHERS)
  })

  it('lists existing courses with a create entry', async () => {
    render(<AdminCoursesModule />)

    expect(await screen.findByText('书法课')).toBeInTheDocument()
    const row = screen.getByText('书法课').closest('tr') as HTMLElement
    expect(within(row).getByText('李老师')).toBeInTheDocument()
    expect(within(row).getByText('60分钟')).toBeInTheDocument()
    expect(within(row).getByText('¥50.00')).toBeInTheDocument()
    expect(screen.getByText('启用中')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '创建' })).toBeInTheDocument()
    expect(screen.getByPlaceholderText('课程名称')).toBeInTheDocument()
  })

  it('creates a new course and assigns a teacher', async () => {
    const created: courseApi.AdminCourse = {
      id: 20,
      name: '绘画课',
      teacherId: 3,
      teacherName: '王老师',
      teacherPhone: '13800000003',
      pricePerLesson: null,
      lessonDurationMinutes: 45,
      active: true,
    }
    vi.mocked(courseApi.createAdminCourse).mockResolvedValue(created)
    render(<AdminCoursesModule />)
    await screen.findByText('书法课')

    fireEvent.change(screen.getByPlaceholderText('课程名称'), { target: { value: '绘画课' } })
    fireEvent.change(screen.getByPlaceholderText('时长（分钟）'), { target: { value: '45' } })
    fireEvent.change(screen.getByLabelText('负责教师'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(courseApi.createAdminCourse).toHaveBeenCalledWith('绘画课', 45, 3, null),
    )
    await waitFor(() => expect(courseApi.fetchAdminCourses).toHaveBeenCalledTimes(2))
  })

  it('shows a friendly error when creating a duplicate-named course', async () => {
    vi.mocked(courseApi.createAdminCourse).mockRejectedValue(new ApiError(409, 'dup'))
    render(<AdminCoursesModule />)
    await screen.findByText('书法课')

    fireEvent.change(screen.getByPlaceholderText('课程名称'), { target: { value: '书法课' } })
    fireEvent.change(screen.getByPlaceholderText('时长（分钟）'), { target: { value: '60' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该课程名称已存在')).toBeInTheDocument()
  })

  it('shows an unconfigured-price placeholder when the course has no price', async () => {
    vi.mocked(courseApi.fetchAdminCourses).mockResolvedValue([{ ...COURSES[0], pricePerLesson: null }])
    render(<AdminCoursesModule />)

    expect(await screen.findByText('未配置')).toBeInTheDocument()
  })

  it('reassigns the teacher for a course', async () => {
    vi.mocked(courseApi.updateAdminCourse).mockResolvedValue(undefined)
    render(<AdminCoursesModule />)
    await screen.findByText('书法课')

    fireEvent.click(screen.getByRole('button', { name: '改派书法课' }))
    fireEvent.change(screen.getByLabelText('负责教师10'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(courseApi.updateAdminCourse).toHaveBeenCalledWith(10, { teacherId: 3 }))
    await waitFor(() => expect(courseApi.fetchAdminCourses).toHaveBeenCalledTimes(2))
  })

  it('shows an error when reassigning the teacher fails', async () => {
    vi.mocked(courseApi.updateAdminCourse).mockRejectedValue(new Error('boom'))
    render(<AdminCoursesModule />)
    await screen.findByText('书法课')

    fireEvent.click(screen.getByRole('button', { name: '改派书法课' }))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('保存失败，请重试')).toBeInTheDocument()
  })

  it('sets the price for a course inline', async () => {
    vi.mocked(courseApi.updateAdminCourse).mockResolvedValue(undefined)
    render(<AdminCoursesModule />)
    await screen.findByText('书法课')

    fireEvent.click(screen.getByRole('button', { name: '设置单价' }))
    fireEvent.change(screen.getByLabelText('单价10'), { target: { value: '80' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(courseApi.updateAdminCourse).toHaveBeenCalledWith(10, { pricePerLesson: 80 }))
    await waitFor(() => expect(courseApi.fetchAdminCourses).toHaveBeenCalledTimes(2))
  })

  it('toggles a course active state', async () => {
    vi.mocked(courseApi.updateAdminCourse).mockResolvedValue(undefined)
    render(<AdminCoursesModule />)
    await screen.findByText('书法课')

    fireEvent.click(screen.getByRole('button', { name: '停用书法课' }))

    await waitFor(() => expect(courseApi.updateAdminCourse).toHaveBeenCalledWith(10, { active: false }))
    await waitFor(() => expect(courseApi.fetchAdminCourses).toHaveBeenCalledTimes(2))
  })
})
