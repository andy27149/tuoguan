import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { SharePosterModal } from './SharePosterModal'
import type { DailyTask } from '../api/dailyTasks'

const STUDENT = { id: 1, name: '小明', schoolClassName: '一年级1班', avatarUrl: null }

function task(overrides: Partial<DailyTask>): DailyTask {
  return { id: 1, studentId: 1, subject: '数学', name: '口算练习', custom: false, completed: false, ...overrides }
}

function setup(tasks: DailyTask[], opts: Partial<{ rating: number; comment: string; dismissed: boolean }> = {}) {
  const onCommentChange = vi.fn()
  const onClose = vi.fn()
  const onShowToast = vi.fn()
  render(
    <SharePosterModal
      student={STUDENT}
      tasks={tasks}
      rating={opts.rating ?? 0}
      comment={opts.comment ?? ''}
      dismissed={opts.dismissed ?? false}
      date="2026-08-23"
      onCommentChange={onCommentChange}
      onClose={onClose}
      onShowToast={onShowToast}
    />,
  )
  return { onCommentChange, onClose, onShowToast }
}

beforeEach(() => {
  Object.assign(navigator, {
    clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
  })
})

describe('SharePosterModal', () => {
  it('shows the celebratory cheer and copies a caption with every task marked done when all tasks are completed', async () => {
    const tasks = [task({ id: 1, completed: true }), task({ id: 2, name: '阅读打卡', subject: '语文', completed: true })]
    setup(tasks, { rating: 5 })

    expect(screen.getByText('今天全部完成啦，为TA的自觉点赞！🎉')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '复制文案' }))

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      '2026-08-23 小明今日成长报告\n完成任务 2/2，今日评价 5星。\n✓ [数学] 口算练习\n✓ [语文] 阅读打卡\n今天全部完成啦，为TA的自觉点赞！🎉',
    )
  })

  it('shows the encouraging cheer and mixed ✓/✗ marks when only some tasks are completed', () => {
    const tasks = [task({ id: 1, completed: true }), task({ id: 2, name: '阅读打卡', subject: '语文', completed: false })]
    setup(tasks)

    expect(screen.getByText('继续加油，今天的任务快完成啦！💪')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '复制文案' }))

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(
      expect.stringContaining('✓ [数学] 口算练习\n✗ [语文] 阅读打卡'),
    )
  })

  it('shows the dismissed-incomplete cheer once the student has been dismissed with unfinished tasks', () => {
    const tasks = [task({ id: 1, completed: false })]
    setup(tasks, { dismissed: true })

    expect(screen.getByText('放学时还有任务没完成，回家一起补上吧～')).toBeInTheDocument()
  })

  it('handles the no-tasks case in both the poster and the copied caption', () => {
    setup([])

    expect(screen.getByText('今天还没有布置任务')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '复制文案' }))

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining('今天还没有布置任务'))
  })

  it('lets a teacher override the cheer with a custom comment used in the caption', () => {
    const tasks = [task({ id: 1, completed: false })]
    setup(tasks, { comment: '今天状态不错' })

    expect(document.querySelector('.share-poster__cheer')).toHaveTextContent('今天状态不错')

    fireEvent.click(screen.getByRole('button', { name: '复制文案' }))

    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining('今天状态不错'))
  })

  it('commits the edited comment on blur', () => {
    const tasks = [task({ id: 1, completed: false })]
    const { onCommentChange } = setup(tasks)

    const textarea = screen.getByPlaceholderText('继续加油，今天的任务快完成啦！💪')
    fireEvent.change(textarea, { target: { value: '加油' } })
    fireEvent.blur(textarea)

    expect(onCommentChange).toHaveBeenCalledWith('加油')
  })

  it('closes on Escape and commits any pending comment change', () => {
    const tasks = [task({ id: 1, completed: false })]
    const { onCommentChange, onClose } = setup(tasks)

    const textarea = screen.getByPlaceholderText('继续加油，今天的任务快完成啦！💪')
    fireEvent.change(textarea, { target: { value: '加油' } })
    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onCommentChange).toHaveBeenCalledWith('加油')
    expect(onClose).toHaveBeenCalled()
  })
})
