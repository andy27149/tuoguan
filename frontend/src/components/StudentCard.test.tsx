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
      pickedUpBy=""
      pickedUpAt=""
      date="2026-08-23"
      onToggleTask={vi.fn()}
      onDeleteTask={vi.fn()}
      onAddFromTemplate={vi.fn()}
      onAddCustom={vi.fn()}
      onUploadAvatar={vi.fn()}
      onSetRating={vi.fn()}
      onSetComment={vi.fn()}
      onSetPickup={vi.fn()}
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

describe('StudentCard pickup button', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('shows a plain label when no pickup is recorded yet', () => {
    setup()

    expect(screen.getByRole('button', { name: '记录接送' })).toBeInTheDocument()
  })

  it('shows the recorded pickup person in the button label', () => {
    setup({ pickedUpBy: '奶奶', pickedUpAt: '17:30' })

    expect(screen.getByRole('button', { name: '记录接送：奶奶' })).toBeInTheDocument()
  })

  it('opens the PickupModal and saves the entered pickup info on close', () => {
    const onSetPickup = vi.fn()
    setup({ onSetPickup })

    fireEvent.click(screen.getByRole('button', { name: '记录接送' }))
    expect(screen.getByText('小明的接送记录')).toBeInTheDocument()

    fireEvent.change(screen.getByPlaceholderText('例如：奶奶'), { target: { value: '爸爸' } })
    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(onSetPickup).toHaveBeenCalledWith(1, '爸爸', '')
  })
})
