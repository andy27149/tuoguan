import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { RollCallResultModal } from './RollCallResultModal'

describe('RollCallResultModal', () => {
  it('renders a success-labeled dialog showing the message for type success', () => {
    render(<RollCallResultModal type="success" message="已确认消课，新增 2 条记录" onClose={vi.fn()} />)

    expect(screen.getByRole('dialog', { name: '消课成功' })).toBeInTheDocument()
    expect(screen.getByText('已确认消课，新增 2 条记录')).toBeInTheDocument()
  })

  it('renders a warning-labeled dialog for type warning', () => {
    render(<RollCallResultModal type="warning" message="该课程尚未配置单价，请联系管理员配置" onClose={vi.fn()} />)

    expect(screen.getByRole('dialog', { name: '消课提醒' })).toBeInTheDocument()
    expect(screen.getByText('该课程尚未配置单价，请联系管理员配置')).toBeInTheDocument()
  })

  it('calls onClose when clicking the 知道了 button', () => {
    const onClose = vi.fn()
    render(<RollCallResultModal type="success" message="ok" onClose={onClose} />)

    fireEvent.click(screen.getByRole('button', { name: '知道了' }))

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('calls onClose when clicking the overlay backdrop', () => {
    const onClose = vi.fn()
    render(<RollCallResultModal type="success" message="ok" onClose={onClose} />)

    fireEvent.click(screen.getByRole('dialog').parentElement as HTMLElement)

    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
