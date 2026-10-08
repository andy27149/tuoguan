import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminBillingModule } from './AdminBillingModule'
import * as adminApi from '../api/admin'
import * as billingApi from '../api/billing'
import { currentMonthString } from '../kanban/date'

vi.mock('../api/admin')
vi.mock('../api/billing')
vi.mock('../components/FeeManagementModal', () => ({
  FeeManagementModal: (props: {
    studentId: number
    month: string
    onClose: () => void
    onSaved: () => void
  }) => (
    <div>
      FeeManagementModalStub:{props.studentId}:{props.month}
      <button type="button" onClick={props.onSaved}>
        触发保存回调
      </button>
      <button type="button" onClick={props.onClose}>
        关闭费用管理
      </button>
    </div>
  ),
}))
vi.mock('../components/BillDetailModal', () => ({
  BillDetailModal: (props: { bill: billingApi.MonthlyBill; studentName: string; onClose: () => void }) => (
    <div>
      BillDetailModalStub:{props.studentName}:{props.bill.id}
      <button type="button" onClick={props.onClose}>
        关闭账单详情
      </button>
    </div>
  ),
}))

const MONTH = currentMonthString()

const CLASSES = [
  { id: 20, name: '一班', teacherId: 2, teacherName: '李老师', teacherPhone: '13800000002' },
  { id: 21, name: '二班', teacherId: 3, teacherName: '王老师', teacherPhone: '13800000003' },
]

const ROWS: billingApi.BillOverviewRow[] = [
  {
    studentId: 100,
    studentName: '小明',
    classRoomId: 20,
    className: '一班',
    teacherName: '李老师',
    billId: 500,
    totalAmount: 800,
    isPaid: false,
    yearMonth: MONTH,
  },
  {
    studentId: 101,
    studentName: '小红',
    classRoomId: 20,
    className: '一班',
    teacherName: '李老师',
    billId: null,
    totalAmount: null,
    isPaid: false,
    yearMonth: MONTH,
  },
]

