import { describe, it, expect, afterEach, vi } from 'vitest'

import { createIdempotencyKeyCache } from './idempotencyKeyCache'

describe('api/idempotencyKeyCache', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('should reuse same key within window', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(0))

    let seq = 0
    const cache = createIdempotencyKeyCache({
      windowMs: 10_000,
      maxSize: 100,
      generateKey: () => `k${(seq += 1)}`
    })

    const k1 = cache.getOrReuse('fp:1')
    const k2 = cache.getOrReuse('fp:1')
    expect(k2).toBe(k1)

    vi.advanceTimersByTime(10_001)
    const k3 = cache.getOrReuse('fp:1')
    expect(k3).not.toBe(k1)
  })

  it('should evict expired fingerprints on subsequent access', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(0))

    let seq = 0
    const cache = createIdempotencyKeyCache({
      windowMs: 10_000,
      maxSize: 100,
      generateKey: () => `k${(seq += 1)}`
    })

    cache.getOrReuse('fp:1')
    cache.getOrReuse('fp:2')
    cache.getOrReuse('fp:3')
    expect(cache.size()).toBe(3)

    vi.advanceTimersByTime(20_000)
    cache.getOrReuse('fp:new')
    expect(cache.size()).toBe(1)
  })

  it('should cap cache size and evict oldest entries', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(0))

    let seq = 0
    const cache = createIdempotencyKeyCache({
      windowMs: 60_000,
      maxSize: 3,
      generateKey: () => `k${(seq += 1)}`
    })

    const k1 = cache.getOrReuse('fp:1')
    cache.getOrReuse('fp:2')
    cache.getOrReuse('fp:3')
    cache.getOrReuse('fp:4')
    cache.getOrReuse('fp:5')

    expect(cache.size()).toBe(3)
    expect(cache.getOrReuse('fp:1')).not.toBe(k1)
  })
})

