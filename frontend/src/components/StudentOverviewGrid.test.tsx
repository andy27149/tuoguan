import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { StudentOverviewGrid } from './StudentOverviewGrid'
import type { Student } from '../api/students'

function student(id: number, name: string): Student {
  return { id, name, schoolClassName: '5.2', enrolled: true, avatarUrl: null, enrolledCourseNames: [] }
}

describe('StudentOverviewGrid', () => {
  it('renders one chip per student with the first character as a fallback avatar', () => {
    render(
      <StudentOverviewGrid
        students={[student(1, 'AA'), student(2, 'BB')]}
        arrivalByStudent={new Map()}
        mealByStudent={new Set()}
        leaveByStudent={new Map()}
        onSelect={vi.fn()}
      />,
    )

    expect(screen.getByText('AA')).toBeInTheDocument()
    expect(screen.getByText('BB')).toBeInTheDocument()
  })

  it('calls onSelect with the tapped student id', () => {
    const onSelect = vi.fn()
    render(
      <StudentOverviewGrid
        students={[student(1, 'AA')]}
        arrivalByStudent={new Map()}
        mealByStudent={new Set()}
        leaveByStudent={new Map()}
        onSelect={onSelect}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'AA' }))
    expect(onSelect).toHaveBeenCalledWith(1)
  })

  // 到了/请假两个状态互斥（后端打卡到了会联动清掉请假记录，反之亦然），用餐只在
  // 到了之后才可能发生——这里锁定三种状态分别映射到正确的 data-status，以及用餐
  // 角标只在 hasMeal 时出现，避免以后改动不小心让两个互斥状态同时生效。
  it('maps arrival/leave state to data-status and shows the meal badge only when applicable', () => {
    render(
      <StudentOverviewGrid
        students={[student(1, '未到'), student(2, '已到'), student(3, '请假')]}
        arrivalByStudent={new Map([[2, '08:00']])}
        mealByStudent={new Set([2])}
        leaveByStudent={new Map([[3, '家长请假']])}
        onSelect={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '未到' })).toHaveAttribute('data-status', 'default')
    expect(screen.getByRole('button', { name: /已到/ })).toHaveAttribute('data-status', 'arrived')
    expect(screen.getByRole('button', { name: '请假' })).toHaveAttribute('data-status', 'leave')

    // 用餐角标渲染成了一个 emoji span，被当作该按钮 accessible name 的一部分
    // （"已到🍚"），所以用这个更宽松的正则确认角标确实出现在"已到"这一个学生上。
    expect(screen.getByRole('button', { name: /已到/ }).textContent).toContain('🍚')
    expect(screen.getByRole('button', { name: '未到' }).textContent).not.toContain('🍚')
  })
})
