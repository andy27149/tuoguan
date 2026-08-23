import { render, screen, fireEvent, waitFor } from '@testing-library/react'
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

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchTeachers).mockResolvedValue(TEACHERS)
    vi.mocked(adminApi.fetchAdminDashboard).mockResolvedValue(DASHBOARD)
  })

  it('lists teachers loaded on mount', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(await screen.findByText(/13800000001/)).toBeInTheDocument()
    expect(screen.getByText(/13800000002/)).toBeInTheDocument()
    expect(screen.getByText(/待修改初始密码/)).toBeInTheDocument()
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
    await screen.findByText(/13800000001/)

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
    await screen.findByText(/13800000001/)

    fireEvent.change(screen.getByPlaceholderText('手机号'), { target: { value: '13800000002' } })
    fireEvent.change(screen.getByPlaceholderText('初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该手机号已注册')).toBeInTheDocument()
  })

  it('calls onBack when the back button is clicked', async () => {
    const onBack = vi.fn()
    render(<AdminDashboardPage onBack={onBack} />)
    await screen.findByText(/13800000001/)

    fireEvent.click(screen.getByRole('button', { name: '返回看板' }))

    expect(onBack).toHaveBeenCalled()
  })
})
