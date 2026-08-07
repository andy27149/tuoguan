import { describe, it, expect } from 'vitest'
import { computeCardStatus } from './cardStatus'

describe('computeCardStatus', () => {
  it('returns default when no tasks are assigned, regardless of dismissal', () => {
    expect(computeCardStatus([], false)).toBe('default')
    expect(computeCardStatus([], true)).toBe('default')
  })

  it('returns default when tasks are in progress and class has not been dismissed', () => {
    expect(computeCardStatus([{ completed: true }, { completed: false }], false)).toBe('default')
  })

  it('returns dismissedIncomplete when dismissed with at least one incomplete task', () => {
    expect(computeCardStatus([{ completed: true }, { completed: false }], true)).toBe('dismissedIncomplete')
  })

  it('returns done when all tasks are completed, taking priority over dismissal', () => {
    expect(computeCardStatus([{ completed: true }, { completed: true }], true)).toBe('done')
    expect(computeCardStatus([{ completed: true }], false)).toBe('done')
  })
})
