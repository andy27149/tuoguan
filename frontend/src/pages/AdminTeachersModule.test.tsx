import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminTeachersModule } from './AdminTeachersModule'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'

vi.mock('../api/admin')

const TEACHERS = [
  { id: 1, phone: '13800000001', name: '张校长', role: 'ADMIN' as const, mustChangePassword: false },
  { id: 2, phone: '13800000002', name: '李老师', role: 'TEACHER' as const, mustChangePassword: true },
]

const CLASSES = [
  { id: 100, name: '一班', teacherId: 2, teacherName: '李老师', teacherPhone: '13800000002' },
]

describe('AdminTeachersModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchTeachers).mockResolvedValue(TEACHERS)
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue(CLASSES)
  })

  it('lists teachers loaded on mount', async () => {
    render(<AdminTeachersModule />)

    await screen.findByText('13800000001', { selector: 'td' })
    const teacherSection = screen.getByText('教师列表（2）').closest('div') as HTMLElement
    expect(within(teacherSection).getByText('13800000002')).toBeInTheDocument()
    expect(within(teacherSection).getByText(/待修改初始密码/)).toBeInTheDocument()
  })

  it('shows the classes each teacher manages, clickable to jump to the class kanban', async () => {
    const onOpenClassKanban = vi.fn()
    render(<AdminTeachersModule onOpenClassKanban={onOpenClassKanban} />)

    const classButton = await screen.findByRole('button', { name: '一班' })
    fireEvent.click(classButton)

    expect(onOpenClassKanban).toHaveBeenCalledWith(100)
    const zhangRow = (await screen.findByText('13800000001', { selector: 'td' })).closest('tr') as HTMLElement
    expect(within(zhangRow).getByText('-')).toBeInTheDocument()
  })

  it('creates a teacher and shows the one-time password banner', async () => {
    const created = { id: 3, phone: '13800000003', name: '王老师', role: 'TEACHER' as const, mustChangePassword: true }
    vi.mocked(adminApi.createTeacher).mockResolvedValue(created)
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

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
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800000002' } })
    fireEvent.change(screen.getByPlaceholderText('教师姓名'), { target: { value: '李老师' } })
    fireEvent.change(screen.getByPlaceholderText('初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该手机号已注册')).toBeInTheDocument()
  })

  it('only shows a delete button for teacher-role rows', async () => {
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

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
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

    fireEvent.click(screen.getByRole('button', { name: '删除教师李老师' }))

    expect(adminApi.fetchTeacherDeletionImpact).toHaveBeenCalledWith(2)
    expect(await screen.findByText(/该教师名下没有学生/)).toBeInTheDocument()
  })

  it('deletes the teacher and refreshes the list after confirming', async () => {
    vi.mocked(adminApi.fetchTeacherDeletionImpact).mockResolvedValue({
      classCount: 1,
      studentCount: 0,
      templateCount: 1,
      hasStudents: false,
    })
    vi.mocked(adminApi.deleteTeacher).mockResolvedValue(undefined)
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

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
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

    fireEvent.click(screen.getByRole('button', { name: '删除教师李老师' }))
    await screen.findByText(/该教师名下没有学生/)
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(await screen.findByText('删除失败，请重试')).toBeInTheDocument()
  })

  it('renames a teacher and refreshes the list', async () => {
    const renamed = { id: 2, phone: '13800000002', name: '李老师改', role: 'TEACHER' as const, mustChangePassword: true }
    vi.mocked(adminApi.updateTeacherName).mockResolvedValue(renamed)
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })
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
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

    fireEvent.click(screen.getByRole('button', { name: '编辑教师李老师' }))
    fireEvent.change(screen.getByLabelText('教师姓名13800000002'), { target: { value: '改动但取消' } })
    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(adminApi.updateTeacherName).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('教师姓名13800000002')).not.toBeInTheDocument()
  })

  it('shows an error when teacher rename fails', async () => {
    vi.mocked(adminApi.updateTeacherName).mockRejectedValue(new Error('boom'))
    render(<AdminTeachersModule />)
    await screen.findByText('13800000001', { selector: 'td' })

    fireEvent.click(screen.getByRole('button', { name: '编辑教师李老师' }))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('保存失败，请重试')).toBeInTheDocument()
  })
})
