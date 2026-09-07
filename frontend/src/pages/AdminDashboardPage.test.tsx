import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminDashboardPage } from './AdminDashboardPage'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'

const logout = vi.fn()

vi.mock('../api/admin')
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ logout }),
}))

const TEACHERS = [
  { id: 1, phone: '13800000001', name: '张校长', role: 'ADMIN' as const, mustChangePassword: false },
  { id: 2, phone: '13800000002', name: '李老师', role: 'TEACHER' as const, mustChangePassword: true },
]

const DASHBOARD = {
  date: '2026-08-23',
  classes: [
    { classRoomId: 1, className: '一班', studentCount: 5, completedStudentCount: 3 },
    { classRoomId: 2, className: '二班', studentCount: 4, completedStudentCount: 4 },
  ],
}

const ADMIN_CLASSES = [{ id: 1, name: '托管一班', teacherId: 2, teacherPhone: '13800000002' }]

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchTeachers).mockResolvedValue(TEACHERS)
    vi.mocked(adminApi.fetchAdminDashboard).mockResolvedValue(DASHBOARD)
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue(ADMIN_CLASSES)
  })

  it('lists teachers loaded on mount', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    await screen.findByText(/13800000001/, { selector: 'span' })
    const teacherSection = screen.getByText('教师列表（2）').closest('div') as HTMLElement
    expect(within(teacherSection).getByText(/13800000002/)).toBeInTheDocument()
    expect(within(teacherSection).getByText(/待修改初始密码/)).toBeInTheDocument()
  })

  it('renders dashboard class summary rows', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(await screen.findByText('一班')).toBeInTheDocument()
    expect(screen.getByText('二班')).toBeInTheDocument()
    await waitFor(() => expect(adminApi.fetchAdminDashboard).toHaveBeenCalled())
  })

  it('creates a teacher and shows the one-time password banner', async () => {
    const created = { id: 3, phone: '13800000003', name: '王老师', role: 'TEACHER' as const, mustChangePassword: true }
    vi.mocked(adminApi.createTeacher).mockResolvedValue(created)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800000003' } })
    fireEvent.change(screen.getByPlaceholderText('教师姓名'), { target: { value: '王老师' } })
    fireEvent.change(screen.getByPlaceholderText('初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(adminApi.createTeacher).toHaveBeenCalledWith('13800000003', '王老师', 'initial123'),
    )
    expect(await screen.findByText(/初始密码 initial123/)).toBeInTheDocument()
  })

  it('shows a duplicate-phone message on 409', async () => {
    vi.mocked(adminApi.createTeacher).mockRejectedValue(new ApiError(409, '冲突'))
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800000002' } })
    fireEvent.change(screen.getByPlaceholderText('教师姓名'), { target: { value: '李老师' } })
    fireEvent.change(screen.getByPlaceholderText('初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该手机号已注册')).toBeInTheDocument()
  })

  it('refreshes the dashboard after creating a teacher', async () => {
    const created = { id: 3, phone: '13800000003', name: '王老师', role: 'TEACHER' as const, mustChangePassword: true }
    vi.mocked(adminApi.createTeacher).mockResolvedValue(created)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })
    expect(adminApi.fetchAdminDashboard).toHaveBeenCalledTimes(1)

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800000003' } })
    fireEvent.change(screen.getByPlaceholderText('教师姓名'), { target: { value: '王老师' } })
    fireEvent.change(screen.getByPlaceholderText('初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(adminApi.fetchAdminDashboard).toHaveBeenCalledTimes(2))
  })

  it('lists existing classes read-only', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(await screen.findByText(/托管一班/)).toBeInTheDocument()
    expect(screen.getByText(/教师 13800000002/)).toBeInTheDocument()
    expect(screen.queryByPlaceholderText('班级名称')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '创建班级' })).not.toBeInTheDocument()
  })

  it('calls onBack when the back button is clicked', async () => {
    const onBack = vi.fn()
    render(<AdminDashboardPage onBack={onBack} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.click(screen.getByRole('button', { name: '返回看板' }))

    expect(onBack).toHaveBeenCalled()
  })

  it('calls logout when the logout button is clicked', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    expect(logout).toHaveBeenCalled()
  })

  it('only shows a delete button for teacher-role rows', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    expect(screen.queryByRole('button', { name: '删除教师张校长' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '删除教师李老师' })).toBeInTheDocument()
  })

  it('fetches deletion impact and opens the modal when delete is clicked', async () => {
    vi.mocked(adminApi.fetchTeacherDeletionImpact).mockResolvedValue({
      classCount: 1,
      studentCount: 0,
      templateCount: 1,
      hasStudents: false,
    })
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.click(screen.getByRole('button', { name: '删除教师李老师' }))

    expect(adminApi.fetchTeacherDeletionImpact).toHaveBeenCalledWith(2)
    expect(await screen.findByText(/该教师名下没有学生/)).toBeInTheDocument()
  })

  it('deletes the teacher and refreshes lists after confirming', async () => {
    vi.mocked(adminApi.fetchTeacherDeletionImpact).mockResolvedValue({
      classCount: 1,
      studentCount: 0,
      templateCount: 1,
      hasStudents: false,
    })
    vi.mocked(adminApi.deleteTeacher).mockResolvedValue(undefined)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.click(screen.getByRole('button', { name: '删除教师李老师' }))
    await screen.findByText(/该教师名下没有学生/)
    expect(adminApi.fetchTeachers).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(adminApi.deleteTeacher).toHaveBeenCalledWith(2, 'DELETE_ALL', undefined))
    await waitFor(() => expect(adminApi.fetchTeachers).toHaveBeenCalledTimes(2))
    expect(screen.queryByText(/该教师名下没有学生/)).not.toBeInTheDocument()
  })

  it('shows an error and keeps the list unchanged when deletion fails', async () => {
    vi.mocked(adminApi.fetchTeacherDeletionImpact).mockResolvedValue({
      classCount: 1,
      studentCount: 0,
      templateCount: 1,
      hasStudents: false,
    })
    vi.mocked(adminApi.deleteTeacher).mockRejectedValue(new Error('boom'))
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.click(screen.getByRole('button', { name: '删除教师李老师' }))
    await screen.findByText(/该教师名下没有学生/)
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(await screen.findByText('删除失败，请重试')).toBeInTheDocument()
  })

  it('asks for confirmation before deleting a class, and does nothing on cancel', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 0 })
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)

    fireEvent.click(screen.getByRole('button', { name: '删除班级托管一班' }))

    expect(adminApi.fetchClassRoomDeletionImpact).toHaveBeenCalledWith(1)
    expect(await screen.findByText('确认删除班级「托管一班」吗？此操作不可恢复。')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '否' }))

    expect(adminApi.deleteClassRoom).not.toHaveBeenCalled()
    expect(screen.queryByText('确认删除班级「托管一班」吗？此操作不可恢复。')).not.toBeInTheDocument()
  })

  it('shows the student count warning when the class has students', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 3 })
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)

    fireEvent.click(screen.getByRole('button', { name: '删除班级托管一班' }))

    expect(
      await screen.findByText('班级「托管一班」下有 3 名学生，删除后班级、学生及相关记录将全部清空，且不可恢复，确认删除吗？'),
    ).toBeInTheDocument()
  })

  it('deletes the class and refreshes lists after confirming', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 0 })
    vi.mocked(adminApi.deleteClassRoom).mockResolvedValue(undefined)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)
    expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '删除班级托管一班' }))
    await screen.findByText('确认删除班级「托管一班」吗？此操作不可恢复。')

    fireEvent.click(screen.getByRole('button', { name: '是' }))

    await waitFor(() => expect(adminApi.deleteClassRoom).toHaveBeenCalledWith(1))
    await waitFor(() => expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(2))
    expect(screen.queryByText('确认删除班级「托管一班」吗？此操作不可恢复。')).not.toBeInTheDocument()
  })

  it('shows an error when class deletion fails', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 0 })
    vi.mocked(adminApi.deleteClassRoom).mockRejectedValue(new Error('boom'))
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)

    fireEvent.click(screen.getByRole('button', { name: '删除班级托管一班' }))
    await screen.findByText('确认删除班级「托管一班」吗？此操作不可恢复。')
    fireEvent.click(screen.getByRole('button', { name: '是' }))

    expect(await screen.findByText('删除失败，请重试')).toBeInTheDocument()
  })

  it('renames a teacher and refreshes the list', async () => {
    const renamed = { id: 2, phone: '13800000002', name: '李老师改', role: 'TEACHER' as const, mustChangePassword: true }
    vi.mocked(adminApi.updateTeacherName).mockResolvedValue(renamed)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })
    expect(adminApi.fetchTeachers).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '编辑教师李老师' }))
    const input = screen.getByLabelText('教师姓名13800000002')
    fireEvent.change(input, { target: { value: '李老师改' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(adminApi.updateTeacherName).toHaveBeenCalledWith(2, '李老师改'))
    await waitFor(() => expect(adminApi.fetchTeachers).toHaveBeenCalledTimes(2))
    expect(screen.queryByLabelText('教师姓名13800000002')).not.toBeInTheDocument()
  })

  it('cancels teacher rename without calling the API', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.click(screen.getByRole('button', { name: '编辑教师李老师' }))
    fireEvent.change(screen.getByLabelText('教师姓名13800000002'), { target: { value: '改动但取消' } })
    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(adminApi.updateTeacherName).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('教师姓名13800000002')).not.toBeInTheDocument()
  })

  it('shows an error when teacher rename fails', async () => {
    vi.mocked(adminApi.updateTeacherName).mockRejectedValue(new Error('boom'))
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'span' })

    fireEvent.click(screen.getByRole('button', { name: '编辑教师李老师' }))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('保存失败，请重试')).toBeInTheDocument()
  })

  it('renames a class and reassigns it to another teacher', async () => {
    const updated = { id: 1, name: '托管一班改', teacherId: 1, teacherPhone: '13800000001' }
    vi.mocked(adminApi.updateClassRoom).mockResolvedValue(updated)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)
    expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '编辑班级托管一班' }))
    fireEvent.change(screen.getByLabelText('班级名称1'), { target: { value: '托管一班改' } })
    expect(screen.queryByLabelText('班级教师1')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(adminApi.updateClassRoom).toHaveBeenCalledWith(1, '托管一班改', 2))
    await waitFor(() => expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(2))
    expect(screen.queryByLabelText('班级名称1')).not.toBeInTheDocument()
  })

  it('shows a duplicate-name error when class rename fails with 409', async () => {
    vi.mocked(adminApi.updateClassRoom).mockRejectedValue(new ApiError(409, '冲突'))
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)

    fireEvent.click(screen.getByRole('button', { name: '编辑班级托管一班' }))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('该教师下已有同名班级')).toBeInTheDocument()
  })
})
