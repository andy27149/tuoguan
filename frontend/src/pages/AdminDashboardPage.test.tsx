import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminDashboardPage } from './AdminDashboardPage'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'

vi.mock('../api/admin')

const TEACHERS = [
  { id: 1, phone: '13800000001', role: 'ADMIN' as const, mustChangePassword: false },
  { id: 2, phone: '13800000002', role: 'TEACHER' as const, mustChangePassword: true },
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

    await screen.findByText(/13800000001/, { selector: 'li' })
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
    const created = { id: 3, phone: '13800000003', role: 'TEACHER' as const, mustChangePassword: true }
    vi.mocked(adminApi.createTeacher).mockResolvedValue(created)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'li' })

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800000003' } })
    fireEvent.change(screen.getByPlaceholderText('初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(adminApi.createTeacher).toHaveBeenCalledWith('13800000003', 'initial123'),
    )
    expect(await screen.findByText(/初始密码 initial123/)).toBeInTheDocument()
  })

  it('shows a duplicate-phone message on 409', async () => {
    vi.mocked(adminApi.createTeacher).mockRejectedValue(new ApiError(409, '冲突'))
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/13800000001/, { selector: 'li' })

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800000002' } })
    fireEvent.change(screen.getByPlaceholderText('初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该手机号已注册')).toBeInTheDocument()
  })

  it('lists existing admin-created classes', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(await screen.findByText(/托管一班/)).toBeInTheDocument()
    expect(screen.getByText(/教师 13800000002/)).toBeInTheDocument()
  })

  it('creates a class for a selected teacher and updates the list', async () => {
    const created = { id: 2, name: '托管二班', teacherId: 1, teacherPhone: '13800000001' }
    vi.mocked(adminApi.createAdminClass).mockResolvedValue(created)
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)

    fireEvent.change(screen.getByPlaceholderText('班级名称'), { target: { value: '托管二班' } })
    fireEvent.change(screen.getByDisplayValue('选择教师'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: '创建班级' }))

    await waitFor(() => expect(adminApi.createAdminClass).toHaveBeenCalledWith('托管二班', 1))
    expect(await screen.findByText(/教师 13800000001/)).toBeInTheDocument()
  })

  it('shows a duplicate-name message on 409 when creating a class', async () => {
    vi.mocked(adminApi.createAdminClass).mockRejectedValue(new ApiError(409, '冲突'))
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await screen.findByText(/托管一班/)

    fireEvent.change(screen.getByPlaceholderText('班级名称'), { target: { value: '托管一班' } })
    fireEvent.change(screen.getByDisplayValue('选择教师'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: '创建班级' }))

    expect(await screen.findByText('该托管班名称已存在')).toBeInTheDocument()
  })

  it('calls onBack when the back button is clicked', async () => {
    const onBack = vi.fn()
    render(<AdminDashboardPage onBack={onBack} />)
    await screen.findByText(/13800000001/, { selector: 'li' })

    fireEvent.click(screen.getByRole('button', { name: '返回看板' }))

    expect(onBack).toHaveBeenCalled()
  })
})
