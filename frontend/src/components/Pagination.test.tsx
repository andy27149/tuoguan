import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('renders the summary text with total items, current page, and total pages', () => {
    render(<Pagination page={2} totalPages={5} totalItems={93} onPageChange={vi.fn()} />)

    expect(screen.getByText('共 93 条 · 第 2 页 / 共 5 页')).toBeInTheDocument()
  })

  it('renders zero items with a valid single-page summary instead of NaN or negative numbers', () => {
    render(<Pagination page={1} totalPages={1} totalItems={0} onPageChange={vi.fn()} />)

    expect(screen.getByText('共 0 条 · 第 1 页 / 共 1 页')).toBeInTheDocument()
  })

  it('disables both buttons when there is only one page', () => {
    render(<Pagination page={1} totalPages={1} totalItems={5} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled()
  })

  it('disables the previous button on the first page but keeps next enabled', () => {
    render(<Pagination page={1} totalPages={3} totalItems={50} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).not.toBeDisabled()
  })

  it('disables the next button on the last page but keeps previous enabled', () => {
    render(<Pagination page={3} totalPages={3} totalItems={50} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '上一页' })).not.toBeDisabled()
  })

  it('enables both buttons on a middle page', () => {
    render(<Pagination page={2} totalPages={3} totalItems={50} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: '上一页' })).not.toBeDisabled()
    expect(screen.getByRole('button', { name: '下一页' })).not.toBeDisabled()
  })

  it('calls onPageChange with the previous page number when clicking 上一页', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={2} totalPages={3} totalItems={50} onPageChange={onPageChange} />)

    fireEvent.click(screen.getByRole('button', { name: '上一页' }))

    expect(onPageChange).toHaveBeenCalledWith(1)
  })

  it('calls onPageChange with the next page number when clicking 下一页', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={2} totalPages={3} totalItems={50} onPageChange={onPageChange} />)

    fireEvent.click(screen.getByRole('button', { name: '下一页' }))

    expect(onPageChange).toHaveBeenCalledWith(3)
  })

  it('does not call onPageChange when clicking a disabled button', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={1} totalPages={1} totalItems={5} onPageChange={onPageChange} />)

    fireEvent.click(screen.getByRole('button', { name: '上一页' }))
    fireEvent.click(screen.getByRole('button', { name: '下一页' }))

    expect(onPageChange).not.toHaveBeenCalled()
  })
})
