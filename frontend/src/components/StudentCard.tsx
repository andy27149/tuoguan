import { useRef, useState } from 'react'
import type { Student } from '../api/students'
import type { DailyTask } from '../api/dailyTasks'
import type { TaskTemplate } from '../api/taskTemplates'
import { computeCardStatus } from '../kanban/cardStatus'
import { subjectColor, subjectIconMarkup } from '../kanban/subjectIcons'
import { currentTimeString } from '../kanban/date'
import { AddTaskForm } from './AddTaskForm'
import { StarRating } from './StarRating'
import { SharePosterModal } from './SharePosterModal'
import { MonthlyStatsModal } from './MonthlyStatsModal'
import { ShareLinkModal } from './ShareLinkModal'
import { ArrivalModal } from './ArrivalModal'

interface StudentCardProps {
  student: Student
  tasks: DailyTask[]
  dismissed: boolean
  templates: TaskTemplate[]
  rating: number
  comment: string
  arrivedAt: string
  date: string
  onToggleTask: (taskId: number, completed: boolean) => void
  onDeleteTask: (taskId: number) => void
  onAddFromTemplate: (studentId: number, templateId: number) => Promise<void>
  onAddCustom: (studentId: number, subject: string, name: string) => Promise<void>
  onUploadAvatar: (studentId: number, file: File) => Promise<void>
  onSetRating: (studentId: number, rating: number) => void
  onSetComment: (studentId: number, comment: string) => void
  onSetArrival: (studentId: number, arrivedAt: string) => void
  onClearArrival: (studentId: number) => void
  onShowToast: (message: string) => void
}

function ProgressRing({
  completed,
  total,
  justCompleted,
}: {
  completed: number
  total: number
  justCompleted: boolean
}) {
  if (total === 0) return null
  const isComplete = completed === total
  const r = 16
  const c = 2 * Math.PI * r
  const offset = c * (1 - completed / total)
  const stroke = isComplete ? 'url(#ringGold)' : 'url(#ringAccent)'

  return (
    <div
      className={`progress-ring-wrap${isComplete ? ' is-complete' : ''}${
        isComplete && justCompleted ? ' is-earning' : ''
      }`}
    >
      <svg className="progress-ring" viewBox="0 0 40 40" aria-hidden="true">
        <circle className="progress-ring__track" cx="20" cy="20" r={r} />
        <circle
          className="progress-ring__fill"
          cx="20"
          cy="20"
          r={r}
          stroke={stroke}
          strokeDasharray={c.toFixed(2)}
          strokeDashoffset={offset.toFixed(2)}
        />
      </svg>
      <span className="progress-ring__count">
        {completed}/{total}
      </span>
      {isComplete && (
        <svg className="medal" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="10" fill="url(#ringGold)" />
          <path
            d="M12 6.5 l1.8 3.7 4 .6 -2.9 2.8 .7 4 -3.6-1.9 -3.6 1.9 .7-4 -2.9-2.8 4-.6 z"
            fill="#fff"
          />
        </svg>
      )}
    </div>
  )
}

