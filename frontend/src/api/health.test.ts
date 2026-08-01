import { describe, it, expect, vi, afterEach } from 'vitest'
import { fetchHealth } from './health'

describe('fetchHealth', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns connected when backend responds with status ok', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ status: 'ok' }),
      }),
    )

    const result = await fetchHealth()

    expect(result).toBe('connected')
  })

  it('returns disconnected when the request throws', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('network error')))

    const result = await fetchHealth()

    expect(result).toBe('disconnected')
  })

  it('returns disconnected when response is not ok', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({}),
      }),
    )

    const result = await fetchHealth()

    expect(result).toBe('disconnected')
  })
})
