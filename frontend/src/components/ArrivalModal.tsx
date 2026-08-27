import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'

interface ArrivalModalProps {
  studentName: string
  arrivedAt: string
  onSave: (arrivedAt: string) => void
  onClear: () => void
  onClose: () => void
}

export function ArrivalModal({ studentName, arrivedAt, onSave, onClear, onClose }: ArrivalModalProps) {
  const [localArrivedAt, setLocalArrivedAt] = useState(arrivedAt)

  const commitAndClose = () => {
    if (localArrivedAt !== arrivedAt) {
      onSave(localArrivedAt)
    }
    onClose()
  }

  useEffect(() => {
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') commitAndClose()
    }
    document.addEventListener('keydown', onKeydown)
    return () => document.removeEventListener('keydown', onKeydown)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [localArrivedAt])

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) commitAndClose()
      }}
    >
      <div className="share-modal" role="dialog" aria-modal="true" aria-label="到达签到">
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={commitAndClose}>
          ×
        </button>

        <div className="stats-modal__header">
          <span className="stats-modal__title">{studentName}的到达签到</span>
        </div>

        <div className="teacher-comment">
          <label className="teacher-comment__label">
            到达时间
            <input
              type="time"
              className="teacher-comment__input"
              value={localArrivedAt}
              onChange={(e) => setLocalArrivedAt(e.target.value)}
            />
          </label>
        </div>

        <div className="share-modal__actions">
          <button
            type="button"
            className="btn-small"
            onClick={() => {
              onClear()
              onClose()
            }}
          >
            清除签到
          </button>
          <button type="button" className="btn-small" onClick={commitAndClose}>
            完成
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
