import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { StarRating } from './StarRating'

describe('StarRating', () => {
  it('renders five buttons, marking stars up to the value as filled', () => {
    render(<StarRating value={3} onChange={vi.fn()} />)

    const stars = [1, 2, 3, 4, 5].map((v) => screen.getByRole('button', { name: `评${v}星` }))
    expect(stars[0]).toHaveClass('is-filled')
    expect(stars[2]).toHaveClass('is-filled')
    expect(stars[3]).not.toHaveClass('is-filled')
  })

  it('calls onChange with the clicked value', () => {
    const onChange = vi.fn()
    render(<StarRating value={0} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: '评4星' }))

    expect(onChange).toHaveBeenCalledWith(4)
  })

  it('toggles off when clicking the current rating again', () => {
    const onChange = vi.fn()
    render(<StarRating value={3} onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: '评3星' }))

    expect(onChange).toHaveBeenCalledWith(0)
  })

  it('renders read-only without interactive buttons', () => {
    render(<StarRating value={2} readOnly />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(document.querySelectorAll('.star-rating__star.is-filled')).toHaveLength(2)
  })
})
