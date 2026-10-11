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
vi.mock('../components/RenewalModal', () => ({
  RenewalModal: (props: { institutionId: number; institutionName: string; onClose: () => void }) => (
    <div>
      RenewalModalStub:{props.institutionId}:{props.institutionName}
      <button type="button" onClick={props.onClose}>
        关闭续费弹窗
      </button>
    </div>
  ),
}))
vi.mock('../components/EditInstitutionContactModal', () => ({
  EditInstitutionContactModal: (props: { institutionId: number; institutionName: string; onClose: () => void }) => (
    <div>
      EditContactModalStub:{props.institutionId}:{props.institutionName}
      <button type="button" onClick={props.onClose}>
        关闭联系信息弹窗
      </button>
    </div>
  ),
}))

const INSTITUTIONS: platformApi.PlatformInstitution[] = [
  {
    id: 1,
    name: '机构A',
    createdAt: '2026-08-01T00:00:00Z',
    teacherCount: 2,
    studentCount: 15,
    classCount: 3,
    contactName: '张校长',
    contactPhone: '13900000001',
    address: '示例路1号',
    validUntil: '2026-12-01',
    currentPlan: 'TRIAL',
    status: 'ACTIVE',
  },
]

function fillCreateForm() {
  fireEvent.change(screen.getByPlaceholderText('机构名称'), { target: { value: '机构B' } })
  fireEvent.change(screen.getByPlaceholderText('管理员手机号'), { target: { value: '13700000002' } })
  fireEvent.change(screen.getByPlaceholderText('管理员初始密码'), { target: { value: 'initial123' } })
  fireEvent.change(screen.getByPlaceholderText('联系电话'), { target: { value: '13900000002' } })
  fireEvent.change(screen.getByLabelText('试用到期日'), { target: { value: '2026-12-31' } })
}

describe('PlatformAdminPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(platformApi.fetchInstitutions).mockResolvedValue(INSTITUTIONS)
  })

  it('lists institutions with scale stats loaded on mount', async () => {
    render(<PlatformAdminPage />)

    expect(await screen.findByText('机构A')).toBeInTheDocument()
    expect(screen.getByText('15')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
    expect(screen.getByText(/张校长/)).toBeInTheDocument()
    expect(screen.getByText(/13900000001/)).toBeInTheDocument()
  })

  it('shows an ACTIVE status badge with the valid-until date', async () => {
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    expect(screen.getByText('有效至 2026-12-01')).toBeInTheDocument()
  })

  it('shows a NONE status badge when validUntil is not set', async () => {
    vi.mocked(platformApi.fetchInstitutions).mockResolvedValue([
      { ...INSTITUTIONS[0], validUntil: null, status: 'NONE', currentPlan: null },
    ])
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    expect(screen.getByText('未设置')).toBeInTheDocument()
  })

  it('shows an EXPIRED status badge with days overdue', async () => {
    const yesterday = new Date()
    yesterday.setDate(yesterday.getDate() - 3)
    vi.mocked(platformApi.fetchInstitutions).mockResolvedValue([
      { ...INSTITUTIONS[0], validUntil: yesterday.toISOString().slice(0, 10), status: 'EXPIRED' },
    ])
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    expect(screen.getByText('已过期 3 天')).toBeInTheDocument()
  })

  it('shows an EXPIRING_SOON status badge with days remaining', async () => {
    const soon = new Date()
    soon.setDate(soon.getDate() + 5)
    vi.mocked(platformApi.fetchInstitutions).mockResolvedValue([
      { ...INSTITUTIONS[0], validUntil: soon.toISOString().slice(0, 10), status: 'EXPIRING_SOON' },
    ])
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    expect(screen.getByText('5 天后到期')).toBeInTheDocument()
  })

  it('creates an institution with contact info and trial date, and shows the one-time password banner', async () => {
    const created = { institutionId: 2, institutionName: '机构B', adminPhone: '13700000002' }
    vi.mocked(platformApi.createInstitution).mockResolvedValue(created)
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    fillCreateForm()
    fireEvent.click(screen.getByRole('button', { name: '开通' }))

    await waitFor(() =>
      expect(platformApi.createInstitution).toHaveBeenCalledWith(
        '机构B',
        '13700000002',
        'initial123',
        '',
        '13900000002',
        '',
        '2026-12-31',
      ),
    )
    expect(await screen.findByText(/初始密码 initial123/)).toBeInTheDocument()
  })

  it('disables submit until the required fields (including contact phone and trial date) are filled', async () => {
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    expect(screen.getByRole('button', { name: '开通' })).toBeDisabled()
    fireEvent.change(screen.getByPlaceholderText('机构名称'), { target: { value: '机构B' } })
    fireEvent.change(screen.getByPlaceholderText('管理员手机号'), { target: { value: '13700000002' } })
    fireEvent.change(screen.getByPlaceholderText('管理员初始密码'), { target: { value: 'initial123' } })
    expect(screen.getByRole('button', { name: '开通' })).toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('联系电话'), { target: { value: '13900000002' } })
    expect(screen.getByRole('button', { name: '开通' })).not.toBeDisabled()
  })

  it('shows a duplicate-phone message on 409', async () => {
    vi.mocked(platformApi.createInstitution).mockRejectedValue(new ApiError(409, '冲突'))
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    fillCreateForm()
    fireEvent.click(screen.getByRole('button', { name: '开通' }))

    expect(await screen.findByText('该手机号已注册')).toBeInTheDocument()
  })

  it('opens the renewal modal for the clicked institution and closes it', async () => {
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    fireEvent.click(screen.getByRole('button', { name: '续费' }))

    expect(await screen.findByText('RenewalModalStub:1:机构A')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '关闭续费弹窗' }))
    expect(screen.queryByText('RenewalModalStub:1:机构A')).not.toBeInTheDocument()
  })

  it('opens the edit-contact modal for the clicked institution and closes it', async () => {
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    fireEvent.click(screen.getByRole('button', { name: '编辑联系信息' }))

    expect(await screen.findByText('EditContactModalStub:1:机构A')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '关闭联系信息弹窗' }))
    expect(screen.queryByText('EditContactModalStub:1:机构A')).not.toBeInTheDocument()
  })

  it('calls logout when the logout button is clicked', async () => {
    render(<PlatformAdminPage />)
    await screen.findByText('机构A')

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    expect(logout).toHaveBeenCalled()
  })
})
