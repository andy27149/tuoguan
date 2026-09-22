import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import * as billingApi from '../api/billing'
import { ApiError } from '../api/client'

interface FeeManagementModalProps {
  studentId: number
  studentName: string
  classRoomId: number
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

  const [extraFees, setExtraFees] = useState<billingApi.StudentExtraFeeRow[]>([])
  const [loadingExtraFees, setLoadingExtraFees] = useState(true)
  const [extraFeeName, setExtraFeeName] = useState('')
  const [extraFeePricePerLesson, setExtraFeePricePerLesson] = useState('')
  const [extraFeeSubmitting, setExtraFeeSubmitting] = useState(false)
  const [extraFeeError, setExtraFeeError] = useState<string | null>(null)

  const [lessonCountInputs, setLessonCountInputs] = useState<Record<number, string>>({})
  const [savingLessonCountId, setSavingLessonCountId] = useState<number | null>(null)
  const [lessonCountError, setLessonCountError] = useState<string | null>(null)

  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEffect(() => {
    billingApi
      .fetchClassBillingRate(classRoomId)
      .then((rate) => setTuitionInput(rate ? String(rate.tuitionRatePerMonth) : ''))
      .finally(() => setLoadingRate(false))
    billingApi
      .fetchStudentLeaveRecords(studentId, month)
      .then(setLeaveRecords)
      .finally(() => setLoadingLeave(false))
    billingApi
      .fetchStudentExtraFees(studentId, month)
      .then((fees) => {
        setExtraFees(fees)
        setLessonCountInputs(Object.fromEntries(fees.map((f) => [f.id, String(f.lessonCount)])))
      })
      .finally(() => setLoadingExtraFees(false))
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

  async function handleAddExtraFee() {
    const name = extraFeeName.trim()
    const pricePerLesson = Number(extraFeePricePerLesson)
    if (!name || !Number.isFinite(pricePerLesson) || pricePerLesson < 0) return
    setExtraFeeSubmitting(true)
    setExtraFeeError(null)
    try {
      await billingApi.addStudentExtraFee(studentId, name, pricePerLesson)
      const fees = await billingApi.fetchStudentExtraFees(studentId, month)
      setExtraFees(fees)
      setLessonCountInputs(Object.fromEntries(fees.map((f) => [f.id, String(f.lessonCount)])))
      setExtraFeeName('')
      setExtraFeePricePerLesson('')
    } catch (err) {
      setExtraFeeError(err instanceof ApiError && err.status === 409 ? '该课外费项目已存在' : '添加失败，请重试')
    } finally {
      setExtraFeeSubmitting(false)
    }
  }

  async function handleDeleteExtraFee(feeId: number) {
    await billingApi.deleteStudentExtraFee(studentId, feeId)
    setExtraFees((prev) => prev.filter((f) => f.id !== feeId))
  }

  async function handleSaveLessonCount(feeId: number) {
    const input = lessonCountInputs[feeId] ?? ''
    const lessonCount = Number(input)
    if (!Number.isInteger(lessonCount) || lessonCount < 0) {
      setLessonCountError('请输入有效的上课数')
      return
    }
    setSavingLessonCountId(feeId)
    setLessonCountError(null)
    try {
      const updated = await billingApi.setExtraFeeLessonCount(studentId, feeId, month, lessonCount)
      setExtraFees((prev) => prev.map((f) => (f.id === feeId ? updated : f)))
    } catch {
      setLessonCountError('保存失败，请重试')
    } finally {
      setSavingLessonCountId(null)
    }
  }

  async function handleSave() {
    const tuition = Number(tuitionInput)
    if (!Number.isFinite(tuition) || tuition < 0) {
      setSaveError('请输入有效的托管费金额')
      return
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
            <h3 className="text-sm font-semibold text-[#241f3d]">课外费管理</h3>
            {loadingExtraFees ? (
              <p className="mt-1 text-xs text-[#7c7391]">加载中...</p>
            ) : (
              <table className="mt-2 w-full text-left text-xs">
                <thead>
                  <tr className="text-[#7c7391]">
                    <th className="py-1 font-normal">课程名称</th>
                    <th className="py-1 font-normal">每节课价格</th>
                    <th className="py-1 font-normal">本月上课数</th>
                    <th className="py-1 font-normal">金额</th>
                    <th className="py-1 font-normal">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {extraFees.map((f) => {
                    const input = lessonCountInputs[f.id] ?? ''
                    const count = Number(input)
                    const amount = f.pricePerLesson * (Number.isFinite(count) ? count : 0)
                    return (
                      <tr key={f.id} className="border-t border-[#ece7de]">
                        <td className="py-1.5">{f.name}</td>
                        <td className="py-1.5">¥{f.pricePerLesson.toFixed(2)}</td>
                        <td className="py-1.5">
                          <input
                            aria-label={`${f.name}上课数`}
                            value={input}
                            onChange={(e) =>
                              setLessonCountInputs((prev) => ({ ...prev, [f.id]: e.target.value }))
                            }
                            className="w-14 rounded-lg border border-[#ece7de] px-1.5 py-0.5"
                          />
                        </td>
                        <td className="py-1.5">¥{amount.toFixed(2)}</td>
                        <td className="py-1.5">
                          <div className="flex items-center gap-1.5">
                            <button
                              type="button"
                              onClick={() => handleSaveLessonCount(f.id)}
                              disabled={savingLessonCountId === f.id}
                              className="rounded-full bg-[#6d5bd0] px-2 py-0.5 text-white disabled:opacity-50"
                            >
                              保存
                            </button>
                            <button
                              type="button"
                              aria-label={`删除课外费${f.name}`}
                              onClick={() => handleDeleteExtraFee(f.id)}
                              className="text-[#b7591f]"
                            >
                              删除
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                  {extraFees.length === 0 && (
                    <tr>
                      <td colSpan={5} className="py-1.5 text-[#7c7391]">
                        暂无课外费项目
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            )}
            {lessonCountError && <p className="mt-1 text-xs text-[#b7591f]">{lessonCountError}</p>}
            <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs">
              <input
                placeholder="课程名称"
                value={extraFeeName}
                onChange={(e) => setExtraFeeName(e.target.value)}
                className="w-24 rounded-lg border border-[#ece7de] px-1.5 py-1"
              />
              <input
                placeholder="每节课价格"
                value={extraFeePricePerLesson}
                onChange={(e) => setExtraFeePricePerLesson(e.target.value)}
                className="w-24 rounded-lg border border-[#ece7de] px-1.5 py-1"
              />
              <button
                type="button"
                onClick={handleAddExtraFee}
                disabled={extraFeeSubmitting || !extraFeeName.trim() || !extraFeePricePerLesson.trim()}
                className="rounded-full bg-[#6d5bd0] px-2.5 py-1 text-white disabled:opacity-50"
              >
                添加
              </button>
            </div>
            {extraFeeError && <p className="mt-1 text-xs text-[#b7591f]">{extraFeeError}</p>}
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
