import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RosterPage } from './RosterPage'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'

vi.mock('../api/classes')
vi.mock('../api/students')

const CLASSES = [{ id: 1, name: '一班' }]
const STUDENTS = [
  { id: 10, name: '小明', schoolClassName: '三年一班', enrolled: true, avatarUrl: null, enrolledCourseNames: [] },
  { id: 11, name: '小红', schoolClassName: '三年二班', enrolled: false, avatarUrl: null, enrolledCourseNames: ['书法课'] },
]

describe('RosterPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(classesApi.fetchClasses).mockResolvedValue(CLASSES)
    vi.mocked(studentsApi.fetchStudents).mockResolvedValue(STUDENTS)
  })

  it('lists students including disabled ones, with no delete button', async () => {
    render(<RosterPage onBack={vi.fn()} />)

    expect(await screen.findByText(/小明/)).toBeInTheDocument()
    expect(screen.getByText(/小红/)).toBeInTheDocument()
    expect(screen.getByText('（已停用）')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /删除/ })).not.toBeInTheDocument()
  })

  it('shows an inline badge for students also enrolled in off-campus courses', async () => {
    render(<RosterPage onBack={vi.fn()} />)

    expect(await screen.findByText(/另报名：书法课/)).toBeInTheDocument()
    expect(screen.getByText(/小明/).textContent).not.toMatch(/另报名/)
  })

  it('shows a hint to contact the admin instead of self-create-class/student forms', async () => {
    render(<RosterPage onBack={vi.fn()} />)
    await screen.findByText(/小明/)

    expect(screen.queryByPlaceholderText('托管班名称')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '新增学生' })).not.toBeInTheDocument()
    expect(screen.getByText('新增学生请联系管理员添加')).toBeInTheDocument()
  })

  it('shows a contact-admin message when there are no classes', async () => {
    vi.mocked(classesApi.fetchClasses).mockResolvedValue([])
    render(<RosterPage onBack={vi.fn()} />)

    expect(await screen.findByText('暂无托管班，请联系管理员创建')).toBeInTheDocument()
  })

  it('edits a student name and school class', async () => {
    vi.mocked(studentsApi.updateStudent).mockResolvedValue(undefined)
    render(<RosterPage onBack={vi.fn()} />)
    await screen.findByText(/小明/)

    fireEvent.click(screen.getAllByRole('button', { name: '编辑' })[0])
    const nameInput = screen.getByDisplayValue('小明')
    fireEvent.change(nameInput, { target: { value: '小明明' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(studentsApi.updateStudent).toHaveBeenCalledWith(10, '小明明', '三年一班', true),
    )
  })

  it('toggles enrolled status', async () => {
    vi.mocked(studentsApi.updateStudent).mockResolvedValue(undefined)
    render(<RosterPage onBack={vi.fn()} />)
    await screen.findByText(/小明/)

    fireEvent.click(screen.getAllByRole('button', { name: '停用' })[0])

    await waitFor(() =>
      expect(studentsApi.updateStudent).toHaveBeenCalledWith(10, '小明', '三年一班', false),
    )
  })

  it('calls onBack when the back button is clicked', async () => {
    const onBack = vi.fn()
    render(<RosterPage onBack={onBack} />)
    await screen.findByText(/小明/)

    fireEvent.click(screen.getByRole('button', { name: '返回看板' }))

    expect(onBack).toHaveBeenCalled()
  })
})
