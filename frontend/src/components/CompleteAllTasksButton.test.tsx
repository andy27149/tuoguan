import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { CompleteAllTasksButton } from './CompleteAllTasksButton'

describe('CompleteAllTasksButton', () => {
  it('is disabled when there are no incomplete tasks', () => {
    render(<CompleteAllTasksButton taskCount={0} studentCount={0} onCompleteAll={vi.fn()} />)

    expect(screen.getByRole('button', { name: '一键完成今日任务' })).toBeDisabled()
  })

  it('asks for confirmation before completing, and does nothing on cancel', () => {
    const onCompleteAll = vi.fn().mockResolvedValue(undefined)
    render(<CompleteAllTasksButton taskCount={5} studentCount={3} onCompleteAll={onCompleteAll} />)

    fireEvent.click(screen.getByRole('button', { name: '一键完成今日任务' }))
    expect(screen.getByText('确定要将 3 个孩子的 5 项未完成任务标记为已完成吗？')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onCompleteAll).not.toHaveBeenCalled()
  })

  it('completes all tasks after confirming', async () => {
    const onCompleteAll = vi.fn().mockResolvedValue(undefined)
    render(<CompleteAllTasksButton taskCount={5} studentCount={3} onCompleteAll={onCompleteAll} />)

    fireEvent.click(screen.getByRole('button', { name: '一键完成今日任务' }))
    fireEvent.click(screen.getByRole('button', { name: '确认完成' }))

    await waitFor(() => expect(onCompleteAll).toHaveBeenCalled())
    await waitFor(() =>
      expect(screen.queryByText('确定要将 3 个孩子的 5 项未完成任务标记为已完成吗？')).not.toBeInTheDocument(),
    )
  })
})
