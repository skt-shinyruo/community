import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { usePostMetaCacheStore } from './postMetaCache'

describe('stores/postMetaCache', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should keep likeCount and expire within short TTL', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(0))

    const store = usePostMetaCacheStore()
    store.setLikeCount(1, 100, 5)

    expect(store.getLikeCount(1, 100)).toBe(5)

    vi.advanceTimersByTime(29_000)
    expect(store.getLikeCount(1, 100)).toBe(5)

    vi.advanceTimersByTime(2_000)
    expect(store.getLikeCount(1, 100)).toBeNull()
  })

  it('should keep likeStatus and clear on auth change', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(0))

    const store = usePostMetaCacheStore()
    store.setLikeStatus(1, 200, true)

    expect(store.getLikeStatus(1, 200)).toBe(true)

    store.clearLikeStatuses()
    expect(store.getLikeStatus(1, 200)).toBeNull()
  })
})

