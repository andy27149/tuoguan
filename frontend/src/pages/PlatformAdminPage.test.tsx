import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { PlatformAdminPage } from './PlatformAdminPage'
import * as platformApi from '../api/platform'
import { ApiError } from '../api/client'

const logout = vi.fn()

vi.mock('../api/platform')
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ logout }),
}))

const INSTITUTIONS = [
  { id: 1, name: '机构A', createdAt: '2026-08-01T00:00:00Z', teacherCount: 2 },
]

describe('PlatformAdminPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(platformApi.fetchInstitutions).mockResolvedValue(INSTITUTIONS)
  })

  it('lists institutions loaded on mount', async () => {
    render(<PlatformAdminPage />)

    expect(await screen.findByText(/机构A/)).toBeInTheDocument()
    expect(screen.getByText(/2 位教师/)).toBeInTheDocument()
  })

  it('creates an institution and shows the one-time password banner', async () => {
    const created = { institutionId: 2, institutionName: '机构B', adminPhone: '13700000002' }
    vi.mocked(platformApi.createInstitution).mockResolvedValue(created)
    render(<PlatformAdminPage />)
    await screen.findByText(/机构A/)

    fireEvent.change(screen.getByPlaceholderText('机构名称'), { target: { value: '机构B' } })
    fireEvent.change(screen.getByPlaceholderText('管理员手机号'), { target: { value: '13700000002' } })
    fireEvent.change(screen.getByPlaceholderText('管理员初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '开通' }))

    await waitFor(() =>
      expect(platformApi.createInstitution).toHaveBeenCalledWith('机构B', '13700000002', 'initial123'),
    )
    expect(await screen.findByText(/初始密码 initial123/)).toBeInTheDocument()
  })

  it('shows a duplicate-phone message on 409', async () => {
    vi.mocked(platformApi.createInstitution).mockRejectedValue(new ApiError(409, '冲突'))
    render(<PlatformAdminPage />)
    await screen.findByText(/机构A/)

    fireEvent.change(screen.getByPlaceholderText('机构名称'), { target: { value: '机构C' } })
    fireEvent.change(screen.getByPlaceholderText('管理员手机号'), { target: { value: '13700000003' } })
    fireEvent.change(screen.getByPlaceholderText('管理员初始密码'), { target: { value: 'initial123' } })
    fireEvent.click(screen.getByRole('button', { name: '开通' }))

    expect(await screen.findByText('该手机号已注册')).toBeInTheDocument()
  })

  it('calls logout when the logout button is clicked', async () => {
    render(<PlatformAdminPage />)
    await screen.findByText(/机构A/)

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    expect(logout).toHaveBeenCalled()
  })
})
