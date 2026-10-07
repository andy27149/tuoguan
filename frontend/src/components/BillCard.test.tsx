import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { BillCard } from './BillCard'
import type { MonthlyBill } from '../api/billing'

const CUSTODY_BILL: MonthlyBill = {
  id: 500,
  institutionId: 1,
  studentId: 100,
  teachingUnitId: 20,
  yearMonth: '2026-09',
  totalWeekdays: 22,
  leaveDays: 2,
  attendanceDays: 20,
  tuitionAmount: 600,
  mealAmount: 200,
  extraFeeTotal: 0,
  totalAmount: 800,
  isPaid: false,
  generatedAt: '2026-09-05T00:00:00Z',
  extraFeeLines: [],
  mealRecordDates: ['2026-09-01', '2026-09-02'],
  leaveLines: [],
}

const PURE_OFF_CAMPUS_BILL: MonthlyBill = {
  ...CUSTODY_BILL,
  teachingUnitId: null,
  tuitionAmount: 0,
  mealAmount: 0,
  totalAmount: 200,
  extraFeeTotal: 200,
  extraFeeLines: [{ id: 1, monthlyBillId: 500, name: '围棋课', pricePerLesson: 50, lessonCount: 4, amount: 200 }],
}

describe('BillCard', () => {
  it('shows custody-specific fields for a student with a 托管班', () => {
    render(<BillCard bill={CUSTODY_BILL} studentName="小明" className="一班" />)

    expect(screen.getByText('一班 · 小明')).toBeInTheDocument()
    expect(screen.queryByText('应出勤天数')).not.toBeInTheDocument()
    expect(screen.queryByText('实际出勤天数')).not.toBeInTheDocument()
    expect(screen.getByText('请假天数')).toBeInTheDocument()
    expect(screen.getByText('托管费')).toBeInTheDocument()
    expect(screen.getByText('餐费')).toBeInTheDocument()
    const mealDaysLabel = screen.getByText('用餐天数')
    expect(mealDaysLabel.previousElementSibling).toHaveTextContent('2')
    expect(screen.getByText('2天 × ¥100.00 = ¥200.00')).toBeInTheDocument()
  })

  it('hides custody-specific fields for a pure off-campus student', () => {
    render(<BillCard bill={PURE_OFF_CAMPUS_BILL} studentName="小外" className="—" />)

    expect(screen.getByText('小外')).toBeInTheDocument()
    expect(screen.queryByText('— · 小外')).not.toBeInTheDocument()
    expect(screen.queryByText('应出勤天数')).not.toBeInTheDocument()
    expect(screen.queryByText('托管费')).not.toBeInTheDocument()
    expect(screen.queryByText('餐费')).not.toBeInTheDocument()
    expect(screen.queryByText('用餐天数')).not.toBeInTheDocument()
    expect(screen.getByText('围棋课')).toBeInTheDocument()
    expect(screen.getByText('4次 × ¥50.00 = ¥200.00')).toBeInTheDocument()
  })
})
