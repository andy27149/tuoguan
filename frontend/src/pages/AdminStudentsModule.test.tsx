import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminStudentsModule } from './AdminStudentsModule'
import * as courseApi from '../api/course'
import * as unitApi from '../api/unit'
import * as billingApi from '../api/billing'

vi.mock('../api/course')
vi.mock('../api/unit')
vi.mock('../api/billing')

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
  {
    id: 3,
    name: '小刚',
    schoolClassName: '五年级一班',
    classRoomId: 20,
    classRoomName: '托管一班',
    offCampusOnly: false,
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
    vi.mocked(courseApi.fetchStudentCourseConsumption).mockResolvedValue([])
    vi.mocked(billingApi.fetchStudentLeaveRecords).mockResolvedValue([])
    vi.mocked(billingApi.fetchClassBillingRate).mockResolvedValue(null)
  })

  it('lists students read-only with 托管/课外 badges', async () => {
    render(<AdminStudentsModule />)

    expect(await screen.findByText('小明')).toBeInTheDocument()
    expect(screen.getByText('小红')).toBeInTheDocument()
    expect(screen.getAllByText('托管一班').length).toBeGreaterThan(0)
    expect(screen.getAllByText('托管').length).toBe(2)
    expect(screen.getByText('纯课外')).toBeInTheDocument()
  })

  it('shows enrolled course names, or a dash when none', async () => {
    render(<AdminStudentsModule />)

    expect(await screen.findByText('书法课')).toBeInTheDocument()
    const rows = screen.getAllByRole('row')
    const rowFor小红 = rows.find((r) => r.textContent?.includes('小红'))
    expect(rowFor小红?.textContent).toContain('—')
  })

  it('shows actions for off-campus and dual-identity students, but a dash placeholder for pure custody students', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    const rows = screen.getAllByRole('row')
    const rowFor小明 = rows.find((r) => r.textContent?.includes('小明')) as HTMLElement
    const rowFor小红 = rows.find((r) => r.textContent?.includes('小红')) as HTMLElement
    const rowFor小刚 = rows.find((r) => r.textContent?.includes('小刚')) as HTMLElement

    expect(within(rowFor小明).getAllByRole('button')).toHaveLength(2)
    expect(within(rowFor小红).getAllByRole('button')).toHaveLength(2)
    expect(within(rowFor小刚).queryAllByRole('button')).toHaveLength(0)
    expect(rowFor小刚.textContent).toContain('—')
  })

  it('opens the course statement modal with the correct student when clicked', async () => {
    render(<AdminStudentsModule />)
    const rowFor小红 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小红')) as HTMLElement

    fireEvent.click(within(rowFor小红).getByRole('button', { name: '对账单/充值' }))

    expect(await screen.findByText('课外账户对账单 - 小红')).toBeInTheDocument()
    await waitFor(() => expect(courseApi.fetchStudentCourseStatement).toHaveBeenCalledWith(2))
  })

  it('opens the fee management modal (no class, no tuition section) for a pure off-campus student', async () => {
    render(<AdminStudentsModule />)
    const rowFor小红 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小红')) as HTMLElement

    fireEvent.click(within(rowFor小红).getByRole('button', { name: '费用管理' }))

    expect(await screen.findByText(/费用管理 - 小红/)).toBeInTheDocument()
    expect(screen.queryByLabelText('本月托管费金额')).not.toBeInTheDocument()
    expect(billingApi.fetchClassBillingRate).not.toHaveBeenCalled()
    expect(courseApi.fetchStudentCourseConsumption).toHaveBeenCalledWith(2, expect.any(String))
  })

  it('opens the fee management modal with the real class room for a dual-identity student', async () => {
    render(<AdminStudentsModule />)
    const rowFor小明 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小明')) as HTMLElement

    fireEvent.click(within(rowFor小明).getByRole('button', { name: '费用管理' }))

    expect(await screen.findByText(/费用管理 - 小明/)).toBeInTheDocument()
    expect(screen.getByLabelText('本月托管费金额')).toBeInTheDocument()
    await waitFor(() => expect(billingApi.fetchClassBillingRate).toHaveBeenCalledWith(20))
    expect(courseApi.fetchStudentCourseConsumption).toHaveBeenCalledWith(1, expect.any(String))
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
