import { type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useEscapeToClose } from '../hooks/useEscapeToClose'

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
  /** 可选的额外表单内容（如收款方式下拉），渲染在提示文字和按钮之间。 */
  children?: ReactNode
  /** 额外禁用确认按钮的条件（如必填的额外表单项还没填），默认不额外禁用。 */
  confirmDisabled?: boolean
  /** 覆盖确认按钮的默认样式（btn-danger/btn-small 那套玻璃拟态渐变色）——管理
   * 后台页面统一用 panel-* 语义色的实心紫色圆角按钮，和弹窗默认风格是两套不同的
   * 视觉语言，调用方按自己所在的页面风格传入即可，不传则保持原样。 */
  confirmClassName?: string
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
  children,
  confirmDisabled = false,
  confirmClassName,
}: ConfirmDialogProps) {
  useEscapeToClose(onCancel)

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
        {children}
        <div className="share-modal__actions">
          <button type="button" className="btn-secondary" onClick={onCancel} disabled={confirming}>
            {cancelLabel}
          </button>
          <button
            type="button"
            className={confirmClassName ?? (danger ? 'btn-danger' : 'btn-small')}
            onClick={onConfirm}
            disabled={confirming || confirmDisabled}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
