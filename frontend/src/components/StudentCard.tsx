import { useRef, useState } from 'react'
import type { Student } from '../api/students'
import type { DailyTask } from '../api/dailyTasks'
import type { TaskTemplate } from '../api/taskTemplates'
import type { MonthlyStats } from '../api/monthlyStats'
import { computeCardStatus } from '../kanban/cardStatus'
import { subjectColor, subjectIconMarkup } from '../kanban/subjectIcons'
import { currentTimeString } from '../kanban/date'
import { AddTaskForm } from './AddTaskForm'
import { StarRating } from './StarRating'
import { SharePosterModal } from './SharePosterModal'
import { MonthlyStatsModal } from './MonthlyStatsModal'
import { ShareLinkModal } from './ShareLinkModal'
import { ArrivalModal } from './ArrivalModal'
import { LeaveModal } from './LeaveModal'
import { ConfirmDialog } from './ConfirmDialog'

interface StudentCardProps {
  student: Student
  tasks: DailyTask[]
  dismissed: boolean
  templates: TaskTemplate[]
  rating: number
  comment: string
  arrivedAt: string
  hasMeal: boolean
  leaveReason: string
  date: string
  readOnly?: boolean
  onToggleTask?: (taskId: number, completed: boolean) => void
  onDeleteTask?: (taskId: number) => void
  onAddFromTemplate?: (studentId: number, templateId: number) => Promise<void>
  onAddCustom?: (studentId: number, subject: string, name: string) => Promise<void>
  onUploadAvatar?: (studentId: number, file: File) => Promise<void>
  onSetRating?: (studentId: number, rating: number) => void
  onSetComment?: (studentId: number, comment: string) => void
  onSetArrival?: (studentId: number, arrivedAt: string) => void
  onClearArrival?: (studentId: number) => void
  onSetMeal?: (studentId: number) => void
  onClearMeal?: (studentId: number) => void
  onSetLeave?: (studentId: number, reason: string) => void
  onClearLeave?: (studentId: number) => void
  onShowToast: (message: string) => void
  statsFetchFn?: (studentId: number, month?: string) => Promise<MonthlyStats>
  shareLinkFetchFn?: (studentId: number) => Promise<{ token: string }>
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
  hasMeal,
  leaveReason,
  date,
  readOnly = false,
  onToggleTask,
  onDeleteTask,
  onAddFromTemplate,
  onAddCustom,
  onUploadAvatar,
  onSetRating,
  onSetComment,
  onSetArrival,
  onClearArrival,
  onSetMeal,
  onClearMeal,
  onSetLeave,
  onClearLeave,
  onShowToast,
  statsFetchFn,
  shareLinkFetchFn,
}: StudentCardProps) {
  const [adding, setAdding] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [sharing, setSharing] = useState(false)
  const [showingStats, setShowingStats] = useState(false)
  const [showingShareLink, setShowingShareLink] = useState(false)
  const [showingArrival, setShowingArrival] = useState(false)
  const [showingLeave, setShowingLeave] = useState(false)
  const [confirmingArrivalOverLeave, setConfirmingArrivalOverLeave] = useState(false)
  const [justCompleted, setJustCompleted] = useState(false)
  const [deletingTask, setDeletingTask] = useState<DailyTask | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const status = computeCardStatus(tasks, dismissed)
  const completedCount = tasks.filter((t) => t.completed).length

  function handleToggle(taskId: number, completed: boolean) {
    const willBeDone =
      completed &&
      tasks.length > 0 &&
      tasks.every((t) => (t.id === taskId ? true : t.completed))
    if (willBeDone) setJustCompleted(true)
    onToggleTask?.(taskId, completed)
  }

  function handleConfirmDeleteTask() {
    if (!deletingTask) return
    onDeleteTask?.(deletingTask.id)
    onShowToast(`已删除「${deletingTask.name}」`)
    setDeletingTask(null)
  }

  function handleArrivalClick() {
    if (arrivedAt) {
      setShowingArrival(true)
    } else if (leaveReason) {
      setConfirmingArrivalOverLeave(true)
    } else {
      onSetArrival?.(student.id, currentTimeString())
    }
  }

  function handleConfirmArrivalOverLeave() {
    setConfirmingArrivalOverLeave(false)
    onSetArrival?.(student.id, currentTimeString())
  }

  function handleMealClick() {
    if (hasMeal) {
      onClearMeal?.(student.id)
    } else {
      onSetMeal?.(student.id)
    }
  }

  async function handleAvatarFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setUploading(true)
    try {
      await onUploadAvatar?.(student.id, file)
    } finally {
      setUploading(false)
    }
  }

  return (
    <div data-testid="student-card" className="student-card" data-status={status}>
      <ProgressRing completed={completedCount} total={tasks.length} justCompleted={justCompleted} />

      <div className="card-head">
        <div className="avatar-wrap">
          {readOnly ? (
            <div className="avatar-btn" aria-hidden="true">
              {student.avatarUrl ? (
                <img src={student.avatarUrl} alt={student.name} className="h-full w-full object-cover" />
              ) : (
                student.name.slice(0, 1)
              )}
            </div>
          ) : (
            <>
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
            </>
          )}
        </div>
        <div>
          <p className="card-head__name">
            {student.name}
            {status === 'dismissedIncomplete' && <span className="flag-chip">🚩 未完成</span>}
          </p>
          <p className="card-head__class">{student.schoolClassName ?? '未填写学籍班'}</p>
        </div>
      </div>

      <div className="today-rating">
        <span className="today-rating__label">今日评价</span>
        <StarRating value={rating} onChange={(v) => onSetRating?.(student.id, v)} readOnly={readOnly} />
      </div>

      <ul className="task-list">
        {tasks.map((task) => (
          <li key={task.id} className={`task-item${task.completed ? ' is-done' : ''}`}>
            <input
              type="checkbox"
              className="task-check"
              checked={task.completed}
              disabled={readOnly}
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
            {!readOnly && (
              <button
                type="button"
                onClick={() => setDeletingTask(task)}
                aria-label={`删除${task.name}`}
                className="task-del"
              >
                ×
              </button>
            )}
          </li>
        ))}
        {tasks.length === 0 && (
          <li className="task-empty" style={{ padding: '8px 0' }}>
            今天还没有任务
          </li>
        )}
      </ul>

      {!readOnly &&
        (adding ? (
          <AddTaskForm
            templates={templates}
            onCancel={() => setAdding(false)}
            onAddFromTemplate={async (templateId) => {
              await onAddFromTemplate?.(student.id, templateId)
              setAdding(false)
            }}
            onAddCustom={async (subject, name) => {
              await onAddCustom?.(student.id, subject, name)
              setAdding(false)
            }}
          />
        ) : (
          <button type="button" onClick={() => setAdding(true)} className="add-task-btn">
            + 添加任务
          </button>
        ))}

      <div className="action-chip-row">
        {readOnly ? (
          arrivedAt && <span className="action-chip is-active action-chip--static">🕐 {arrivedAt}</span>
        ) : (
          <button type="button" className={`action-chip${arrivedAt ? ' is-active' : ''}`} onClick={handleArrivalClick}>
            🕐 {arrivedAt || '到了'}
          </button>
        )}

        {readOnly ? (
          hasMeal && <span className="action-chip is-active action-chip--static">🍚 已用餐</span>
        ) : (
          <button
            type="button"
            className={`action-chip${hasMeal ? ' is-active' : ''}`}
            onClick={handleMealClick}
            disabled={!arrivedAt}
            title={!arrivedAt ? '需先到了才能用餐' : undefined}
          >
            🍚 {hasMeal ? '已用餐' : '用餐'}
          </button>
        )}

        {!readOnly && (
          <button
            type="button"
            className={`action-chip${leaveReason ? ' is-active' : ''}`}
            onClick={() => setShowingLeave(true)}
            disabled={!!arrivedAt}
            title={arrivedAt ? '已到了，不能请假' : undefined}
          >
            🌴 {leaveReason ? '已请假' : '请假'}
          </button>
        )}

        {!readOnly && (
          <button type="button" className="action-chip" onClick={() => setSharing(true)}>
            🖼 海报
          </button>
        )}

        <button type="button" className="action-chip" onClick={() => setShowingStats(true)}>
          📊 统计
        </button>

        <button type="button" className="action-chip" onClick={() => setShowingShareLink(true)}>
          🔗 链接
        </button>
      </div>

      {sharing && (
        <SharePosterModal
          student={student}
          tasks={tasks}
          rating={rating}
          comment={comment}
          dismissed={dismissed}
          date={date}
          onCommentChange={(value) => onSetComment?.(student.id, value)}
          onClose={() => setSharing(false)}
          onShowToast={onShowToast}
        />
      )}

      {showingStats && (
        <MonthlyStatsModal
          studentId={student.id}
          studentName={student.name}
          onClose={() => setShowingStats(false)}
          fetchFn={statsFetchFn}
        />
      )}

      {showingShareLink && (
        <ShareLinkModal
          studentId={student.id}
          studentName={student.name}
          onClose={() => setShowingShareLink(false)}
          onShowToast={onShowToast}
          fetchFn={shareLinkFetchFn}
        />
      )}

      {showingArrival && (
        <ArrivalModal
          studentName={student.name}
          arrivedAt={arrivedAt}
          onSave={(newArrivedAt) => onSetArrival?.(student.id, newArrivedAt)}
          onClear={() => onClearArrival?.(student.id)}
          onClose={() => setShowingArrival(false)}
        />
      )}

      {showingLeave && (
        <LeaveModal
          studentName={student.name}
          reason={leaveReason}
          onSave={(newReason) => onSetLeave?.(student.id, newReason)}
          onClear={() => onClearLeave?.(student.id)}
          onClose={() => setShowingLeave(false)}
        />
      )}

      {confirmingArrivalOverLeave && (
        <ConfirmDialog
          title="打卡到了"
          message={`${student.name}今天已登记请假，打卡到了将清除这条请假记录，确认要打卡吗？`}
          confirmLabel="确认打卡"
          onConfirm={handleConfirmArrivalOverLeave}
          onCancel={() => setConfirmingArrivalOverLeave(false)}
        />
      )}

      {deletingTask && (
        <ConfirmDialog
          title="删除任务"
          message={`确认从今天的任务中删除「${deletingTask.name}」吗？已记录的完成/评价状态将一并删除。`}
          confirmLabel="确认删除"
          onConfirm={handleConfirmDeleteTask}
          onCancel={() => setDeletingTask(null)}
        />
      )}
    </div>
  )
}
