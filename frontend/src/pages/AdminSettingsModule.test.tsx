import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminSettingsModule } from './AdminSettingsModule'
import * as institutionApi from '../api/institution'
import * as billingApi from '../api/billing'

vi.mock('../api/institution')
vi.mock('../api/billing')

const INSTITUTION: institutionApi.InstitutionSettings = {
  id: 1,
  name: '阳光托管班',
  logoUrl: null,
}

const RATES: billingApi.ClassBillingRateRow[] = [
  { classRoomId: 20, className: '一班', tuitionRatePerMonth: 300, mealRatePerDay: 10 },
  { classRoomId: 21, className: '二班', tuitionRatePerMonth: null, mealRatePerDay: null },
]

describe('AdminSettingsModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(institutionApi.fetchInstitutionSettings).mockResolvedValue(INSTITUTION)
    vi.mocked(billingApi.fetchAllClassBillingRates).mockResolvedValue(RATES)
  })

  it('loads and displays the institution name and class billing rates', async () => {
    render(<AdminSettingsModule />)

    expect(await screen.findByDisplayValue('阳光托管班')).toBeInTheDocument()
    expect(screen.getByText('一班')).toBeInTheDocument()
    expect(screen.getByText('¥300.00')).toBeInTheDocument()
    expect(screen.getByText('¥10.00')).toBeInTheDocument()
    expect(screen.getAllByText('未配置')).toHaveLength(2)
  })

  it('saves the institution name', async () => {
    const updated = { ...INSTITUTION, name: '快乐托管班' }
    vi.mocked(institutionApi.updateInstitutionName).mockResolvedValue(updated)
    render(<AdminSettingsModule />)
    await screen.findByDisplayValue('阳光托管班')

    fireEvent.change(screen.getByDisplayValue('阳光托管班'), { target: { value: '快乐托管班' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(institutionApi.updateInstitutionName).toHaveBeenCalledWith('快乐托管班'))
  })

  it('uploads a logo', async () => {
    const updated = { ...INSTITUTION, logoUrl: 'https://example.com/logo.png' }
    vi.mocked(institutionApi.uploadInstitutionLogo).mockResolvedValue(updated)
    render(<AdminSettingsModule />)
    await screen.findByDisplayValue('阳光托管班')

    const file = new File(['x'], 'logo.png', { type: 'image/png' })
    fireEvent.change(screen.getByLabelText('上传托管班 Logo'), { target: { files: [file] } })

    await waitFor(() => expect(institutionApi.uploadInstitutionLogo).toHaveBeenCalledWith(file))
    expect(await screen.findByAltText('托管班 Logo')).toHaveAttribute('src', 'https://example.com/logo.png')
  })

  it('bulk-sets the billing rate for all classes', async () => {
    const updatedRates = RATES.map((r) => ({ ...r, tuitionRatePerMonth: 400, mealRatePerDay: 15 }))
    vi.mocked(billingApi.bulkSetClassBillingRate).mockResolvedValue(updatedRates)
    render(<AdminSettingsModule />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByLabelText('托管费（元/月）'), { target: { value: '400' } })
    fireEvent.change(screen.getByLabelText('餐费（元/天）'), { target: { value: '15' } })
    fireEvent.click(screen.getByRole('button', { name: '一键设置所有班级' }))

    await waitFor(() => expect(billingApi.bulkSetClassBillingRate).toHaveBeenCalledWith(400, 15))
    expect(await screen.findAllByText('¥400.00')).toHaveLength(2)
  })

  it('edits the billing rate for a single class', async () => {
    const saved = {
      id: 1,
      institutionId: 1,
      classRoomId: 21,
      tuitionRatePerMonth: 250,
      mealRatePerDay: 8,
      updatedAt: '2026-09-01T00:00:00Z',
    }
    vi.mocked(billingApi.upsertClassBillingRate).mockResolvedValue(saved)
    render(<AdminSettingsModule />)
    await screen.findByText('二班')

    fireEvent.click(screen.getAllByRole('button', { name: '单独设置' })[1])
    const row = screen.getByText('二班').closest('tr') as HTMLElement
    fireEvent.change(screen.getByLabelText('二班托管费'), { target: { value: '250' } })
    fireEvent.change(screen.getByLabelText('二班餐费'), { target: { value: '8' } })
    fireEvent.click(within(row).getByRole('button', { name: '保存' }))

    await waitFor(() => expect(billingApi.upsertClassBillingRate).toHaveBeenCalledWith(21, 250, 8))
    expect(await screen.findByText('¥250.00')).toBeInTheDocument()
  })

  it('shows a validation error for invalid bulk rate input', async () => {
    render(<AdminSettingsModule />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByLabelText('托管费（元/月）'), { target: { value: 'abc' } })
    fireEvent.change(screen.getByLabelText('餐费（元/天）'), { target: { value: '15' } })
    fireEvent.click(screen.getByRole('button', { name: '一键设置所有班级' }))

    expect(await screen.findByText('请输入有效的单价')).toBeInTheDocument()
    expect(billingApi.bulkSetClassBillingRate).not.toHaveBeenCalled()
  })
})
