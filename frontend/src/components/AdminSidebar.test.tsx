import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { AdminSidebar, isModuleHidden } from './AdminSidebar'

describe('isModuleHidden', () => {
  it('never hides modules without a hiddenWhen rule', () => {
    expect(isModuleHidden('teachers', false, false)).toBe(false)
    expect(isModuleHidden('students', false, false)).toBe(false)
    expect(isModuleHidden('pricing', false, false)).toBe(false)
    expect(isModuleHidden('settings', false, false)).toBe(false)
  })

  it('hides custody-gated modules when custody is disabled', () => {
    expect(isModuleHidden('classes', false, true)).toBe(true)
    expect(isModuleHidden('taskStats', false, true)).toBe(true)
    expect(isModuleHidden('billing', false, true)).toBe(true)
  })

  it('shows custody-gated modules when custody is enabled', () => {
    expect(isModuleHidden('classes', true, true)).toBe(false)
    expect(isModuleHidden('taskStats', true, true)).toBe(false)
    expect(isModuleHidden('billing', true, true)).toBe(false)
  })

  it('hides the off-campus module when off-campus is disabled', () => {
    expect(isModuleHidden('courses', true, false)).toBe(true)
    expect(isModuleHidden('courses', true, true)).toBe(false)
  })
})

describe('AdminSidebar', () => {
  it('renders every nav item grouped under its section label when both features are enabled', () => {
    render(<AdminSidebar active="teachers" onSelect={vi.fn()} custodyEnabled offCampusEnabled />)

    expect(screen.getByText('基础设置')).toBeInTheDocument()
    expect(screen.getByText('结构信息')).toBeInTheDocument()
    expect(screen.getByText('运营产出')).toBeInTheDocument()
    expect(screen.getByText('财务')).toBeInTheDocument()

    for (const label of ['基础配置', '教师列表', '托管班级', '课程管理', '学生总览', '任务完成情况', '定价中心', '账单管理']) {
      expect(screen.getByRole('button', { name: label })).toBeInTheDocument()
    }
  })

  it('hides custody-only items and their group when custody is disabled, keeping always-on items', () => {
    render(<AdminSidebar active="teachers" onSelect={vi.fn()} custodyEnabled={false} offCampusEnabled />)

    expect(screen.queryByRole('button', { name: '托管班级' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '账单管理' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '任务完成情况' })).not.toBeInTheDocument()
    expect(screen.queryByText('运营产出')).not.toBeInTheDocument()

    expect(screen.getByRole('button', { name: '课程管理' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '定价中心' })).toBeInTheDocument()
    expect(screen.getByText('财务')).toBeInTheDocument()
  })

  it('hides the off-campus item when off-campus is disabled, keeping custody items', () => {
    render(<AdminSidebar active="teachers" onSelect={vi.fn()} custodyEnabled offCampusEnabled={false} />)

    expect(screen.queryByRole('button', { name: '课程管理' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '托管班级' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '账单管理' })).toBeInTheDocument()
  })

  it('marks the active item with aria-current', () => {
    render(<AdminSidebar active="pricing" onSelect={vi.fn()} custodyEnabled offCampusEnabled />)

    expect(screen.getByRole('button', { name: '定价中心' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: '教师列表' })).not.toHaveAttribute('aria-current')
  })
})
