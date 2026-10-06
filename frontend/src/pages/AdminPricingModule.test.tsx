import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminPricingModule } from './AdminPricingModule'
import * as billingApi from '../api/billing'
import * as unitApi from '../api/unit'

vi.mock('../api/billing')
vi.mock('../api/unit')

const RATES: billingApi.ClassBillingRateRow[] = [
  { classRoomId: 20, className: '一班', tuitionRatePerMonth: 300, mealRatePerDay: 10 },
  { classRoomId: 21, className: '二班', tuitionRatePerMonth: null, mealRatePerDay: null },
]

const COURSES: unitApi.TeachingUnit[] = [
  {
    id: 30,
    name: '书法课',
    billingMode: 'LESSON_COUNT',
    teacherId: 1,
    teacherName: '王老师',
    teacherPhone: '13900000001',
    lessonDurationMinutes: 60,
    pricePerLesson: 50,
    active: true,
  },
  {
    id: 31,
    name: '数学课',
    billingMode: 'LESSON_COUNT',
    teacherId: 2,
    teacherName: '李老师',
    teacherPhone: '13900000002',
    lessonDurationMinutes: 45,
    pricePerLesson: null,
    active: true,
  },
]

describe('AdminPricingModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(billingApi.fetchAllClassBillingRates).mockResolvedValue(RATES)
    vi.mocked(unitApi.fetchTeachingUnits).mockResolvedValue(COURSES)
  })

  it('shows the pricing card when custody is enabled', async () => {
    render(<AdminPricingModule custodyEnabled offCampusEnabled={false} />)

    expect(await screen.findByText('托管班级定价')).toBeInTheDocument()
    expect(screen.getByText('一班')).toBeInTheDocument()
  })

  it('hides the custody pricing card when custody is disabled', async () => {
    render(<AdminPricingModule custodyEnabled={false} offCampusEnabled={false} />)

    expect(screen.queryByText('托管班级定价')).not.toBeInTheDocument()
    expect(billingApi.fetchAllClassBillingRates).not.toHaveBeenCalled()
  })

  it('bulk-sets the billing rate for all classes', async () => {
    const updatedRates = RATES.map((r) => ({ ...r, tuitionRatePerMonth: 400, mealRatePerDay: 15 }))
    vi.mocked(billingApi.bulkSetClassBillingRate).mockResolvedValue(updatedRates)
    render(<AdminPricingModule custodyEnabled offCampusEnabled={false} />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByLabelText('托管费（元/月）'), { target: { value: '400' } })
    fireEvent.change(screen.getByLabelText('餐费（元/天）'), { target: { value: '15' } })
    fireEvent.click(screen.getByRole('button', { name: '一键设置所有班级' }))
    expect(billingApi.bulkSetClassBillingRate).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByRole('button', { name: '确认设置' }))

    await waitFor(() => expect(billingApi.bulkSetClassBillingRate).toHaveBeenCalledWith(400, 15))
    expect(await screen.findAllByText('¥400.00')).toHaveLength(2)
  })

  it('does nothing when the bulk-set confirmation is cancelled', async () => {
    render(<AdminPricingModule custodyEnabled offCampusEnabled={false} />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByLabelText('托管费（元/月）'), { target: { value: '400' } })
    fireEvent.change(screen.getByLabelText('餐费（元/天）'), { target: { value: '15' } })
    fireEvent.click(screen.getByRole('button', { name: '一键设置所有班级' }))
    fireEvent.click(await screen.findByRole('button', { name: '取消' }))

    expect(billingApi.bulkSetClassBillingRate).not.toHaveBeenCalled()
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
    render(<AdminPricingModule custodyEnabled offCampusEnabled={false} />)
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
    render(<AdminPricingModule custodyEnabled offCampusEnabled={false} />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByLabelText('托管费（元/月）'), { target: { value: 'abc' } })
    fireEvent.change(screen.getByLabelText('餐费（元/天）'), { target: { value: '15' } })
    fireEvent.click(screen.getByRole('button', { name: '一键设置所有班级' }))

    expect(await screen.findByText('请输入有效的单价')).toBeInTheDocument()
    expect(billingApi.bulkSetClassBillingRate).not.toHaveBeenCalled()
  })

  it('shows the off-campus pricing card listing all courses when off-campus is enabled', async () => {
    render(<AdminPricingModule custodyEnabled={false} offCampusEnabled />)

    expect(await screen.findByText('课外课定价')).toBeInTheDocument()
    expect(screen.getByText('书法课')).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
    expect(screen.getByText('未配置')).toBeInTheDocument()
    expect(unitApi.fetchTeachingUnits).toHaveBeenCalledWith('LESSON_COUNT')
  })

  it('hides the off-campus pricing card when off-campus is disabled', async () => {
    render(<AdminPricingModule custodyEnabled={false} offCampusEnabled={false} />)

    expect(screen.queryByText('课外课定价')).not.toBeInTheDocument()
    expect(unitApi.fetchTeachingUnits).not.toHaveBeenCalled()
  })

  it('edits a course price from the pricing center', async () => {
    vi.mocked(unitApi.updateTeachingUnit).mockResolvedValue({ ...COURSES[1], pricePerLesson: 80 })
    render(<AdminPricingModule custodyEnabled={false} offCampusEnabled />)
    await screen.findByText('数学课')

    const row = screen.getByText('数学课').closest('tr') as HTMLElement
    fireEvent.click(within(row).getByRole('button', { name: '设置单价' }))
    fireEvent.change(screen.getByLabelText('数学课单价'), { target: { value: '80' } })
    fireEvent.click(within(row).getByRole('button', { name: '保存' }))

    await waitFor(() => expect(unitApi.updateTeachingUnit).toHaveBeenCalledWith(31, { pricePerLesson: 80 }))
  })

  it('shows a validation error for invalid course price input', async () => {
    render(<AdminPricingModule custodyEnabled={false} offCampusEnabled />)
    await screen.findByText('数学课')

    const row = screen.getByText('数学课').closest('tr') as HTMLElement
    fireEvent.click(within(row).getByRole('button', { name: '设置单价' }))
    fireEvent.change(screen.getByLabelText('数学课单价'), { target: { value: 'abc' } })
    fireEvent.click(within(row).getByRole('button', { name: '保存' }))

    expect(await screen.findByText('请输入有效的单价')).toBeInTheDocument()
    expect(unitApi.updateTeachingUnit).not.toHaveBeenCalled()
  })

  it('explains that "未配置" does not mean there is no billing history', async () => {
    render(<AdminPricingModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')

    expect(screen.getByText(/该班级还没有默认单价/)).toBeInTheDocument()
    expect(screen.getByText(/这门课没有历史账单/)).toBeInTheDocument()
  })
})
