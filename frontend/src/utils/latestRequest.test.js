import { describe, expect, it } from 'vitest'

import { createLatestRequestTracker } from './latestRequest'

describe('latestRequest', () => {
  it('invalidates older tokens when a newer request starts', () => {
    const tracker = createLatestRequestTracker()

    const first = tracker.begin()
    const second = tracker.begin()

    expect(tracker.isCurrent(first)).toBe(false)
    expect(tracker.isCurrent(second)).toBe(true)
  })

  it('can explicitly invalidate the current token set', () => {
    const tracker = createLatestRequestTracker()
    const token = tracker.begin()

    tracker.invalidate()

    expect(tracker.isCurrent(token)).toBe(false)
  })

  it('can bind request handles to the captured route or session scope', () => {
    let scope = 'profile-a:viewer-a'
    const tracker = createLatestRequestTracker({ getScope: () => scope })
    const first = tracker.begin()

    expect(tracker.isCurrent(first)).toBe(true)
    scope = 'profile-b:viewer-a'
    expect(tracker.isCurrent(first)).toBe(false)

    const second = tracker.begin()
    expect(tracker.isCurrent(second)).toBe(true)
    expect(tracker.isCurrent(first)).toBe(false)
  })
})
