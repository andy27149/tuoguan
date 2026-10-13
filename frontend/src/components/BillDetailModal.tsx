import { useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import html2canvas from 'html2canvas-pro'
import { PAYMENT_METHOD_LABEL, type MonthlyBill } from '../api/billing'
import { BillCard } from './BillCard'
import { useEscapeToClose } from '../hooks/useEscapeToClose'

interface BillDetailModalProps {
  bill: MonthlyBill
  studentName: string
  className: string
  onClose: () => void
}

export async function exportBillAsImage(element: HTMLElement, filename: string) {
  // scale: 3（原先是 2）：卡片宽度固定 360px，在多数手机屏幕上以实际尺寸查看/转发时
  // 分辨率明显不够清晰（产品反馈 #52），提到 3 倍后导出图约 1080px 宽，接近主流手机
  // 截图的清晰度。
  const canvas = await html2canvas(element, { backgroundColor: '#ffffff', scale: 3 })
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

  useEscapeToClose(onClose)

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
        className="relative w-full max-w-md rounded-2xl border border-panel-border bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="账单详情"
      >
        <button
          type="button"
          aria-label="关闭弹窗"
          onClick={onClose}
          className="absolute right-2 top-2 rounded-lg px-1.5 py-0.5 text-xl leading-none text-panel-muted hover:bg-[#faf7ff] hover:text-panel-ink"
        >
          ×
        </button>
        <div className="flex justify-center border-b border-panel-border p-3">
          <BillCard ref={cardRef} bill={bill} studentName={studentName} className={className} />
        </div>
        {bill.isPaid && bill.paymentMethod && (
          <p className="px-4 pt-2 text-xs text-panel-muted">收款方式：{PAYMENT_METHOD_LABEL[bill.paymentMethod]}</p>
        )}
        {exportError && <p className="px-4 pt-2 text-xs text-panel-warn">{exportError}</p>}
        <div className="flex justify-end gap-2 p-3">
          <button
            type="button"
            onClick={onClose}
            className="rounded-full border border-panel-border px-3 py-1 text-sm text-[#5d5480] hover:bg-[#faf7ff]"
          >
            关闭
          </button>
          <button
            type="button"
            onClick={handleExport}
            disabled={exporting}
            className="rounded-full bg-panel-primary px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            {exporting ? '导出中...' : '导出图片'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
