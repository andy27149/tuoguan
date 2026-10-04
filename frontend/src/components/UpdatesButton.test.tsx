import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import { UpdatesButton } from './UpdatesButton'
import { PRODUCT_UPDATES } from '../updates'

describe('UpdatesButton', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('shows an unseen-updates dot when nothing has been marked as seen yet', () => {
    const { container } = render(<UpdatesButton />)

    expect(container.querySelector('.updates-btn__dot')).toBeInTheDocument()
  })

  it('opens the updates list and clears the dot when clicked', () => {
    const { container } = render(<UpdatesButton />)

    fireEvent.click(screen.getByRole('button', { name: '最近更新' }))

    expect(screen.getByText('最近更新')).toBeInTheDocument()
    expect(screen.getByText(PRODUCT_UPDATES[0].items[0])).toBeInTheDocument()
    expect(container.querySelector('.updates-btn__dot')).not.toBeInTheDocument()
  })

  it('does not show the dot again after reopening once marked as seen', () => {
    window.localStorage.setItem('lastSeenUpdateId', PRODUCT_UPDATES[0].id)
    const { container } = render(<UpdatesButton />)

    expect(container.querySelector('.updates-btn__dot')).not.toBeInTheDocument()
  })
})
