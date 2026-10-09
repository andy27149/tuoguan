import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { EnrollmentCard } from './EnrollmentCard'
import * as adminOverviewApi from '../api/adminOverview'

vi.mock('../api/adminOverview')

describe('EnrollmentCard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('renders total/custody/off-campus counts and the byTeacher list', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 32,
      custodyCount: 18,
      offCampusOnlyCount: 14,
      byTeacher: [
        { teacherName: '李琦', studentCount: 12 },
        { teacherName: '王老师', studentCount: 6 },
      ],
    })

    render(<EnrollmentCard />)

    expect(await screen.findByText('32')).toBeInTheDocument()
    expect(screen.getByText('名在读学生')).toBeInTheDocument()
    expect(screen.getByText('托管班 18')).toBeInTheDocument()
    expect(screen.getByText('纯课外课 14')).toBeInTheDocument()
    expect(screen.getByText('李琦')).toBeInTheDocument()
    expect(screen.getByText('12 人')).toBeInTheDocument()
    expect(screen.getByText('王老师')).toBeInTheDocument()
    expect(screen.getByText('6 人')).toBeInTheDocument()
  })

  it('sizes the two proportion-bar segments from the counts', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 32,
      custodyCount: 18,
      offCampusOnlyCount: 14,
      byTeacher: [],
    })

    render(<EnrollmentCard />)
    await screen.findByText('32')

    const custodySegment = screen.getByTestId('enrollment-bar-custody')
    const offCampusSegment = screen.getByTestId('enrollment-bar-off-campus')
    expect(custodySegment.style.width).toBe('56%')
    expect(offCampusSegment.style.width).toBe('44%')
  })

  it('renders 0% bars instead of NaN% when totalCount is 0', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 0,
      custodyCount: 0,
      offCampusOnlyCount: 0,
      byTeacher: [],
    })

    render(<EnrollmentCard />)
    await screen.findByText('0')

    const custodySegment = screen.getByTestId('enrollment-bar-custody')
    const offCampusSegment = screen.getByTestId('enrollment-bar-off-campus')
    expect(custodySegment.style.width).toBe('0%')
    expect(offCampusSegment.style.width).toBe('0%')
    expect(custodySegment.style.width).not.toContain('NaN')
    expect(offCampusSegment.style.width).not.toContain('NaN')
  })

  it('shows a placeholder line when byTeacher is empty', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockResolvedValue({
      totalCount: 14,
      custodyCount: 0,
      offCampusOnlyCount: 14,
      byTeacher: [],
    })

    render(<EnrollmentCard />)
    await screen.findByText('14')

    expect(screen.getByText('暂无托管班学生')).toBeInTheDocument()
  })

  it('shows an error message when the fetch rejects', async () => {
    vi.mocked(adminOverviewApi.fetchEnrollmentSummary).mockRejectedValue(new Error('network error'))

    render(<EnrollmentCard />)

    expect(await screen.findByText('加载失败，请刷新重试')).toBeInTheDocument()
  })
})
