import { useEffect, useState } from 'react'
import { fetchEnrollmentSummary, type EnrollmentSummary, type TeacherStudentCount } from '../api/adminOverview'
import { LOAD_FAILURE_MESSAGE } from '../constants'

export function EnrollmentCard() {
  const [summary, setSummary] = useState<EnrollmentSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchEnrollmentSummary()
      .then(setSummary)
      .catch(() => setError(LOAD_FAILURE_MESSAGE))
  }, [])

  return (
    <section className="rounded-2xl border border-panel-border bg-white p-5 shadow-[0_1px_3px_rgba(36,31,61,0.06)]">
      <h2 className="font-['Sora'] text-base font-semibold text-panel-ink">在读规模</h2>

      {error && <p className="mt-2 text-sm text-panel-danger">{error}</p>}
      {!error && !summary && <p className="mt-2 text-sm text-panel-muted">加载中...</p>}

      {summary && (
        <>
          <p className="mt-1 font-['Sora'] text-3xl font-semibold text-panel-ink">
            {summary.totalCount} <span className="text-base font-normal text-panel-muted">名在读学生</span>
          </p>

          <div className="mt-3 flex h-2 overflow-hidden rounded-full bg-[#f1f0f4]">
            <div
              data-testid="enrollment-bar-custody"
              className="h-full bg-panel-primary"
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
              <span className="h-2 w-2 rounded-full bg-panel-primary" />
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
              <p className="text-sm text-panel-muted">暂无托管班学生</p>
            ) : (
              <div className="space-y-1.5 text-sm text-[#5d5480]">
                {summary.byTeacher.map((entry) => (
                  <div key={entry.teacherId} className="flex items-center justify-between">
                    <span>{teacherLabel(entry, summary.byTeacher)}</span>
                    <span className="font-medium text-panel-ink">{entry.studentCount} 人</span>
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

// 两个不同老师恰好同名时（比如力达托管 3 年级和 4 年级都有一个"刘老师"），只显示姓名
// 会让这个列表出现两行一模一样、分不清谁是谁的"刘老师"——只在真的撞名时才加手机号，
// 避免绝大多数不撞名的情况下列表被手机号弄得很啰嗦。
function teacherLabel(entry: TeacherStudentCount, all: TeacherStudentCount[]): string {
  const sameName = all.filter((t) => t.teacherName === entry.teacherName)
  if (sameName.length > 1 && entry.teacherPhone) {
    return `${entry.teacherName}（${entry.teacherPhone}）`
  }
  return entry.teacherName
}
