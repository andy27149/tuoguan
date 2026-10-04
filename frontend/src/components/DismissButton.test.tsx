import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { DismissButton } from './DismissButton'

describe('DismissButton', () => {
  it('asks for confirmation before dismissing, and does nothing on cancel', () => {
    const onDismiss = vi.fn().mockResolvedValue(undefined)
    render(<DismissButton dismissed={false} onDismiss={onDismiss} onUndoDismiss={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '放学' }))
    expect(screen.getByText(/放学后，本班所有未完成的学生/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onDismiss).not.toHaveBeenCalled()
  })

  it('dismisses the class after confirming', async () => {
    const onDismiss = vi.fn().mockResolvedValue(undefined)
    render(<DismissButton dismissed={false} onDismiss={onDismiss} onUndoDismiss={vi.fn()} />)

    fireEvent.click(screen.getByRole('button', { name: '放学' }))
    fireEvent.click(screen.getByRole('button', { name: '确认放学' }))

    await waitFor(() => expect(onDismiss).toHaveBeenCalled())
  })

  it('undoes dismissal immediately without a confirmation dialog', async () => {
    const onUndoDismiss = vi.fn().mockResolvedValue(undefined)
    render(<DismissButton dismissed={true} onDismiss={vi.fn()} onUndoDismiss={onUndoDismiss} />)

    fireEvent.click(screen.getByRole('button', { name: '撤销放学' }))

    await waitFor(() => expect(onUndoDismiss).toHaveBeenCalled())
    expect(screen.queryByText(/放学后，本班所有未完成的学生/)).not.toBeInTheDocument()
  })
})
