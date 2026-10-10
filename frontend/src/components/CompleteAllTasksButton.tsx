import { useState } from 'react'
import { ConfirmDialog } from './ConfirmDialog'

interface CompleteAllTasksButtonProps {
  taskCount: number
  studentCount: number
  onCompleteAll: () => Promise<void>
}

export function CompleteAllTasksButton({ taskCount, studentCount, onCompleteAll }: CompleteAllTasksButtonProps) {
  const [submitting, setSubmitting] = useState(false)
  const [confirming, setConfirming] = useState(false)

  async function handleConfirm() {
    setSubmitting(true)
    try {
      await onCompleteAll()
    } finally {
      setSubmitting(false)
      setConfirming(false)
    }
  }

  return (
    <>
      <button type="button" onClick={() => setConfirming(true)} disabled={taskCount === 0} className="btn-secondary">
        一键完成今日任务
      </button>

      {confirming && (
        <ConfirmDialog
          title="一键完成今日任务"
          message={`确定要将 ${studentCount} 个孩子的 ${taskCount} 项未完成任务标记为已完成吗？`}
          confirmLabel="确认完成"
          danger={false}
          confirming={submitting}
          onConfirm={handleConfirm}
          onCancel={() => setConfirming(false)}
        />
      )}
    </>
  )
}
