import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import type { Teacher, TeacherDeletionImpact } from '../api/admin'

interface DeleteTeacherModalProps {
  teacherName: string
  impact: TeacherDeletionImpact | null
  otherTeachers: Teacher[]
  onConfirm: (mode: 'DELETE_ALL' | 'TRANSFER', targetTeacherId?: number) => void
  onClose: () => void
  submitting: boolean
}

export function DeleteTeacherModal({
  teacherName,
  impact,
  otherTeachers,
  onConfirm,
  onClose,
  submitting,
}: DeleteTeacherModalProps) {
  const [mode, setMode] = useState<'DELETE_ALL' | 'TRANSFER'>('DELETE_ALL')
  const [targetTeacherId, setTargetTeacherId] = useState<string>('')

  useEffect(() => {
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeydown)
    return () => document.removeEventListener('keydown', onKeydown)
  }, [onClose])

  function handleConfirm() {
    if (impact?.hasStudents && mode === 'TRANSFER') {
      if (!targetTeacherId) return
      onConfirm('TRANSFER', Number(targetTeacherId))
    } else {
      onConfirm('DELETE_ALL')
    }
  }

  const confirmDisabled =
    submitting || (impact?.hasStudents === true && mode === 'TRANSFER' && !targetTeacherId)

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="share-modal" role="dialog" aria-modal="true" aria-label="删除教师">
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={onClose}>
          ×
        </button>

        <div className="stats-modal__header">
          <span className="stats-modal__title">删除教师「{teacherName}」</span>
        </div>

        {impact === null && <p className="stats-status">加载中...</p>}

        {impact !== null && !impact.hasStudents && (
          <p className="share-hint">
            该教师名下没有学生，删除后其班级（{impact.classCount} 个）与任务库模板（{impact.templateCount} 个）
            将一并删除，且不可恢复，确认删除吗？
          </p>
        )}

        {impact !== null && impact.hasStudents && (
          <>
            <p className="share-hint">
              该教师名下有 {impact.classCount} 个班级、{impact.studentCount} 名学生、{impact.templateCount} 个任务库模板，请选择处理方式：
            </p>
            <div className="mt-2 space-y-2 text-sm">
              <label className="flex items-center gap-2">
                <input
                  type="radio"
                  name="delete-mode"
                  value="DELETE_ALL"
                  checked={mode === 'DELETE_ALL'}
                  onChange={() => setMode('DELETE_ALL')}
                />
                删除全部（班级、学生、任务库一并删除，不可恢复）
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="radio"
                  name="delete-mode"
                  value="TRANSFER"
                  checked={mode === 'TRANSFER'}
                  onChange={() => setMode('TRANSFER')}
                />
                转移给其他老师（班级与任务库整体转移，学生保留）
              </label>
              {mode === 'TRANSFER' && (
                <select
                  value={targetTeacherId}
                  onChange={(e) => setTargetTeacherId(e.target.value)}
                  className="w-full rounded border px-2 py-1 text-sm"
                >
                  <option value="">请选择目标老师</option>
                  {otherTeachers.map((teacher) => (
                    <option key={teacher.id} value={teacher.id}>
                      {teacher.name ? `${teacher.name} · ${teacher.phone}` : teacher.phone}
                    </option>
                  ))}
                </select>
              )}
            </div>
          </>
        )}

        {impact !== null && (
          <div className="share-modal__actions">
            <button type="button" className="btn-secondary" onClick={onClose} disabled={submitting}>
              取消
            </button>
            <button type="button" className="btn-small" onClick={handleConfirm} disabled={confirmDisabled}>
              确认删除
            </button>
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}
