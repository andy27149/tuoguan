import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { CreateStudentModal } from './CreateStudentModal'
import type { TeachingUnit } from '../api/unit'
import { ApiError } from '../api/client'

function unit(id: number, name: string): TeachingUnit {
  return {
    id,
    name,
    billingMode: 'LESSON_COUNT',
    teacherId: 900,
    teacherName: '王老师',
    teacherPhone: '13900000000',
    lessonDurationMinutes: 60,
    pricePerLesson: 50,
    active: true,
  }
}

const CLASS_ROOMS: TeachingUnit[] = [unit(1, '托管A班')]
const COURSES: TeachingUnit[] = [unit(10, '书法课'), unit(11, '围棋课')]

function setup(overrides: Partial<React.ComponentProps<typeof CreateStudentModal>> = {}) {
  const onCreate = vi.fn().mockResolvedValue(undefined)
  const onClose = vi.fn()
  render(
    <CreateStudentModal
      classRooms={CLASS_ROOMS}
      offCampusCourses={COURSES}
      onCreate={onCreate}
      onClose={onClose}
      {...overrides}
    />,
  )
  return { onCreate, onClose }
}

describe('CreateStudentModal', () => {
  it('renders the name, school class, class room, and course fields', () => {
    setup()

    expect(screen.getByLabelText('姓名')).toBeInTheDocument()
    expect(screen.getByLabelText('学籍班（选填）')).toBeInTheDocument()
    expect(screen.getByLabelText('托管班（选填）')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '书法课' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '围棋课' })).toBeInTheDocument()
  })

  it('does not render the course section at all when there are no off-campus courses', () => {
    setup({ offCampusCourses: [] })

    expect(screen.queryByText('课外课（可多选）')).not.toBeInTheDocument()
  })

  it('disables the create button until a name is entered', () => {
    setup()

    expect(screen.getByRole('button', { name: '创建' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    expect(screen.getByRole('button', { name: '创建' })).not.toBeDisabled()
  })

  it('toggles a course chip selected state on click and updates the selected count', () => {
    setup()

    expect(screen.getByText('已选 0 门')).toBeInTheDocument()
    const chip = screen.getByRole('button', { name: '书法课' })
    expect(chip).toHaveAttribute('aria-pressed', 'false')

    fireEvent.click(chip)

    expect(chip).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('已选 1 门')).toBeInTheDocument()

    fireEvent.click(chip)

    expect(chip).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByText('已选 0 门')).toBeInTheDocument()
  })

  it('calls onClose when clicking the close button or the cancel button', () => {
    const { onClose } = setup()

    fireEvent.click(screen.getByRole('button', { name: '关闭' }))
    expect(onClose).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '取消' }))
    expect(onClose).toHaveBeenCalledTimes(2)
  })

  it('calls onCreate with trimmed name, nullable fields, and selected course ids, then closes on success', async () => {
    const { onCreate, onClose } = setup()

    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '  小明  ' } })
    fireEvent.change(screen.getByLabelText('学籍班（选填）'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('托管班（选填）'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: '书法课' }))

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(onCreate).toHaveBeenCalledWith('小明', null, 1, [10]))
    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
  })

  it('shows a friendly message and does not close when onCreate rejects with a 404/400 ApiError', async () => {
    const onCreate = vi.fn().mockRejectedValue(new ApiError(404, 'not found'))
    const { onClose } = setup({ onCreate })
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('所选托管班不可用，请刷新后重试')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows a generic failure message and does not close for any other error', async () => {
    const onCreate = vi.fn().mockRejectedValue(new Error('network down'))
    const { onClose } = setup({ onCreate })
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('创建失败，请重试')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('disables the create button while the submission is in flight', async () => {
    let resolveCreate: () => void = () => {}
    const onCreate = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          resolveCreate = resolve
        }),
    )
    setup({ onCreate })
    fireEvent.change(screen.getByLabelText('姓名'), { target: { value: '小明' } })

    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(screen.getByRole('button', { name: '创建' })).toBeDisabled()
    resolveCreate()
    await waitFor(() => expect(onCreate).toHaveBeenCalled())
  })
})