export function StudentCard({
  student,
  tasks,
  dismissed,
  templates,
  rating,
  comment,
  arrivedAt,
  date,
  onToggleTask,
  onDeleteTask,
  onAddFromTemplate,
  onAddCustom,
  onUploadAvatar,
  onSetRating,
  onSetComment,
  onSetArrival,
  onClearArrival,
  onShowToast,
}: StudentCardProps) {
  const [adding, setAdding] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [sharing, setSharing] = useState(false)
  const [showingStats, setShowingStats] = useState(false)
  const [showingShareLink, setShowingShareLink] = useState(false)
  const [showingArrival, setShowingArrival] = useState(false)
  const [justCompleted, setJustCompleted] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const status = computeCardStatus(tasks, dismissed)
  const completedCount = tasks.filter((t) => t.completed).length

  function handleToggle(taskId: number, completed: boolean) {
    const willBeDone =
      completed &&
      tasks.length > 0 &&
      tasks.every((t) => (t.id === taskId ? true : t.completed))
    if (willBeDone) setJustCompleted(true)
    onToggleTask(taskId, completed)
  }

  function handleArrivalClick() {
    if (arrivedAt) {
      setShowingArrival(true)
    } else {
      onSetArrival(student.id, currentTimeString())
    }
  }

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
    <div data-testid="student-card" className="student-card" data-status={status}>
      <ProgressRing completed={completedCount} total={tasks.length} justCompleted={justCompleted} />

      <div className="card-head">
        <div className="avatar-wrap">
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            aria-label={`上传${student.name}的头像`}
            disabled={uploading}
            className="avatar-btn"
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
            hidden
            onChange={handleAvatarFileChange}
          />
        </div>
        <div>
          <p className="card-head__name">
            {student.name}
            {status === 'dismissedIncomplete' && <span className="flag-chip">🚩 未完成</span>}
          </p>
          <p className="card-head__class">{student.schoolClassName}</p>
        </div>
      </div>

      <div className="today-rating">
        <span className="today-rating__label">今日评价</span>
        <StarRating value={rating} onChange={(v) => onSetRating(student.id, v)} />
      </div>

      <ul className="task-list">
        {tasks.map((task) => (
          <li key={task.id} className={`task-item${task.completed ? ' is-done' : ''}`}>
            <input
              type="checkbox"
              className="task-check"
              checked={task.completed}
              onChange={(e) => handleToggle(task.id, e.target.checked)}
              aria-label={task.name}
            />
            <span
              className="task-subject"
              style={{ color: subjectColor(task.subject) }}
              dangerouslySetInnerHTML={{ __html: subjectIconMarkup(task.subject, task.id) }}
            />
            <span className="task-text">
              [{task.subject}] {task.name}
            </span>
            <button
              type="button"
              onClick={() => onDeleteTask(task.id)}
              aria-label={`删除${task.name}`}
              className="task-del"
            >
              ×
            </button>
          </li>
        ))}
        {tasks.length === 0 && (
          <li className="task-empty" style={{ padding: '8px 0' }}>
            今天还没有任务
          </li>
        )}
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
        <button type="button" onClick={() => setAdding(true)} className="add-task-btn">
          + 添加任务
        </button>
      )}

      <button type="button" className="share-btn" onClick={() => setSharing(true)}>
        分享给家长
      </button>

      <button type="button" className="share-btn" onClick={() => setShowingStats(true)}>
        月度统计
      </button>

      <button type="button" className="share-btn" onClick={() => setShowingShareLink(true)}>
        家长链接
      </button>

      <button type="button" className="share-btn" onClick={handleArrivalClick}>
        {arrivedAt ? `到了 · ${arrivedAt}` : '到了'}
      </button>

      {sharing && (
        <SharePosterModal
          student={student}
          tasks={tasks}
          rating={rating}
          comment={comment}
          dismissed={dismissed}
          date={date}
          onCommentChange={(value) => onSetComment(student.id, value)}
          onClose={() => setSharing(false)}
          onShowToast={onShowToast}
        />
      )}

      {showingStats && (
        <MonthlyStatsModal
          studentId={student.id}
          studentName={student.name}
          onClose={() => setShowingStats(false)}
        />
      )}

      {showingShareLink && (
        <ShareLinkModal
          studentId={student.id}
          studentName={student.name}
          onClose={() => setShowingShareLink(false)}
          onShowToast={onShowToast}
        />
      )}

      {showingArrival && (
        <ArrivalModal
          studentName={student.name}
          arrivedAt={arrivedAt}
          onSave={(newArrivedAt) => onSetArrival(student.id, newArrivedAt)}
          onClear={() => onClearArrival(student.id)}
          onClose={() => setShowingArrival(false)}
        />
      )}
    </div>
  )
}
