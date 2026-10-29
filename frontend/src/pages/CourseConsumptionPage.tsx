import { useEffect, useState } from 'react'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'
import { BrandMark } from '../brand/BrandMark'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { RollCallResultModal } from '../components/RollCallResultModal'
import { ShareLinkModal } from '../components/ShareLinkModal'
import { Toast } from '../components/Toast'
import { useToast } from '../hooks/useToast'
import { LOAD_FAILURE_MESSAGE } from '../constants'

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
  const [confirmingRollCall, setConfirmingRollCall] = useState(false)
  const [rollCallResult, setRollCallResult] = useState<RollCallResult | null>(null)
  const [sharingLinkStudent, setSharingLinkStudent] = useState<courseApi.CourseRosterEntry | null>(null)
  const { toastMessage, showToast } = useToast()

  // 用餐登记只对纯课外课学生生效（双重身份学生的用餐走托管看板那套，这里不重复
  // 记录/计费）。跟托管看板的"用餐"图标一个交互：点一下立即生效、再点一下取消，
  // 不是先勾选一批人再单独点"确认用餐"——这是两件独立的事实，不需要走消课那套
  // 批量+二次确认的流程。
  const [mealRecordedStudentIds, setMealRecordedStudentIds] = useState<Set<number>>(new Set())

  // 架构review发现的真实漏记风险：这个页面的日期选择器允许老师切到过去的日期做
  // 补登记，但用餐状态只在切课程时重置（applyRoster），不会随日期切换而重置——
  // 如果不清空，老师把日期从"今天"改成"昨天"（补登记昨天忘记标记的用餐），UI
  // 还残留着"今天"点过的"已用餐"图标，老师会误以为昨天也记过了从而不再点击，
  // 导致昨天这顿饭真的没被记录。日期一变就清空，让老师回到诚实的起点（全部未
  // 用餐），而不是显示一个确定过时/错误的状态。
  useEffect(() => {
    setMealRecordedStudentIds(new Set())
  }, [rollCallDate])

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
        setLoadError(LOAD_FAILURE_MESSAGE)
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
    setMealRecordedStudentIds(new Set())
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

  // 乐观更新：立即翻转图标状态，接口失败再翻回去——跟托管看板 KanbanPage 的
  // handleSetMeal/handleClearMeal 是同一套写法。
  async function handleMealClick(studentId: number) {
    if (activeCourseId === null) return
    const alreadyRecorded = mealRecordedStudentIds.has(studentId)
    setMealRecordedStudentIds((prev) => {
      const next = new Set(prev)
      if (alreadyRecorded) {
        next.delete(studentId)
      } else {
        next.add(studentId)
      }
      return next
    })
    try {
      if (alreadyRecorded) {
        await courseApi.clearMeal(activeCourseId, studentId, rollCallDate)
        showToast('已取消用餐记录')
      } else {
        await courseApi.setMeal(activeCourseId, studentId, rollCallDate)
        showToast('已记录用餐')
      }
    } catch {
      setMealRecordedStudentIds((prev) => {
        const next = new Set(prev)
        if (alreadyRecorded) {
          next.add(studentId)
        } else {
          next.delete(studentId)
        }
        return next
      })
    }
  }

  function handleRollCallClick() {
    if (activeCourseId === null) return
    setRollCallResult(null)
    if (rollCallDate > todayDateString()) {
      setRollCallResult({ type: 'warning', message: '无法对未来日期进行消课处理！' })
      return
    }
    setConfirmingRollCall(true)
  }

  async function submitRollCall() {
    if (activeCourseId === null) return
    setConfirmingRollCall(false)
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
                {!c.active && <span className="ml-1 text-xs text-panel-muted">（已停用）</span>}
              </button>
            ))}
          </div>
        )}
      </header>

      <main className="mx-auto max-w-2xl space-y-4 px-4 pt-4">
        {loadError && <p className="text-sm text-panel-danger">{loadError}</p>}
        {loading && <p className="text-sm text-panel-muted">加载中...</p>}

        {!loading && courses.length === 0 && (
          <p className="text-sm text-panel-muted">暂无课外课，请联系管理员分配</p>
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
                const hasMeal = mealRecordedStudentIds.has(entry.studentId)
                return (
                  <li key={entry.studentId}>
                    <label
                      className={`roster-card ${present ? 'is-present' : 'is-absent'}${unpriced ? ' roster-card--disabled' : ''}`}
                    >
                      <input
                        type="checkbox"
                        aria-label={entry.name}
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
                      <span className="roster-card__actions">
                        {entry.offCampusOnly && (
                          <button
                            type="button"
                            className={`roster-card__action-btn${hasMeal ? ' is-active' : ''}`}
                            aria-label={`${entry.name}${hasMeal ? '已用餐，点击取消' : '用餐'}`}
                            onClick={(e) => {
                              e.preventDefault()
                              e.stopPropagation()
                              void handleMealClick(entry.studentId)
                            }}
                          >
                            🍚
                          </button>
                        )}
                        <button
                          type="button"
                          className="roster-card__action-btn"
                          aria-label={`查看${entry.name}的家长专属链接`}
                          onClick={(e) => {
                            e.preventDefault()
                            e.stopPropagation()
                            setSharingLinkStudent(entry)
                          }}
                        >
                          🔗
                        </button>
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
                  onClick={handleRollCallClick}
                  disabled={unpriced || rollCallSubmitting || presentStudentIds.size === 0}
                >
                  确认消课（{presentStudentIds.size}人）
                </button>
              </div>
            )}
          </>
        )}
      </main>

      {confirmingRollCall && (
        <ConfirmDialog
          title="确认消课"
          message={`请确认针对${presentStudentIds.size}个孩子进行消课处理吗，此操作不可更改？`}
          confirmLabel="确认消课"
          confirming={rollCallSubmitting}
          onConfirm={submitRollCall}
          onCancel={() => setConfirmingRollCall(false)}
        />
      )}

      {rollCallResult && (
        <RollCallResultModal
          type={rollCallResult.type}
          message={rollCallResult.message}
          onClose={() => setRollCallResult(null)}
        />
      )}

      {sharingLinkStudent && (
        <ShareLinkModal
          studentId={sharingLinkStudent.studentId}
          studentName={sharingLinkStudent.name}
          onClose={() => setSharingLinkStudent(null)}
          onShowToast={showToast}
        />
      )}

      <Toast message={toastMessage} />
    </div>
  )
}
