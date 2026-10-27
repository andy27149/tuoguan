import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { AnnualPromotionWizard } from './AnnualPromotionWizard'
import * as unitApi from '../api/unit'
import * as courseApi from '../api/course'
import { ApiError } from '../api/client'

vi.mock('../api/unit')
vi.mock('../api/course')

function unit(id: number, name: string): unitApi.TeachingUnit {
  return {
    id,
    name,
    billingMode: 'MONTHLY',
    teacherId: 1,
    teacherName: '王老师',
    teacherPhone: '13900000001',
    lessonDurationMinutes: null,
    pricePerLesson: null,
    active: true,
  }
}

function student(id: number, classRoomId: number | null, enrolled = true): courseApi.AdminStudent {
  return {
    id,
    name: `学生${id}`,
    schoolClassName: null,
    classRoomId,
    classRoomName: null,
    offCampusOnly: false,
    enrolled,
    teacherId: null,
    teacherName: null,
    teacherPhone: null,
    enrolledCourseNames: [],
    enrolledCourseIds: [],
  }
}

describe('AnnualPromotionWizard', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue([])
  })

  it('auto-prefills an ascending chain parsed from unit names, leaving the highest grade outcome unset', async () => {
    const units = [unit(3, '3年级托管班'), unit(1, '1年级托管班'), unit(2, '2年级托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    const sourceSelects = await screen.findAllByLabelText('源班级')
    expect(sourceSelects).toHaveLength(3)
    expect(sourceSelects[0]).toHaveValue('1')
    expect(sourceSelects[1]).toHaveValue('2')
    expect(sourceSelects[2]).toHaveValue('3')

    const outcomeSelects = screen.getAllByLabelText('归宿')
    expect(outcomeSelects[0]).toHaveValue('MOVE_TO_UNIT')
    expect(outcomeSelects[1]).toHaveValue('MOVE_TO_UNIT')
    expect(outcomeSelects[2]).toHaveValue('')

    const targetSelects = screen.getAllByLabelText('目标班级')
    expect(targetSelects).toHaveLength(2)
    expect(targetSelects[0]).toHaveValue('2')
    expect(targetSelects[1]).toHaveValue('3')
  })

  it('does not prefill any step when no unit name contains a number', () => {
    const units = [unit(1, '托管班'), unit(2, '课后班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    expect(screen.queryByLabelText('源班级')).not.toBeInTheDocument()
    expect(screen.getByText('暂无步骤，点击下方按钮添加')).toBeInTheDocument()
  })

  it('adds and removes step rows', () => {
    const units = [unit(1, '托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    expect(screen.queryByLabelText('源班级')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '+ 添加步骤' }))
    expect(screen.getAllByLabelText('源班级')).toHaveLength(1)

    fireEvent.click(screen.getByRole('button', { name: '删除该步骤' }))
    expect(screen.queryByLabelText('源班级')).not.toBeInTheDocument()
  })

  it('shows the enrolled headcount badge scoped to classRoomId and enrolled students only', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue([
      student(1, 10, true),
      student(2, 10, true),
      student(3, 10, false),
      student(4, 20, true),
    ])
    const units = [unit(10, '1年级托管班'), unit(20, '2年级托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    expect(await screen.findByText('2人在读')).toBeInTheDocument()
  })

  it('hides and clears the target unit select when switching outcome away from 转入下一班级', () => {
    const units = [unit(1, '1年级托管班'), unit(2, '2年级托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    expect(screen.getByLabelText('目标班级')).toBeInTheDocument()

    fireEvent.change(screen.getAllByLabelText('归宿')[0], { target: { value: 'DEACTIVATE' } })

    expect(screen.queryByLabelText('目标班级')).not.toBeInTheDocument()
  })

  it('disables moving to the confirm screen until every step has its required fields filled', () => {
    const units = [unit(1, '1年级托管班'), unit(2, '2年级托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    expect(screen.getByRole('button', { name: '下一步' })).toBeDisabled()

    fireEvent.change(screen.getAllByLabelText('归宿')[1], { target: { value: 'DEACTIVATE' } })

    expect(screen.getByRole('button', { name: '下一步' })).not.toBeDisabled()
  })

  it('lists all steps and the total on the confirm screen without calling runAnnualPromotion yet', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue([
      student(1, 1, true),
      student(2, 1, true),
      student(3, 2, true),
    ])
    const units = [unit(1, '1年级托管班'), unit(2, '2年级托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    await screen.findByText('2人在读')
    fireEvent.change(screen.getAllByLabelText('归宿')[1], { target: { value: 'DEACTIVATE' } })
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))

    expect(screen.getByText('「1年级托管班」（2人）→ 「2年级托管班」')).toBeInTheDocument()
    expect(screen.getByText('「2年级托管班」（1人）→ 停用')).toBeInTheDocument()
    expect(screen.getByText('共迁移 3 名学生')).toBeInTheDocument()
    expect(unitApi.runAnnualPromotion).not.toHaveBeenCalled()
  })

  it('calls runAnnualPromotion with the built payload and shows the result modal on success', async () => {
    vi.mocked(courseApi.fetchAdminStudents).mockResolvedValue([student(1, 1, true), student(2, 2, true)])
    vi.mocked(unitApi.runAnnualPromotion).mockResolvedValue({ totalMovedCount: 2, steps: [] })
    const units = [unit(1, '1年级托管班'), unit(2, '2年级托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    fireEvent.change(screen.getAllByLabelText('归宿')[1], { target: { value: 'OFF_CAMPUS' } })
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    fireEvent.click(screen.getByRole('button', { name: '确认执行' }))

    await waitFor(() =>
      expect(unitApi.runAnnualPromotion).toHaveBeenCalledWith([
        { sourceTeachingUnitId: 1, outcome: 'MOVE_TO_UNIT', targetTeachingUnitId: 2 },
        { sourceTeachingUnitId: 2, outcome: 'OFF_CAMPUS', targetTeachingUnitId: null },
      ]),
    )
    expect(await screen.findByRole('dialog', { name: '学年晋级完成' })).toBeInTheDocument()
  })

  it('stays on the confirm screen and shows an error when submission fails, re-enabling the button', async () => {
    vi.mocked(unitApi.runAnnualPromotion).mockRejectedValue(new ApiError(400, '检测到循环的晋级链'))
    const units = [unit(1, '1年级托管班'), unit(2, '2年级托管班')]
    render(<AnnualPromotionWizard monthlyUnits={units} onClose={vi.fn()} />)

    fireEvent.change(screen.getAllByLabelText('归宿')[1], { target: { value: 'DEACTIVATE' } })
    fireEvent.click(screen.getByRole('button', { name: '下一步' }))
    fireEvent.click(screen.getByRole('button', { name: '确认执行' }))

    expect(await screen.findByText('检测到循环的晋级链')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '确认执行' })).not.toBeDisabled()
    expect(screen.queryByRole('dialog', { name: '学年晋级完成' })).not.toBeInTheDocument()
  })
})
