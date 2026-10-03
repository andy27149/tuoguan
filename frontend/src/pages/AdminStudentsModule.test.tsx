import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminStudentsModule } from './AdminStudentsModule'
import * as courseApi from '../api/course'
import * as unitApi from '../api/unit'

vi.mock('../api/course')
vi.mock('../api/unit')

const STUDENTS: courseApi.AdminStudent[] = [
  {
    id: 1,
    name: '小明',
    schoolClassName: '三年级一班',
    classRoomId: 20,
    classRoomName: '托管一班',
    offCampusOnly: false,
    enrolledCourseNames: ['书法课'],
  },
  {
    id: 2,
    name: '小红',
    schoolClassName: '四年级二班',
    classRoomId: null,
    classRoomName: null,
    offCampusOnly: true,
    enrolledCourseNames: [],
  },
]

const CLASS_ROOMS: unitApi.TeachingUnit[] = [
  {
    id: 20,
    name: '托管一班',
    billingMode: 'MONTHLY',
    teacherId: 3,
    teacherName: '王老师',
    teacherPhone: '13900000003',
    lessonDurationMinutes: null,
    pricePerLesson: null,
    active: true,
  },
]

describe('AdminStudentsModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue(STUDENTS)
    vi.mocked(courseApi.fetchStudentCourseStatement).mockResolvedValue({
      balances: [],
      recharges: [],
      consumptions: [],
    })
    vi.mocked(unitApi.fetchTeachingUnits).mockResolvedValue(CLASS_ROOMS)
  })

  it('lists students read-only with 托管/课外 badges', async () => {
    render(<AdminStudentsModule />)

    expect(await screen.findByText('小明')).toBeInTheDocument()
    expect(screen.getByText('小红')).toBeInTheDocument()
    expect(screen.getAllByText('托管一班').length).toBeGreaterThan(0)
    expect(screen.getByText('托管')).toBeInTheDocument()
    expect(screen.getByText('纯课外')).toBeInTheDocument()
  })

  it('shows enrolled course names, or a dash when none', async () => {
    render(<AdminStudentsModule />)

    expect(await screen.findByText('书法课')).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    const rowFor小红 = rows.find((r) => r.textContent?.includes('小红'))
    expect(rowFor小红?.textContent).toContain('—')
  })

  it('only shows the 对账单/充值 action for pure off-campus students', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    const rows = screen.getAllByRole('row')
    const rowFor小明 = rows.find((r) => r.textContent?.includes('小明'))
    const rowFor小红 = rows.find((r) => r.textContent?.includes('小红'))

    expect(rowFor小明 && Array.from(rowFor小明.querySelectorAll('button'))).toHaveLength(0)
    expect(rowFor小红 && Array.from(rowFor小红.querySelectorAll('button'))).toHaveLength(1)
  })

  it('opens the course statement modal with the correct student when clicked', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小红')

    fireEvent.click(screen.getByRole('button', { name: '对账单/充值' }))

    expect(await screen.findByText('课外账户对账单 - 小红')).toBeInTheDocument()
    await waitFor(() => expect(courseApi.fetchStudentCourseStatement).toHaveBeenCalledWith(2))
  })

  it('shows an empty state when there are no students', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue([])
    render(<AdminStudentsModule />)

    expect(await screen.findByText('暂无学生')).toBeInTheDocument()
  })

  it('shows an error message when loading fails', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockRejectedValue(new Error('boom'))
    render(<AdminStudentsModule />)

    expect(await screen.findByText('加载学生列表失败，请刷新重试')).toBeInTheDocument()
  })

  it('creates a pure off-campus student when no teaching unit is picked', async () => {
    vi.mocked(courseApi.createAdminStudent).mockResolvedValue({
      id: 3,
      name: '小刚',
      schoolClassName: null,
      classRoomId: null,
      classRoomName: null,
      offCampusOnly: true,
      enrolledCourseNames: [],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小刚' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小刚', null, null))
    await waitFor(() => expect(courseApi.fetchAdminStudents).toHaveBeenCalledTimes(2))
  })

  it('creates a student attached to a selected teaching unit', async () => {
    vi.mocked(courseApi.createAdminStudent).mockResolvedValue({
      id: 4,
      name: '小芳',
      schoolClassName: '三年级一班',
      classRoomId: 20,
      classRoomName: '托管一班',
      offCampusOnly: false,
      enrolledCourseNames: [],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小芳' } })
    fireEvent.change(screen.getByPlaceholderText('学籍班'), { target: { value: '三年级一班' } })
    fireEvent.change(screen.getByLabelText('托管班'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小芳', '三年级一班', 20),
    )
  })

  it('shows an error when student creation fails', async () => {
    vi.mocked(courseApi.createAdminStudent).mockRejectedValue(new Error('boom'))
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小刚' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('创建失败，请重试')).toBeInTheDocument()
  })
})
