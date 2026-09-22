import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { FeeManagementModal } from './FeeManagementModal'
import * as billingApi from '../api/billing'
import { ApiError } from '../api/client'

vi.mock('../api/billing')

const RATE: billingApi.ClassBillingRate = {
  id: 1,
  institutionId: 1,
  classRoomId: 20,
  tuitionRatePerMonth: 300,
  mealRatePerDay: 10,
  updatedAt: '2026-09-01T00:00:00Z',
}

const LEAVE_RECORDS: billingApi.StudentLeaveRecord[] = [
  {
    id: 1,
    institutionId: 1,
    studentId: 100,
    classRoomId: 20,
    leaveDate: '2026-09-05',
    reason: '感冒',
    createdAt: '2026-09-05T00:00:00Z',
  },
]

const EXTRA_FEES: billingApi.StudentExtraFeeRow[] = [
  {
    id: 1,
    name: '数学课',
    pricePerLesson: 50,
    lessonCount: 4,
    amount: 200,
  },
]

function setup() {
  const onClose = vi.fn()
  const onSaved = vi.fn()
  render(
    <FeeManagementModal
      studentId={100}
      studentName="小明"
      classRoomId={20}
      className="一班"
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
    vi.mocked(billingApi.fetchStudentLeaveRecords).mockResolvedValue(LEAVE_RECORDS)
    vi.mocked(billingApi.fetchStudentExtraFees).mockResolvedValue(EXTRA_FEES)
  })

  it('prefills the tuition amount from the class billing rate', async () => {
    setup()

    expect(await screen.findByLabelText('本月托管费金额')).toHaveValue('300')
    expect(billingApi.fetchStudentLeaveRecords).toHaveBeenCalledWith(100, '2026-09')
    expect(billingApi.fetchStudentExtraFees).toHaveBeenCalledWith(100, '2026-09')
  })

  it('shows existing leave records and adds a new one', async () => {
    vi.mocked(billingApi.registerStudentLeaveRange).mockResolvedValue([])
    vi.mocked(billingApi.fetchStudentLeaveRecords)
      .mockResolvedValueOnce(LEAVE_RECORDS)
      .mockResolvedValueOnce([
        ...LEAVE_RECORDS,
        {
          id: 2,
          institutionId: 1,
          studentId: 100,
          classRoomId: 20,
          leaveDate: '2026-09-10',
          reason: null,
          createdAt: '2026-09-10T00:00:00Z',
        },
      ])
    setup()
    expect(await screen.findByText('2026-09-05')).toBeInTheDocument()

    const [startInput, endInput] = (screen.getAllByDisplayValue('') as HTMLInputElement[]).filter(
      (el) => el.type === 'date',
    )
    fireEvent.change(startInput, { target: { value: '2026-09-10' } })
    fireEvent.change(endInput, { target: { value: '2026-09-10' } })
    fireEvent.click(screen.getByRole('button', { name: '登记请假' }))

    await waitFor(() =>
      expect(billingApi.registerStudentLeaveRange).toHaveBeenCalledWith(100, '2026-09-10', '2026-09-10', undefined),
    )
    expect(await screen.findByText('2026-09-10')).toBeInTheDocument()
  })

  it('shows a friendly error when leave dates are invalid', async () => {
    vi.mocked(billingApi.registerStudentLeaveRange).mockRejectedValue(new ApiError(400, '结束日期不能早于开始日期'))
    setup()
    await screen.findByText('2026-09-05')

    const dateInputs = screen.getAllByDisplayValue('') as HTMLInputElement[]
    const [startInput, endInput] = dateInputs.filter((el) => el.type === 'date')
    fireEvent.change(startInput, { target: { value: '2026-09-10' } })
    fireEvent.change(endInput, { target: { value: '2026-09-01' } })
    fireEvent.click(screen.getByRole('button', { name: '登记请假' }))

    expect(await screen.findByText('结束日期不能早于开始日期')).toBeInTheDocument()
  })

  it('cancels an existing leave record', async () => {
    vi.mocked(billingApi.cancelStudentLeave).mockResolvedValue(undefined)
    setup()
    await screen.findByText('2026-09-05')

    fireEvent.click(screen.getByRole('button', { name: '取消请假2026-09-05' }))

    await waitFor(() => expect(billingApi.cancelStudentLeave).toHaveBeenCalledWith(100, '2026-09-05'))
    await waitFor(() => expect(screen.queryByText('2026-09-05')).not.toBeInTheDocument())
  })

  it('shows existing extra fees with computed amount and adds a new one', async () => {
    const created: billingApi.StudentExtraFee = {
      id: 2,
      institutionId: 1,
      studentId: 100,
      name: '英语课',
      pricePerLesson: 40,
      createdAt: '2026-09-01T00:00:00Z',
    }
    const newRow: billingApi.StudentExtraFeeRow = {
      id: 2,
      name: '英语课',
      pricePerLesson: 40,
      lessonCount: 0,
      amount: 0,
    }
    vi.mocked(billingApi.addStudentExtraFee).mockResolvedValue(created)
    vi.mocked(billingApi.fetchStudentExtraFees)
      .mockResolvedValueOnce(EXTRA_FEES)
      .mockResolvedValueOnce([...EXTRA_FEES, newRow])
    setup()
    expect(await screen.findByText('数学课')).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
    expect(screen.getByText('¥200.00')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('课程名称'), { target: { value: '英语课' } })
    fireEvent.change(screen.getByPlaceholderText('每节课价格'), { target: { value: '40' } })
    fireEvent.click(screen.getByRole('button', { name: '添加' }))

    await waitFor(() => expect(billingApi.addStudentExtraFee).toHaveBeenCalledWith(100, '英语课', 40))
    expect(await screen.findByText('英语课')).toBeInTheDocument()
  })

  it('shows a duplicate-name error for extra fees on 409', async () => {
    vi.mocked(billingApi.addStudentExtraFee).mockRejectedValue(new ApiError(409, '冲突'))
    setup()
    await screen.findByText('数学课')

    fireEvent.change(screen.getByPlaceholderText('课程名称'), { target: { value: '数学课' } })
    fireEvent.change(screen.getByPlaceholderText('每节课价格'), { target: { value: '50' } })
    fireEvent.click(screen.getByRole('button', { name: '添加' }))

    expect(await screen.findByText('该课外费项目已存在')).toBeInTheDocument()
  })

  it('deletes an extra fee', async () => {
    vi.mocked(billingApi.deleteStudentExtraFee).mockResolvedValue(undefined)
    setup()
    await screen.findByText('数学课')

    fireEvent.click(screen.getByRole('button', { name: '删除课外费数学课' }))

    await waitFor(() => expect(billingApi.deleteStudentExtraFee).toHaveBeenCalledWith(100, 1))
    await waitFor(() => expect(screen.queryByText('数学课')).not.toBeInTheDocument())
  })

  it('edits and saves the lesson count for an extra fee, recomputing the amount live', async () => {
    const updated: billingApi.StudentExtraFeeRow = {
      id: 1,
      name: '数学课',
      pricePerLesson: 50,
      lessonCount: 6,
      amount: 300,
    }
    vi.mocked(billingApi.setExtraFeeLessonCount).mockResolvedValue(updated)
    setup()
    await screen.findByText('数学课')

    const countInput = screen.getByLabelText('数学课上课数')
    fireEvent.change(countInput, { target: { value: '6' } })
    expect(screen.getByText('¥300.00')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(billingApi.setExtraFeeLessonCount).toHaveBeenCalledWith(100, 1, '2026-09', 6),
    )
  })

  it('shows a validation error for an invalid lesson count', async () => {
    setup()
    await screen.findByText('数学课')

    fireEvent.change(screen.getByLabelText('数学课上课数'), { target: { value: 'abc' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('请输入有效的上课数')).toBeInTheDocument()
    expect(billingApi.setExtraFeeLessonCount).not.toHaveBeenCalled()
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
