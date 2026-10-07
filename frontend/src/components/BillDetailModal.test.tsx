import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import html2canvas from 'html2canvas-pro'
import { BillDetailModal } from './BillDetailModal'
import type { MonthlyBill } from '../api/billing'

vi.mock('html2canvas-pro')

const BILL: MonthlyBill = {
  id: 500,
  institutionId: 1,
  studentId: 100,
  teachingUnitId: 20,
  yearMonth: '2026-09',
  totalWeekdays: 22,
  leaveDays: 2,
  attendanceDays: 20,
  tuitionAmount: 600,
  mealAmount: 200,
  extraFeeTotal: 200,
  totalAmount: 1000,
  isPaid: false,
  generatedAt: '2026-09-05T00:00:00Z',
  extraFeeLines: [{ id: 1, monthlyBillId: 500, name: '数学课', pricePerLesson: 50, lessonCount: 4, amount: 200 }],
  mealRecordDates: ['2026-09-03', '2026-09-01', '2026-09-02'],
  leaveLines: [
    { id: 1, monthlyBillId: 500, leaveDate: '2026-09-04', reason: '发烧' },
    { id: 2, monthlyBillId: 500, leaveDate: '2026-09-05', reason: null },
  ],
}

describe('BillDetailModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the bill detail card', () => {
    render(<BillDetailModal bill={BILL} studentName="小明" className="一班" onClose={vi.fn()} />)

    expect(screen.getByText('2026-09 月度账单')).toBeInTheDocument()
    expect(screen.getByText(/一班 · 小明/)).toBeInTheDocument()
    expect(screen.getByText('¥600.00')).toBeInTheDocument()
    expect(screen.getAllByText('¥200.00')).toHaveLength(2)
    expect(screen.getByText('数学课')).toBeInTheDocument()
    expect(screen.getByText('¥1000.00')).toBeInTheDocument()
  })

  it('lists meal record dates in ascending order with a count', () => {
    render(<BillDetailModal bill={BILL} studentName="小明" className="一班" onClose={vi.fn()} />)

    expect(screen.getByText('用餐日期（共3天）')).toBeInTheDocument()
    const dateEls = screen.getAllByText(/^2026-09-0[123]$/)
    expect(dateEls.map((el) => el.textContent)).toEqual(['2026-09-01', '2026-09-02', '2026-09-03'])
  })

  it('does not show a meal date section when there are none', () => {
    render(
      <BillDetailModal
        bill={{ ...BILL, mealRecordDates: [] }}
        studentName="小明"
        className="一班"
        onClose={vi.fn()}
      />,
    )

    expect(screen.queryByText(/用餐日期/)).not.toBeInTheDocument()
  })

  it('lists leave dates as pill chips, same style as meal dates, falling back to a placeholder reason when missing', () => {
    render(<BillDetailModal bill={BILL} studentName="小明" className="一班" onClose={vi.fn()} />)

    expect(screen.getByText('请假日期（共2天）')).toBeInTheDocument()
    expect(screen.getByText('2026-09-04 · 发烧')).toBeInTheDocument()
    expect(screen.getByText('2026-09-05 · 未填写原因')).toBeInTheDocument()
  })

  it('does not show a leave date section when there are none', () => {
    render(
      <BillDetailModal bill={{ ...BILL, leaveLines: [] }} studentName="小明" className="一班" onClose={vi.fn()} />,
    )

    expect(screen.queryByText(/请假日期/)).not.toBeInTheDocument()
  })

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn()
    render(<BillDetailModal bill={BILL} studentName="小明" className="一班" onClose={onClose} />)

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))

    expect(onClose).toHaveBeenCalled()
  })

  it('exports the bill card as an image when the export button is clicked', async () => {
    const canvas = document.createElement('canvas')
    vi.spyOn(canvas, 'toDataURL').mockReturnValue('data:image/png;base64,fake')
    vi.mocked(html2canvas).mockResolvedValue(canvas)
    const clickSpy = vi.fn()
    const originalCreateElement = document.createElement.bind(document)
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = originalCreateElement(tag)
      if (tag === 'a') el.click = clickSpy
      return el
    })

    render(<BillDetailModal bill={BILL} studentName="小明" className="一班" onClose={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: '导出图片' }))

    await waitFor(() => expect(html2canvas).toHaveBeenCalled())
    expect(clickSpy).toHaveBeenCalled()

    vi.mocked(document.createElement).mockRestore()
  })

  it('shows an error message when export fails', async () => {
    vi.mocked(html2canvas).mockRejectedValue(new Error('boom'))
    render(<BillDetailModal bill={BILL} studentName="小明" className="一班" onClose={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '导出图片' }))

    expect(await screen.findByText('导出图片失败，请重试')).toBeInTheDocument()
  })
})
