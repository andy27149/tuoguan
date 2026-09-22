import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminTaskStatsModule } from './AdminTaskStatsModule'
import * as adminApi from '../api/admin'
import { todayDateString } from '../kanban/date'

vi.mock('../api/admin')

const DASHBOARD = {
  date: todayDateString(),
  classes: [
    { classRoomId: 10, className: '一班', studentCount: 5, checkinCount: 4, completedStudentCount: 3 },
    { classRoomId: 11, className: '二班', studentCount: 4, checkinCount: 4, completedStudentCount: 4 },
  ],
}

describe('AdminTaskStatsModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchAdminDashboard).mockResolvedValue(DASHBOARD)
  })

  it('defaults the date picker to today and fetches the dashboard', async () => {
    render(<AdminTaskStatsModule />)

    await waitFor(() => expect(adminApi.fetchAdminDashboard).toHaveBeenCalledWith(todayDateString()))
    const input = screen.getByDisplayValue(todayDateString()) as HTMLInputElement
    expect(input.type).toBe('date')
  })

  it('renders dashboard class summary rows', async () => {
    render(<AdminTaskStatsModule />)

    expect(await screen.findByText('一班')).toBeInTheDocument()
    const row = screen.getByText('一班').closest('tr') as HTMLElement
    expect(row).toHaveTextContent('5')
    expect(row).toHaveTextContent('4')
    expect(row).toHaveTextContent('3')
    expect(screen.getByText('二班')).toBeInTheDocument()
  })

  it('shows a loading state before the dashboard resolves', () => {
    let resolve: (value: typeof DASHBOARD) => void = () => {}
    vi.mocked(adminApi.fetchAdminDashboard).mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    render(<AdminTaskStatsModule />)

    expect(screen.getByText('加载中...')).toBeInTheDocument()
    resolve(DASHBOARD)
  })

  it('shows an error state when loading fails', async () => {
    vi.mocked(adminApi.fetchAdminDashboard).mockRejectedValue(new Error('boom'))
    render(<AdminTaskStatsModule />)

    expect(await screen.findByText('加载看板数据失败，请刷新重试')).toBeInTheDocument()
  })

  it('refetches the dashboard when the date changes', async () => {
    render(<AdminTaskStatsModule />)
    await screen.findByText('一班')
    expect(adminApi.fetchAdminDashboard).toHaveBeenCalledTimes(1)

    fireEvent.change(screen.getByDisplayValue(todayDateString()), { target: { value: '2026-09-01' } })

    await waitFor(() => expect(adminApi.fetchAdminDashboard).toHaveBeenCalledWith('2026-09-01'))
    expect(adminApi.fetchAdminDashboard).toHaveBeenCalledTimes(2)
  })
})
