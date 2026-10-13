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

function DateChips({ dates }: { dates: string[] }) {
  return (
    <div className="mt-1 flex flex-wrap gap-1.5">
      {[...dates].sort().map((d) => (
        <span key={d} className="rounded-full bg-[#faf7ff] px-2 py-0.5 text-[11px] text-[#5d5480]">
          {d}
        </span>
      ))}
    </div>
  )
}

export const BillCard = forwardRef<HTMLDivElement, BillCardProps>(function BillCard(
  { bill, studentName, className },
  ref,
) {
  const isPureOffCampus = bill.teachingUnitId === null
  const hasMealDates = !isPureOffCampus && bill.mealRecordDates.length > 0
  const hasLeaveLines = !isPureOffCampus && bill.leaveLines.length > 0

  return (
    <div ref={ref} className="w-[360px] space-y-3 bg-white p-5 text-sm text-[#241f3d]">
      <div className="border-b border-[#ece7de] pb-2 text-center">
        <p className="font-['Sora'] text-base font-bold text-[#241f3d]">{bill.yearMonth} 月度账单</p>
        <p className="mt-0.5 text-xs text-[#7c7391]">
          {isPureOffCampus ? studentName : `${className} · ${studentName}`}
        </p>
      </div>

      {!isPureOffCampus && (
        <div className="grid grid-cols-2 gap-2 text-center text-xs text-[#7c7391]">
          <div>
            <p className="font-['Sora'] text-base font-semibold text-[#241f3d]">{bill.leaveDays}</p>
            <p>请假天数</p>
          </div>
          <div>
            <p className="font-['Sora'] text-base font-semibold text-[#241f3d]">{bill.mealRecordDates.length}</p>
            <p>用餐天数</p>
          </div>
        </div>
      )}

      <div className="space-y-2 border-t border-[#ece7de] pt-2">
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
            {bill.mealRecordDates.length > 0 && (
              <p className="pl-2 text-[11px] text-[#7c7391]">
                {bill.mealRecordDates.length}天 × ¥{formatAmount(bill.mealAmount / bill.mealRecordDates.length)} = ¥{formatAmount(bill.mealAmount)}
              </p>
            )}
            {hasMealDates && (
              <div className="pl-2">
                <p className="text-[11px] font-medium text-[#5d5480]">用餐日期（共{bill.mealRecordDates.length}天）</p>
                <DateChips dates={bill.mealRecordDates} />
              </div>
            )}
            {hasLeaveLines && (
              <div className="pl-2">
                <p className="text-[11px] font-medium text-[#5d5480]">请假日期（共{bill.leaveLines.length}天）</p>
                <div className="mt-1 flex flex-wrap gap-1.5">
                  {[...bill.leaveLines]
                    .sort((a, b) => a.leaveDate.localeCompare(b.leaveDate))
                    .map((line) => (
                      <span
                        key={line.leaveDate}
                        className="rounded-full bg-[#faf7ff] px-2 py-0.5 text-[11px] text-[#5d5480]"
                      >
                        {line.leaveDate} · {line.reason ?? '未填写原因'}
                      </span>
                    ))}
                </div>
              </div>
            )}
          </>
        )}
        {bill.extraFeeLines.map((line) => (
          <div key={line.id}>
            <div className="flex justify-between text-[#5d5480]">
              <span>{line.name}</span>
              <span>¥{formatAmount(line.amount)}</span>
            </div>
            {line.settledByRecharge ? (
              <p className="pl-2 text-[11px] text-[#7c7391]">
                共{line.lessonCount}次消课
                {line.amount === 0
                  ? '（已通过后续充值补齐，本次账单不收费）'
                  : `，按¥${formatAmount(line.amount)}计费（部分已通过后续充值补齐）`}
              </p>
            ) : (
              <p className="pl-2 text-[11px] text-[#7c7391]">
                {line.lessonCount}次 × ¥{formatAmount(line.pricePerLesson)} = ¥{formatAmount(line.amount)}
              </p>
            )}
            {line.consumptionDates.length > 0 && (
              <div className="pl-2">
                <DateChips dates={line.consumptionDates} />
              </div>
            )}
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
