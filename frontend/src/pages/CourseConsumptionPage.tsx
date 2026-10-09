import { useEffect, useState } from 'react'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'
import { BrandMark } from '../brand/BrandMark'

interface CourseConsumptionPageProps {
  onBack: () => void
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
  const [rollCallError, setRollCallError] = useState<string | null>(null)
  const [rollCallSuccess, setRollCallSuccess] = useState<string | null>(null)

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
    setRollCallError(null)
    setRollCallSuccess(null)
    courseApi
      .fetchCourseRoster(activeCourseId)
      .then(applyRoster)
      .catch(() => setLoadError('加载花名册失败，请刷新重试'))
      .finally(() => setLoading(false))
  }, [activeCourseId])

  function applyRoster(list: courseApi.CourseRosterEntry[]) {
    const sorted = [...list].sort((a, b) => {
      // 纯课外课学生优先展示，托管班学生（双重身份）排在后面，组内按姓名排序。
      if (a.offCampusOnly !== b.offCampusOnly) return a.offCampusOnly ? -1 : 1
      return a.name.localeCompare(b.name)
    })
    setRoster(sorted)
    setPresentStudentIds(new Set(sorted.map((r) => r.studentId)))
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
    setRollCallError(null)
    setRollCallSuccess(null)
    if (rollCallDate > todayDateString()) {
      setRollCallError('无法对未来日期进行消课处理！')
      return
    }
    setRollCallSubmitting(true)
    try {
      const created = await courseApi.recordBatchConsumption(activeCourseId, rollCallDate, Array.from(presentStudentIds))
      setRollCallSuccess(created.length > 0 ? `已确认消课，新增 ${created.length} 条记录` : '该日期已全部确认过，无新增记录')
    } catch (err) {
      setRollCallError(
        err instanceof ApiError && err.status === 400 ? '该课程尚未配置单价，请联系管理员配置' : '消课失败，请重试',
      )
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
          <div className="rounded-lg border border-gray-200 bg-white p-3">
            <h2 className="text-sm font-medium text-gray-700">花名册（{roster.length}）</h2>
            {unpriced && (
              <p className="mt-1 text-xs text-amber-600">该课程尚未配置单价，请联系管理员配置后再消课</p>
            )}
            <ul className="mt-2 space-y-2">
              {roster.map((entry) => (
                <li key={entry.studentId} className="rounded border border-gray-100 p-2 text-sm">
                  <label className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={presentStudentIds.has(entry.studentId)}
                      onChange={() => togglePresent(entry.studentId)}
                      disabled={unpriced}
                    />
                    <span>
                      {entry.name}
                      {entry.schoolClassName && <span className="text-gray-500"> · {entry.schoolClassName}</span>}
                      <span
                        className={
                          entry.offCampusOnly
                            ? 'ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-700'
                            : 'ml-2 rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-700'
                        }
                      >
                        {entry.offCampusOnly ? '纯课外（扣课时余额）' : '托管（计入月度账单）'}
                      </span>
                      {entry.balance !== null && (
                        <span
                          className={
                            entry.balance <= 0
                              ? 'ml-2 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700'
                              : entry.balance < 3
                                ? 'ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700'
                                : 'ml-2 rounded-full bg-gray-100 px-2 py-0.5 text-xs text-gray-600'
                          }
                        >
                          余额：{entry.balance} 课时
                        </span>
                      )}
                    </span>
                  </label>
                </li>
              ))}
              {roster.length === 0 && <li className="text-xs text-gray-400">该课程暂无学生</li>}
            </ul>

            {roster.length > 0 && (
              <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-gray-100 pt-3">
                <input
                  type="date"
                  aria-label="消课日期"
                  value={rollCallDate}
                  onChange={(e) => setRollCallDate(e.target.value)}
                  className="rounded border px-2 py-1 text-sm"
                />
                <button
                  type="button"
                  onClick={submitRollCall}
                  disabled={unpriced || rollCallSubmitting || presentStudentIds.size === 0}
                  className="rounded bg-blue-600 px-3 py-1 text-sm text-white disabled:opacity-50"
                >
                  确认消课（{presentStudentIds.size}人）
                </button>
              </div>
            )}
            {rollCallError && (
              <p role="alert" className="mt-1 text-xs text-red-600">
                {rollCallError}
              </p>
            )}
            {rollCallSuccess && !rollCallError && (
              <p role="status" className="mt-1 text-xs text-green-600">
                {rollCallSuccess}
              </p>
            )}

            <p className="mt-3 border-t border-gray-100 pt-3 text-xs text-gray-400">
              课外课报名由管理员在后台统一配置
            </p>
          </div>
        )}
      </main>
    </div>
  )
}
