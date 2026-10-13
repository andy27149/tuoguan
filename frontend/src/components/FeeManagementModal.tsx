import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import * as billingApi from '../api/billing'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'
import { useEscapeToClose } from '../hooks/useEscapeToClose'

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
    courseApi
      .fetchStudentCourseConsumption(studentId, month)
      .then(setCourseConsumption)
      .finally(() => setLoadingCourseConsumption(false))
  }, [studentId, classRoomId, month])

  useEscapeToClose(onClose)

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
        className="max-h-[90vh] w-full max-w-[39.2rem] overflow-y-auto rounded-2xl border border-panel-border bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="费用管理"
      >
        <div className="flex items-center justify-between border-b border-panel-border p-4">
          <h2 className="font-['Sora'] text-base font-semibold text-panel-ink">
            费用管理 - {studentName}（{className}，{month}）
          </h2>
          <button
            type="button"
            aria-label="关闭"
            onClick={onClose}
            className="rounded-lg px-1.5 py-0.5 text-xl leading-none text-panel-muted hover:bg-[#faf7ff] hover:text-panel-ink"
          >
            ×
          </button>
        </div>

        <div className="space-y-4 p-4">
          {classRoomId !== null && (
            <section>
              <h3 className="text-sm font-semibold text-panel-ink">本月托管费金额</h3>
              {loadingRate ? (
                <p className="mt-1 text-xs text-panel-muted">加载中...</p>
              ) : (
                <div className="mt-2 flex items-center gap-2 text-sm">
                  <span>¥</span>
                  <input
                    aria-label="本月托管费金额"
                    value={tuitionInput}
                    onChange={(e) => setTuitionInput(e.target.value)}
                    className="w-24 rounded-lg border border-panel-border px-2 py-1 text-sm"
                  />
                  <span className="text-xs text-panel-muted">默认取班级托管费单价，仅本月生效</span>
                </div>
              )}
            </section>
          )}

          <section>
            <h3 className="text-sm font-semibold text-panel-ink">课外课消课（本月，只读）</h3>
            <p className="mt-1 text-xs text-panel-muted">
              已用预充值余额抵扣的消课不会重复计入账单，只有余额覆盖不了的部分才会收费。
            </p>
            {loadingCourseConsumption ? (
              <p className="mt-1 text-xs text-panel-muted">加载中...</p>
            ) : (
              <table className="mt-2 w-full text-left text-xs">
                <thead>
                  <tr className="text-panel-muted">
                    <th className="py-1 font-normal">课程名称</th>
                    <th className="py-1 font-normal">当前单价</th>
                    <th className="py-1 font-normal">本月消课</th>
                    <th className="py-1 font-normal">金额（需入账单）</th>
                  </tr>
                </thead>
                <tbody>
                  {courseConsumption.map((row) => (
                    <tr key={row.courseId} className="border-t border-panel-border">
                      <td className="py-1.5">{row.courseName}</td>
                      <td className="py-1.5">
                        {row.pricePerLesson !== null ? `¥${row.pricePerLesson.toFixed(2)}` : '未配置'}
                      </td>
                      <td className="py-1.5">
                        {row.lessonCount + row.coveredByBalanceCount} 次
                        {row.coveredByBalanceCount > 0 && (
                          <span className="text-panel-muted">（其中 {row.coveredByBalanceCount} 次已用预充值抵扣）</span>
                        )}
                      </td>
                      <td className="py-1.5">
                        ¥{row.amount.toFixed(2)}
                        {row.settledByRecharge && <span className="text-panel-muted">（已通过后续充值补齐）</span>}
                      </td>
                    </tr>
                  ))}
                  {courseConsumption.length === 0 && (
                    <tr>
                      <td colSpan={4} className="py-1.5 text-panel-muted">
                        本月暂无课外课消课记录
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
          </section>

          {saveError && <p className="text-sm text-panel-warn">{saveError}</p>}
        </div>

        <div className="flex justify-end gap-2 border-t border-panel-border p-3">
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="rounded-full border border-panel-border px-3 py-1 text-sm text-[#5d5480] disabled:opacity-50 hover:bg-[#faf7ff]"
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving}
            className="rounded-full bg-panel-primary px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            {saving ? '生成中...' : '保存并生成账单'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
