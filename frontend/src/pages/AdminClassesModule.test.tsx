import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminClassesModule } from './AdminClassesModule'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'

vi.mock('../api/admin')

const TEACHERS = [
  { id: 1, phone: '13800000001', name: '张校长', role: 'ADMIN' as const, mustChangePassword: false },
  { id: 2, phone: '13800000002', name: '李老师', role: 'TEACHER' as const, mustChangePassword: false },
  { id: 3, phone: '13800000003', name: '王老师', role: 'TEACHER' as const, mustChangePassword: false },
]

const CLASSES = [
  {
    id: 10,
    name: '一班',
    teacherId: 2,
    teacherName: '李老师',
    teacherPhone: '13800000002',
    studentCount: 3,
  },
]

describe('AdminClassesModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchAdminClasses).mockResolvedValue(CLASSES)
    vi.mocked(adminApi.fetchTeachers).mockResolvedValue(TEACHERS)
  })

  it('lists existing classes read-only', async () => {
    render(<AdminClassesModule />)

    expect(await screen.findByText(/一班/)).toBeInTheDocument()
    expect(screen.getByText('李老师')).toBeInTheDocument()
    expect(screen.getByText('13800000002')).toBeInTheDocument()
  })

  it('loads the teacher dropdown data from its own fetchTeachers call', async () => {
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)

    expect(adminApi.fetchTeachers).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '编辑班级一班' }))
    const select = screen.getByLabelText('班级教师10') as HTMLSelectElement
    const optionLabels = Array.from(select.options).map((o) => o.textContent)
    expect(optionLabels).toEqual(['李老师', '王老师'])
  })

  it('asks for confirmation before deleting a class, and does nothing on cancel', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 0 })
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)

    fireEvent.click(screen.getByRole('button', { name: '删除班级一班' }))
    await screen.findByText(/确认删除班级「一班」吗/)

    fireEvent.click(screen.getByRole('button', { name: '否' }))

    expect(adminApi.deleteClassRoom).not.toHaveBeenCalled()
    expect(screen.queryByText(/确认删除班级「一班」吗/)).not.toBeInTheDocument()
  })

  it('shows the student count warning when the class has students', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 3 })
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)

    fireEvent.click(screen.getByRole('button', { name: '删除班级一班' }))

    expect(await screen.findByText(/班级「一班」下有 3 名学生/)).toBeInTheDocument()
  })

  it('deletes the class and refreshes the list after confirming', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 0 })
    vi.mocked(adminApi.deleteClassRoom).mockResolvedValue(undefined)
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)
    expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '删除班级一班' }))
    await screen.findByText(/确认删除班级「一班」吗/)
    fireEvent.click(screen.getByRole('button', { name: '是' }))

    await waitFor(() => expect(adminApi.deleteClassRoom).toHaveBeenCalledWith(10))
    await waitFor(() => expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(2))
  })

  it('shows an error when class deletion fails', async () => {
    vi.mocked(adminApi.fetchClassRoomDeletionImpact).mockResolvedValue({ studentCount: 0 })
    vi.mocked(adminApi.deleteClassRoom).mockRejectedValue(new Error('boom'))
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)

    fireEvent.click(screen.getByRole('button', { name: '删除班级一班' }))
    await screen.findByText(/确认删除班级「一班」吗/)
    fireEvent.click(screen.getByRole('button', { name: '是' }))

    expect(await screen.findByText('删除失败，请重试')).toBeInTheDocument()
  })

  it('renames a class and reassigns it to another teacher', async () => {
    const updated = { ...CLASSES[0], name: '一班改', teacherId: 3, teacherName: '王老师', teacherPhone: '13800000003' }
    vi.mocked(adminApi.updateClassRoom).mockResolvedValue(updated)
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)
    expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: '编辑班级一班' }))
    fireEvent.change(screen.getByLabelText('班级名称10'), { target: { value: '一班改' } })
    fireEvent.change(screen.getByLabelText('班级教师10'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() => expect(adminApi.updateClassRoom).toHaveBeenCalledWith(10, '一班改', 3))
    await waitFor(() => expect(adminApi.fetchAdminClasses).toHaveBeenCalledTimes(2))
  })

  it('shows a duplicate-name error when class rename fails with 409', async () => {
    vi.mocked(adminApi.updateClassRoom).mockRejectedValue(new ApiError(409, '冲突'))
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)

    fireEvent.click(screen.getByRole('button', { name: '编辑班级一班' }))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('该教师下已有同名班级')).toBeInTheDocument()
  })

  it('warns before saving when reassigning a class to a different teacher', async () => {
    render(<AdminClassesModule />)
    await screen.findByText(/一班/)

    fireEvent.click(screen.getByRole('button', { name: '编辑班级一班' }))
    fireEvent.change(screen.getByLabelText('班级教师10'), { target: { value: '3' } })

    expect(await screen.findByText(/该操作将把班级「一班」（含班内学生、任务库等全部内容）整体转移给新教师/)).toBeInTheDocument()
  })
})
