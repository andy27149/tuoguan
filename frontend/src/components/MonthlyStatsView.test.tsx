import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { MonthlyStatsView } from './MonthlyStatsView'
import type { MonthlyStats } from '../api/monthlyStats'
import { currentMonthString, todayDateString } from '../kanban/date'

const MONTH = currentMonthString()
const TODAY = todayDateString()

function buildStats(day: Partial<MonthlyStats['days'][number]>): MonthlyStats {
  return {
    completedDays: 1,
    incompleteDays: 0,
    dailyRates: [],
    averageRating: 0,
    dailyRatings: [],
    days: [
      {
        date: TODAY,
        tasks: [],
        rating: 0,
        comment: '',
        arrivedAt: null,
        hadMeal: false,
        leaveReason: null,
        ...day,
      },
    ],
  }
}

describe('MonthlyStatsView day detail', () => {
  it('shows that the student had a meal on the selected day', () => {
    const stats = buildStats({ hadMeal: true })
    render(
      <MonthlyStatsView
        stats={stats}
        month={MONTH}
        onMonthChange={vi.fn()}
        selectedDate={TODAY}
        onSelectDate={vi.fn()}
      />,
    )

    expect(screen.getByText('🍚 今日已用餐')).toBeInTheDocument()
  })

  it('does not show meal info when the student did not have a meal', () => {
    const stats = buildStats({ hadMeal: false })
    render(
      <MonthlyStatsView
        stats={stats}
        month={MONTH}
        onMonthChange={vi.fn()}
        selectedDate={TODAY}
        onSelectDate={vi.fn()}
      />,
    )

    expect(screen.queryByText('🍚 今日已用餐')).not.toBeInTheDocument()
  })

  it('shows the leave reason on the selected day', () => {
    const stats = buildStats({ leaveReason: '生病' })
    render(
      <MonthlyStatsView
        stats={stats}
        month={MONTH}
        onMonthChange={vi.fn()}
        selectedDate={TODAY}
        onSelectDate={vi.fn()}
      />,
    )

    expect(screen.getByText('🌴 请假：生病')).toBeInTheDocument()
  })

  it('no longer renders the daily star-rating trend chart', () => {
    const stats = buildStats({})
    render(
      <MonthlyStatsView
        stats={stats}
        month={MONTH}
        onMonthChange={vi.fn()}
        selectedDate={TODAY}
        onSelectDate={vi.fn()}
      />,
    )

    expect(screen.queryByText('每日获星趋势')).not.toBeInTheDocument()
  })
})
