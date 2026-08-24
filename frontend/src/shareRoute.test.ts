import { describe, it, expect } from 'vitest'
import { resolveShareToken } from './shareRoute'

describe('resolveShareToken', () => {
  it('extracts the token from a /share/:token path', () => {
    expect(resolveShareToken('/share/abc123')).toBe('abc123')
  })

  it('returns null for the root path', () => {
    expect(resolveShareToken('/')).toBeNull()
  })

  it('returns null for unrelated paths', () => {
    expect(resolveShareToken('/kanban')).toBeNull()
  })

  it('returns null when the token segment is empty', () => {
    expect(resolveShareToken('/share/')).toBeNull()
  })

  it('returns null when there are extra path segments after the token', () => {
    expect(resolveShareToken('/share/abc123/extra')).toBeNull()
  })
})
