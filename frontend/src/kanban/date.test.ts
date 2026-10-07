import { describe, it, expect } from 'vitest'
import { formatDate, formatDateTime } from './date'

describe('formatDate', () => {
  it('formats a UTC ISO timestamp as YYYY-MM-DD in Beijing time', () => {
    expect(formatDate('2026-01-05T12:00:00Z')).toBe('2026-01-05')
  })

  it('rolls over to the next Beijing day when the UTC time is late enough', () => {
    // UTC 20:00 is Beijing 04:00 the next day (UTC+8)
    expect(formatDate('2026-01-05T20:00:00Z')).toBe('2026-01-06')
  })
})

describe('formatDateTime', () => {
  it('formats a UTC ISO timestamp as YYYY-MM-DD HH:mm in Beijing time, regardless of the host timezone', () => {
    expect(formatDateTime('2026-01-05T08:30:00Z')).toBe('2026-01-05 16:30')
  })
})
