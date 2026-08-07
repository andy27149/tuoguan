import { useState } from 'react'

interface DismissButtonProps {
  dismissed: boolean
  onDismiss: () => Promise<void>
  onUndoDismiss: () => Promise<void>
}

export function DismissButton({ dismissed, onDismiss, onUndoDismiss }: DismissButtonProps) {
  const [submitting, setSubmitting] = useState(false)

  async function handleClick() {
    setSubmitting(true)
    try {
      if (dismissed) {
        await onUndoDismiss()
      } else {
        await onDismiss()
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
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
  )
}
