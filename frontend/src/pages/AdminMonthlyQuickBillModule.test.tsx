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
  {
    studentId: 100,
    name: '小明',
    mealDays: 0,
    leaveDays: 0,
    courses: [{ courseId: 200, name: '羽毛球课', consumptionCount: 0 }],
  },
  { studentId: 101, name: '小红', mealDays: 0, leaveDays: 0, courses: [] },
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

  it('prefills already-saved numbers and locks a course that already has consumption recorded', async () => {
    vi.mocked(quickBillApi.fetchQuickBillStudents).mockResolvedValue([
      {
        studentId: 100,
        name: '小明',
        mealDays: 5,
        leaveDays: 1,
        courses: [{ courseId: 200, name: '羽毛球课', consumptionCount: 3 }],
      },
    ])
    render(<AdminMonthlyQuickBillModule />)
    await screen.findByText('小明')

    expect(screen.getByDisplayValue('5')).toBeInTheDocument()
    expect(screen.getByDisplayValue('1')).toBeInTheDocument()
    // 已经有消课记录的课外课不该再是输入框，是一段锁死的说明文字
    expect(screen.getByText('3节（已记录）')).toBeInTheDocument()
    expect(screen.queryByRole('spinbutton', { name: /羽毛球/ })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /保存.*本月数据/ }))

    // 哪怕羽毛球课之前存的是3节，提交也不该带上它——带上会被后端拒绝，还会连累用餐/
    // 请假这两项本来能成功的一起失败。
    await waitFor(() =>
      expect(quickBillApi.submitQuickBill).toHaveBeenCalledWith(100, {
        month: expect.any(String),
        mealDays: 5,
        leaveDays: 1,
        courseConsumptions: [],
      }),
    )
  })

  it('saves data only for students with non-zero input, without generating a bill', async () => {
    render(<AdminMonthlyQuickBillModule />)
    await screen.findByText('小明')

    const mealInputs = screen.getAllByDisplayValue('')
    // 第一行（小明）：用餐天数 / 请假天数 / 课外课节数 分别是前三个空输入框
    fireEvent.change(mealInputs[0], { target: { value: '3' } })
    fireEvent.change(mealInputs[2], { target: { value: '4' } })

    fireEvent.click(screen.getByRole('button', { name: /保存.*本月数据/ }))

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
    // 保存数据不应该连带生成账单——这是机构自己的决定
    expect(billingApi.generateClassBills).not.toHaveBeenCalled()
  })

  it('generates the bill independently of saving data, using whatever data already exists', async () => {
    render(<AdminMonthlyQuickBillModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: /生成.*本月账单/ }))

    await waitFor(() => expect(billingApi.generateClassBills).toHaveBeenCalledWith(10, expect.any(String)))
    expect(quickBillApi.submitQuickBill).not.toHaveBeenCalled()
    expect(await screen.findByText('¥530.00')).toBeInTheDocument()
  })

  it('shows an error message when saving data fails', async () => {
    vi.mocked(quickBillApi.submitQuickBill).mockRejectedValue(new Error('boom'))
    render(<AdminMonthlyQuickBillModule />)
    await screen.findByText('小明')

    const mealInputs = screen.getAllByDisplayValue('')
    fireEvent.change(mealInputs[0], { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: /保存.*本月数据/ }))

    expect(await screen.findByText(/保存数据失败/)).toBeInTheDocument()
  })

  it('shows an error message when generating the bill fails', async () => {
    vi.mocked(billingApi.generateClassBills).mockRejectedValue(new Error('boom'))
    render(<AdminMonthlyQuickBillModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: /生成.*本月账单/ }))

    expect(await screen.findByText(/生成账单失败/)).toBeInTheDocument()
  })
})
