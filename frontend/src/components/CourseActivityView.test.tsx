import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { CourseActivityView } from './CourseActivityView'
import type { CourseActivityRow } from '../api/publicShare'

const ROWS: CourseActivityRow[] = [
  { courseId: 1, courseName: '围棋课', recentConsumptionDates: ['2026-10-01', '2026-09-28'] },
  { courseId: 2, courseName: '书法课', recentConsumptionDates: ['2026-10-02'] },
]

describe('CourseActivityView', () => {
  it('shows only the course taken on the selected date', () => {
    render(<CourseActivityView rows={ROWS} selectedDate="2026-10-01" />)

    expect(screen.getByText('课外课情况')).toBeInTheDocument()
    expect(screen.getByText('围棋课')).toBeInTheDocument()
    expect(screen.queryByText('书法课')).not.toBeInTheDocument()
  })

  it('shows an empty-state message when no course was taken on the selected date', () => {
    render(<CourseActivityView rows={ROWS} selectedDate="2026-10-15" />)

    expect(screen.getByText('当天没有课外课记录')).toBeInTheDocument()
    expect(screen.queryByText('围棋课')).not.toBeInTheDocument()
    expect(screen.queryByText('书法课')).not.toBeInTheDocument()
  })

  it('shows the empty-state message when no date is selected', () => {
    render(<CourseActivityView rows={ROWS} selectedDate={null} />)

    expect(screen.getByText('当天没有课外课记录')).toBeInTheDocument()
  })

  it('shows every course taken on the selected date when there is more than one', () => {
    const rows: CourseActivityRow[] = [
      { courseId: 1, courseName: '围棋课', recentConsumptionDates: ['2026-10-01'] },
      { courseId: 2, courseName: '书法课', recentConsumptionDates: ['2026-10-01'] },
    ]
    render(<CourseActivityView rows={rows} selectedDate="2026-10-01" />)

    expect(screen.getByText('围棋课')).toBeInTheDocument()
    expect(screen.getByText('书法课')).toBeInTheDocument()
  })
})
