import { render, screen, fireEvent, waitFor, within } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AdminTeachingUnitsModule } from './AdminTeachingUnitsModule'
import * as unitApi from '../api/unit'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'

vi.mock('../api/unit')
vi.mock('../api/admin')

const TEACHERS: adminApi.Teacher[] = [
  { id: 1, phone: '13900000001', name: '王老师', role: 'TEACHER', mustChangePassword: false },
  { id: 2, phone: '13900000002', name: '李老师', role: 'TEACHER', mustChangePassword: false },
]

const CLASS_ROOM: unitApi.TeachingUnit = {
  id: 10,
  name: '一班',
  billingMode: 'MONTHLY',
  teacherId: 1,
  teacherName: '王老师',
  teacherPhone: '13900000001',
  lessonDurationMinutes: null,
  pricePerLesson: null,
  active: true,
}

const COURSE: unitApi.TeachingUnit = {
  id: 20,
  name: '书法课',
  billingMode: 'LESSON_COUNT',
  teacherId: 2,
  teacherName: '李老师',
  teacherPhone: '13900000002',
  lessonDurationMinutes: 60,
  pricePerLesson: 50,
  active: true,
}

function mockUnits(classRooms: unitApi.TeachingUnit[], courses: unitApi.TeachingUnit[]) {
  vi.mocked(unitApi.fetchTeachingUnits).mockImplementation((billingMode) => {
    if (billingMode === 'MONTHLY') return Promise.resolve(classRooms)
    if (billingMode === 'LESSON_COUNT') return Promise.resolve(courses)
    return Promise.resolve([...classRooms, ...courses])
  })
}

