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
  mealRecordDates: ['2026-09-02', '2026-09-01'],
  leaveLines: [{ id: 1, monthlyBillId: 500, leaveDate: '2026-09-10', reason: '发烧' }],
}

const PURE_OFF_CAMPUS_BILL: MonthlyBill = {
  ...CUSTODY_BILL,
  teachingUnitId: null,
  tuitionAmount: 0,
  mealAmount: 0,
  totalAmount: 200,
  extraFeeTotal: 200,
  extraFeeLines: [{
    id: 1,
    monthlyBillId: 500,
    name: '围棋课',
    pricePerLesson: 50,
    lessonCount: 4,
    amount: 200,
    consumptionDates: ['2026-09-03', '2026-09-01', '2026-09-08', '2026-09-15'],
    settledByRecharge: false,
  }],
  leaveLines: [],
}

const FULLY_SETTLED_BILL: MonthlyBill = {
  ...PURE_OFF_CAMPUS_BILL,
  totalAmount: 0,
  extraFeeTotal: 0,
  extraFeeLines: [{
    id: 2,
    monthlyBillId: 500,
    name: '语文',
    pricePerLesson: 80,
    lessonCount: 7,
    amount: 0,
    consumptionDates: ['2020-01-02', '2020-01-03', '2020-01-04', '2020-01-05', '2020-01-06', '2020-01-07', '2020-01-08'],
    settledByRecharge: true,
  }],
}

const PARTIALLY_SETTLED_BILL: MonthlyBill = {
  ...FULLY_SETTLED_BILL,
  totalAmount: 320,
  extraFeeTotal: 320,
  extraFeeLines: [{
    ...FULLY_SETTLED_BILL.extraFeeLines[0],
    amount: 320,
  }],
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

  it('shows meal and leave dates as chips, so the exported image carries the same detail as the on-screen view', () => {
    render(<BillCard bill={CUSTODY_BILL} studentName="小明" className="一班" />)

    expect(screen.getByText('用餐日期（共2天）')).toBeInTheDocument()
    const mealDateEls = screen.getAllByText(/^2026-09-0[12]$/)
    expect(mealDateEls.map((el) => el.textContent)).toEqual(['2026-09-01', '2026-09-02'])
    expect(screen.getByText('请假日期（共1天）')).toBeInTheDocument()
    expect(screen.getByText('2026-09-10 · 发烧')).toBeInTheDocument()
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

  it('shows the specific consumption dates for a course extra-fee line', () => {
    render(<BillCard bill={PURE_OFF_CAMPUS_BILL} studentName="小外" className="—" />)

    const dateEls = screen.getAllByText(/^2026-09-(01|03|08|15)$/)
    expect(dateEls.map((el) => el.textContent)).toEqual(['2026-09-01', '2026-09-03', '2026-09-08', '2026-09-15'])
  })

  it('shows a settled note instead of the per-lesson math when a course fee was fully settled by a later recharge', () => {
    render(<BillCard bill={FULLY_SETTLED_BILL} studentName="孙三" className="—" />)

    expect(screen.getByText('共7次消课（已通过后续充值补齐，本次账单不收费）')).toBeInTheDocument()
    expect(screen.queryByText('7次 × ¥80.00 = ¥0.00')).not.toBeInTheDocument()
  })

  it('shows a partial-settlement note with the reduced amount when a course fee was only partly settled by a later recharge', () => {
    render(<BillCard bill={PARTIALLY_SETTLED_BILL} studentName="孙三" className="—" />)

    expect(screen.getByText('共7次消课，按¥320.00计费（部分已通过后续充值补齐）')).toBeInTheDocument()
    expect(screen.queryByText('7次 × ¥80.00 = ¥320.00')).not.toBeInTheDocument()
  })
})
