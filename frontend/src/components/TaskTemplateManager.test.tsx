import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { TaskTemplateManager } from './TaskTemplateManager'

const TEMPLATES = [
  { id: 1, subject: '数学', name: '口算练习' },
  { id: 2, subject: '语文', name: '阅读打卡' },
]

describe('TaskTemplateManager', () => {
  it('is collapsed by default and expands on click', () => {
    render(<TaskTemplateManager templates={TEMPLATES} onCreate={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.queryByText('[数学] 口算练习')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '任务库管理（2）展开' }))

    expect(screen.getByText('[数学] 口算练习')).toBeInTheDocument()
    expect(screen.getByText('[语文] 阅读打卡')).toBeInTheDocument()
  })

  it('shows an empty-library message when there are no templates', () => {
    render(<TaskTemplateManager templates={[]} onCreate={vi.fn()} onDelete={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '任务库管理（0）展开' }))

    expect(screen.getByText('任务库为空')).toBeInTheDocument()
  })

  it('submits subject and name and clears the form on success', async () => {
    const onCreate = vi.fn().mockResolvedValue(undefined)
    render(<TaskTemplateManager templates={[]} onCreate={onCreate} onDelete={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: '任务库管理（0）展开' }))

    fireEvent.change(screen.getByPlaceholderText('科目'), { target: { value: '英语' } })
    fireEvent.change(screen.getByPlaceholderText('任务名称'), { target: { value: '单词听写' } })
    fireEvent.click(screen.getByRole('button', { name: '添加' }))

    await waitFor(() => expect(onCreate).toHaveBeenCalledWith('英语', '单词听写'))
    await waitFor(() => expect(screen.getByPlaceholderText('科目')).toHaveValue(''))
  })

  it('disables the submit button until both fields are filled', () => {
    render(<TaskTemplateManager templates={[]} onCreate={vi.fn()} onDelete={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: '任务库管理（0）展开' }))

    expect(screen.getByRole('button', { name: '添加' })).toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('科目'), { target: { value: '英语' } })
    expect(screen.getByRole('button', { name: '添加' })).toBeDisabled()

    fireEvent.change(screen.getByPlaceholderText('任务名称'), { target: { value: '单词听写' } })
    expect(screen.getByRole('button', { name: '添加' })).not.toBeDisabled()
  })

  it('calls onDelete with the template id', () => {
    const onDelete = vi.fn().mockResolvedValue(undefined)
    render(<TaskTemplateManager templates={TEMPLATES} onCreate={vi.fn()} onDelete={onDelete} />)
    fireEvent.click(screen.getByRole('button', { name: '任务库管理（2）展开' }))

    fireEvent.click(screen.getByRole('button', { name: '删除模板口算练习' }))

    expect(onDelete).toHaveBeenCalledWith(1)
  })
})
