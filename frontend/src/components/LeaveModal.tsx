import { useState } from 'react'
import { createPortal } from 'react-dom'

interface LeaveModalProps {
  studentName: string
  reason: string
  onSave: (reason: string) => void
  onClear: () => void
  onClose: () => void
}

export function LeaveModal({ studentName, reason, onSave, onClear, onClose }: LeaveModalProps) {
  const [localReason, setLocalReason] = useState(reason)
  const trimmedReason = localReason.trim()

  function handleSave() {
    if (!trimmedReason) return
    onSave(trimmedReason)
    onClose()
  }

  function handleClear() {
    onClear()
    onClose()
  }

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="share-modal" role="dialog" aria-modal="true" aria-label="请假登记">
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={onClose}>
          ×
        </button>

        <div className="stats-modal__header">
          <span className="stats-modal__title">{studentName}的请假登记</span>
        </div>

        <div className="teacher-comment">
          <label className="teacher-comment__label">
            请假原因
            <input
              type="text"
              className="teacher-comment__input"
              value={localReason}
              onChange={(e) => setLocalReason(e.target.value)}
              placeholder="例如：发烧、事假"
            />
          </label>
        </div>

        <div className="share-modal__actions">
          {reason && (
            <button type="button" className="btn-small" onClick={handleClear}>
              清除请假
            </button>
          )}
          <button type="button" className="btn-small" onClick={handleSave} disabled={!trimmedReason}>
            保存
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
