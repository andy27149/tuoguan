import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'

interface AdminCourseStatementModalProps {
  studentId: number
  studentName: string
  onClose: () => void
}

export function AdminCourseStatementModal({ studentId, studentName, onClose }: AdminCourseStatementModalProps) {
  const [statement, setStatement] = useState<courseApi.StudentCourseStatement | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [courses, setCourses] = useState<courseApi.AdminCourse[]>([])
  const [rechargeCourseId, setRechargeCourseId] = useState('')
  const [rechargeLessonCount, setRechargeLessonCount] = useState('')
  const [rechargeNote, setRechargeNote] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  function loadStatement() {
    setLoading(true)
    setLoadError(null)
    courseApi
      .fetchStudentCourseStatement(studentId)
      .then(setStatement)
      .catch(() => setLoadError('加载对账单失败，请刷新重试'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadStatement()
    courseApi
      .fetchAdminCourses()
      .then((all) => setCourses(all.filter((c) => c.active)))
      .catch(() => setCourses([]))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [studentId])

  async function handleRecharge() {
    const courseId = Number(rechargeCourseId)
    const lessonCount = Number(rechargeLessonCount)
    if (!rechargeCourseId) {
      setSubmitError('请选择课程')
      return
    }
    if (!Number.isInteger(lessonCount) || lessonCount <= 0) {
      setSubmitError('请输入有效的课时数')
      return
    }
    setSubmitting(true)
    setSubmitError(null)
    try {
      await courseApi.rechargeStudentAccount(studentId, courseId, lessonCount, rechargeNote.trim() || null)
      setRechargeLessonCount('')
      setRechargeNote('')
      loadStatement()
    } catch (err) {
      setSubmitError(err instanceof ApiError && err.status === 400 ? '该学生不支持预充值' : '充值失败，请重试')
    } finally {
      setSubmitting(false)
    }
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        className="max-h-[90vh] w-full max-w-[39.2rem] overflow-y-auto rounded-2xl border border-[#ece7de] bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="课外账户对账单"
      >
        <div className="border-b border-[#ece7de] p-4">
          <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">课外账户对账单 - {studentName}</h2>
        </div>

        <div className="space-y-4 p-4">
          {loading && <p className="text-sm text-[#7c7391]">加载中...</p>}
          {loadError && <p className="text-sm text-[#b7591f]">{loadError}</p>}

          {statement && (
            <>
              <section>
                <h3 className="text-sm font-semibold text-[#241f3d]">各课程课时余额</h3>
                <ul className="mt-2 space-y-1 text-sm">
                  {statement.balances.map((b) => (
                    <li
                      key={b.courseId}
                      className="flex items-center justify-between rounded-lg border border-[#ece7de] px-3 py-2"
                    >
                      <span className="text-[#241f3d]">{b.courseName ?? `课程#${b.courseId}`}</span>
                      <span
                        className={
                          b.balance < 0
                            ? 'font-semibold text-[#b7591f]'
                            : 'font-semibold text-[#2f9e6e]'
                        }
                      >
                        剩 {b.balance} 课时（充 {b.lessonsRecharged} · 消 {b.lessonsConsumed}）
                      </span>
                    </li>
                  ))}
                  {statement.balances.length === 0 && <li className="text-[#7c7391]">暂无课程记录</li>}
                </ul>
              </section>

              <section>
                <h3 className="text-sm font-semibold text-[#241f3d]">充值课时</h3>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-sm">
                  <select
                    aria-label="充值课程"
                    value={rechargeCourseId}
                    onChange={(e) => setRechargeCourseId(e.target.value)}
                    className="rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                  >
                    <option value="">选择课程</option>
                    {courses.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                  <input
                    aria-label="充值课时数"
                    placeholder="课时数"
                    value={rechargeLessonCount}
                    onChange={(e) => setRechargeLessonCount(e.target.value)}
                    className="w-20 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                  />
                  <input
                    placeholder="备注（选填）"
                    value={rechargeNote}
                    onChange={(e) => setRechargeNote(e.target.value)}
                    className="w-32 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                  />
                  <button
                    type="button"
                    onClick={handleRecharge}
                    disabled={submitting || !rechargeCourseId || !rechargeLessonCount.trim()}
                    className="rounded-full bg-[#6d5bd0] px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
                  >
                    充值
                  </button>
                </div>
                {submitError && <p className="mt-1 text-xs text-[#b7591f]">{submitError}</p>}
              </section>

              <section>
                <h3 className="text-sm font-semibold text-[#241f3d]">充值记录</h3>
                <ul className="mt-2 space-y-1 text-xs text-[#5d5480]">
                  {statement.recharges.map((r) => (
                    <li key={r.id} className="flex justify-between border-b border-[#ece7de] py-1">
                      <span>
                        {new Date(r.createdAt).toLocaleString()} · {r.courseName ?? `课程#${r.courseId}`}
                        {r.note && ` · ${r.note}`}
                      </span>
                      <span className="font-medium text-[#241f3d]">+{r.lessonCount} 课时</span>
                    </li>
                  ))}
                  {statement.recharges.length === 0 && <li className="text-[#7c7391]">暂无充值记录</li>}
                </ul>
              </section>

              <section>
                <h3 className="text-sm font-semibold text-[#241f3d]">消课记录</h3>
                <ul className="mt-2 space-y-1 text-xs text-[#5d5480]">
                  {statement.consumptions.map((c) => (
                    <li key={c.id} className="flex justify-between border-b border-[#ece7de] py-1">
                      <span>
                        {c.consumptionDate} · {c.courseName ?? `课程#${c.courseId}`}
                        {c.teacherName && ` · ${c.teacherName}老师`}
                      </span>
                      <span className="font-medium text-[#241f3d]">-1 课时</span>
                    </li>
                  ))}
                  {statement.consumptions.length === 0 && <li className="text-[#7c7391]">暂无消课记录</li>}
                </ul>
              </section>
            </>
          )}
        </div>

        <div className="flex justify-end gap-2 border-t border-[#ece7de] p-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-[#ece7de] px-3 py-1 text-sm text-[#5d5480] hover:bg-[#faf7ff]"
          >
            关闭
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
