import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ParentSharePage } from './ParentSharePage'
import * as publicShareApi from '../api/publicShare'
import type { PublicShare } from '../api/publicShare'
import { currentMonthString, shiftMonthString, todayDateString } from '../kanban/date'

vi.mock('../api/publicShare')

const TODAY = todayDateString()

const SHARE: PublicShare = {
  studentName: '小美',
  schoolClassName: '三年级1班',
  avatarUrl: null,
  stats: {
    completedDays: 1,
    incompleteDays: 1,
    dailyRates: [{ date: TODAY, rate: 1 }],
    averageRating: 4.5,
    dailyRatings: [{ date: TODAY, rating: 5 }],
    days: [
      {
        date: TODAY,
        tasks: [{ id: 1, subject: '数学', name: '口算练习', completed: true }],
        rating: 5,
        comment: '今天状态不错',
        arrivedAt: '17:45',
      },
    ],
  },
  courseStatement: null,
}

const PURE_OFF_CAMPUS_SHARE: PublicShare = {
  studentName: '小外',
  schoolClassName: null,
  avatarUrl: null,
  stats: null,
  courseStatement: {
    balances: [{ courseId: 1, courseName: '书法课', lessonsRecharged: 10, lessonsConsumed: 3, balance: 7 }],
    recharges: [
      { id: 1, studentId: 1, courseId: 1, courseName: '书法课', lessonCount: 10, note: '微信转账', createdAt: '2026-09-01T10:00:00Z' },
    ],
    consumptions: [
      { id: 1, studentId: 1, courseId: 1, courseName: '书法课', consumptionDate: '2026-09-05', priceSnapshot: 80, teacherName: '王老师' },
    ],
  },
}

describe('ParentSharePage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(publicShareApi.fetchPublicShare).mockResolvedValue(SHARE)
  })

  it('shows the student header and monthly stats fetched for the given token', async () => {
    render(<ParentSharePage token="abc123" />)

    expect(await screen.findByText('小美')).toBeInTheDocument()
    expect(screen.getByText('三年级1班')).toBeInTheDocument()
    expect(screen.getByText(/完成天数 1 \/ 未完成天数 1/)).toBeInTheDocument()
    expect(publicShareApi.fetchPublicShare).toHaveBeenCalledWith('abc123', currentMonthString())
  })

  it('shows an error message when the token is invalid', async () => {
    vi.mocked(publicShareApi.fetchPublicShare).mockRejectedValue(new Error('not found'))
    render(<ParentSharePage token="unknown" />)

    expect(await screen.findByText('链接无效或已失效，请联系老师获取新的链接')).toBeInTheDocument()
  })

  it('fetches the previous month when clicking 上月', async () => {
    render(<ParentSharePage token="abc123" />)
    await screen.findByText('小美')

    fireEvent.click(screen.getByRole('button', { name: /上月/ }))

    await waitFor(() =>
      expect(publicShareApi.fetchPublicShare).toHaveBeenLastCalledWith(
        'abc123',
        shiftMonthString(currentMonthString(), -1),
      ),
    )
  })

  it('shows the pickup info recorded by the teacher', async () => {
    render(<ParentSharePage token="abc123" />)
    await screen.findByText('小美')

    expect(screen.getByText('到达托管班：17:45')).toBeInTheDocument()
  })

  it('renders no edit controls', async () => {
    render(<ParentSharePage token="abc123" />)
    await screen.findByText('小美')

    expect(screen.queryByRole('button', { name: /添加任务/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('shows the course statement instead of monthly stats for a pure off-campus student', async () => {
    vi.mocked(publicShareApi.fetchPublicShare).mockResolvedValue(PURE_OFF_CAMPUS_SHARE)
    render(<ParentSharePage token="course-token" />)

    expect(await screen.findByText('小外')).toBeInTheDocument()
    expect(screen.getByText('书法课')).toBeInTheDocument()
    expect(screen.getByText('剩 7 课时')).toBeInTheDocument()
    expect(screen.getByText(/王老师老师/)).toBeInTheDocument()
    expect(screen.queryByText(/完成天数/)).not.toBeInTheDocument()
  })
})
