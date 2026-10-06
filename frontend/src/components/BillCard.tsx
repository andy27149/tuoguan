import { forwardRef } from 'react'
import type { MonthlyBill } from '../api/billing'
import { formatDateTime } from '../kanban/date'

interface BillCardProps {
  bill: MonthlyBill
  studentName: string
  className: string
}

function formatAmount(value: number): string {
  return value.toFixed(2)
}

export const BillCard = forwardRef<HTMLDivElement, BillCardProps>(function BillCard(
  { bill, studentName, className },
  ref,
) {
  const isPureOffCampus = bill.teachingUnitId === null

  return (
    <div ref={ref} className="w-80 space-y-3 bg-white p-4 text-sm text-[#241f3d]">
      <div className="border-b border-[#ece7de] pb-2 text-center">
        <p className="font-['Sora'] text-base font-bold text-[#241f3d]">{bill.yearMonth} 月度账单</p>
        <p className="mt-0.5 text-xs text-[#7c7391]">
          {isPureOffCampus ? studentName : `${className} · ${studentName}`}
        </p>
      </div>

      {!isPureOffCampus && (
        <div className="grid grid-cols-4 gap-2 text-center text-xs text-[#7c7391]">
          <div>
            <p className="font-['Sora'] text-base font-semibold text-[#241f3d]">{bill.totalWeekdays}</p>
            <p>应出勤天数</p>
          </div>
          <div>
            <p className="font-['Sora'] text-base font-semibold text-[#241f3d]">{bill.leaveDays}</p>
            <p>请假天数</p>
          </div>
          <div>
            <p className="font-['Sora'] text-base font-semibold text-[#241f3d]">{bill.attendanceDays}</p>
            <p>实际出勤天数</p>
          </div>
          <div>
            <p className="font-['Sora'] text-base font-semibold text-[#241f3d]">{bill.mealRecordDates.length}</p>
            <p>用餐天数</p>
          </div>
        </div>
      )}

      <div className="space-y-1 border-t border-[#ece7de] pt-2">
        {!isPureOffCampus && (
          <>
            <div className="flex justify-between">
              <span>托管费</span>
              <span>¥{formatAmount(bill.tuitionAmount)}</span>
            </div>
            <div className="flex justify-between">
              <span>餐费</span>
              <span>¥{formatAmount(bill.mealAmount)}</span>
            </div>
          </>
        )}
        {bill.extraFeeLines.map((line) => (
          <div key={line.id} className="flex justify-between text-[#5d5480]">
            <span>{line.name}</span>
            <span>¥{formatAmount(line.amount)}</span>
          </div>
        ))}
      </div>

      <div className="flex justify-between border-t border-[#ece7de] pt-2 text-base font-['Sora'] font-bold text-[#6d5bd0]">
        <span>合计</span>
        <span>¥{formatAmount(bill.totalAmount)}</span>
      </div>

      <p className="text-center text-[11px] text-[#7c7391]">
        生成时间 {formatDateTime(bill.generatedAt)}
      </p>
    </div>
  )
})
