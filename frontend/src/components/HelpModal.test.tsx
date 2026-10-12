import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { HelpModal } from './HelpModal'

describe('HelpModal', () => {
  it('shows admin-specific steps and FAQ when role is admin', () => {
    render(<HelpModal role="admin" onClose={vi.fn()} />)

    expect(screen.getByText('建教师账号')).toBeInTheDocument()
    expect(screen.getByText('学生总览：管全机构学生的地方')).toBeInTheDocument()
    expect(screen.getByText('一个学生既在托管班又报了课外课，算哪种？')).toBeInTheDocument()
  })

  it('does not show teacher-specific content when role is admin', () => {
    render(<HelpModal role="admin" onClose={vi.fn()} />)

    expect(screen.queryByText('课外课：点名消课')).not.toBeInTheDocument()
    expect(screen.queryByText('消课点错了，能撤销吗？')).not.toBeInTheDocument()
  })

  it('shows teacher-specific steps and FAQ when role is teacher', () => {
    render(<HelpModal role="teacher" onClose={vi.fn()} />)

    expect(screen.getByText('托管看板：每天的主战场')).toBeInTheDocument()
    expect(screen.getByText('课外课：点名消课')).toBeInTheDocument()
    expect(screen.getByText('消课到底扣谁的钱、扣多少？')).toBeInTheDocument()
  })

  it('does not show admin-specific content when role is teacher', () => {
    render(<HelpModal role="teacher" onClose={vi.fn()} />)

    expect(screen.queryByText('建教师账号')).not.toBeInTheDocument()
    expect(screen.queryByText('账单管理：怎么收钱')).not.toBeInTheDocument()
  })

  it('calls onClose when the close button is clicked', () => {
    const onClose = vi.fn()
    render(<HelpModal role="admin" onClose={onClose} />)

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))

    expect(onClose).toHaveBeenCalled()
  })
})
