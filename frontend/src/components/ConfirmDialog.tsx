import { useEffect } from 'react'
import { createPortal } from 'react-dom'

interface ConfirmDialogProps {
  title: string
  message: string
  onConfirm: () => void
  onCancel: () => void
  confirming?: boolean
  /** 确认按钮文案，默认"确认"；危险操作建议传入具体动词，如"确认删除" */
  confirmLabel?: string
  cancelLabel?: string
  /** 是否为不可逆/高风险操作，决定确认按钮是否用警示红色。默认 true。 */
  danger?: boolean
}

// 全站统一的二次确认弹窗：按钮文案固定"取消 / 确认XX"，危险操作统一红色，
// 都带×关闭，避免像此前那样一个产品里同时存在三套风格不同的确认弹窗。
export function ConfirmDialog({
  title,
  message,
  onConfirm,
  onCancel,
  confirming = false,
  confirmLabel = '确认',
  cancelLabel = '取消',
  danger = true,
}: ConfirmDialogProps) {
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
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={onCancel}>
          ×
        </button>
        <div className="stats-modal__header">
          <span className="stats-modal__title">{title}</span>
        </div>
        <p className="share-hint">{message}</p>
        <div className="share-modal__actions">
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={confirming}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={danger ? 'btn-danger' : 'btn-small'}
            onClick={onConfirm}
            disabled={confirming}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
