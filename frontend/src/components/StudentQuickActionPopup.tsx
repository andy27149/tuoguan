import { useState } from 'react'
import { createPortal } from 'react-dom'
import type { Student } from '../api/students'
import type { DailyTask } from '../api/dailyTasks'
import type { TaskTemplate } from '../api/taskTemplates'
import type { MonthlyStats } from '../api/monthlyStats'
import { StudentCard } from './StudentCard'
import { useEscapeToClose } from '../hooks/useEscapeToClose'
import { currentTimeString } from '../kanban/date'

interface StudentQuickActionPopupProps {
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
  onClose: () => void
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

const DEFAULT_LEAVE_REASON = '请假'

// 总览网格点头像弹出的快捷操作层：先给三个最高频按钮（到了/用餐/请假），
// 点"更多"之后弹层整体换成完整的 StudentCard（评价/任务/海报/统计/链接一个
// 不少），不是在快捷按钮下面继续往下堆，避免同样的操作出现两遍。只读模式下
// （管理员查看别人的班）没有任何可操作的快捷按钮，直接展示只读版完整卡片。
export function StudentQuickActionPopup({
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
  onClose,
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
}: StudentQuickActionPopupProps) {
  const [stage, setStage] = useState<'quick' | 'full'>(readOnly ? 'full' : 'quick')
  useEscapeToClose(onClose)

  function handleArrivalTap() {
    if (arrivedAt) {
      onClearArrival?.(student.id)
    } else {
      onSetArrival?.(student.id, currentTimeString())
    }
  }

  function handleMealTap() {
    if (!arrivedAt) return
    if (hasMeal) {
      onClearMeal?.(student.id)
    } else {
      onSetMeal?.(student.id)
    }
  }

  function handleLeaveTap() {
    if (arrivedAt) return
    if (leaveReason) {
      onClearLeave?.(student.id)
    } else {
      onSetLeave?.(student.id, DEFAULT_LEAVE_REASON)
    }
  }

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div className="share-modal student-popup" data-stage={stage} role="dialog" aria-modal="true" aria-label={student.name}>
        {/* "返回"/快捷按钮/"更多"按钮和下面 full 阶段的完整卡片是互斥的两套界面，
            用条件渲染而不是纯 CSS 隐藏——CSS 隐藏虽然视觉上一样，但会让这些按钮
            在另一个阶段仍然挂在 DOM 里、可以被 Tab 键聚焦到，是真实的可访问性问题，
            条件渲染顺带也让这部分逻辑能被组件测试覆盖到。 */}
        {stage === 'full' && !readOnly && (
          <button type="button" className="popup-back" onClick={() => setStage('quick')}>
            ‹ 返回
          </button>
        )}
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={onClose}>
          ×
        </button>

        {stage === 'quick' && (
          <div className="popup-head">
            <span className="chip-avatar-wrap">
              <span className="chip-avatar" aria-hidden="true">
                {student.avatarUrl ? <img src={student.avatarUrl} alt="" /> : student.name.slice(0, 1)}
              </span>
            </span>
            <div>
              <p className="popup-name">{student.name}</p>
              <p className="popup-sub">{student.schoolClassName ?? '未填写学籍班'}</p>
            </div>
          </div>
        )}

        {stage === 'quick' && !readOnly && (
          <div className="quick-actions">
            <button
              type="button"
              className={`quick-action-btn${arrivedAt ? ' is-active' : ''}`}
              onClick={handleArrivalTap}
            >
              <span className="qa-icon">🕐</span>
              到了
              <span className="qa-state">{arrivedAt || '点击打卡'}</span>
            </button>
            <button
              type="button"
              className={`quick-action-btn${hasMeal ? ' is-active' : ''}`}
              onClick={handleMealTap}
              disabled={!arrivedAt}
            >
              <span className="qa-icon">🍚</span>
              用餐
              <span className="qa-state">{hasMeal ? '已用餐' : arrivedAt ? '去用餐' : '需先到了'}</span>
            </button>
            <button
              type="button"
              className={`quick-action-btn is-warn${leaveReason ? ' is-active' : ''}`}
              onClick={handleLeaveTap}
              disabled={!!arrivedAt}
            >
              <span className="qa-icon">🌴</span>
              请假
              <span className="qa-state">{leaveReason ? '已请假' : arrivedAt ? '已到不可请假' : '点击请假'}</span>
            </button>
          </div>
        )}

        {stage === 'quick' && !readOnly && (
          <button type="button" className="more-btn" onClick={() => setStage('full')}>
            更多 ›
          </button>
        )}

        {stage === 'full' && (
          <div className="full-card-body">
            <StudentCard
              student={student}
              tasks={tasks}
              dismissed={dismissed}
              templates={templates}
              rating={rating}
              comment={comment}
              arrivedAt={arrivedAt}
              hasMeal={hasMeal}
              leaveReason={leaveReason}
              date={date}
              readOnly={readOnly}
              onToggleTask={onToggleTask}
              onDeleteTask={onDeleteTask}
              onAddFromTemplate={onAddFromTemplate}
              onAddCustom={onAddCustom}
              onUploadAvatar={onUploadAvatar}
              onSetRating={onSetRating}
              onSetComment={onSetComment}
              onSetArrival={onSetArrival}
              onClearArrival={onClearArrival}
              onSetMeal={onSetMeal}
              onClearMeal={onClearMeal}
              onSetLeave={onSetLeave}
              onClearLeave={onClearLeave}
              onShowToast={onShowToast}
              statsFetchFn={statsFetchFn}
              shareLinkFetchFn={shareLinkFetchFn}
            />
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}
