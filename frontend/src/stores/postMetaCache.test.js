import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const { batchUserSummary, getLikeCounts, getLikeStatuses } = vi.hoisted(() => ({
  batchUserSummary: vi.fn(),
  getLikeCounts: vi.fn(),
  getLikeStatuses: vi.fn()
}))

vi.mock('../api/services/userService', () => ({
  batchUserSummary
}))

vi.mock('../api/services/socialService', () => ({
  getLikeCounts,
  getLikeStatuses
}))

import { usePostMetaCacheStore } from './postMetaCache'

describe('stores/postMetaCache', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    batchUserSummary.mockReset()
    getLikeCounts.mockReset()
    getLikeStatuses.mockReset()
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

  it('should cache user summaries by UUID string ids', async () => {
    const userId = '11111111-1111-7111-8111-111111111111'
    const summary = { id: userId, username: 'alice' }
    batchUserSummary.mockResolvedValue({
      data: [summary],
      traceId: 'trace-user-summary'
    })

    const store = usePostMetaCacheStore()
    const users = await store.ensureUserSummaries([userId, userId, null])

    expect(batchUserSummary).toHaveBeenCalledWith([userId])
    expect(users).toEqual({
      [userId]: summary
    })
    expect(store.getUser(userId)).toEqual(summary)
  })

  it('should reject like count responses that omit requested entity ids', async () => {
    const entityId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    getLikeCounts.mockResolvedValue({
      data: {},
      traceId: 'trace-like-counts'
    })

    const store = usePostMetaCacheStore()

    await expect(store.ensureLikeCounts(1, [entityId])).rejects.toThrow(`点赞数缺少实体 ${entityId}`)
  })

  it('should reject like status responses that omit requested entity ids', async () => {
    const entityId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    getLikeStatuses.mockResolvedValue({
      data: {},
      traceId: 'trace-like-statuses'
    })

    const store = usePostMetaCacheStore()

    await expect(store.ensureLikeStatuses(1, [entityId])).rejects.toThrow(`点赞状态缺少实体 ${entityId}`)
  })
})
