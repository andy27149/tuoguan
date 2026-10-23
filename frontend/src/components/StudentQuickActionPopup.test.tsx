import type { ComponentProps } from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { StudentQuickActionPopup } from './StudentQuickActionPopup'
import type { Student } from '../api/students'

vi.mock('../api/students')
vi.mock('qrcode', () => ({ default: { toCanvas: vi.fn() } }))

const STUDENT: Student = {
  id: 1,
  name: 'AA',
  schoolClassName: '5.2',
  enrolled: true,
  avatarUrl: null,
  enrolledCourseNames: [],
}

function setup(overrides: Partial<ComponentProps<typeof StudentQuickActionPopup>> = {}) {
  return render(
    <StudentQuickActionPopup
      student={STUDENT}
      tasks={[]}
      dismissed={false}
      templates={[]}
      rating={0}
      comment=""
      arrivedAt=""
      hasMeal={false}
      leaveReason=""
      date="2026-10-23"
      onClose={vi.fn()}
      onSetArrival={vi.fn()}
      onClearArrival={vi.fn()}
      onSetMeal={vi.fn()}
      onClearMeal={vi.fn()}
      onSetLeave={vi.fn()}
      onClearLeave={vi.fn()}
      onShowToast={vi.fn()}
      {...overrides}
    />,
  )
}

describe('StudentQuickActionPopup', () => {
  it('shows the three quick actions with correct enabled/disabled state when nothing is set yet', () => {
    setup()

    expect(screen.getByRole('button', { name: /到了.*点击打卡/ })).toBeEnabled()
    // 用餐依赖先到了，没到之前应该是禁用的，而不是允许提前记录用餐。
    expect(screen.getByRole('button', { name: /用餐.*需先到了/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /请假.*点击请假/ })).toBeEnabled()
  })

  it('tapping 到了 when not arrived calls onSetArrival with a HH:mm time', () => {
    const onSetArrival = vi.fn()
    setup({ onSetArrival })

    fireEvent.click(screen.getByRole('button', { name: /到了.*点击打卡/ }))

    expect(onSetArrival).toHaveBeenCalledWith(1, expect.stringMatching(/^\d{2}:\d{2}$/))
  })

  it('tapping 到了 again when already arrived calls onClearArrival instead of setting a new time', () => {
    const onClearArrival = vi.fn()
    const onSetArrival = vi.fn()
    setup({ arrivedAt: '08:00', onClearArrival, onSetArrival })

    fireEvent.click(screen.getByRole('button', { name: /到了.*08:00/ }))

    expect(onClearArrival).toHaveBeenCalledWith(1)
    expect(onSetArrival).not.toHaveBeenCalled()
  })

  it('用餐 toggles once arrived, and 请假 is disabled while arrived (到了/请假互斥)', () => {
    const onSetMeal = vi.fn()
    setup({ arrivedAt: '08:00', onSetMeal })

    const mealBtn = screen.getByRole('button', { name: /用餐.*去用餐/ })
    expect(mealBtn).toBeEnabled()
    fireEvent.click(mealBtn)
    expect(onSetMeal).toHaveBeenCalledWith(1)

    expect(screen.getByRole('button', { name: /请假.*已到不可请假/ })).toBeDisabled()
  })

  it('请假 sets a default reason when tapped before arrival, and clears it when tapped again', () => {
    const onSetLeave = vi.fn()
    setup({ onSetLeave })

    fireEvent.click(screen.getByRole('button', { name: /请假.*点击请假/ }))
    expect(onSetLeave).toHaveBeenCalledWith(1, expect.any(String))

    const onClearLeave = vi.fn()
    setup({ leaveReason: '家长请假半天', onClearLeave })
    fireEvent.click(screen.getByRole('button', { name: /请假.*已请假/ }))
    expect(onClearLeave).toHaveBeenCalledWith(1)
  })

  // "更多"之后弹层整体变成完整的 StudentCard，不是在快捷按钮下面继续往下堆——
  // 这里锁定快捷按钮真的被隐藏了，完整卡片的内容（评价/任务/操作按钮）确实渲染
  // 出来了，且能用"返回"退回快捷视图，避免以后回归成两份重复的操作按钮。
  it('"更多" replaces the quick actions with the full StudentCard, and 返回 restores the quick view', async () => {
    // 弹层用 createPortal 挂到 document.body 上，不在 render() 返回的本地
    // container 里，所以这里直接查 document.body。
    setup({ arrivedAt: '08:00', hasMeal: true })
    expect(document.body.querySelector('.quick-actions')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '更多 ›' }))

    // 快捷按钮区整个从 DOM 里消失（条件渲染，不是只靠 CSS 藏起来），不是在它
    // 下面继续往下堆完整卡片的内容。
    expect(document.body.querySelector('.quick-actions')).not.toBeInTheDocument()
    expect(await screen.findByText('今日评价')).toBeInTheDocument()
    expect(screen.getByText('今天还没有任务')).toBeInTheDocument()
    // 完整卡片里的操作条里应该还能看到同样的到了/用餐状态，证明传给 StudentCard 的
    // 是同一份真实数据，不是快捷视图自己编的另一份。
    expect(screen.getByRole('button', { name: /08:00/ })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '‹ 返回' }))
    expect(document.body.querySelector('.quick-actions')).toBeInTheDocument()
    expect(document.body.querySelector('.full-card-body')).not.toBeInTheDocument()
  })

  it('readOnly mode skips the quick stage entirely and shows the read-only full card directly', () => {
    setup({ readOnly: true, arrivedAt: '08:00' })

    expect(screen.queryByRole('button', { name: /^到了/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '更多 ›' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '‹ 返回' })).not.toBeInTheDocument()
    expect(screen.getByText('今日评价')).toBeInTheDocument()
  })

  it('closes via the × button', () => {
    const onClose = vi.fn()
    setup({ onClose })

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))
    expect(onClose).toHaveBeenCalled()
  })
})
