import { useEffect, useState } from 'react'
import { fetchEnrollmentSummary, type EnrollmentSummary } from '../api/adminOverview'

export function EnrollmentCard() {
  const [summary, setSummary] = useState<EnrollmentSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchEnrollmentSummary()
      .then(setSummary)
      .catch(() => setError('加载失败，请刷新重试'))
  }, [])

  return (
    <section className="rounded-2xl border border-[#ece7de] bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">在读规模</h2>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      {!error && !summary && <p className="mt-2 text-sm text-[#7c7391]">加载中...</p>}

      {summary && (
        <>
          <p className="mt-1 font-['Sora'] text-3xl font-semibold text-[#241f3d]">
            {summary.totalCount} <span className="text-base font-normal text-[#7c7391]">名在读学生</span>
          </p>

          <div className="mt-3 flex h-2 overflow-hidden rounded-full bg-[#f1f0f4]">
            <div
              data-testid="enrollment-bar-custody"
              className="h-full bg-[#6d5bd0]"
              style={{ width: `${pct(summary.custodyCount, summary.totalCount)}%` }}
            />
            <div
              data-testid="enrollment-bar-off-campus"
              className="h-full bg-[#b7a9f0]"
              style={{ width: `${pct(summary.offCampusOnlyCount, summary.totalCount)}%` }}
            />
          </div>
          <div className="mt-2 flex items-center justify-between text-xs">
            <span className="flex items-center gap-1.5 text-[#5d5480]">
              <span className="h-2 w-2 rounded-full bg-[#6d5bd0]" />
              托管班 {summary.custodyCount}
            </span>
            <span className="flex items-center gap-1.5 text-[#5d5480]">
              <span className="h-2 w-2 rounded-full bg-[#b7a9f0]" />
              纯课外课 {summary.offCampusOnlyCount}
            </span>
          </div>

          <div className="mt-4 space-y-2 border-t border-[#f3f0ea] pt-3">
            <p className="text-xs text-[#a79fc2]">按老师分布</p>
            {summary.byTeacher.length === 0 ? (
              <p className="text-sm text-[#7c7391]">暂无托管班学生</p>
            ) : (
              <div className="space-y-1.5 text-sm text-[#5d5480]">
                {summary.byTeacher.map((entry) => (
                  <div key={entry.teacherName} className="flex items-center justify-between">
                    <span>{entry.teacherName}</span>
                    <span className="font-medium text-[#241f3d]">{entry.studentCount} 人</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </section>
  )
}

function pct(count: number, total: number): number {
  return total > 0 ? Math.round((count / total) * 100) : 0
}
