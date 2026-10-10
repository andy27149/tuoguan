import { useEffect, useState } from 'react'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'
import { BrandMark } from '../brand/BrandMark'
import { RollCallResultModal } from '../components/RollCallResultModal'

interface CourseConsumptionPageProps {
  onBack: () => void
}

interface RollCallResult {
  type: 'success' | 'warning'
  message: string
}

function todayDateString() {
  return new Date().toISOString().slice(0, 10)
}

export function CourseConsumptionPage({ onBack }: CourseConsumptionPageProps) {
  const [courses, setCourses] = useState<courseApi.Course[]>([])
  const [activeCourseId, setActiveCourseId] = useState<number | null>(null)
  const [roster, setRoster] = useState<courseApi.CourseRosterEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [rollCallDate, setRollCallDate] = useState(todayDateString())
  const [presentStudentIds, setPresentStudentIds] = useState<Set<number>>(new Set())
  const [rollCallSubmitting, setRollCallSubmitting] = useState(false)
  const [rollCallResult, setRollCallResult] = useState<RollCallResult | null>(null)

  useEffect(() => {
    loadCourses()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function loadCourses() {
    setLoading(true)
    setLoadError(null)
    courseApi
      .fetchMyCourses()
      .then((list) => {
        setCourses(list)
        setActiveCourseId((prev) => prev ?? (list.length > 0 ? list[0].id : null))
        if (list.length === 0) setLoading(false)
      })
      .catch(() => {
        setLoadError('加载失败，请刷新重试')
        setLoading(false)
      })
  }

  useEffect(() => {
    if (activeCourseId === null) return
    setLoading(true)
    setLoadError(null)
    setRollCallResult(null)
    courseApi
      .fetchCourseRoster(activeCourseId)
      .then(applyRoster)
      .catch(() => setLoadError('加载花名册失败，请刷新重试'))
      .finally(() => setLoading(false))
  }, [activeCourseId])

  function applyRoster(list: courseApi.CourseRosterEntry[]) {
    // 老师不需要关心这个学生是纯课外课还是托管班（双重身份）——那是后端的记账口径，
    // 所以花名册不再按身份分组，直接按姓名排序。
    const sorted = [...list].sort((a, b) => a.name.localeCompare(b.name))
    setRoster(sorted)
    setPresentStudentIds(new Set(sorted.map((r) => r.studentId)))
  }

  function balanceBadgeClass(balance: number): string {
    if (balance <= 0) return 'balance-badge balance-badge--danger'
    if (balance < 3) return 'balance-badge balance-badge--warn'
    return 'balance-badge balance-badge--ok'
  }

  function togglePresent(studentId: number) {
    setPresentStudentIds((prev) => {
      const next = new Set(prev)
      if (next.has(studentId)) {
        next.delete(studentId)
      } else {
        next.add(studentId)
      }
      return next
    })
  }

  async function submitRollCall() {
    if (activeCourseId === null) return
    setRollCallResult(null)
    if (rollCallDate > todayDateString()) {
      setRollCallResult({ type: 'warning', message: '无法对未来日期进行消课处理！' })
      return
    }
    setRollCallSubmitting(true)
    try {
      const created = await courseApi.recordBatchConsumption(activeCourseId, rollCallDate, Array.from(presentStudentIds))
      setRollCallResult({
        type: 'success',
        message: created.length > 0 ? `已确认消课，新增 ${created.length} 条记录` : '该日期已全部确认过，无新增记录',
      })
    } catch (err) {
      setRollCallResult({
        type: 'warning',
        message: err instanceof ApiError && err.status === 400 ? '该课程尚未配置单价，请联系管理员配置' : '消课失败，请重试',
      })
    } finally {
      setRollCallSubmitting(false)
    }
  }

  const activeCourse = courses.find((c) => c.id === activeCourseId) ?? null
  const unpriced = activeCourse !== null && activeCourse.pricePerLesson === null

  return (
    <div className="min-h-screen pb-8">
      <header className="app-header">
        <div className="app-header__top">
          <h1 className="app-header__title">
            <BrandMark size={22} />
            课外消课
          </h1>
          <button type="button" onClick={onBack} className="logout-btn">
            返回看板
          </button>
        </div>
        {courses.length > 0 && (
          <div className="class-tabs" role="tablist">
            {courses.map((c) => (
              <button
                key={c.id}
                type="button"
                role="tab"
                aria-selected={activeCourseId === c.id}
                className="class-tab"
                onClick={() => setActiveCourseId(c.id)}
              >
                {c.name}
                {!c.active && <span className="ml-1 text-xs text-gray-400">（已停用）</span>}
              </button>
            ))}
          </div>
        )}
      </header>

      <main className="mx-auto max-w-2xl space-y-4 px-4 pt-4">
        {loadError && <p className="text-sm text-red-600">{loadError}</p>}
        {loading && <p className="text-sm text-gray-400">加载中...</p>}

        {!loading && courses.length === 0 && (
          <p className="text-sm text-gray-400">暂无课外课，请联系管理员分配</p>
        )}

        {!loading && activeCourseId !== null && courses.length > 0 && (
          <>
            <div className="roster-heading">
              <h2>花名册（{roster.length}）</h2>
              <span>已选 {presentStudentIds.size} 人</span>
            </div>
            {unpriced && <div className="unpriced-banner">该课程尚未配置单价，请联系管理员配置后再消课</div>}

            <ul className="roster-grid">
              {roster.map((entry) => {
                const present = presentStudentIds.has(entry.studentId)
                return (
                  <li key={entry.studentId}>
                    <label
                      className={`roster-card ${present ? 'is-present' : 'is-absent'}${unpriced ? ' roster-card--disabled' : ''}`}
                    >
                      <input
                        type="checkbox"
                        className="roster-card__input"
                        checked={present}
                        onChange={() => togglePresent(entry.studentId)}
                        disabled={unpriced}
                      />
                      <span className="roster-card__avatar" aria-hidden="true">
                        {entry.name.slice(0, 1)}
                      </span>
                      <span className="roster-card__body">
                        <span className="roster-card__name">{entry.name}</span>
                        <span className="roster-card__class">{entry.schoolClassName ?? '—'}</span>
                        {entry.balance !== null && (
                          <span className={balanceBadgeClass(entry.balance)}>余额：{entry.balance} 课时</span>
                        )}
                      </span>
                      <span className="roster-card__check" aria-hidden="true">
                        <svg viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round">
                          <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                      </span>
                    </label>
                  </li>
                )
              })}
              {roster.length === 0 && <li className="roster-empty">该课程暂无学生</li>}
            </ul>

            <p className="admin-hint">课外课报名由管理员在后台统一配置</p>

            {roster.length > 0 && (
              <div className="rollcall-bar">
                <input
                  type="date"
                  aria-label="消课日期"
                  value={rollCallDate}
                  onChange={(e) => setRollCallDate(e.target.value)}
                />
                <button
                  type="button"
                  className="submit"
                  onClick={submitRollCall}
                  disabled={unpriced || rollCallSubmitting || presentStudentIds.size === 0}
                >
                  确认消课（{presentStudentIds.size}人）
                </button>
              </div>
            )}
          </>
        )}
      </main>

      {rollCallResult && (
        <RollCallResultModal
          type={rollCallResult.type}
          message={rollCallResult.message}
          onClose={() => setRollCallResult(null)}
        />
      )}
    </div>
  )
}
