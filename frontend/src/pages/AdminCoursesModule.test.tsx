import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminCoursesModule } from './AdminCoursesModule'
import * as courseApi from '../api/course'
import * as adminApi from '../api/admin'

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

  it('lists existing courses read-only, with no create entry', async () => {
    render(<AdminCoursesModule />)

    expect(await screen.findByText('书法课')).toBeInTheDocument()
    expect(screen.getByText('李老师')).toBeInTheDocument()
    expect(screen.getByText('60分钟')).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
    expect(screen.getByText('启用中')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '创建' })).not.toBeInTheDocument()
    expect(screen.queryByPlaceholderText('课程名称')).not.toBeInTheDocument()
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

  it('shows a hint pointing to the pricing center instead of an inline price editor', async () => {
    render(<AdminCoursesModule />)
    await screen.findByText('书法课')

    expect(screen.getByText('请到定价中心修改')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /设置单价/ })).not.toBeInTheDocument()
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
