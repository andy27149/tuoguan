import { describe, it, expect } from 'vitest'
import { teacherLabel } from './teacherLabel'

describe('teacherLabel', () => {
  it('appends 老师 to a bare name', () => {
    expect(teacherLabel('张伟')).toBe('张伟老师')
  })

  it('does not double-append when the name already ends with 老师', () => {
    expect(teacherLabel('王老师')).toBe('王老师')
  })
})
