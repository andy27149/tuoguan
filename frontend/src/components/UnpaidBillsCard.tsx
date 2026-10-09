import { useEffect, useState } from 'react'
import { fetchUnpaidBills, type UnpaidBillSummary } from '../api/adminOverview'

interface UnpaidBillsCardProps {
  onOpenBilling: () => void
}

export function UnpaidBillsCard({ onOpenBilling }: UnpaidBillsCardProps) {
  const [summary, setSummary] = useState<UnpaidBillSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchUnpaidBills()
      .then(setSummary)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <div className="flex items-baseline justify-between">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">账单欠费提醒</h2>
        <span className="text-xs text-[#7c7391]">全部未缴费（不限月份）</span>
      </div>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      {!error && !summary && <p className="mt-3 text-sm text-[#7c7391]">加载中...</p>}

      {!error && summary && (
        <>
          <p className="mt-1 font-['Sora'] text-3xl font-semibold text-[#241f3d]">
            {summary.count}{' '}
            <span className="text-base font-normal text-[#7c7391]">
              笔未缴费，共 ¥{summary.totalAmount.toFixed(2)}
            </span>
          </p>

          {summary.rows.length === 0 ? (
            <p className="mt-4 text-sm text-[#7c7391]">暂无欠费账单</p>
          ) : (
            <div className="mt-4 divide-y divide-[#f3f0ea]">
              {summary.rows.map((row) => (
                <div key={`${row.studentId}-${row.yearMonth}`} className="flex items-center justify-between py-2.5">
                  <div>
                    <p className="text-sm font-medium text-[#241f3d]">{row.studentName}</p>
                    <p className="text-xs text-[#7c7391]">
                      {row.className} · {row.yearMonth}
                    </p>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="font-['Sora'] text-sm font-semibold text-[#241f3d]">
                      ¥{row.totalAmount.toFixed(2)}
                    </span>
                    <span className="rounded-full bg-[#fdf1e6] px-2.5 py-1 text-xs font-medium text-[#b7591f]">
                      ● 未缴费
                    </span>
                  </div>
                </div>
              ))}
            </div>
          )}

          <button
            type="button"
            onClick={onOpenBilling}
            className="mt-3 text-xs font-medium text-[#6d5bd0] hover:underline"
          >
            查看账单管理 →
          </button>
        </>
      )}
    </section>
  )
}
