import { useState } from 'react'
import { ConfirmDialog } from './ConfirmDialog'

interface DismissButtonProps {
  dismissed: boolean
  onDismiss: () => Promise<void>
  onUndoDismiss: () => Promise<void>
}

export function DismissButton({ dismissed, onDismiss, onUndoDismiss }: DismissButtonProps) {
  const [submitting, setSubmitting] = useState(false)
  const [confirming, setConfirming] = useState(false)

  async function handleClick() {
    if (dismissed) {
      setSubmitting(true)
      try {
        await onUndoDismiss()
      } finally {
        setSubmitting(false)
      }
      return
    }
    // 放学会一次性标记全班完成状态，且家长端可见，先确认；撤销放学是纠正性操作，直接生效。
    setConfirming(true)
  }

  async function handleConfirmDismiss() {
    setSubmitting(true)
    try {
      await onDismiss()
    } finally {
      setSubmitting(false)
      setConfirming(false)
    }
  }

  return (
    <>
      <button
        type="button"
        onClick={handleClick}
        disabled={submitting}
        className={`rounded px-3 py-1.5 text-sm font-medium disabled:opacity-50 ${
          dismissed ? 'bg-gray-200 text-gray-700' : 'bg-orange-500 text-white'
        }`}
      >
        {dismissed ? '撤销放学' : '放学'}
      </button>

      {confirming && (
        <ConfirmDialog
          title="放学"
          message="放学后，本班所有未完成的学生将被标记为「未完成」，家长端可见。确认放学吗？"
          confirmLabel="确认放学"
          danger={false}
          confirming={submitting}
          onConfirm={handleConfirmDismiss}
          onCancel={() => setConfirming(false)}
        />
      )}
    </>
  )
}
