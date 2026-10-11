import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import * as platformApi from '../api/platform'

interface RenewalModalProps {
  institutionId: number
  institutionName: string
  currentValidUntil: string | null
  onClose: () => void
  onSaved: () => void
}

const PLAN_LABELS: Record<platformApi.RenewalPlan, string> = {
  TRIAL: '试用',
  MONTHLY: '月',
  HALF_YEAR: '半年',
  YEARLY: '年',
}

const PLAN_DEFAULT_AMOUNT: Record<platformApi.RenewalPlan, string> = {
  TRIAL: '',
  MONTHLY: '299',
  HALF_YEAR: '1499',
  YEARLY: '2899',
}

function todayString(): string {
  return new Date().toISOString().slice(0, 10)
}

function addDays(dateStr: string, days: number): string {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function addMonths(dateStr: string, months: number): string {
  const d = new Date(dateStr)
  const day = d.getDate()
  // 先把日期挪到 1 号再加月份，避免原日期是 29/30/31 号时，JS 的 setMonth
  // 溢出进位到下下个月（比如 1 月 31 号 + 1 个月被 JS 算成 3 月 3 号）。
  d.setDate(1)
  d.setMonth(d.getMonth() + months)
  const lastDayOfTargetMonth = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()
  d.setDate(Math.min(day, lastDayOfTargetMonth))
  return d.toISOString().slice(0, 10)
}

function baseDateFor(currentValidUntil: string | null): string {
  const today = todayString()
  if (!currentValidUntil) return today
  return currentValidUntil > today ? currentValidUntil : today
}

function suggestValidUntil(plan: platformApi.RenewalPlan, currentValidUntil: string | null): string {
  const base = baseDateFor(currentValidUntil)
  switch (plan) {
    case 'TRIAL':
      return addDays(base, 30)
    case 'MONTHLY':
      return addMonths(base, 1)
    case 'HALF_YEAR':
      return addMonths(base, 6)
    case 'YEARLY':
      return addMonths(base, 12)
  }
}

export function RenewalModal({
  institutionId,
  institutionName,
  currentValidUntil,
  onClose,
  onSaved,
}: RenewalModalProps) {
  const [history, setHistory] = useState<platformApi.RenewalRecord[]>([])
  const [loadingHistory, setLoadingHistory] = useState(true)

  const [plan, setPlan] = useState<platformApi.RenewalPlan>('MONTHLY')
  const [amount, setAmount] = useState(PLAN_DEFAULT_AMOUNT.MONTHLY)
  const [validUntil, setValidUntil] = useState(suggestValidUntil('MONTHLY', currentValidUntil))
  const [note, setNote] = useState('')

  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEffect(() => {
    platformApi
      .fetchRenewalHistory(institutionId)
      .then(setHistory)
      .finally(() => setLoadingHistory(false))
  }, [institutionId])

  function handlePlanChange(nextPlan: platformApi.RenewalPlan) {
    setPlan(nextPlan)
    setAmount(PLAN_DEFAULT_AMOUNT[nextPlan])
    setValidUntil(suggestValidUntil(nextPlan, currentValidUntil))
  }

  async function handleSubmit() {
    if (!validUntil) return
    setSaving(true)
    setSaveError(null)
    try {
      const parsedAmount = amount.trim() === '' ? null : Number(amount)
      await platformApi.createRenewal(institutionId, plan, parsedAmount, validUntil, note.trim())
      onSaved()
      onClose()
    } catch {
      setSaveError('续费记录失败，请重试')
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
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl border border-gray-200 bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="续费"
      >
        <div className="border-b border-gray-100 p-4">
          <h2 className="text-base font-semibold text-gray-800">续费 - {institutionName}</h2>
        </div>

        <div className="space-y-4 p-4">
          <section>
            <h3 className="text-sm font-semibold text-gray-700">历史记录</h3>
            {loadingHistory ? (
              <p className="mt-1 text-xs text-gray-400">加载中...</p>
            ) : (
              <ul className="mt-2 space-y-1 text-xs text-gray-600">
                {history.map((record) => (
                  <li key={record.id}>
                    <span>{PLAN_LABELS[record.plan]}</span>
                    {' · '}
                    <span>{record.amount !== null ? `¥${record.amount}` : '—'}</span>
                    {' · 到期'}
                    <span>{record.validUntil}</span>
                    {' · '}
                    <span>{record.recordedByTeacherName}</span>
                    {record.note && (
                      <>
                        {' · '}
                        <span>{record.note}</span>
                      </>
                    )}
                  </li>
                ))}
                {history.length === 0 && <li>暂无记录</li>}
              </ul>
            )}
          </section>

          <section className="flex flex-wrap items-end gap-2">
            <label className="flex flex-col text-xs text-gray-500">
              套餐
              <select
                aria-label="套餐"
                value={plan}
                onChange={(e) => handlePlanChange(e.target.value as platformApi.RenewalPlan)}
                className="rounded border px-2 py-1 text-sm text-gray-900"
              >
                <option value="TRIAL">试用</option>
                <option value="MONTHLY">月</option>
                <option value="HALF_YEAR">半年</option>
                <option value="YEARLY">年</option>
              </select>
            </label>
            <label className="flex flex-col text-xs text-gray-500">
              金额
              <input
                aria-label="金额"
                type="number"
                value={amount}
                disabled={plan === 'TRIAL'}
                onChange={(e) => setAmount(e.target.value)}
                className="w-24 rounded border px-2 py-1 text-sm text-gray-900 disabled:opacity-50"
              />
            </label>
            <label className="flex flex-col text-xs text-gray-500">
              新的到期日
              <input
                aria-label="新的到期日"
                type="date"
                value={validUntil}
                onChange={(e) => setValidUntil(e.target.value)}
                className="rounded border px-2 py-1 text-sm text-gray-900"
              />
            </label>
            <label className="flex flex-1 flex-col text-xs text-gray-500">
              备注（选填）
              <input
                aria-label="备注（选填）"
                value={note}
                onChange={(e) => setNote(e.target.value)}
                className="rounded border px-2 py-1 text-sm text-gray-900"
              />
            </label>
          </section>

          {saveError && <p className="text-sm text-red-600">{saveError}</p>}
        </div>

        <div className="flex justify-end gap-2 border-t border-gray-100 p-3">
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="rounded-full border border-gray-200 px-3 py-1 text-sm text-gray-600 disabled:opacity-50"
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={saving || !validUntil}
            className="rounded-full bg-blue-600 px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            {saving ? '提交中...' : '提交'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
