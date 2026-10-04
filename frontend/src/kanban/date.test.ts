import { describe, it, expect } from 'vitest'
import { formatDate, formatDateTime } from './date'

describe('formatDate', () => {
  it('formats an ISO timestamp as YYYY-MM-DD', () => {
    expect(formatDate('2026-01-05T12:00:00Z')).toBe('2026-01-05')
  })
})

describe('formatDateTime', () => {
  it('formats an ISO timestamp as YYYY-MM-DD HH:mm', () => {
    expect(formatDateTime('2026-01-05T08:30:00')).toBe('2026-01-05 08:30')
  })
})
