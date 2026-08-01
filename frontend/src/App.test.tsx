import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import App from './App'
import * as healthApi from './api/health'

describe('App', () => {
  it('shows connected message when backend health check succeeds', async () => {
    vi.spyOn(healthApi, 'fetchHealth').mockResolvedValue('connected')

    render(<App />)

    await waitFor(() => {
      expect(screen.getByTestId('health-status')).toHaveTextContent('后端已连接')
    })
  })

  it('shows disconnected message when backend health check fails', async () => {
    vi.spyOn(healthApi, 'fetchHealth').mockResolvedValue('disconnected')

    render(<App />)

    await waitFor(() => {
      expect(screen.getByTestId('health-status')).toHaveTextContent('后端未连接')
    })
  })
})
