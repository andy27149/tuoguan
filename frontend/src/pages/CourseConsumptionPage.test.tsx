import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { CourseConsumptionPage } from './CourseConsumptionPage'
import * as courseApi from '../api/course'
import * as studentsApi from '../api/students'
import { ApiError } from '../api/client'
import QRCode from 'qrcode'

vi.mock('../api/course')
vi.mock('../api/students')
vi.mock('qrcode', () => ({
  default: { toCanvas: vi.fn() },
}))

const COURSES: courseApi.Course[] = [
  { id: 1, name: '书法课', pricePerLesson: 50, lessonDurationMinutes: 60, active: true },
]

const ROSTER: courseApi.CourseRosterEntry[] = [
  { studentId: 100, name: '小明', schoolClassName: '三年级一班', offCampusOnly: false, balance: null },
  { studentId: 200, name: '小红', schoolClassName: null, offCampusOnly: true, balance: 3 },
]

function setup() {
  const onBack = vi.fn()
  render(<CourseConsumptionPage onBack={onBack} />)
  return { onBack }
}

describe('CourseConsumptionPage', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue(COURSES)
    vi.mocked(courseApi.fetchCourseRoster).mockResolvedValue(ROSTER)
    vi.mocked(studentsApi.fetchShareLink).mockResolvedValue({ token: 'abc123' })
    vi.mocked(QRCode.toCanvas).mockResolvedValue(undefined as never)
  })

  it('shows a message to contact the admin when there are no assigned courses', async () => {
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([])
    setup()
    expect(await screen.findByText('暂无课外课，请联系管理员分配')).toBeInTheDocument()
  })

  it('shows the balance badge for any student with a non-null balance, color-coded by threshold', async () => {
    vi.mocked(courseApi.fetchCourseRoster).mockResolvedValue([
      { studentId: 100, name: '小明', schoolClassName: '三年级一班', offCampusOnly: false, balance: null },
      { studentId: 200, name: '小红', schoolClassName: null, offCampusOnly: true, balance: 5 },
      { studentId: 300, name: '小刚', schoolClassName: null, offCampusOnly: true, balance: -1 },
      { studentId: 400, name: '小芳', schoolClassName: null, offCampusOnly: true, balance: 2 },
      // 托管班学生（双重身份）只要对本课程充值过，也应该展示余额——不再要求 offCampusOnly。
      { studentId: 500, name: '小强', schoolClassName: null, offCampusOnly: false, balance: 1 },
    ])
    setup()
    await screen.findByText('小明')

    expect(screen.queryByText(/小明.*余额/)).not.toBeInTheDocument()
    expect(screen.getByText('余额：5 课时')).toHaveClass('balance-badge--ok')
    expect(screen.getByText('余额：-1 课时')).toHaveClass('balance-badge--danger')
    expect(screen.getByText('余额：2 课时')).toHaveClass('balance-badge--warn')
    expect(screen.getByText('余额：1 课时')).toHaveClass('balance-badge--warn')
  })

  it('does not show a billing-mode classification badge — that bookkeeping is the backend\'s job, not the teacher\'s', async () => {
    setup()
    await screen.findByText('小明')

    expect(screen.queryByText('托管（计入月度账单）')).not.toBeInTheDocument()
    expect(screen.queryByText('纯课外（扣课时余额）')).not.toBeInTheDocument()
  })

  it('sorts the roster alphabetically by name, with no billing-mode grouping', async () => {
    vi.mocked(courseApi.fetchCourseRoster).mockResolvedValue([
      { studentId: 100, name: '小丙', schoolClassName: null, offCampusOnly: false, balance: null },
      { studentId: 200, name: '小乙', schoolClassName: null, offCampusOnly: true, balance: 100 },
      { studentId: 300, name: '小甲', schoolClassName: null, offCampusOnly: false, balance: 1 },
      { studentId: 400, name: '小丁', schoolClassName: null, offCampusOnly: true, balance: -5 },
    ])
    setup()
    await screen.findByText('小乙')

    const names = screen
      .getAllByRole('listitem')
      .map((li) => li.querySelector('.roster-card__name')?.textContent)
    expect(names).toEqual(['小丁', '小丙', '小乙', '小甲'])
  })

  it('shows a hint that course enrollment is managed by the admin, with no self-service controls', async () => {
    setup()
    await screen.findByText('小明')

    expect(screen.getByText('课外课报名由管理员在后台统一配置')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '新增并报名' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '添加到花名册' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '移出' })).not.toBeInTheDocument()
  })

  it('defaults all roster students to present and submits a roll call for everyone', async () => {
    vi.mocked(courseApi.recordBatchConsumption).mockResolvedValue([
      {
        id: 1,
        studentId: 100,
        courseId: 1,
        courseName: null,
        consumptionDate: '2026-09-30',
        priceSnapshot: 50,
        teacherName: null,
      },
      {
        id: 2,
        studentId: 200,
        courseId: 1,
        courseName: null,
        consumptionDate: '2026-09-30',
        priceSnapshot: 50,
        teacherName: null,
      },
    ])
    setup()
    await screen.findByText('小明')

    expect(screen.getByRole('checkbox', { name: /小明/ })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /小红/ })).toBeChecked()

    fireEvent.click(screen.getByRole('button', { name: '确认消课（2人）' }))

    // 花名册按姓名排序：小明 在 小红 前面。
    await waitFor(() =>
      expect(courseApi.recordBatchConsumption).toHaveBeenCalledWith(1, expect.any(String), [100, 200]),
    )
  })

  it('shows a success result modal after confirming a roll call, dismissible via 知道了', async () => {
    vi.mocked(courseApi.recordBatchConsumption).mockResolvedValue([
      {
        id: 1,
        studentId: 100,
        courseId: 1,
        courseName: null,
        consumptionDate: '2026-09-30',
        priceSnapshot: 50,
        teacherName: null,
      },
    ])
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '确认消课（2人）' }))

    const dialog = await screen.findByRole('dialog', { name: '消课成功' })
    expect(within(dialog).getByText('已确认消课，新增 1 条记录')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '知道了' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('rejects submitting a roll call for a future date with a warning result modal', async () => {
    setup()
    await screen.findByText('小明')

    fireEvent.change(screen.getByLabelText('消课日期'), { target: { value: '2099-01-01' } })
    fireEvent.click(screen.getByRole('button', { name: '确认消课（2人）' }))

    const dialog = await screen.findByRole('dialog', { name: '消课提醒' })
    expect(within(dialog).getByText('无法对未来日期进行消课处理！')).toBeInTheDocument()
    expect(courseApi.recordBatchConsumption).not.toHaveBeenCalled()
  })

  it('unchecking a student excludes them from the submitted roll call', async () => {
    vi.mocked(courseApi.recordBatchConsumption).mockResolvedValue([])
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('checkbox', { name: /小红/ }))
    expect(screen.getByRole('button', { name: '确认消课（1人）' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '确认消课（1人）' }))

    await waitFor(() =>
      expect(courseApi.recordBatchConsumption).toHaveBeenCalledWith(1, expect.any(String), [100]),
    )
  })

  it('shows a friendly error when the roll call submission fails on an unpriced course', async () => {
    vi.mocked(courseApi.recordBatchConsumption).mockRejectedValue(new ApiError(400, 'unpriced'))
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '确认消课（2人）' }))

    const dialog = await screen.findByRole('dialog', { name: '消课提醒' })
    expect(within(dialog).getByText('该课程尚未配置单价，请联系管理员配置')).toBeInTheDocument()
  })

  it('shows an unpriced-course warning and disables roll call when the course has no price', async () => {
    vi.mocked(courseApi.fetchMyCourses).mockResolvedValue([{ ...COURSES[0], pricePerLesson: null }])
    setup()
    await screen.findByText('小明')

    expect(screen.getByText('该课程尚未配置单价，请联系管理员配置后再消课')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /小明/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /确认消课/ })).toBeDisabled()
  })

  it('opens the parent share-link modal for a student without toggling their attendance checkbox', async () => {
    setup()
    await screen.findByText('小明')

    expect(screen.getByRole('checkbox', { name: /小明/ })).toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: '查看小明的家长专属链接' }))

    expect(await screen.findByRole('dialog', { name: '家长专属链接' })).toBeInTheDocument()
    expect(studentsApi.fetchShareLink).toHaveBeenCalledWith(100)
    expect(screen.getByRole('checkbox', { name: /小明/ })).toBeChecked()
  })

  it('closes the share-link modal via its 完成 button', async () => {
    setup()
    await screen.findByText('小明')

    fireEvent.click(screen.getByRole('button', { name: '查看小明的家长专属链接' }))
    await screen.findByRole('dialog', { name: '家长专属链接' })
    fireEvent.click(screen.getByRole('button', { name: '完成' }))

    expect(screen.queryByRole('dialog', { name: '家长专属链接' })).not.toBeInTheDocument()
  })
})
