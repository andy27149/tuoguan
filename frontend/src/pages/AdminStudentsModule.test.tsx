import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminStudentsModule } from './AdminStudentsModule'
import * as courseApi from '../api/course'

vi.mock('../api/course')

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

describe('AdminStudentsModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue(STUDENTS)
    vi.mocked(courseApi.fetchStudentCourseStatement).mockResolvedValue({
      balances: [],
      recharges: [],
      consumptions: [],
    })
    vi.mocked(courseApi.fetchAdminCourses).mockResolvedValue([])
  })

  it('lists students read-only with 托管/课外 badges, and no create entry', async () => {
    render(<AdminStudentsModule />)

    expect(await screen.findByText('小明')).toBeInTheDocument()
    expect(screen.getByText('小红')).toBeInTheDocument()
    expect(screen.getByText('托管一班')).toBeInTheDocument()
    expect(screen.getByText('托管')).toBeInTheDocument()
    expect(screen.getByText('纯课外')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '创建' })).not.toBeInTheDocument()
    expect(screen.queryByPlaceholderText('学生姓名')).not.toBeInTheDocument()
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
})
