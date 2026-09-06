import { useEffect } from 'react'
import { createPortal } from 'react-dom'

interface ConfirmDialogProps {
  title: string
  message: string
  onConfirm: () => void
  onCancel: () => void
  confirming?: boolean
}

export function ConfirmDialog({ title, message, onConfirm, onCancel, confirming = false }: ConfirmDialogProps) {
  useEffect(() => {
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKeydown)
    return () => document.removeEventListener('keydown', onKeydown)
  }, [onCancel])

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) onCancel()
      }}
    >
      <div className="share-modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="stats-modal__header">
          <span className="stats-modal__title">{title}</span>
        </div>
        <p className="share-hint">{message}</p>
        <div className="share-modal__actions">
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={confirming}>
            否
          </button>
          <button type="button" className="btn-small" onClick={onConfirm} disabled={confirming}>
            是
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
