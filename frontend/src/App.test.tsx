import { render, screen } from '@testing-library/react'
import { describe, it, expect, beforeEach } from 'vitest'
import App from './App'

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('shows the login form when no session token is stored', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: '托管班看板登录' })).toBeInTheDocument()
  })
})
