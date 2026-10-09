import { useEffect, useState } from 'react'
import { fetchLowBalance, type LowBalanceRow } from '../api/adminOverview'
import { AdminCourseStatementModal } from './AdminCourseStatementModal'

export function LowBalanceCard() {
  const [rows, setRows] = useState<LowBalanceRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [statementStudent, setStatementStudent] = useState<{ id: number; name: string } | null>(null)

  useEffect(() => {
    fetchLowBalance()
      .then(setRows)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <div className="flex items-baseline justify-between">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">课时不足预警</h2>
        <span className="text-xs text-[#7c7391]">阈值 &lt; 3 课时</span>
      </div>

      {error && <p className="mt-2 text-sm text-[#b91c1c]">{error}</p>}

      {!error && rows === null && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}

      {!error && rows !== null && (
        <>
          <p className="mt-1 font-['Sora'] text-3xl font-semibold text-[#241f3d]">
            {rows.length} <span className="text-base font-normal text-[#7c7391]">名学生需要续费</span>
          </p>

          {rows.length === 0 ? (
            <p className="mt-4 text-sm text-[#7c7391]">没有需要续费的学生</p>
          ) : (
            <div className="mt-4 divide-y divide-[#f3f0ea]">
              {rows.map((row) => (
                <div key={`${row.studentId}-${row.courseId}`} className="flex items-center justify-between py-2.5">
                  <div>
                    <p className="text-sm font-medium text-[#241f3d]">{row.studentName}</p>
                    <p className="text-xs text-[#7c7391]">{row.courseName}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={
                        row.balance <= 0
                          ? 'rounded-full bg-[#fee2e2] px-2.5 py-1 text-xs font-medium text-[#b91c1c]'
                          : 'rounded-full bg-[#fef3c7] px-2.5 py-1 text-xs font-medium text-[#b45309]'
                      }
                    >
                      ● 余额 {row.balance} 课时
                    </span>
                    <button
                      type="button"
                      onClick={() => setStatementStudent({ id: row.studentId, name: row.studentName })}
                      className="rounded-full border border-[#ece7de] px-3 py-1 text-xs text-[#5d5480] hover:bg-[#faf7ff]"
                    >
                      去充值
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </>
      )}

      {statementStudent && (
        <AdminCourseStatementModal
          studentId={statementStudent.id}
          studentName={statementStudent.name}
          onClose={() => setStatementStudent(null)}
        />
      )}
    </section>
  )
}
