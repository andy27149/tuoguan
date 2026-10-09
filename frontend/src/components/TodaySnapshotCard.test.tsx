import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { TodaySnapshotCard } from './TodaySnapshotCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

describe('TodaySnapshotCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders the three fraction lines', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockResolvedValue({
      arrivedCount: 15,
      mealCount: 10,
      leaveCount: 1,
      totalCustodyStudentCount: 18,
    })

    render(<TodaySnapshotCard />)

    expect(await screen.findByText('15 / 18')).toBeInTheDocument()
    expect(screen.getByText('10 / 18')).toBeInTheDocument()
    expect(screen.getByText('1 / 18')).toBeInTheDocument()
  })

  it('uses the neutral lavender color for the leave bar, never an alert color', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockResolvedValue({
      arrivedCount: 15,
      mealCount: 10,
      leaveCount: 1,
      totalCustodyStudentCount: 18,
    })

    render(<TodaySnapshotCard />)
    await screen.findByText('1 / 18')

    const leaveBar = screen.getByTestId('today-bar-leave')
    expect(leaveBar).toHaveClass('bg-[#b7a9f0]')
    expect(leaveBar).not.toHaveClass('bg-[#b7591f]')
    expect(leaveBar).not.toHaveClass('bg-red-500')
    expect(leaveBar).not.toHaveClass('bg-amber-500')
  })

  it('renders 0% bars instead of NaN% when totalCustodyStudentCount is 0', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockResolvedValue({
      arrivedCount: 0,
      mealCount: 0,
      leaveCount: 0,
      totalCustodyStudentCount: 0,
    })

    render(<TodaySnapshotCard />)
    await screen.findAllByText('0 / 0')

    const arrivedBar = screen.getByTestId('today-bar-arrived')
    const mealBar = screen.getByTestId('today-bar-meal')
    const leaveBar = screen.getByTestId('today-bar-leave')
    expect(arrivedBar.style.width).toBe('0%')
    expect(mealBar.style.width).toBe('0%')
    expect(leaveBar.style.width).toBe('0%')
  })

  it('shows an error message when the fetch rejects', async () => {
    vi.mocked(adminOverviewApi.fetchTodaySnapshot).mockRejectedValue(new Error('network error'))

    render(<TodaySnapshotCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })
})
