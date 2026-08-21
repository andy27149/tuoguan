import { useRef, useState } from 'react'
import type { Student } from '../api/students'
import type { DailyTask } from '../api/dailyTasks'
import type { TaskTemplate } from '../api/taskTemplates'
import { computeCardStatus } from '../kanban/cardStatus'
import { AddTaskForm } from './AddTaskForm'

interface StudentCardProps {
  student: Student
  tasks: DailyTask[]
  dismissed: boolean
  templates: TaskTemplate[]
  onToggleTask: (taskId: number, completed: boolean) => void
  onDeleteTask: (taskId: number) => void
  onAddFromTemplate: (studentId: number, templateId: number) => Promise<void>
  onAddCustom: (studentId: number, subject: string, name: string) => Promise<void>
  onUploadAvatar: (studentId: number, file: File) => Promise<void>
}

const STATUS_STYLES = {
  done: 'bg-green-50 border-green-400',
  dismissedIncomplete: 'bg-red-50 border-red-400',
  default: 'bg-white border-gray-200',
} as const

export function StudentCard({
  student,
  tasks,
  dismissed,
  templates,
  onToggleTask,
  onDeleteTask,
  onAddFromTemplate,
  onAddCustom,
  onUploadAvatar,
}: StudentCardProps) {
  const [adding, setAdding] = useState(false)
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const status = computeCardStatus(tasks, dismissed)

  async function handleAvatarFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setUploading(true)
    try {
      await onUploadAvatar(student.id, file)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div
      data-testid="student-card"
      className={`relative rounded-lg border p-3 shadow-sm ${STATUS_STYLES[status]}`}
    >
      {status === 'done' && (
        <span className="absolute right-3 top-3 rotate-12 rounded border-2 border-green-600 px-2 py-0.5 text-xs font-bold text-green-600">
          已完成
        </span>
      )}

      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          aria-label={`上传${student.name}的头像`}
          disabled={uploading}
          className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-gray-300 text-sm font-medium text-gray-700 disabled:opacity-50"
        >
          {student.avatarUrl ? (
            <img src={student.avatarUrl} alt={student.name} className="h-full w-full object-cover" />
          ) : (
            student.name.slice(0, 1)
          )}
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          className="hidden"
          onChange={handleAvatarFileChange}
        />
        <div>
          <p className="font-medium">{student.name}</p>
          <p className="text-xs text-gray-500">{student.schoolClassName}</p>
        </div>
      </div>

      <ul className="mt-2 space-y-1">
        {tasks.map((task) => (
          <li key={task.id} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={task.completed}
              onChange={(e) => onToggleTask(task.id, e.target.checked)}
              aria-label={task.name}
            />
            <span className={`flex-1 ${task.completed ? 'text-gray-400 line-through' : ''}`}>
              [{task.subject}] {task.name}
            </span>
            <button
              type="button"
              onClick={() => onDeleteTask(task.id)}
              aria-label={`删除${task.name}`}
              className="text-gray-400 hover:text-red-500"
            >
              ×
            </button>
          </li>
        ))}
        {tasks.length === 0 && <li className="text-xs text-gray-400">今天还没有任务</li>}
      </ul>

      {adding ? (
        <AddTaskForm
          templates={templates}
          onCancel={() => setAdding(false)}
          onAddFromTemplate={async (templateId) => {
            await onAddFromTemplate(student.id, templateId)
            setAdding(false)
          }}
          onAddCustom={async (subject, name) => {
            await onAddCustom(student.id, subject, name)
            setAdding(false)
          }}
        />
      ) : (
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="mt-2 text-xs font-medium text-blue-600"
        >
          + 添加任务
        </button>
      )}
    </div>
  )
}
