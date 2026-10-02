import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminPricingModule } from './AdminPricingModule'
import * as billingApi from '../api/billing'

vi.mock('../api/billing')

const RATES: billingApi.ClassBillingRateRow[] = [
  { classRoomId: 20, className: '一班', tuitionRatePerMonth: 300, mealRatePerDay: 10 },
  { classRoomId: 21, className: '二班', tuitionRatePerMonth: null, mealRatePerDay: null },
]

describe('AdminPricingModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(billingApi.fetchAllClassBillingRates).mockResolvedValue(RATES)
  })

  it('shows the pricing card when custody is enabled', async () => {
    render(<AdminPricingModule custodyEnabled />)

    expect(await screen.findByText('托管班级定价')).toBeInTheDocument()
    expect(screen.getByText('一班')).toBeInTheDocument()
  })

  it('hides the custody pricing card when custody is disabled', async () => {
    render(<AdminPricingModule custodyEnabled={false} />)

    expect(screen.queryByText('托管班级定价')).not.toBeInTheDocument()
    expect(billingApi.fetchAllClassBillingRates).not.toHaveBeenCalled()
  })

  it('bulk-sets the billing rate for all classes', async () => {
    const updatedRates = RATES.map((r) => ({ ...r, tuitionRatePerMonth: 400, mealRatePerDay: 15 }))
    vi.mocked(billingApi.bulkSetClassBillingRate).mockResolvedValue(updatedRates)
    render(<AdminPricingModule custodyEnabled />)
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
    render(<AdminPricingModule custodyEnabled />)
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
    render(<AdminPricingModule custodyEnabled />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByLabelText('托管费（元/月）'), { target: { value: 'abc' } })
    fireEvent.change(screen.getByLabelText('餐费（元/天）'), { target: { value: '15' } })
    fireEvent.click(screen.getByRole('button', { name: '一键设置所有班级' }))

    expect(await screen.findByText('请输入有效的单价')).toBeInTheDocument()
    expect(billingApi.bulkSetClassBillingRate).not.toHaveBeenCalled()
  })
})
