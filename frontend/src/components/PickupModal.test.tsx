import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { PickupModal } from './PickupModal'

describe('PickupModal', () => {
  it('shows the initial pickup values and echoes edits', () => {
    render(
      <PickupModal
        studentName="小明"
        pickedUpBy="奶奶"
        pickedUpAt="17:30"
        onSave={vi.fn()}
        onClose={vi.fn()}
      />,
    )

    expect(screen.getByText('小明的接送记录')).toBeInTheDocument()
    expect(screen.getByDisplayValue('奶奶')).toBeInTheDocument()
    expect(screen.getByDisplayValue('17:30')).toBeInTheDocument()
  })

  it('calls onSave with the edited values and onClose when finishing', () => {
    const onSave = vi.fn()
    const onClose = vi.fn()
    render(
      <PickupModal
        studentName="小明"
        pickedUpBy=""
        pickedUpAt=""
        onSave={onSave}
        onClose={onClose}
      />,
    )

    fireEvent.change(screen.getByPlaceholderText('例如：奶奶'), { target: { value: '妈妈' } })
    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(onSave).toHaveBeenCalledWith('妈妈', '')
    expect(onClose).toHaveBeenCalled()
  })

  it('does not call onSave when nothing changed', () => {
    const onSave = vi.fn()
    const onClose = vi.fn()
    render(
      <PickupModal
        studentName="小明"
        pickedUpBy="奶奶"
        pickedUpAt="17:30"
        onSave={onSave}
        onClose={onClose}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(onSave).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalled()
  })
})
