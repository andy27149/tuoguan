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

function setup() {
  return render(
    <StudentCard
      student={STUDENT}
      tasks={[]}
      dismissed={false}
      templates={[]}
      rating={0}
      comment=""
      date="2026-08-23"
      onToggleTask={vi.fn()}
      onDeleteTask={vi.fn()}
      onAddFromTemplate={vi.fn()}
      onAddCustom={vi.fn()}
      onUploadAvatar={vi.fn()}
      onSetRating={vi.fn()}
      onSetComment={vi.fn()}
      onShowToast={vi.fn()}
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
