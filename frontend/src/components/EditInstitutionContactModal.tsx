import { useState } from 'react'
import { createPortal } from 'react-dom'
import * as platformApi from '../api/platform'
import { useEscapeToClose } from '../hooks/useEscapeToClose'

interface EditInstitutionContactModalProps {
  institutionId: number
  institutionName: string
  initialContactName: string | null
  initialContactPhone: string | null
  initialAddress: string | null
  onClose: () => void
  onSaved: () => void
}

export function EditInstitutionContactModal({
  institutionId,
  institutionName,
  initialContactName,
  initialContactPhone,
  initialAddress,
  onClose,
  onSaved,
}: EditInstitutionContactModalProps) {
  const [contactName, setContactName] = useState(initialContactName ?? '')
  const [contactPhone, setContactPhone] = useState(initialContactPhone ?? '')
  const [address, setAddress] = useState(initialAddress ?? '')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  useEscapeToClose(onClose)

  async function handleSubmit() {
    setSaving(true)
    setSaveError(null)
    try {
      await platformApi.updateInstitutionContact(institutionId, contactName.trim(), contactPhone.trim(), address.trim())
      onSaved()
      onClose()
    } catch {
      setSaveError('保存失败，请重试')
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
        className="w-full max-w-sm rounded-2xl border border-panel-border bg-white shadow-xl"
        role="dialog"
        aria-modal="true"
        aria-label="编辑联系信息"
      >
        <div className="flex items-center justify-between border-b border-panel-border p-4">
          <h2 className="text-base font-semibold text-panel-ink">编辑联系信息 - {institutionName}</h2>
          <button
            type="button"
            aria-label="关闭"
            onClick={onClose}
            className="rounded-lg px-1.5 py-0.5 text-xl leading-none text-panel-muted hover:bg-[#faf7ff] hover:text-panel-ink"
          >
            ×
          </button>
        </div>

        <div className="space-y-3 p-4">
          <label className="flex flex-col text-xs text-panel-muted">
            联系人姓名
            <input
              aria-label="联系人姓名"
              value={contactName}
              onChange={(e) => setContactName(e.target.value)}
              className="mt-1 rounded border px-2 py-1 text-sm text-panel-ink"
            />
          </label>
          <label className="flex flex-col text-xs text-panel-muted">
            联系电话
            <input
              aria-label="联系电话"
              value={contactPhone}
              onChange={(e) => setContactPhone(e.target.value)}
              className="mt-1 rounded border px-2 py-1 text-sm text-panel-ink"
            />
          </label>
          <label className="flex flex-col text-xs text-panel-muted">
            地址
            <input
              aria-label="地址"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              className="mt-1 rounded border px-2 py-1 text-sm text-panel-ink"
            />
          </label>
          {saveError && <p className="text-sm text-panel-danger">{saveError}</p>}
        </div>

        <div className="flex justify-end gap-2 border-t border-panel-border p-3">
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="rounded-full border border-panel-border px-3 py-1 text-sm text-panel-muted disabled:opacity-50"
          >
            取消
          </button>
          <button
            type="button"
            onClick={handleSubmit}
            disabled={saving || !contactPhone.trim()}
            className="rounded-full bg-panel-primary px-3 py-1 text-sm font-medium text-white disabled:opacity-50"
          >
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
