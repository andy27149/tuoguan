import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import * as billingApi from '../api/billing'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'

interface FeeManagementModalProps {
  studentId: number
  studentName: string
  // null：纯课外课学生，没有托管班——跳过托管费单价查询和输入框，只处理课外课消课账单。
  classRoomId: number | null
  className: string
  month: string
  onClose: () => void
  onSaved: () => void
}

export function FeeManagementModal({
  studentId,
  studentName,
  classRoomId,
  className,
  month,
  onClose,
  onSaved,
}: FeeManagementModalProps) {
  const [tuitionInput, setTuitionInput] = useState('')
  const [loadingRate, setLoadingRate] = useState(true)

  const [leaveRecords, setLeaveRecords] = useState<billingApi.StudentLeaveRecord[]>([])
  const [loadingLeave, setLoadingLeave] = useState(true)
  const [leaveStart, setLeaveStart] = useState('')
  const [leaveEnd, setLeaveEnd] = useState('')
  const [leaveReason, setLeaveReason] = useState('')
  const [leaveSubmitting, setLeaveSubmitting] = useState(false)
  const [leaveError, setLeaveError] = useState<string | null>(null)

  const [courseConsumption, setCourseConsumption] = useState<courseApi.CourseConsumptionSummaryRow[]>([])
  const [loadingCourseConsumption, setLoadingCourseConsumption] = useState(true)

  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEffect(() => {
    if (classRoomId === null) {
      setLoadingRate(false)
    } else {
      billingApi
        .fetchClassBillingRate(classRoomId)
        .then((rate) => setTuitionInput(rate ? String(rate.tuitionRatePerMonth) : ''))
        .finally(() => setLoadingRate(false))
    }
    billingApi
      .fetchStudentLeaveRecords(studentId, month)
      .then(setLeaveRecords)
      .finally(() => setLoadingLeave(false))
    courseApi
      .fetchStudentCourseConsumption(studentId, month)
      .then(setCourseConsumption)
      .finally(() => setLoadingCourseConsumption(false))
  }, [studentId, classRoomId, month])

  async function handleAddLeave() {
    if (!leaveStart || !leaveEnd) return
    setLeaveSubmitting(true)
    setLeaveError(null)
    try {
      await billingApi.registerStudentLeaveRange(studentId, leaveStart, leaveEnd, leaveReason || undefined)
      const records = await billingApi.fetchStudentLeaveRecords(studentId, month)
      setLeaveRecords(records)
      setLeaveStart('')
      setLeaveEnd('')
      setLeaveReason('')
    } catch (err) {
      setLeaveError(err instanceof ApiError && err.status === 400 ? '结束日期不能早于开始日期' : '登记失败，请重试')
    } finally {
      setLeaveSubmitting(false)
    }
  }

  async function handleCancelLeave(date: string) {
    await billingApi.cancelStudentLeave(studentId, date)
    setLeaveRecords((prev) => prev.filter((r) => r.leaveDate !== date))
  }

  async function handleSave() {
    let tuition: number | undefined
    if (classRoomId !== null) {
      tuition = Number(tuitionInput)
      if (!Number.isFinite(tuition) || tuition < 0) {
        setSaveError('请输入有效的托管费金额')
        return
      }
    }
    setSaving(true)
    setSaveError(null)
    try {
      await billingApi.generateStudentBill(studentId, month, tuition)
      onSaved()
      onClose()
    } catch (err) {
      setSaveError(
        err instanceof ApiError && err.status === 400 ? '班级未配置计费单价，请先配置' : '生成账单失败，请重试',
      )
    } finally {
      setSaving(false)
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
        aria-label="费用管理"
      >
        <div className="border-b border-[#ece7de] p-4">
          <h2 className="font-['Sora'] text-base font-semibold text-[#241f3d]">
            费用管理 - {studentName}（{className}，{month}）
          </h2>
        </div>

        <div className="space-y-4 p-4">
          {classRoomId !== null && (
            <section>
              <h3 className="text-sm font-semibold text-[#241f3d]">本月托管费金额</h3>
              {loadingRate ? (
                <p className="mt-1 text-xs text-[#7c7391]">加载中...</p>
              ) : (
                <div className="mt-2 flex items-center gap-2 text-sm">
                  <span>¥</span>
                  <input
                    aria-label="本月托管费金额"
                    value={tuitionInput}
                    onChange={(e) => setTuitionInput(e.target.value)}
                    className="w-24 rounded-lg border border-[#ece7de] px-2 py-1 text-sm"
                  />
                  <span className="text-xs text-[#7c7391]">默认取班级托管费单价，仅本月生效</span>
                </div>
              )}
            </section>
          )}

          <section>
            <h3 className="text-sm font-semibold text-[#241f3d]">请假管理</h3>
            {loadingLeave ? (
              <p className="mt-1 text-xs text-[#7c7391]">加载中...</p>
            ) : (
              <ul className="mt-2 flex flex-wrap gap-1.5 text-xs">
                {leaveRecords.map((r) => (
                  <li
                    key={r.leaveDate}
                    className="flex items-center gap-1 rounded-full border border-[#ece7de] bg-[#faf7ff] px-2 py-0.5"
                  >
                    {r.leaveDate}
                    <button
                      type="button"
                      aria-label={`取消请假${r.leaveDate}`}
                      onClick={() => handleCancelLeave(r.leaveDate)}
                      className="text-[#b7591f]"
                    >
                      ×
                    </button>
                  </li>
                ))}
                {leaveRecords.length === 0 && <li className="text-[#7c7391]">本月暂无请假记录</li>}
              </ul>
            )}
            <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
              <input
                type="date"
                value={leaveStart}
                onChange={(e) => setLeaveStart(e.target.value)}
                className="rounded-lg border border-[#ece7de] px-1.5 py-1"
              />
              <span>至</span>
              <input
                type="date"
                value={leaveEnd}
                onChange={(e) => setLeaveEnd(e.target.value)}
                className="rounded-lg border border-[#ece7de] px-1.5 py-1"
              />
              <input
                placeholder="事由（选填）"
                value={leaveReason}
                onChange={(e) => setLeaveReason(e.target.value)}
                className="w-24 rounded-lg border border-[#ece7de] px-1.5 py-1"
              />
              <button
                type="button"
                onClick={handleAddLeave}
                disabled={leaveSubmitting || !leaveStart || !leaveEnd}
                className="rounded-full bg-[#6d5bd0] px-2.5 py-1 text-white disabled:opacity-50"
              >
                登记请假
              </button>
            </div>
            {leaveError && <p className="mt-1 text-xs text-[#b7591f]">{leaveError}</p>}
          </section>

          <section>
            <h3 className="text-sm font-semibold text-[#241f3d]">课外课消课（本月，只读）</h3>
            <p className="mt-1 text-xs text-[#7c7391]">
              已用预充值余额抵扣的消课不会重复计入账单，只有余额覆盖不了的部分才会收费。
            </p>
            {loadingCourseConsumption ? (
              <p className="mt-1 text-xs text-[#7c7391]">加载中...</p>
            ) : (
              <table className="mt-2 w-full text-left text-xs">
                <thead>
                  <tr className="text-[#7c7391]">
                    <th className="py-1 font-normal">课程名称</th>
                    <th className="py-1 font-normal">当前单价</th>
                    <th className="py-1 font-normal">本月消课</th>
                    <th className="py-1 font-normal">金额（需入账单）</th>
                  </tr>
                </thead>
                <tbody>
                  {courseConsumption.map((row) => (
                    <tr key={row.courseId} className="border-t border-[#ece7de]">
                      <td className="py-1.5">{row.courseName}</td>
                      <td className="py-1.5">
                        {row.pricePerLesson !== null ? `¥${row.pricePerLesson.toFixed(2)}` : '未配置'}
                      </td>
                      <td className="py-1.5">
                        {row.lessonCount + row.coveredByBalanceCount} 次
                        {row.coveredByBalanceCount > 0 && (
                          <span className="text-[#7c7391]">（其中 {row.coveredByBalanceCount} 次已用预充值抵扣）</span>
                        )}
                      </td>
                      <td className="py-1.5">¥{row.amount.toFixed(2)}</td>
                    </tr>
                  ))}
                  {courseConsumption.length === 0 && (
                    <tr>
                      <td colSpan={4} className="py-1.5 text-[#7c7391]">
                        本月暂无课外课消课记录
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
          </section>

          {saveError && <p className="text-sm text-[#b7591f]">{saveError}</p>}
        </div>

        <div className="flex justify-end gap-2 border-t border-[#ece7de] p-3">
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="rounded-full border border-[#ece7de] px-3 py-1 text-sm text-[#5d5480] disabled:opacity-50 hover:bg-[#faf7ff]"
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="rounded-full bg-[#6d5bd0] px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            {saving ? '生成中...' : '保存并生成账单'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
