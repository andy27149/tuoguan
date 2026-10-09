import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RevenueCard } from './RevenueCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

const SNAPSHOT: adminOverviewApi.RevenueSnapshot = {
  tuitionMonth: '2026-09',
  tuitionBilled: 9200,
  tuitionCollected: 7650,
  consumptionMonth: '2026-10',
  offCampusConsumptionCount: 14,
}

describe('RevenueCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the heading with no month in it, and each row with its own month label', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue(SNAPSHOT)
    render(<RevenueCard />)

    const heading = await screen.findByRole('heading', { name: '收入快照' })
    expect(heading).toBeInTheDocument()
    expect(heading.textContent).toBe('收入快照')

    const tuitionRow = await screen.findByTestId('revenue-tuition-label')
    expect(tuitionRow.textContent).toBe('托管费实收 / 应收（2026-09）')

    const consumptionRow = screen.getByTestId('revenue-consumption-label')
    expect(consumptionRow.textContent).toBe('课外课消课次数（2026-10 至今）')
  })

  it('formats tuition billed and collected with two decimal places', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue({
      ...SNAPSHOT,
      tuitionBilled: 800,
      tuitionCollected: 800,
    })
    render(<RevenueCard />)

    expect(await screen.findByText('¥800.00 / ¥800.00')).toBeInTheDocument()
  })

  it('computes the collection-rate bar width from collected/billed', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue(SNAPSHOT)
    render(<RevenueCard />)

    const bar = await screen.findByTestId('revenue-collection-bar')
    expect(bar.style.width).toBe('83%')
    expect(await screen.findByText('已收 83%')).toBeInTheDocument()
  })

  it('renders a 0%-width bar, not NaN%, when tuitionBilled is 0', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue({
      ...SNAPSHOT,
      tuitionBilled: 0,
      tuitionCollected: 0,
    })
    render(<RevenueCard />)

    const bar = await screen.findByTestId('revenue-collection-bar')
    expect(bar.style.width).toBe('0%')
    expect(bar.style.width).not.toContain('NaN')
    expect(await screen.findByText('已收 0%')).toBeInTheDocument()
  })

  it('renders the consumption count as a plain integer with 次, never as currency', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockResolvedValue(SNAPSHOT)
    render(<RevenueCard />)

    const label = await screen.findByTestId('revenue-consumption-label')
    const row = label.parentElement as HTMLElement
    expect(row.textContent).toContain('14')
    expect(row.textContent).toContain('次')
    expect(row.textContent).not.toContain('¥')
  })

  it('shows an error message when the fetch rejects', async () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockRejectedValue(new Error('network'))
    render(<RevenueCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
    expect(screen.queryByText('托管费实收 / 应收')).not.toBeInTheDocument()
  })

  it('shows a loading state before the fetch resolves', () => {
    vi.mocked(adminOverviewApi.fetchRevenueSnapshot).mockReturnValue(new Promise(() => {}))
    render(<RevenueCard />)

    expect(screen.getByText('加载中...')).toBeInTheDocument()
  })
})