describe('AdminBillingModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue(CLASSES)
    vi.mocked(billingApi.fetchBillOverview).mockResolvedValue(ROWS)
  })

  it('loads the current-month overview for all classes by default', async () => {
    render(<AdminBillingModule />)

    await waitFor(() => expect(billingApi.fetchBillOverview).toHaveBeenCalledWith(MONTH, undefined, undefined, false))
    expect(await screen.findByText('小明')).toBeInTheDocument()
    expect(screen.getByText('小红')).toBeInTheDocument()
    expect(screen.getByText('¥800.00')).toBeInTheDocument()
    expect(screen.getByText('-')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '未缴费' })).toBeInTheDocument()
    expect(screen.getByText('未生成')).toBeInTheDocument()
  })

  it('refetches the overview when the class filter changes', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('班级'), { target: { value: '21' } })

    await waitFor(() => expect(billingApi.fetchBillOverview).toHaveBeenCalledWith(MONTH, 21, undefined, false))
  })

  it('refetches with offCampusOnly when the 纯课外课学生 filter is selected', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('班级'), { target: { value: 'off-campus' } })

    await waitFor(() =>
      expect(billingApi.fetchBillOverview).toHaveBeenCalledWith(MONTH, undefined, undefined, true),
    )
  })

  it('refetches the overview when searching by student name', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('搜索学生姓名'), { target: { value: '小明' } })

    await waitFor(() => expect(billingApi.fetchBillOverview).toHaveBeenCalledWith(MONTH, undefined, '小明', false))
  })

  it('caps the month picker at the current month and refetches on change', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    const monthInput = screen.getByLabelText('月份') as HTMLInputElement
    expect(monthInput.max).toBe(MONTH)

    fireEvent.change(monthInput, { target: { value: '2026-08' } })

    await waitFor(() =>
      expect(billingApi.fetchBillOverview).toHaveBeenCalledWith('2026-08', undefined, undefined, false),
    )
  })

  it('shows an all-months view with a month column when the month filter is cleared', async () => {
    const allMonthsRows: billingApi.BillOverviewRow[] = [
      { ...ROWS[0], yearMonth: '2026-07' },
      { ...ROWS[0], billId: 501, totalAmount: 900, yearMonth: '2026-08' },
    ]
    vi.mocked(billingApi.fetchBillOverview).mockResolvedValueOnce(ROWS).mockResolvedValueOnce(allMonthsRows)
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    const monthInput = screen.getByLabelText('月份') as HTMLInputElement
    fireEvent.change(monthInput, { target: { value: '' } })

    await waitFor(() =>
      expect(billingApi.fetchBillOverview).toHaveBeenCalledWith(undefined, undefined, undefined, false),
    )
    expect(await screen.findByText('2026-07')).toBeInTheDocument()
    expect(screen.getByText('2026-08')).toBeInTheDocument()
  })

  it('asks for confirmation before toggling paid status, and does nothing on cancel', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '未缴费' }))
    expect(await screen.findByText(/标记为已缴费吗/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(screen.getByRole('button', { name: '未缴费' })).toBeInTheDocument()
    expect(billingApi.setBillPaid).not.toHaveBeenCalled()
  })

  it('toggles paid status after confirming', async () => {
    vi.mocked(billingApi.setBillPaid).mockResolvedValue({} as billingApi.MonthlyBill)
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '未缴费' }))
    fireEvent.click(await screen.findByRole('button', { name: '确认' }))

    expect(screen.getByRole('button', { name: '已缴费' })).toBeInTheDocument()
    await waitFor(() => expect(billingApi.setBillPaid).toHaveBeenCalledWith(500, true))
  })

  it('reverts the paid toggle if the update fails', async () => {
    vi.mocked(billingApi.setBillPaid).mockRejectedValue(new Error('boom'))
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '未缴费' }))
    fireEvent.click(await screen.findByRole('button', { name: '确认' }))

    await waitFor(() => expect(screen.getByRole('button', { name: '未缴费' })).toBeInTheDocument())
  })

  it('does not allow toggling paid status when no bill has been generated yet', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小红')

    expect(screen.getByText('未生成')).toBeInTheDocument()
    expect(billingApi.setBillPaid).not.toHaveBeenCalled()
  })

  it('opens the fee management modal and reloads the overview after saving', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getAllByRole('button', { name: '费用管理' })[0])

    expect(await screen.findByText(`FeeManagementModalStub:100:${MONTH}`)).toBeInTheDocument()
    expect(billingApi.fetchBillOverview).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '触发保存回调' }))
    await waitFor(() => expect(billingApi.fetchBillOverview).toHaveBeenCalledTimes(2))

    fireEvent.click(screen.getByRole('button', { name: '关闭费用管理' }))
    expect(screen.queryByText(`FeeManagementModalStub:100:${MONTH}`)).not.toBeInTheDocument()
  })

  it('opens the bill detail modal when clicking a generated bill amount', async () => {
    const bill = { id: 500, yearMonth: MONTH } as billingApi.MonthlyBill
    vi.mocked(billingApi.fetchBillDetail).mockResolvedValue(bill)
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '¥800.00' }))

    expect(await screen.findByText('BillDetailModalStub:小明:500')).toBeInTheDocument()
    expect(billingApi.fetchBillDetail).toHaveBeenCalledWith(500)

    fireEvent.click(screen.getByRole('button', { name: '关闭账单详情' }))
    expect(screen.queryByText('BillDetailModalStub:小明:500')).not.toBeInTheDocument()
  })

  it('shows an error when loading bill detail fails', async () => {
    vi.mocked(billingApi.fetchBillDetail).mockRejectedValue(new Error('boom'))
    render(<AdminBillingModule />)
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '¥800.00' }))

    expect(await screen.findByText('加载账单详情失败，请重试')).toBeInTheDocument()
  })

  it('does not render a clickable amount when no bill has been generated', async () => {
    render(<AdminBillingModule />)
    await screen.findByText('小红')

    const dash = screen.getByText('-')
    expect(dash.tagName).not.toBe('BUTTON')
    expect(billingApi.fetchBillDetail).not.toHaveBeenCalled()
  })
})
