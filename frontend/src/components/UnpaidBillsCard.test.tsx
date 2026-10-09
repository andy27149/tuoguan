import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { UnpaidBillsCard } from './UnpaidBillsCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

const SUMMARY: adminOverviewApi.UnpaidBillSummary = {
  count: 3,
  totalAmount: 2250,
  rows: [
    { studentId: 1, studentName: '李强', className: '托管A班', yearMonth: '2026-09', totalAmount: 800 },
    { studentId: 2, studentName: '王芳', className: '托管B班', yearMonth: '2026-09', totalAmount: 650 },
    { studentId: 2, studentName: '王芳', className: '托管B班', yearMonth: '2026-08', totalAmount: 800 },
  ],
}

describe('UnpaidBillsCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the headline and every row from the fetched summary', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue(SUMMARY)
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    const headline = await screen.findByText('笔未缴费，共 ¥2250.00')
    expect(headline.closest('p')).toHaveTextContent('3 笔未缴费，共 ¥2250.00')
    expect(screen.getByText('李强')).toBeInTheDocument()
    expect(screen.getAllByText('王芳')).toHaveLength(2)
    expect(screen.getByText('托管A班 · 2026-09')).toBeInTheDocument()
    expect(screen.getByText('托管B班 · 2026-09')).toBeInTheDocument()
    expect(screen.getByText('托管B班 · 2026-08')).toBeInTheDocument()
    expect(screen.getAllByText('¥800.00')).toHaveLength(2)
    expect(screen.getByText('¥650.00')).toBeInTheDocument()
  })

  it('renders the same student twice as two distinct rows when they owe for two different months', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue(SUMMARY)
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    await screen.findByText('李强')
    expect(screen.getByText('托管B班 · 2026-09')).toBeInTheDocument()
    expect(screen.getByText('托管B班 · 2026-08')).toBeInTheDocument()
  })

  it('shows a loading state that is distinct from the empty state before the fetch resolves', () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockReturnValue(new Promise(() => {}))
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    expect(screen.getByText('加载中...')).toBeInTheDocument()
    expect(screen.queryByText('暂无欠费账单')).not.toBeInTheDocument()
  })

  it('shows an empty state but keeps the billing link when there are no unpaid bills', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue({ count: 0, totalAmount: 0, rows: [] })
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    expect(await screen.findByText('暂无欠费账单')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '查看账单管理 →' })).toBeInTheDocument()
  })

  it('shows an error message when the fetch fails', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockRejectedValue(new Error('network error'))
    render(<UnpaidBillsCard onOpenBilling={vi.fn()} />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })

  it('calls onOpenBilling exactly once when the link is clicked', async () => {
    vi.mocked(adminOverviewApi.fetchUnpaidBills).mockResolvedValue(SUMMARY)
    const onOpenBilling = vi.fn()
    render(<UnpaidBillsCard onOpenBilling={onOpenBilling} />)

    await screen.findByText('李强')
    fireEvent.click(screen.getByRole('button', { name: '查看账单管理 →' }))

    await waitFor(() => expect(onOpenBilling).toHaveBeenCalledTimes(1))
  })
})
