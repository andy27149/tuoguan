import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import type { DailyTask } from '../api/dailyTasks'
import { computeCardStatus } from '../kanban/cardStatus'
import { subjectColor, subjectIconMarkup } from '../kanban/subjectIcons'
import { StarRating } from './StarRating'

interface SharePosterStudent {
  id: number
  name: string
  schoolClassName: string
  avatarUrl?: string | null
}

interface SharePosterModalProps {
  student: SharePosterStudent
  tasks: DailyTask[]
  rating: number
  comment: string
  dismissed: boolean
  date: string
  onCommentChange: (comment: string) => void
  onClose: () => void
  onShowToast: (message: string) => void
}

function defaultCheer(tasks: DailyTask[], dismissed: boolean): string {
  const status = computeCardStatus(tasks, dismissed)
  const total = tasks.length
  const done = tasks.filter((t) => t.completed).length
  const allDone = total > 0 && done === total
  if (allDone) return '今天全部完成啦，为TA的自觉点赞！🎉'
  if (status === 'dismissedIncomplete') return '放学时还有任务没完成，回家一起补上吧～'
  return '继续加油，今天的任务快完成啦！💪'
}

export function SharePosterModal({
  student,
  tasks,
  rating,
  comment,
  dismissed,
  date,
  onCommentChange,
  onClose,
  onShowToast,
}: SharePosterModalProps) {
  const [localComment, setLocalComment] = useState(comment)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  const total = tasks.length
  const done = tasks.filter((t) => t.completed).length
  const allDone = total > 0 && done === total
  const cheer = defaultCheer(tasks, dismissed)
  const cheerText = localComment.trim() ? localComment.trim() : cheer

  const commitAndClose = () => {
    if (localComment !== comment) {
      onCommentChange(localComment)
    }
    onClose()
  }

  useEffect(() => {
    const onKeydown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') commitAndClose()
    }
    document.addEventListener('keydown', onKeydown)
    return () => document.removeEventListener('keydown', onKeydown)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [localComment])

  const handleCopyCaption = () => {
    const taskLines =
      total === 0
        ? '今天还没有布置任务'
        : tasks.map((t) => `${t.completed ? '✓' : '✗'} [${t.subject}] ${t.name}`).join('\n')
    const caption = `${date} ${student.name}今日成长报告\n完成任务 ${done}/${total}，今日评价 ${rating || 0}星。\n${taskLines}\n${cheerText}`

    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(caption).then(
        () => onShowToast('文案已复制，去微信粘贴发送吧'),
        () => onShowToast('复制失败，请手动选择文字复制'),
      )
    } else {
      onShowToast('复制失败，请手动选择文字复制')
    }
  }

  return createPortal(
    <div
      className="share-overlay is-visible"
      onClick={(e) => {
        if (e.target === e.currentTarget) commitAndClose()
      }}
    >
      <div className="share-modal" role="dialog" aria-modal="true" aria-label="分享给家长">
        <button type="button" className="share-modal__close" aria-label="关闭" onClick={commitAndClose}>
          ×
        </button>

        <div className="share-poster">
          <div className="share-poster__header">
            <span>今日成长报告</span>
            <span className="share-poster__date">{date}</span>
          </div>
          <div className="share-poster__who">
            <div className="share-poster__avatar-wrap">
              <div className="share-poster__avatar">
                {student.avatarUrl ? (
                  <img src={student.avatarUrl} alt={student.name} />
                ) : (
                  student.name.slice(0, 1)
                )}
              </div>
              {allDone && (
                <svg className="share-poster__medal" viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="12" r="10" fill="url(#ringGold)" />
                  <path
                    d="M8 12.5l2.5 2.5 5-5.5"
                    stroke="#fff"
                    strokeWidth="2"
                    fill="none"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              )}
            </div>
            <div>
              <p className="share-poster__name">{student.name}</p>
              <p className="share-poster__class">{student.schoolClassName}</p>
            </div>
          </div>

          <StarRating value={rating} readOnly />

          <p className="share-poster__summary">
            今日完成任务 {done}/{total}
          </p>

          <ul className="share-poster__tasks">
            {total === 0 ? (
              <li className="share-poster__task">今天还没有布置任务</li>
            ) : (
              tasks.map((t) => (
                <li key={t.id} className={`share-poster__task${t.completed ? ' is-done' : ''}`}>
                  <span
                    className="task-subject"
                    style={{ color: subjectColor(t.subject) }}
                    dangerouslySetInnerHTML={{ __html: subjectIconMarkup(t.subject, t.id) }}
                  />
                  <span>
                    [{t.subject}] {t.name}
                  </span>
                  <span className="share-poster__task-mark">{t.completed ? '✓' : '—'}</span>
                </li>
              ))
            )}
          </ul>

          <p className="share-poster__cheer">
            {cheerText.split('\n').map((line, i) => (
              <span key={i}>
                {i > 0 && <br />}
                {line}
              </span>
            ))}
          </p>
        </div>

        <div className="teacher-comment">
          <label className="teacher-comment__label">
            今日评语（可编辑，会显示在海报上）
            <textarea
              ref={textareaRef}
              className="teacher-comment__input"
              rows={2}
              maxLength={120}
              placeholder={cheer}
              value={localComment}
              onChange={(e) => setLocalComment(e.target.value)}
              onBlur={() => {
                if (localComment !== comment) onCommentChange(localComment)
              }}
            />
          </label>
        </div>

        <p className="share-hint">长按海报可保存图片，或复制文案后发送到微信</p>

        <div className="share-modal__actions">
          <button type="button" className="btn-secondary" onClick={handleCopyCaption}>
            复制文案
          </button>
          <button type="button" className="btn-small" onClick={commitAndClose}>
            完成
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
