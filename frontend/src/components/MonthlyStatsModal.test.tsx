import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MonthlyStatsModal } from './MonthlyStatsModal'
import * as monthlyStatsApi from '../api/monthlyStats'
import type { MonthlyStats } from '../api/monthlyStats'
import { currentMonthString, shiftMonthString, todayDateString } from '../kanban/date'

vi.mock('../api/monthlyStats')

const TODAY = todayDateString()

const STATS: MonthlyStats = {
  completedDays: 1,
  incompleteDays: 1,
  dailyRates: [
    { date: '2026-08-01', rate: 1 },
    { date: TODAY, rate: 0.5 },
  ],
  averageRating: 4.5,
  dailyRatings: [
    { date: '2026-08-01', rating: 4 },
    { date: TODAY, rating: 5 },
  ],
  days: [
    {
      date: '2026-08-01',
      tasks: [{ id: 1, subject: '数学', name: '口算练习', completed: true }],
      rating: 4,
      comment: '',
      pickedUpBy: null,
      pickedUpAt: null,
    },
    {
      date: TODAY,
      tasks: [
        { id: 2, subject: '数学', name: '口算练习', completed: true },
        { id: 3, subject: '英语', name: '单词听写', completed: false },
      ],
      rating: 5,
      comment: '今天状态不错',
      pickedUpBy: '奶奶',
      pickedUpAt: '17:30',
    },
  ],
}

const EMPTY_STATS: MonthlyStats = {
  completedDays: 0,
  incompleteDays: 0,
  dailyRates: [],
  averageRating: 0,
  dailyRatings: [],
  days: [],
}

describe('MonthlyStatsModal', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(monthlyStatsApi.fetchMonthlyStats).mockResolvedValue(STATS)
  })

  it('shows the fetched completed/incomplete counts and average rating', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)

    expect(await screen.findByText(/完成天数 1 \/ 未完成天数 1/)).toBeInTheDocument()
    expect(screen.getByText('4.5')).toBeInTheDocument()
    expect(monthlyStatsApi.fetchMonthlyStats).toHaveBeenCalledWith(10, currentMonthString())
  })

  it('defaults the selected day to today and shows its tasks/rating/comment', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)

    await screen.findByText(/完成天数/)

    expect(screen.getByText(TODAY)).toBeInTheDocument()
    expect(screen.getByText(/单词听写/)).toBeInTheDocument()
    expect(screen.getByText('今天状态不错')).toBeInTheDocument()
  })

  it('switches the day detail when another calendar day is clicked', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)
    await screen.findByText(/完成天数/)

    fireEvent.click(screen.getByRole('button', { name: '1' }))

    expect(await screen.findByText('2026-08-01')).toBeInTheDocument()
    expect(screen.getByText(/口算练习/)).toBeInTheDocument()
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

  it('shows the pickup info for a day that recorded it', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)
    await screen.findByText(/完成天数/)

    expect(screen.getByText('接送人：奶奶 · 17:30')).toBeInTheDocument()
  })

  it('does not show a pickup line for a day with no pickup recorded', async () => {
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)
    await screen.findByText(/完成天数/)

    fireEvent.click(screen.getByRole('button', { name: '1' }))
    await screen.findByText('2026-08-01')

    expect(screen.queryByText(/接送人：/)).not.toBeInTheDocument()
  })

  it('shows empty-data messages for the charts and day detail when there is no data', async () => {
    vi.mocked(monthlyStatsApi.fetchMonthlyStats).mockResolvedValue(EMPTY_STATS)
    render(<MonthlyStatsModal studentId={10} studentName="小明" onClose={vi.fn()} />)

    expect(await screen.findByText('本月暂无评星数据')).toBeInTheDocument()
    expect(screen.getByText('本月暂无任务数据')).toBeInTheDocument()
    expect(screen.getByText('当天没有任务记录')).toBeInTheDocument()
  })
})
