import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import App from './App'
import * as authApi from './api/auth'
import { setToken } from './api/client'

vi.mock('./api/auth', async () => {
  const actual = await vi.importActual<typeof import('./api/auth')>('./api/auth')
  return { ...actual, fetchMe: vi.fn() }
})

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it('shows the login form when no session token is stored', async () => {
    render(<App />)

    expect(await screen.findByRole('heading', { name: '托管班看板登录' })).toBeInTheDocument()
  })

  it('routes a platform admin straight to the platform management page', async () => {
    setToken('platform-token')
    vi.mocked(authApi.fetchMe).mockResolvedValue({
      id: 1,
      phone: '13700000001',
      institutionId: null,
      role: 'PLATFORM_ADMIN',
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: '平台管理' })).toBeInTheDocument()
  })
})