describe('AdminTeachingUnitsModule', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(adminApi.fetchTeachers).mockResolvedValue(TEACHERS)
    mockUnits([CLASS_ROOM], [COURSE])
  })

  it('shows both tabs when both features are enabled, defaulting to 托管班', async () => {
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)

    expect(await screen.findByText('一班')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '托管班' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '课外课' })).toBeInTheDocument()
  })

  it('hides the tab switcher and only loads 课外课 when custody is disabled', async () => {
    render(<AdminTeachingUnitsModule custodyEnabled={false} offCampusEnabled />)

    expect(await screen.findByText('书法课')).toBeInTheDocument()
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
    expect(unitApi.fetchTeachingUnits).toHaveBeenCalledWith('LESSON_COUNT')
  })

  it('switches tabs and loads the other billing mode, showing duration/price columns only for 课外课', async () => {
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')
    expect(screen.queryByText('时长')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('tab', { name: '课外课' }))

    expect(await screen.findByText('书法课')).toBeInTheDocument()
    expect(screen.getByText('时长')).toBeInTheDocument()
    expect(screen.getByText('60分钟')).toBeInTheDocument()
    expect(screen.getByText('¥50.00')).toBeInTheDocument()
  })

  it('creates a 托管班 with just a name and teacher', async () => {
    vi.mocked(unitApi.createTeachingUnit).mockResolvedValue(CLASS_ROOM)
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByPlaceholderText('托管班名称'), { target: { value: '二班' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(unitApi.createTeachingUnit).toHaveBeenCalledWith({
        billingMode: 'MONTHLY',
        name: '二班',
        teacherId: 1,
        lessonDurationMinutes: undefined,
        pricePerLesson: undefined,
      }),
    )
  })

  it('shows a duplicate-name message on 409 when creating', async () => {
    vi.mocked(unitApi.createTeachingUnit).mockRejectedValue(new ApiError(409, '冲突'))
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')

    fireEvent.change(screen.getByPlaceholderText('托管班名称'), { target: { value: '一班' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('该教师下已有同名班级')).toBeInTheDocument()
  })

  it('requires a valid duration before creating a 课外课', async () => {
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')
    fireEvent.click(screen.getByRole('tab', { name: '课外课' }))
    await screen.findByText('书法课')

    fireEvent.change(screen.getByPlaceholderText('课外课名称'), { target: { value: '数学课' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    expect(await screen.findByText('请输入有效的时长')).toBeInTheDocument()
    expect(unitApi.createTeachingUnit).not.toHaveBeenCalled()
  })

  it('creates a 课外课 with duration and price', async () => {
    vi.mocked(unitApi.createTeachingUnit).mockResolvedValue(COURSE)
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')
    fireEvent.click(screen.getByRole('tab', { name: '课外课' }))
    await screen.findByText('书法课')

    fireEvent.change(screen.getByPlaceholderText('课外课名称'), { target: { value: '数学课' } })
    fireEvent.change(screen.getByLabelText('负责教师'), { target: { value: '2' } })
    fireEvent.change(screen.getByPlaceholderText('时长（分钟）'), { target: { value: '45' } })
    fireEvent.change(screen.getByPlaceholderText('单价（选填）'), { target: { value: '80' } })
    fireEvent.click(screen.getByRole('button', { name: '创建' }))

    await waitFor(() =>
      expect(unitApi.createTeachingUnit).toHaveBeenCalledWith({
        billingMode: 'LESSON_COUNT',
        name: '数学课',
        teacherId: 2,
        lessonDurationMinutes: 45,
        pricePerLesson: 80,
      }),
    )
  })

  it('edits a unit name and reassigns its teacher', async () => {
    vi.mocked(unitApi.updateTeachingUnit).mockResolvedValue({ ...CLASS_ROOM, name: '一班改', teacherId: 2 })
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')

    fireEvent.click(screen.getByRole('button', { name: '编辑一班' }))
    fireEvent.change(screen.getByLabelText('名称10'), { target: { value: '一班改' } })
    fireEvent.change(screen.getByLabelText('负责教师10'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: '保存' }))

    await waitFor(() =>
      expect(unitApi.updateTeachingUnit).toHaveBeenCalledWith(10, { name: '一班改', teacherId: 2 }),
    )
  })

  it('shows the course price read-only, pointing to the pricing center instead of an inline editor', async () => {
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')
    fireEvent.click(screen.getByRole('tab', { name: '课外课' }))
    await screen.findByText('书法课')

    expect(screen.getByText('¥50.00')).toBeInTheDocument()
    expect(screen.getByText('（在定价中心设置）')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '设置单价' })).not.toBeInTheDocument()
  })

  it('shows a jump link to the pricing center when onOpenPricing is provided', async () => {
    const onOpenPricing = vi.fn()
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled onOpenPricing={onOpenPricing} />)
    await screen.findByText('一班')
    fireEvent.click(screen.getByRole('tab', { name: '课外课' }))
    await screen.findByText('书法课')

    expect(screen.queryByText('（在定价中心设置）')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '前往定价中心设置' }))

    expect(onOpenPricing).toHaveBeenCalled()
  })

  it('toggles active status for a 课外课 row but not for 托管班', async () => {
    vi.mocked(unitApi.updateTeachingUnit).mockResolvedValue({ ...COURSE, active: false })
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')

    const classRow = screen.getAllByRole('row').find((r) => r.textContent?.includes('一班'))
    expect(classRow && within(classRow).queryByRole('button', { name: /停用|启用/ })).toBeFalsy()

    fireEvent.click(screen.getByRole('tab', { name: '课外课' }))
    await screen.findByText('书法课')
    fireEvent.click(screen.getByRole('button', { name: '停用书法课' }))
    expect(unitApi.updateTeachingUnit).not.toHaveBeenCalled()
    fireEvent.click(await screen.findByRole('button', { name: '确认停用' }))

    await waitFor(() => expect(unitApi.updateTeachingUnit).toHaveBeenCalledWith(20, { active: false }))
  })

  it('re-enables a unit immediately without asking for confirmation', async () => {
    mockUnits([CLASS_ROOM], [{ ...COURSE, active: false }])
    vi.mocked(unitApi.updateTeachingUnit).mockResolvedValue({ ...COURSE, active: true })
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')

    fireEvent.click(screen.getByRole('tab', { name: '课外课' }))
    await screen.findByText('书法课')
    fireEvent.click(screen.getByRole('button', { name: '启用书法课' }))

    await waitFor(() => expect(unitApi.updateTeachingUnit).toHaveBeenCalledWith(20, { active: true }))
    expect(screen.queryByRole('button', { name: '确认停用' })).not.toBeInTheDocument()
  })

  it('deletes a unit after confirming the impact dialog', async () => {
    vi.mocked(unitApi.fetchTeachingUnitDeletionImpact).mockResolvedValue({ studentCount: 3 })
    vi.mocked(unitApi.deleteTeachingUnit).mockResolvedValue(undefined)
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)
    await screen.findByText('一班')

    fireEvent.click(screen.getByRole('button', { name: '删除一班' }))
    expect(await screen.findByText(/下有 3 名学生/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '确认删除' }))

    await waitFor(() => expect(unitApi.deleteTeachingUnit).toHaveBeenCalledWith(10))
  })

  it('shows an empty state per tab', async () => {
    mockUnits([], [])
    render(<AdminTeachingUnitsModule custodyEnabled offCampusEnabled />)

    expect(await screen.findByText('暂无托管班')).toBeInTheDocument()
  })
})
