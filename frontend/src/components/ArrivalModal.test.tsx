import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { ArrivalModal } from './ArrivalModal'

describe('ArrivalModal', () => {
  it('shows the initial arrival time and echoes edits', () => {
    render(
      <ArrivalModal studentName="小明" arrivedAt="17:30" onSave={vi.fn()} onClear={vi.fn()} onClose={vi.fn()} />,
    )

    expect(screen.getByText('小明的到达签到')).toBeInTheDocument()
    expect(screen.getByDisplayValue('17:30')).toBeInTheDocument()
  })

  it('calls onSave with the edited value and onClose when finishing', () => {
    const onSave = vi.fn()
    const onClose = vi.fn()
    render(
      <ArrivalModal studentName="小明" arrivedAt="17:30" onSave={onSave} onClear={vi.fn()} onClose={onClose} />,
    )

    fireEvent.change(screen.getByLabelText('到达时间'), { target: { value: '18:00' } })
    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(onSave).toHaveBeenCalledWith('18:00')
    expect(onClose).toHaveBeenCalled()
  })

  it('does not call onSave when nothing changed', () => {
    const onSave = vi.fn()
    const onClose = vi.fn()
    render(
      <ArrivalModal studentName="小明" arrivedAt="17:30" onSave={onSave} onClear={vi.fn()} onClose={onClose} />,
    )

    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(onSave).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })

  it('calls onClear and onClose when clearing the check-in', () => {
    const onClear = vi.fn()
    const onClose = vi.fn()
    render(
      <ArrivalModal studentName="小明" arrivedAt="17:30" onSave={vi.fn()} onClear={onClear} onClose={onClose} />,
    )

    fireEvent.click(screen.getByRole('button', { name: '清除签到' }))

    expect(onClear).toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })
})
