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

const STUDENT: Student = { id: 1, name: '小明', schoolClassName: '一年级1班', enrolled: true, avatarUrl: null }

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

  it('opens the ShareLinkModal showing the student-specific title when the 家长链接 button is clicked', async () => {
    setup()

    fireEvent.click(screen.getByRole('button', { name: '家长链接' }))

    expect(await screen.findByText('小明的家长链接')).toBeInTheDocument()
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
