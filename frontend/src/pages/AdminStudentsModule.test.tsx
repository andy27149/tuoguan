import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
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
    enrolled: true,
    teacherName: '王老师',
    enrolledCourseNames: ['书法课'],
    enrolledCourseIds: [30],
  },
  {
    id: 2,
    name: '小红',
    schoolClassName: '四年级二班',
    classRoomId: null,
    classRoomName: null,
    offCampusOnly: true,
    enrolled: true,
    teacherName: null,
    enrolledCourseNames: [],
    enrolledCourseIds: [],
  },
  {
    id: 3,
    name: '小刚',
    schoolClassName: '五年级一班',
    classRoomId: 20,
    classRoomName: '托管一班',
    offCampusOnly: false,
    enrolled: true,
    teacherName: '王老师',
    enrolledCourseNames: [],
    enrolledCourseIds: [],
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

const OFF_CAMPUS_COURSES: unitApi.TeachingUnit[] = [
  {
    id: 30,
    name: '书法课',
    billingMode: 'LESSON_COUNT',
    teacherId: 3,
    teacherName: '王老师',
    teacherPhone: '13900000003',
    lessonDurationMinutes: 45,
    pricePerLesson: null,
    active: true,
  },
  {
    id: 31,
    name: '围棋课',
    billingMode: 'LESSON_COUNT',
    teacherId: 3,
    teacherName: '王老师',
    teacherPhone: '13900000003',
    lessonDurationMinutes: 45,
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
    vi.mocked(unitApi.fetchTeachingUnits).mockImplementation((billingMode) =>
      Promise.resolve(billingMode === 'LESSON_COUNT' ? OFF_CAMPUS_COURSES : CLASS_ROOMS),
    )
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
    await screen.findByText('小明')

    const rows = screen.getAllByRole('row')
    const rowFor小明 = rows.find((r) => r.textContent?.includes('小明'))
    const rowFor小红 = rows.find((r) => r.textContent?.includes('小红'))
    expect(within(rowFor小明 as HTMLElement).getByText('书法课')).toBeInTheDocument()
    expect(rowFor小红?.textContent).toContain('—')
  })

  it('shows 对账单/充值 for off-campus and dual-identity students with a course enrollment, but not for pure custody students', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    const rows = screen.getAllByRole('row')
    const rowFor小明 = rows.find((r) => r.textContent?.includes('小明')) as HTMLElement
    const rowFor小红 = rows.find((r) => r.textContent?.includes('小红')) as HTMLElement
    const rowFor小刚 = rows.find((r) => r.textContent?.includes('小刚')) as HTMLElement

    // 编辑 + 停用 对每个学生都有；对账单/充值 只有纯课外课/双重身份学生才有。
    // 费用管理已经并到账单管理页面，这里不再重复提供。
    expect(within(rowFor小明).getAllByRole('button')).toHaveLength(3)
    expect(within(rowFor小红).getAllByRole('button')).toHaveLength(3)
    expect(within(rowFor小刚).getAllByRole('button')).toHaveLength(2)
    expect(screen.queryByRole('button', { name: '费用管理' })).not.toBeInTheDocument()
  })

  it('shows the custody teacher name and enabled/disabled status badges', async () => {
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    expect(screen.getAllByText('王老师').length).toBe(2)
    expect(screen.getAllByText('已启用').length).toBe(3)
  })

  it('opens the course statement modal with the correct student when clicked', async () => {
    render(<AdminStudentsModule />)
    const rowFor小红 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小红')) as HTMLElement

    fireEvent.click(within(rowFor小红).getByRole('button', { name: '对账单/充值' }))

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
      enrolled: true,
      teacherName: null,
      enrolledCourseNames: [],
      enrolledCourseIds: [],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小刚' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() => expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小刚', null, null, []))
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
      enrolled: true,
      teacherName: '王老师',
      enrolledCourseNames: [],
      enrolledCourseIds: [],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小芳' } })
    fireEvent.change(screen.getByPlaceholderText('学籍班'), { target: { value: '三年级一班' } })
    fireEvent.change(screen.getByLabelText('托管班'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小芳', '三年级一班', 20, []),
    )
  })

  it('creates a student enrolled in the selected off-campus courses', async () => {
    vi.mocked(courseApi.createAdminStudent).mockResolvedValue({
      id: 5,
      name: '小华',
      schoolClassName: null,
      classRoomId: null,
      classRoomName: null,
      offCampusOnly: true,
      enrolled: true,
      teacherName: null,
      enrolledCourseNames: ['书法课'],
      enrolledCourseIds: [30],
    })
    render(<AdminStudentsModule />)
    await screen.findByText('小明')

    fireEvent.change(screen.getByPlaceholderText('姓名'), { target: { value: '小华' } })
    fireEvent.click(screen.getByLabelText('书法课'))
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(courseApi.createAdminStudent).toHaveBeenCalledWith('小华', null, null, [30]),
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

  it('edits a student name, school class, and teaching unit assignment', async () => {
    vi.mocked(courseApi.updateAdminStudent).mockResolvedValue({ ...STUDENTS[2], name: '小刚（转班）' })
    render(<AdminStudentsModule />)
    const rowFor小刚 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小刚')) as HTMLElement

    fireEvent.click(within(rowFor小刚).getByRole('button', { name: '编辑小刚' }))
    fireEvent.change(screen.getByLabelText('学生姓名3'), { target: { value: '小刚（转班）' } })
    fireEvent.change(screen.getByLabelText('托管班3'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(courseApi.updateAdminStudent).toHaveBeenCalledWith(3, '小刚（转班）', '五年级一班', null, true, []),
    )
    await waitFor(() => expect(courseApi.fetchAdminStudents).toHaveBeenCalledTimes(2))
  })

  it('pre-checks the courses a student is already enrolled in when editing', async () => {
    render(<AdminStudentsModule />)
    const rowFor小明 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小明')) as HTMLElement

    fireEvent.click(within(rowFor小明).getByRole('button', { name: '编辑小明' }))

    expect(screen.getByLabelText('书法课1')).toBeChecked()
    expect(screen.getByLabelText('围棋课1')).not.toBeChecked()
  })

  it('updates a student course enrollment when checkboxes change in edit mode', async () => {
    vi.mocked(courseApi.updateAdminStudent).mockResolvedValue(STUDENTS[0])
    render(<AdminStudentsModule />)
    const rowFor小明 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小明')) as HTMLElement

    fireEvent.click(within(rowFor小明).getByRole('button', { name: '编辑小明' }))
    fireEvent.click(screen.getByLabelText('书法课1'))
    fireEvent.click(screen.getByLabelText('围棋课1'))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(courseApi.updateAdminStudent).toHaveBeenCalledWith(1, '小明', '三年级一班', 20, true, [31]),
    )
  })

  it('cancels an edit without calling the API', async () => {
    render(<AdminStudentsModule />)
    const rowFor小刚 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小刚')) as HTMLElement

    fireEvent.click(within(rowFor小刚).getByRole('button', { name: '编辑小刚' }))
    fireEvent.change(screen.getByLabelText('学生姓名3'), { target: { value: '改坏了' } })
    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(courseApi.updateAdminStudent).not.toHaveBeenCalled()
    expect(screen.getByText('小刚')).toBeInTheDocument()
  })

  it('shows an error when saving an edit fails', async () => {
    vi.mocked(courseApi.updateAdminStudent).mockRejectedValue(new Error('boom'))
    render(<AdminStudentsModule />)
    const rowFor小刚 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小刚')) as HTMLElement

    fireEvent.click(within(rowFor小刚).getByRole('button', { name: '编辑小刚' }))
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    expect(await screen.findByText('保存失败，请重试')).toBeInTheDocument()
  })

  it('deactivates a student after confirming', async () => {
    vi.mocked(courseApi.updateAdminStudent).mockResolvedValue({ ...STUDENTS[2], enrolled: false })
    render(<AdminStudentsModule />)
    const rowFor小刚 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小刚')) as HTMLElement

    fireEvent.click(within(rowFor小刚).getByRole('button', { name: '停用小刚' }))
    expect(await screen.findByText(/停用小刚后/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '确认停用' }))

    await waitFor(() =>
      expect(courseApi.updateAdminStudent).toHaveBeenCalledWith(3, '小刚', '五年级一班', 20, false, []),
    )
    await waitFor(() => expect(courseApi.fetchAdminStudents).toHaveBeenCalledTimes(2))
  })

  it('does not deactivate when the confirmation is cancelled', async () => {
    render(<AdminStudentsModule />)
    const rowFor小刚 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小刚')) as HTMLElement

    fireEvent.click(within(rowFor小刚).getByRole('button', { name: '停用小刚' }))
    fireEvent.click(screen.getByRole('button', { name: '取消' }))

    expect(courseApi.updateAdminStudent).not.toHaveBeenCalled()
  })

  it('reactivates a disabled student immediately without a confirmation dialog', async () => {
    const disabledStudent = { ...STUDENTS[2], enrolled: false }
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue([STUDENTS[0], STUDENTS[1], disabledStudent])
    vi.mocked(courseApi.updateAdminStudent).mockResolvedValue({ ...disabledStudent, enrolled: true })
    render(<AdminStudentsModule />)
    const rowFor小刚 = (await screen.findAllByRole('row')).find((r) => r.textContent?.includes('小刚')) as HTMLElement

    expect(within(rowFor小刚).getByText('已停用')).toBeInTheDocument()
    fireEvent.click(within(rowFor小刚).getByRole('button', { name: '启用小刚' }))

    await waitFor(() =>
      expect(courseApi.updateAdminStudent).toHaveBeenCalledWith(3, '小刚', '五年级一班', 20, true, []),
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
