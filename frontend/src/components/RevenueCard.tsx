import { useEffect, useState } from 'react'
import { fetchRevenueSnapshot, type RevenueSnapshot } from '../api/adminOverview'

export function RevenueCard() {
  const [snapshot, setSnapshot] = useState<RevenueSnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchRevenueSnapshot()
      .then(setSnapshot)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">收入快照</h2>

      {error && <p className="mt-3 text-sm text-red-600">{error}</p>}

      {!error && !snapshot && <p className="mt-3 text-sm text-[#7c7391]">加载中...</p>}

      {!error && snapshot && (
        <>
          <div className="mt-3">
            <div className="flex items-baseline justify-between">
              <span data-testid="revenue-tuition-label" className="text-sm text-[#5d5480]">
                托管费实收 / 应收
                <span className="ml-1 text-xs text-[#a79fc2]">（{snapshot.tuitionMonth}）</span>
              </span>
              <span className="font-['Sora'] text-sm font-semibold text-[#241f3d]">
                ¥{snapshot.tuitionCollected.toFixed(2)} / ¥{snapshot.tuitionBilled.toFixed(2)}
              </span>
            </div>
            <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-[#f1f0f4]">
              <div
                data-testid="revenue-collection-bar"
                className="h-full rounded-full bg-[#2f9e6e]"
                style={{
                  width: `${
                    snapshot.tuitionBilled > 0
                      ? Math.round((snapshot.tuitionCollected / snapshot.tuitionBilled) * 100)
                      : 0
                  }%`,
                }}
              />
            </div>
            <p className="mt-1 text-xs text-[#a79fc2]">
              已收{' '}
              {snapshot.tuitionBilled > 0
                ? Math.round((snapshot.tuitionCollected / snapshot.tuitionBilled) * 100)
                : 0}
              %
            </p>
          </div>

          <div className="mt-4 flex items-center justify-between border-t border-[#f3f0ea] pt-3">
            <span data-testid="revenue-consumption-label" className="text-sm text-[#5d5480]">
              课外课消课次数
              <span className="ml-1 text-xs text-[#a79fc2]">（{snapshot.consumptionMonth} 至今）</span>
            </span>
            <span className="font-['Sora'] text-lg font-semibold text-[#241f3d]">
              {snapshot.offCampusConsumptionCount} <span className="text-xs font-normal text-[#7c7391]">次</span>
            </span>
          </div>
        </>
      )}
    </section>
  )
}
