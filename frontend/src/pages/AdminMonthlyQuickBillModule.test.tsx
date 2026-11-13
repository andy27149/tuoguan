import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminMonthlyQuickBillModule } from './AdminMonthlyQuickBillModule'
import * as adminApi from '../api/admin'
import * as billingApi from '../api/billing'
import * as quickBillApi from '../api/monthlyQuickBill'

vi.mock('../api/admin')
vi.mock('../api/billing')
vi.mock('../api/monthlyQuickBill')

const CLASSES: adminApi.AdminClassRoom[] = [
  { id: 10, name: '一班', teacherId: 1, teacherName: '王老师', teacherPhone: '13900000001' },
]

const STUDENTS: quickBillApi.QuickBillStudent[] = [
  { studentId: 100, name: '小明', courses: [{ courseId: 200, name: '羽毛球课' }] },
  { studentId: 101, name: '小红', courses: [] },
]

const BILL: billingApi.MonthlyBill = {
  id: 1,
  institutionId: 1,
  studentId: 100,
  teachingUnitId: 10,
  yearMonth: '2030-01',
  totalWeekdays: 22,
  leaveDays: 0,
  attendanceDays: 22,
  tuitionAmount: 500,
  mealAmount: 30,
  extraFeeTotal: 0,
  totalAmount: 530,
  isPaid: false,
  paymentMethod: null,
  generatedAt: '2030-01-01T00:00:00Z',
  extraFeeLines: [],
  mealRecordDates: ['2030-01-02', '2030-01-03', '2030-01-04'],
  leaveLines: [],
}

describe('AdminMonthlyQuickBillModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue(CLASSES)
    vi.mocked(quickBillApi.fetchQuickBillStudents).mockResolvedValue(STUDENTS)
    vi.mocked(quickBillApi.submitQuickBill).mockResolvedValue(undefined)
    vi.mocked(billingApi.generateClassBills).mockResolvedValue([BILL])
  })

  it('loads the class and renders students with their course columns', async () => {
    render(<AdminMonthlyQuickBillModule />)

    expect(await screen.findByText('小明')).toBeInTheDocument()
    expect(screen.getByText('小红')).toBeInTheDocument()
    expect(screen.getByText('羽毛球课（节数）')).toBeInTheDocument()
    // 小红没报课外课，那一格应该是占位符而不是输入框
    const row = screen.getByText('小红').closest('tr') as HTMLElement
    expect(row.textContent).toContain('—')
  })

  it('submits only students with non-zero input, then generates the class bill', async () => {
    render(<AdminMonthlyQuickBillModule />)
    await screen.findByText('小明')

    const mealInputs = screen.getAllByDisplayValue('')
    // 第一行（小明）：用餐天数 / 请假天数 / 课外课节数 分别是前三个空输入框
    fireEvent.change(mealInputs[0], { target: { value: '3' } })
    fireEvent.change(mealInputs[2], { target: { value: '4' } })

    fireEvent.click(screen.getByRole('button', { name: /生成.*本月账单/ }))

    await waitFor(() =>
      expect(quickBillApi.submitQuickBill).toHaveBeenCalledWith(100, {
        month: expect.any(String),
        mealDays: 3,
        leaveDays: 0,
        courseConsumptions: [{ courseId: 200, count: 4 }],
      }),
    )
    // 小红全部留空，不应该发起提交
    expect(quickBillApi.submitQuickBill).toHaveBeenCalledTimes(1)
    expect(billingApi.generateClassBills).toHaveBeenCalledWith(10, expect.any(String))
    expect(await screen.findByText('¥530.00')).toBeInTheDocument()
  })

  it('shows an error message when submission fails', async () => {
    vi.mocked(quickBillApi.submitQuickBill).mockRejectedValue(new Error('boom'))
    render(<AdminMonthlyQuickBillModule />)
    await screen.findByText('小明')

    const mealInputs = screen.getAllByDisplayValue('')
    fireEvent.change(mealInputs[0], { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: /生成.*本月账单/ }))

    expect(await screen.findByText(/生成账单失败/)).toBeInTheDocument()
  })
})
