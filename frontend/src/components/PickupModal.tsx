import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'

interface PickupModalProps {
  studentName: string
  pickedUpBy: string
  pickedUpAt: string
  onSave: (pickedUpBy: string, pickedUpAt: string) => void
  onClose: () => void
}

export function PickupModal({ studentName, pickedUpBy, pickedUpAt, onSave, onClose }: PickupModalProps) {
  const [localPickedUpBy, setLocalPickedUpBy] = useState(pickedUpBy)
  const [localPickedUpAt, setLocalPickedUpAt] = useState(pickedUpAt)

  const commitAndClose = () => {
    if (localPickedUpBy !== pickedUpBy || localPickedUpAt !== pickedUpAt) {
      onSave(localPickedUpBy, localPickedUpAt)
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
  }, [localPickedUpBy, localPickedUpAt])

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) commitAndClose()
      }}
    >
      <div className="share-modal" role="dialog" aria-modal="true" aria-label="记录接送">
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={commitAndClose}>
          ×
        </button>

        <div className="stats-modal__header">
          <span className="stats-modal__title">{studentName}的接送记录</span>
        </div>

        <div className="teacher-comment">
          <label className="teacher-comment__label">
            接送人
            <input
              type="text"
              className="teacher-comment__input"
              maxLength={100}
              placeholder="例如：奶奶"
              value={localPickedUpBy}
              onChange={(e) => setLocalPickedUpBy(e.target.value)}
            />
          </label>
        </div>

        <div className="teacher-comment">
          <label className="teacher-comment__label">
            接送时间
            <input
              type="time"
              className="teacher-comment__input"
              value={localPickedUpAt}
              onChange={(e) => setLocalPickedUpAt(e.target.value)}
            />
          </label>
        </div>

        <div className="share-modal__actions">
          <button type="button" className="btn-small" onClick={commitAndClose}>
            完成
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
