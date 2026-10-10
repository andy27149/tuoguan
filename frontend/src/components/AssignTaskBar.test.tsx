import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { AssignTaskBar } from './AssignTaskBar'
import type { SchoolClassGroup } from '../kanban/schoolClass'

const TEMPLATES = [
  { id: 1, subject: '数学', name: '口算练习' },
  { id: 2, subject: '语文', name: '阅读打卡' },
]

const ONE_GROUP: SchoolClassGroup[] = [
  { schoolClassName: '一年级1班', students: [{ id: 1 } as never, { id: 2 } as never] },
]

const TWO_GROUPS: SchoolClassGroup[] = [
  { schoolClassName: '一年级1班', students: [{ id: 1 } as never] },
  { schoolClassName: '一年级2班', students: [{ id: 2 } as never, { id: 3 } as never] },
]

const UNASSIGNED_GROUP: SchoolClassGroup[] = [
  { schoolClassName: null, students: [{ id: 1 } as never] },
]

describe('AssignTaskBar', () => {
  it('shows a plain hint (no tabs) when there is only one school class', () => {
    render(
      <AssignTaskBar
        studentsBySchoolClass={ONE_GROUP}
        templates={TEMPLATES}
        onAssign={vi.fn()}
        onAssignAll={vi.fn()}
      />,
    )

    expect(screen.getByText('分配对象：一年级1班（2人）')).toBeInTheDocument()
    expect(screen.queryByRole('tab')).not.toBeInTheDocument()
  })

  it('renders an "all students" tab plus one tab per school class, defaulting to "all students"', () => {
    render(
      <AssignTaskBar
        studentsBySchoolClass={TWO_GROUPS}
        templates={TEMPLATES}
        onAssign={vi.fn()}
        onAssignAll={vi.fn()}
      />,
    )

    const tabs = screen.getAllByRole('tab')
    expect(tabs.map((t) => t.textContent)).toEqual(['全部学生（3人）', '一年级1班（1人）', '一年级2班（2人）'])
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: '批量分配给全部学生（0）' })).toBeInTheDocument()
  })

  it('switches the assign target on click', () => {
    render(
      <AssignTaskBar
        studentsBySchoolClass={TWO_GROUPS}
        templates={TEMPLATES}
        onAssign={vi.fn()}
        onAssignAll={vi.fn()}
      />,
    )

    const tabs = screen.getAllByRole('tab')
    fireEvent.click(tabs[2])

    expect(tabs[2]).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: '批量分配给一年级2班（0）' })).toBeInTheDocument()
  })

  it('clears selected templates when switching target', () => {
    render(
      <AssignTaskBar
        studentsBySchoolClass={TWO_GROUPS}
        templates={TEMPLATES}
        onAssign={vi.fn()}
        onAssignAll={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByText('[数学] 口算练习'))
    expect(screen.getByRole('button', { name: '批量分配给全部学生（1）' })).toBeInTheDocument()

    fireEvent.click(screen.getAllByRole('tab')[1])

    expect(screen.getByRole('button', { name: '批量分配给一年级1班（0）' })).toBeInTheDocument()
  })

  it('shows an empty-library message when there are no templates', () => {
    render(
      <AssignTaskBar studentsBySchoolClass={ONE_GROUP} templates={[]} onAssign={vi.fn()} onAssignAll={vi.fn()} />,
    )

    expect(screen.getByText('任务库为空，请先在任务库中添加模板')).toBeInTheDocument()
  })

  it('calls onAssign with the target school class and selected template ids, then clears selection', async () => {
    const onAssign = vi.fn().mockResolvedValue(undefined)
    render(
      <AssignTaskBar
        studentsBySchoolClass={ONE_GROUP}
        templates={TEMPLATES}
        onAssign={onAssign}
        onAssignAll={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByText('[数学] 口算练习'))
    fireEvent.click(screen.getByText('[语文] 阅读打卡'))
    fireEvent.click(screen.getByRole('button', { name: '批量分配给一年级1班（2）' }))

    await waitFor(() => expect(onAssign).toHaveBeenCalledWith('一年级1班', [1, 2]))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '批量分配给一年级1班（0）' })).toBeInTheDocument(),
    )
  })

  it('calls onAssignAll with the selected template ids when the "all students" tab is active', async () => {
    const onAssignAll = vi.fn().mockResolvedValue(undefined)
    render(
      <AssignTaskBar
        studentsBySchoolClass={TWO_GROUPS}
        templates={TEMPLATES}
        onAssign={vi.fn()}
        onAssignAll={onAssignAll}
      />,
    )

    fireEvent.click(screen.getByText('[数学] 口算练习'))
    fireEvent.click(screen.getByRole('button', { name: '批量分配给全部学生（1）' }))

    await waitFor(() => expect(onAssignAll).toHaveBeenCalledWith([1]))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '批量分配给全部学生（0）' })).toBeInTheDocument(),
    )
  })

  it('disables the submit button until a template is selected', () => {
    render(
      <AssignTaskBar
        studentsBySchoolClass={ONE_GROUP}
        templates={TEMPLATES}
        onAssign={vi.fn()}
        onAssignAll={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '批量分配给一年级1班（0）' })).toBeDisabled()

    fireEvent.click(screen.getByText('[数学] 口算练习'))

    expect(screen.getByRole('button', { name: '批量分配给一年级1班（1）' })).not.toBeDisabled()
  })

  it('still works when the only group has no school class name (null)', async () => {
    const onAssign = vi.fn().mockResolvedValue(undefined)
    render(
      <AssignTaskBar
        studentsBySchoolClass={UNASSIGNED_GROUP}
        templates={TEMPLATES}
        onAssign={onAssign}
        onAssignAll={vi.fn()}
      />,
    )

    expect(screen.getByText('分配对象：未分班（1人）')).toBeInTheDocument()

    fireEvent.click(screen.getByText('[数学] 口算练习'))
    const button = screen.getByRole('button', { name: '批量分配给未分班（1）' })
    expect(button).not.toBeDisabled()

    fireEvent.click(button)

    await waitFor(() => expect(onAssign).toHaveBeenCalledWith(null, [1]))
  })
})
