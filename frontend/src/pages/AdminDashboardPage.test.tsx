import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminDashboardPage } from './AdminDashboardPage'
import * as institutionApi from '../api/institution'

const logout = vi.fn()
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ logout }),
}))

vi.mock('../api/institution')

vi.mock('./AdminTeachersModule', () => ({
  AdminTeachersModule: () => <div>TeachersModuleStub</div>,
}))
vi.mock('./AdminClassesModule', () => ({
  AdminClassesModule: () => <div>ClassesModuleStub</div>,
}))
vi.mock('./AdminTaskStatsModule', () => ({
  AdminTaskStatsModule: () => <div>TaskStatsModuleStub</div>,
}))
vi.mock('./AdminBillingModule', () => ({
  AdminBillingModule: () => <div>BillingModuleStub</div>,
}))
vi.mock('./AdminSettingsModule', () => ({
  AdminSettingsModule: () => <div>SettingsModuleStub</div>,
}))
vi.mock('./AdminPricingModule', () => ({
  AdminPricingModule: () => <div>PricingModuleStub</div>,
}))

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(institutionApi.fetchInstitutionSettings).mockResolvedValue({
      id: 1,
      name: '阳光托管班',
      logoUrl: null,
      custodyEnabled: true,
      offCampusEnabled: true,
    })
  })

  it('renders the header and defaults to the teachers module', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(await screen.findByText('阳光托管班')).toBeInTheDocument()
    expect(screen.getByText('TeachersModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('ClassesModuleStub')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '教师列表' })).toHaveAttribute('aria-current', 'page')
  })

  it('falls back to the default title while institution settings are loading', () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    expect(screen.getByText('机构管理')).toBeInTheDocument()
  })

  it('switches to the settings module when its sidebar item is clicked', async () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)
    await waitFor(() => expect(institutionApi.fetchInstitutionSettings).toHaveBeenCalled())

    fireEvent.click(screen.getByRole('button', { name: '基础配置' }))

    expect(screen.getByText('SettingsModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('TeachersModuleStub')).not.toBeInTheDocument()
  })

  it('calls onBack when the back button is clicked', () => {
    const onBack = vi.fn()
    render(<AdminDashboardPage onBack={onBack} />)

    fireEvent.click(screen.getByRole('button', { name: '返回看板' }))

    expect(onBack).toHaveBeenCalled()
  })

  it('calls logout when the logout button is clicked', () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }))

    expect(logout).toHaveBeenCalled()
  })

  it('switches to the classes module when its sidebar item is clicked', () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '托管班级' }))

    expect(screen.getByText('ClassesModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('TeachersModuleStub')).not.toBeInTheDocument()
  })

  it('switches to the task stats module when its sidebar item is clicked', () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '任务完成情况' }))

    expect(screen.getByText('TaskStatsModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('TeachersModuleStub')).not.toBeInTheDocument()
  })

  it('switches to the billing module when its sidebar item is clicked', () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '账单管理' }))

    expect(screen.getByText('BillingModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('TeachersModuleStub')).not.toBeInTheDocument()
  })

  it('switches to the pricing module when its sidebar item is clicked', () => {
    render(<AdminDashboardPage onBack={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '定价中心' }))

    expect(screen.getByText('PricingModuleStub')).toBeInTheDocument()
    expect(screen.queryByText('TeachersModuleStub')).not.toBeInTheDocument()
  })

  it('hides custody-only nav items when the institution has custody disabled', async () => {
    vi.mocked(institutionApi.fetchInstitutionSettings).mockResolvedValue({
      id: 1,
      name: '阳光托管班',
      logoUrl: null,
      custodyEnabled: false,
      offCampusEnabled: true,
    })
    render(<AdminDashboardPage onBack={vi.fn()} />)

    await screen.findByText('阳光托管班')
    expect(screen.queryByRole('button', { name: '托管班级' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '任务完成情况' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '账单管理' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '定价中心' })).toBeInTheDocument()
  })
})
