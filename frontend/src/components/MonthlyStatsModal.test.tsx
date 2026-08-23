import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MonthlyStatsModal } from './MonthlyStatsModal'
import * as monthlyStatsApi from '../api/monthlyStats'
import { currentMonthString, shiftMonthString } from '../kanban/date'

vi.mock('../api/monthlyStats')

const STATS = {
  completedDays: 12,
  incompleteDays: 3,
  dailyRates: [
    { date: '2026-08-01', rate: 1 },
    { date: '2026-08-02', rate: 0.5 },
  ],
  averageRating: 4.2,
}

describe('MonthlyStatsModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(monthlyStatsApi.fetchMonthlyStats).mockResolvedValue(STATS)
  })

  it('shows the fetched completed/incomplete counts and average rating', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)

    expect(await screen.findByText(/完成天数 12 \/ 未完成天数 3/)).toBeInTheDocument()
    expect(screen.getByText('4.2')).toBeInTheDocument()
    expect(monthlyStatsApi.fetchMonthlyStats).toHaveBeenCalledWith(10, currentMonthString())
  })

  it('fetches the previous month when clicking 上月', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)
    await screen.findByText(/完成天数/)

    fireEvent.click(screen.getByRole('button', { name: /上月/ }))

    await waitFor(() =>
      expect(monthlyStatsApi.fetchMonthlyStats).toHaveBeenLastCalledWith(
        10,
        shiftMonthString(currentMonthString(), -1),
      ),
    )
  })

  it('fetches the next month when clicking 下月', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)
    await screen.findByText(/完成天数/)

    fireEvent.click(screen.getByRole('button', { name: /下月/ }))

    await waitFor(() =>
      expect(monthlyStatsApi.fetchMonthlyStats).toHaveBeenLastCalledWith(
        10,
        shiftMonthString(currentMonthString(), 1),
      ),
    )
  })

  it('shows an empty-data message when dailyRates is empty', async () => {
    vi.mocked(monthlyStatsApi.fetchMonthlyStats).mockResolvedValue({
      completedDays: 0,
      incompleteDays: 0,
      dailyRates: [],
      averageRating: 0,
    })
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)

    expect(await screen.findByText('本月暂无数据')).toBeInTheDocument()
  })
})
