import { useEffect, useState } from 'react'
import { fetchTodaySnapshot, type TodaySnapshot } from '../api/adminOverview'

const TODAY = new Date().toISOString().slice(0, 10)

export function TodaySnapshotCard() {
  const [snapshot, setSnapshot] = useState<TodaySnapshot | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchTodaySnapshot()
      .then(setSnapshot)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <div className="flex items-baseline justify-between">
        <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">今日运营快照</h2>
        <span className="text-xs text-[#7c7391]">{TODAY}</span>
      </div>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {!error && !snapshot && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}

      {snapshot && (
        <div className="mt-4 space-y-3">
          <Row
            testId="today-bar-arrived"
            icon="🕐"
            label="到了"
            count={snapshot.arrivedCount}
            total={snapshot.totalCustodyStudentCount}
            barColor="bg-[#2f9e6e]"
          />
          <Row
            testId="today-bar-meal"
            icon="🍚"
            label="用餐"
            count={snapshot.mealCount}
            total={snapshot.totalCustodyStudentCount}
            barColor="bg-[#2f9e6e]"
          />
          <Row
            testId="today-bar-leave"
            icon="🌴"
            label="请假"
            count={snapshot.leaveCount}
            total={snapshot.totalCustodyStudentCount}
            barColor="bg-[#b7a9f0]"
          />
        </div>
      )}
    </section>
  )
}

function Row({
  testId,
  icon,
  label,
  count,
  total,
  barColor,
}: {
  testId: string
  icon: string
  label: string
  count: number
  total: number
  barColor: string
}) {
  const width = total > 0 ? Math.round((count / total) * 100) : 0
  return (
    <div>
      <div className="flex items-center justify-between text-sm">
        <span className="text-[#5d5480]">
          {icon} {label}
        </span>
        <span className="font-['Sora'] font-semibold text-[#241f3d]">
          {count} / {total}
        </span>
      </div>
      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-[#f1f0f4]">
        <div data-testid={testId} className={`h-full rounded-full ${barColor}`} style={{ width: `${width}%` }} />
      </div>
    </div>
  )
}
