import { useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import html2canvas from 'html2canvas-pro'
import type { MonthlyBill } from '../api/billing'
import { BillCard } from './BillCard'

interface BillDetailModalProps {
  bill: MonthlyBill
  studentName: string
  className: string
  onClose: () => void
}

export async function exportBillAsImage(element: HTMLElement, filename: string) {
  const canvas = await html2canvas(element, { backgroundColor: '#ffffff', scale: 2 })
  const link = document.createElement('a')
  link.download = filename
  link.href = canvas.toDataURL('image/png')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

export function BillDetailModal({ bill, studentName, className, onClose }: BillDetailModalProps) {
  const cardRef = useRef<HTMLDivElement>(null)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)
  const isPureOffCampus = bill.teachingUnitId === null
  const hasMealDates = !isPureOffCampus && bill.mealRecordDates.length > 0
  const hasLeaveLines = !isPureOffCampus && bill.leaveLines.length > 0

  async function handleExport() {
    if (!cardRef.current) return
    setExporting(true)
    setExportError(null)
    try {
      await exportBillAsImage(cardRef.current, `${studentName}-${bill.yearMonth}-账单.png`)
    } catch {
      setExportError('导出图片失败，请重试')
    } finally {
      setExporting(false)
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
        className="w-full max-w-sm rounded-2xl border border-[#ece7de] bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="账单详情"
      >
        <div className="flex justify-center border-b border-[#ece7de] p-3">
          <BillCard ref={cardRef} bill={bill} studentName={studentName} className={className} />
        </div>
        {hasMealDates && (
          <div className="border-b border-[#ece7de] px-4 py-3">
            <p className="text-xs font-medium text-[#5d5480]">用餐日期（共{bill.mealRecordDates.length}天）</p>
            <div className="mt-1.5 flex flex-wrap gap-1.5">
              {[...bill.mealRecordDates]
                .sort()
                .map((d) => (
                  <span
                    key={d}
                    className="rounded-full bg-[#faf7ff] px-2 py-0.5 text-xs text-[#5d5480]"
                  >
                    {d}
                  </span>
                ))}
            </div>
          </div>
        )}
        {hasLeaveLines && (
          <div className="border-b border-[#ece7de] px-4 py-3">
            <p className="text-xs font-medium text-[#5d5480]">请假日期（共{bill.leaveLines.length}天）</p>
            <div className="mt-1.5 flex flex-wrap gap-1.5">
              {[...bill.leaveLines]
                .sort((a, b) => a.leaveDate.localeCompare(b.leaveDate))
                .map((line) => (
                  <span
                    key={line.leaveDate}
                    className="rounded-full bg-[#faf7ff] px-2 py-0.5 text-xs text-[#5d5480]"
                  >
                    {line.leaveDate} · {line.reason ?? '未填写原因'}
                  </span>
                ))}
            </div>
          </div>
        )}
        {exportError && <p className="px-4 pt-2 text-xs text-[#b7591f]">{exportError}</p>}
        <div className="flex justify-end gap-2 p-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-[#ece7de] px-3 py-1 text-sm text-[#5d5480] hover:bg-[#faf7ff]"
          >
            关闭
          </button>
          <button
            type="button"
            onClick={handleExport}
            disabled={exporting}
            className="rounded-full bg-[#6d5bd0] px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            {exporting ? '导出中...' : '导出图片'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
