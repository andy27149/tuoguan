import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RosterPage } from './RosterPage'
import * as classesApi from '../api/classes'
import * as studentsApi from '../api/students'
import { ApiError } from '../api/client'

vi.mock('../api/classes')
vi.mock('../api/students')

const CLASSES = [{ id: 1, name: '一班' }]
const STUDENTS = [
  { id: 10, name: '小明', schoolClassName: '三年一班', enrolled: true, avatarUrl: null },
  { id: 11, name: '小红', schoolClassName: '三年二班', enrolled: false, avatarUrl: null },
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

  it('creates a new class and switches to it', async () => {
    const created = { id: 2, name: '二班' }
    vi.mocked(classesApi.createClass).mockResolvedValue(created)
    render(<RosterPage onBack={vi.fn()} />)
    await screen.findByText(/小明/)

    fireEvent.change(screen.getByPlaceholderText('托管班名称'), { target: { value: '二班' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(classesApi.createClass).toHaveBeenCalledWith('二班'))
    expect(await screen.findByRole('tab', { name: '二班' })).toBeInTheDocument()
  })

  it('shows a duplicate-name message on 409', async () => {
    vi.mocked(classesApi.createClass).mockRejectedValue(new ApiError(409, '冲突'))
    render(<RosterPage onBack={vi.fn()} />)
    await screen.findByText(/小明/)

    fireEvent.change(screen.getByPlaceholderText('托管班名称'), { target: { value: '一班' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该托管班名称已存在')).toBeInTheDocument()
  })

  it('creates a student in the active class', async () => {
    vi.mocked(studentsApi.createStudent).mockResolvedValue(undefined)
    render(<RosterPage onBack={vi.fn()} />)
    await screen.findByText(/小明/)

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小刚' } })
    fireEvent.change(screen.getByPlaceholderText('学籍班'), { target: { value: '三年三班' } })
    fireEvent.click(screen.getByRole('button', { name: '新增学生' }))

    await waitFor(() =>
      expect(studentsApi.createStudent).toHaveBeenCalledWith(1, '小刚', '三年三班'),
    )
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
