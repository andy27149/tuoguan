import type { ComponentProps } from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { StudentCard } from './StudentCard'
import * as studentsApi from '../api/students'
import type { Student } from '../api/students'
import QRCode from 'qrcode'

vi.mock('../api/students')
vi.mock('qrcode', () => ({
  default: { toCanvas: vi.fn() },
}))

const STUDENT: Student = {
  id: 1,
  name: '小明',
  schoolClassName: '一年级1班',
  enrolled: true,
  avatarUrl: null,
  enrolledCourseNames: [],
}

function setup(overrides: Partial<ComponentProps<typeof StudentCard>> = {}) {
  return render(
    <StudentCard
      student={STUDENT}
      tasks={[]}
      dismissed={false}
      templates={[]}
      rating={0}
      comment=""
      arrivedAt=""
      hasMeal={false}
      date="2026-08-23"
      onToggleTask={vi.fn()}
      onDeleteTask={vi.fn()}
      onAddFromTemplate={vi.fn()}
      onAddCustom={vi.fn()}
      onUploadAvatar={vi.fn()}
      onSetRating={vi.fn()}
      onSetComment={vi.fn()}
      onSetArrival={vi.fn()}
      onClearArrival={vi.fn()}
      onSetMeal={vi.fn()}
      onClearMeal={vi.fn()}
      onShowToast={vi.fn()}
      {...overrides}
    />,
  )
}

describe('StudentCard share-link button', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(studentsApi.fetchShareLink).mockResolvedValue({ token: 'abc123' })
    vi.mocked(QRCode.toCanvas).mockResolvedValue(undefined as never)
  })

  it('opens the ShareLinkModal showing the student-specific title when the 家长专属链接 button is clicked', async () => {
    setup()

    fireEvent.click(screen.getByRole('button', { name: '家长专属链接' }))

    expect(await screen.findByText('小明的家长专属链接')).toBeInTheDocument()
    expect(studentsApi.fetchShareLink).toHaveBeenCalledWith(1)
  })
})

describe('StudentCard arrival button', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('shows a plain label when arrival is not recorded yet', () => {
    setup()

    expect(screen.getByRole('button', { name: '到了' })).toBeInTheDocument()
  })

  it('shows the recorded arrival time in the button label', () => {
    setup({ arrivedAt: '15:40' })

    expect(screen.getByRole('button', { name: '到了 · 15:40' })).toBeInTheDocument()
  })

  it('checks in immediately with the current time on first click, without opening a modal', () => {
    const onSetArrival = vi.fn()
    setup({ onSetArrival })

    fireEvent.click(screen.getByRole('button', { name: '到了' }))

    expect(onSetArrival).toHaveBeenCalledWith(1, expect.stringMatching(/^\d{2}:\d{2}$/))
    expect(screen.queryByText('小明的到达签到')).not.toBeInTheDocument()
  })

  it('opens the ArrivalModal to edit or clear when arrival is already recorded', () => {
    const onSetArrival = vi.fn()
    setup({ arrivedAt: '15:40', onSetArrival })

    fireEvent.click(screen.getByRole('button', { name: '到了 · 15:40' }))
    expect(screen.getByText('小明的到达签到')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('到达时间'), { target: { value: '16:00' } })
    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(onSetArrival).toHaveBeenCalledWith(1, '16:00')
  })

  it('clears the arrival record when 清除签到 is clicked', () => {
    const onClearArrival = vi.fn()
    setup({ arrivedAt: '15:40', onClearArrival })

    fireEvent.click(screen.getByRole('button', { name: '到了 · 15:40' }))
    fireEvent.click(screen.getByRole('button', { name: '清除签到' }))

    expect(onClearArrival).toHaveBeenCalledWith(1)
  })
})

describe('StudentCard meal button', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('shows a plain label when meal is not recorded yet', () => {
    setup()

    expect(screen.getByRole('button', { name: '用餐' })).toBeInTheDocument()
  })

  it('marks the meal immediately on click when not yet recorded', () => {
    const onSetMeal = vi.fn()
    setup({ onSetMeal })

    fireEvent.click(screen.getByRole('button', { name: '用餐' }))

    expect(onSetMeal).toHaveBeenCalledWith(1)
  })

  it('shows 已用餐 and clears on click when meal is already recorded', () => {
    const onClearMeal = vi.fn()
    setup({ hasMeal: true, onClearMeal })

    const button = screen.getByRole('button', { name: '已用餐' })
    fireEvent.click(button)

    expect(onClearMeal).toHaveBeenCalledWith(1)
  })

  it('shows a static label in read-only mode when meal is recorded, and nothing when it is not', () => {
    const { rerender } = setup({ readOnly: true, hasMeal: true })
    expect(screen.getByText('今日已用餐')).toBeInTheDocument()

    rerender(
      <StudentCard
        student={STUDENT}
        tasks={[]}
        dismissed={false}
        templates={[]}
        rating={0}
        comment=""
        arrivedAt=""
        hasMeal={false}
        date="2026-08-23"
        readOnly
        onShowToast={vi.fn()}
      />,
    )
    expect(screen.queryByText('今日已用餐')).not.toBeInTheDocument()
  })
})

describe('StudentCard task deletion', () => {
  const TASK = { id: 5, studentId: 1, subject: '数学', name: '口算练习', completed: false, date: '2026-08-23' }

  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('asks for confirmation before deleting a task, and does nothing on cancel', () => {
    const onDeleteTask = vi.fn()
    setup({ tasks: [TASK], onDeleteTask })

    fireEvent.click(screen.getByRole('button', { name: '删除口算练习' }))
    expect(screen.getByText(/确认从今天的任务中删除/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onDeleteTask).not.toHaveBeenCalled()
    expect(screen.queryByText(/确认从今天的任务中删除/)).not.toBeInTheDocument()
  })

  it('deletes the task and shows a toast after confirming', () => {
    const onDeleteTask = vi.fn()
    const onShowToast = vi.fn()
    setup({ tasks: [TASK], onDeleteTask, onShowToast })

    fireEvent.click(screen.getByRole('button', { name: '删除口算练习' }))
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(onDeleteTask).toHaveBeenCalledWith(5)
    expect(onShowToast).toHaveBeenCalledWith('已删除「口算练习」')
  })
})
