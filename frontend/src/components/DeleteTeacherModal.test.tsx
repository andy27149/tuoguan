import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { DeleteTeacherModal } from './DeleteTeacherModal'
import type { Teacher } from '../api/admin'

const OTHER_TEACHERS: Teacher[] = [
  { id: 2, phone: '13800000002', name: '李老师', role: 'TEACHER', mustChangePassword: false },
  { id: 3, phone: '13800000003', name: '', role: 'TEACHER', mustChangePassword: false },
]

describe('DeleteTeacherModal', () => {
  it('shows a loading placeholder while impact is null', () => {
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={null}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={vi.fn()}
        onClose={vi.fn()}
        submitting={false}
      />,
    )

    expect(screen.getByText('加载中...')).toBeInTheDocument()
  })

  it('shows the simple confirmation text when the teacher has no students', () => {
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={{ classCount: 1, studentCount: 0, templateCount: 2, hasStudents: false }}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={vi.fn()}
        onClose={vi.fn()}
        submitting={false}
      />,
    )

    expect(screen.getByText(/该教师名下没有学生/)).toBeInTheDocument()
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  it('confirms DELETE_ALL directly when the teacher has no students', () => {
    const onConfirm = vi.fn()
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={{ classCount: 1, studentCount: 0, templateCount: 2, hasStudents: false }}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={onConfirm}
        onClose={vi.fn()}
        submitting={false}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(onConfirm).toHaveBeenCalledWith('DELETE_ALL')
  })

  it('shows the two-way choice when the teacher has students', () => {
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={{ classCount: 1, studentCount: 3, templateCount: 2, hasStudents: true }}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={vi.fn()}
        onClose={vi.fn()}
        submitting={false}
      />,
    )

    expect(screen.getByLabelText(/删除全部/)).toBeInTheDocument()
    expect(screen.getByLabelText(/转移给其他老师/)).toBeInTheDocument()
  })

  it('disables the confirm button in transfer mode until a target teacher is chosen', () => {
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={{ classCount: 1, studentCount: 3, templateCount: 2, hasStudents: true }}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={vi.fn()}
        onClose={vi.fn()}
        submitting={false}
      />,
    )

    fireEvent.click(screen.getByLabelText(/转移给其他老师/))
    expect(screen.getByRole('button', { name: '确认删除' })).toBeDisabled()

    fireEvent.change(screen.getByRole('combobox'), { target: { value: '2' } })
    expect(screen.getByRole('button', { name: '确认删除' })).not.toBeDisabled()
  })

  it('confirms TRANSFER with the selected target teacher id', () => {
    const onConfirm = vi.fn()
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={{ classCount: 1, studentCount: 3, templateCount: 2, hasStudents: true }}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={onConfirm}
        onClose={vi.fn()}
        submitting={false}
      />,
    )

    fireEvent.click(screen.getByLabelText(/转移给其他老师/))
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(onConfirm).toHaveBeenCalledWith('TRANSFER', 3)
  })

  it('confirms DELETE_ALL when a student-having teacher keeps the default mode', () => {
    const onConfirm = vi.fn()
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={{ classCount: 1, studentCount: 3, templateCount: 2, hasStudents: true }}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={onConfirm}
        onClose={vi.fn()}
        submitting={false}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    expect(onConfirm).toHaveBeenCalledWith('DELETE_ALL')
  })

  it('closes when the close button is clicked', () => {
    const onClose = vi.fn()
    render(
      <DeleteTeacherModal
        teacherName="王老师"
        impact={{ classCount: 1, studentCount: 0, templateCount: 0, hasStudents: false }}
        otherTeachers={OTHER_TEACHERS}
        onConfirm={vi.fn()}
        onClose={onClose}
        submitting={false}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(onClose).toHaveBeenCalled()
  })
})
