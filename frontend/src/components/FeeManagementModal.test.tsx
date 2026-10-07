import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { FeeManagementModal } from './FeeManagementModal'
import * as billingApi from '../api/billing'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'

vi.mock('../api/billing')
vi.mock('../api/course')

const RATE: billingApi.ClassBillingRate = {
  id: 1,
  institutionId: 1,
  classRoomId: 20,
  tuitionRatePerMonth: 300,
  mealRatePerDay: 10,
  updatedAt: '2026-09-01T00:00:00Z',
}

const COURSE_CONSUMPTION: courseApi.CourseConsumptionSummaryRow[] = [
  {
    courseId: 1,
    courseName: '数学课',
    pricePerLesson: 50,
    lessonCount: 4,
    amount: 200,
    coveredByBalanceCount: 0,
  },
]

function setup(classRoomId: number | null = 20) {
  const onClose = vi.fn()
  const onSaved = vi.fn()
  render(
    <FeeManagementModal
      studentId={100}
      studentName="小明"
      classRoomId={classRoomId}
      className={classRoomId === null ? '—' : '一班'}
      month="2026-09"
      onClose={onClose}
      onSaved={onSaved}
    />,
  )
  return { onClose, onSaved }
}

describe('FeeManagementModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(billingApi.fetchClassBillingRate).mockResolvedValue(RATE)
    vi.mocked(courseApi.fetchStudentCourseConsumption).mockResolvedValue(COURSE_CONSUMPTION)
  })

  it('prefills the tuition amount from the class billing rate', async () => {
    setup()

    expect(await screen.findByLabelText('本月托管费金额')).toHaveValue('300')
    expect(courseApi.fetchStudentCourseConsumption).toHaveBeenCalledWith(100, '2026-09')
  })

  it('shows read-only course consumption for the month with computed amount', async () => {
    setup()

    expect(await screen.findByText('数学课')).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
    expect(screen.getByText('4 次')).toBeInTheDocument()
    expect(screen.getByText('¥200.00')).toBeInTheDocument()
  })

  it('shows an unconfigured-price placeholder when a course has no price set', async () => {
    vi.mocked(courseApi.fetchStudentCourseConsumption).mockResolvedValue([
      { courseId: 2, courseName: '未定价课', pricePerLesson: null, lessonCount: 2, amount: 0, coveredByBalanceCount: 0 },
    ])
    setup()

    expect(await screen.findByText('未定价课')).toBeInTheDocument()
    expect(screen.getByText('未配置')).toBeInTheDocument()
  })

  it('shows the coverage breakdown when some consumption was covered by prepaid balance', async () => {
    vi.mocked(courseApi.fetchStudentCourseConsumption).mockResolvedValue([
      { courseId: 1, courseName: '围棋课', pricePerLesson: 50, lessonCount: 1, amount: 50, coveredByBalanceCount: 2 },
    ])
    setup(null)

    expect(await screen.findByText('围棋课')).toBeInTheDocument()
    expect(screen.getByText('3 次')).toBeInTheDocument()
    expect(screen.getByText('（其中 2 次已用预充值抵扣）')).toBeInTheDocument()
    // 单价和应收金额在这组测试数据里恰好都是 ¥50.00（1 次未覆盖 × 单价 50），两格都应渲染。
    expect(screen.getAllByText('¥50.00')).toHaveLength(2)
  })

  it('does not show the tuition section or query the class billing rate for a pure off-campus student', async () => {
    setup(null)
    await screen.findByText('数学课')

    expect(screen.queryByLabelText('本月托管费金额')).not.toBeInTheDocument()
    expect(screen.queryByText('本月托管费金额')).not.toBeInTheDocument()
    expect(billingApi.fetchClassBillingRate).not.toHaveBeenCalled()
  })

  it('generates the bill for a pure off-campus student without a tuition override', async () => {
    vi.mocked(billingApi.generateStudentBill).mockResolvedValue({} as billingApi.MonthlyBill)
    const { onSaved } = setup(null)
    await screen.findByText('数学课')

    fireEvent.click(screen.getByRole('button', { name: '保存并生成账单' }))

    await waitFor(() => expect(billingApi.generateStudentBill).toHaveBeenCalledWith(100, '2026-09', undefined))
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
  })

  it('shows an empty-state message when there is no course consumption this month', async () => {
    vi.mocked(courseApi.fetchStudentCourseConsumption).mockResolvedValue([])
    setup()

    expect(await screen.findByText('本月暂无课外课消课记录')).toBeInTheDocument()
  })

  it('has no add/edit controls for course consumption (read-only)', async () => {
    setup()
    await screen.findByText('数学课')

    expect(screen.queryByPlaceholderText('课程名称')).not.toBeInTheDocument()
    expect(screen.queryByPlaceholderText('每节课价格')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '删除课外费数学课' })).not.toBeInTheDocument()
  })

  it('closes without saving when cancel is clicked', async () => {
    const { onClose } = setup()
    await screen.findByLabelText('本月托管费金额')

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onClose).toHaveBeenCalled()
    expect(billingApi.generateStudentBill).not.toHaveBeenCalled()
  })

  it('saves the tuition override and generates the bill', async () => {
    vi.mocked(billingApi.generateStudentBill).mockResolvedValue({} as billingApi.MonthlyBill)
    const { onClose, onSaved } = setup()
    await screen.findByLabelText('本月托管费金额')

    fireEvent.change(screen.getByLabelText('本月托管费金额'), { target: { value: '350' } })
    fireEvent.click(screen.getByRole('button', { name: '保存并生成账单' }))

    await waitFor(() => expect(billingApi.generateStudentBill).toHaveBeenCalledWith(100, '2026-09', 350))
    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(onClose).toHaveBeenCalled()
  })

  it('rejects an invalid tuition amount without saving', async () => {
    setup()
    await screen.findByLabelText('本月托管费金额')

    fireEvent.change(screen.getByLabelText('本月托管费金额'), { target: { value: 'abc' } })
    fireEvent.click(screen.getByRole('button', { name: '保存并生成账单' }))

    expect(await screen.findByText('请输入有效的托管费金额')).toBeInTheDocument()
    expect(billingApi.generateStudentBill).not.toHaveBeenCalled()
  })

  it('shows a helpful message when the class has no billing rate configured', async () => {
    vi.mocked(billingApi.generateStudentBill).mockRejectedValue(new ApiError(400, '未配置'))
    setup()
    await screen.findByLabelText('本月托管费金额')

    fireEvent.click(screen.getByRole('button', { name: '保存并生成账单' }))

    expect(await screen.findByText('班级未配置计费单价，请先配置')).toBeInTheDocument()
  })
})
