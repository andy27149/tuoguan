import { createPortal } from 'react-dom'

interface RollCallResultModalProps {
  type: 'success' | 'warning'
  message: string
  onClose: () => void
  /** 对话框标题/无障碍标签，默认沿用消课场景的文案；其它场景（如批量生成账单）传入自己的标题。 */
  title?: string
}

export function RollCallResultModal({ type, message, onClose, title }: RollCallResultModalProps) {
  const isSuccess = type === 'success'

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title ?? (isSuccess ? '消课成功' : '消课提醒')}
        className="w-full max-w-xs rounded-2xl border border-[#ece7de] bg-white p-5 text-center shadow-[0_20px_50px_rgba(36,31,61,0.25)]"
      >
        <div
          aria-hidden="true"
          className={
            isSuccess
              ? 'mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-[#e6f6ee] text-[#2f9e6e]'
              : 'mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-[#fdeee1] text-[#b7591f]'
          }
        >
          {isSuccess ? (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={3} strokeLinecap="round" strokeLinejoin="round">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
          ) : (
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 9v4" />
              <path d="M12 17h.01" />
              <path d="M10.29 3.86 1.82 18a1 1 0 0 0 .86 1.5h18.64a1 1 0 0 0 .86-1.5L13.71 3.86a1 1 0 0 0-1.72 0Z" />
            </svg>
          )}
        </div>
        <p className="mt-3 text-sm font-medium text-[#241f3d]">{message}</p>
        <button
          type="button"
          onClick={onClose}
          className="mt-4 w-full rounded-full bg-[#6d5bd0] px-4 py-2 text-sm font-medium text-white"
        >
          知道了
        </button>
      </div>
    </div>,
    document.body,
  )
}
