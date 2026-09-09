import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import {
  followUser,
  getFollowStatus,
  getFollowStatuses,
  getLikeCounts,
  getLikeStatuses,
  listFollowers,
  listFollowees,
  setLike,
  unfollowUser
} from './socialService'
import { useAuthStore } from '../../stores/auth'

describe('api/services/socialService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
    vi.restoreAllMocks()
  })

  it('write requests should send only canonical social fields', async () => {
    const entityId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onPost('/api/likes').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ entityType: 1, entityId, liked: true })
      return [200, { code: 0, message: '', data: { liked: true, likeCount: 1 }, traceId: 'trace-like' }]
    })
    mock.onPost('/api/follows').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({ entityType: 3, entityId })
      return [200, { code: 0, message: '', data: null, traceId: 'trace-follow' }]
    })

    await setLike({
      entityType: 1,
      entityId,
      liked: true
    })
    await followUser(3, entityId)
  })

  it('follow relation lists use cursor endpoints and normalize their page contract', async () => {
    const userId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const relation = { targetId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb' }
    mock = new MockAdapter(http)
    mock.onGet(`/api/follows/${userId}/followees/page`).reply((config) => {
      expect(config.params).toEqual({ cursor: 'followee-cursor', size: 20, entityType: 3 })
      expect(config.params).not.toHaveProperty('page')
      return [200, {
        code: 0,
        message: '',
        data: { items: [relation], nextCursor: 'next-followee', hasNext: true },
        traceId: 'trace-followees'
      }]
    })
    mock.onGet(`/api/follows/${userId}/followers/page`).reply((config) => {
      expect(config.params).toEqual({ cursor: 'follower-cursor', size: 10, entityType: 3 })
      expect(config.params).not.toHaveProperty('page')
      return [200, {
        code: 0,
        message: '',
        data: { items: [relation], nextCursor: '', hasNext: true },
        traceId: 'trace-followers'
      }]
    })

    await expect(listFollowees(userId, { cursor: 'followee-cursor', size: 20 })).resolves.toEqual({
      data: { items: [relation], nextCursor: 'next-followee', hasNext: true },
      traceId: 'trace-followees'
    })
    await expect(listFollowers(userId, { cursor: 'follower-cursor' })).resolves.toEqual({
      data: { items: [relation], nextCursor: '', hasNext: false },
      traceId: 'trace-followers'
    })
  })

  it('getLikeCounts should preserve UUID entity ids in batch query params', async () => {
    const entityA = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const entityB = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    mock = new MockAdapter(http)
    mock.onGet('/api/likes/counts').reply((config) => {
      expect(config.params).toMatchObject({
        entityType: 2,
        entityIds: `${entityA},${entityB}`
      })
      return [200, {
        code: 0,
        message: '',
        data: {
          [entityA]: 3,
          [entityB]: 7
        },
        traceId: 'trace-like-counts'
      }]
    })

    const resp = await getLikeCounts(2, [entityA, entityB, entityA, null])

    expect(resp.traceId).toBe('trace-like-counts')
    expect(resp.data).toEqual({
      [entityA]: 3,
      [entityB]: 7
    })
  })

  it('getLikeCounts should reject non-object response data instead of treating it as empty', async () => {
    const entityA = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onGet('/api/likes/counts').reply(200, {
      code: 0,
      message: '',
      data: null,
      traceId: 'trace-like-counts'
    })

    await expect(getLikeCounts(2, [entityA])).rejects.toThrow('批量查询点赞数响应非法')
  })

  it('getLikeStatuses should reject non-object response data instead of treating it as empty', async () => {
    const entityA = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onGet('/api/likes/statuses').reply(200, {
      code: 0,
      message: '',
      data: null,
      traceId: 'trace-like-statuses'
    })

    await expect(getLikeStatuses(2, [entityA])).rejects.toThrow('批量查询点赞状态响应非法')
  })

  it('does not let an in-flight batch status response overwrite a completed follow mutation', async () => {
    const entityA = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const entityB = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    mock = new MockAdapter(http)
    let resolveStaleStatuses
    mock.onGet('/api/follows/statuses').replyOnce(() => new Promise((resolve) => {
      resolveStaleStatuses = () => resolve([200, {
        code: 0,
        message: '',
        data: { [entityA]: false, [entityB]: true },
        traceId: 'trace-stale-statuses'
      }])
    }))
    mock.onPost('/api/follows').reply(200, {
      code: 0,
      message: '',
      data: null,
      traceId: 'trace-follow'
    })

    const pending = getFollowStatuses(3, [entityA, entityB])
    while (!resolveStaleStatuses) await Promise.resolve()

    await followUser(3, entityA)
    resolveStaleStatuses()

    await expect(pending).resolves.toEqual({
      data: { [entityA]: true, [entityB]: true },
      traceId: 'trace-stale-statuses'
    })

    const cached = await getFollowStatuses(3, [entityA, entityB])
    expect(cached.data).toEqual({ [entityA]: true, [entityB]: true })
    expect(mock.history.get).toHaveLength(1)
  })

  it('does not let an in-flight single status response overwrite a completed unfollow mutation', async () => {
    const entityId = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
    mock = new MockAdapter(http)
    let resolveStaleStatus
    mock.onGet('/api/follows/status').replyOnce(() => new Promise((resolve) => {
      resolveStaleStatus = () => resolve([200, {
        code: 0,
        message: '',
        data: true,
        traceId: 'trace-stale-status'
      }])
    }))
    mock.onDelete('/api/follows').reply(200, {
      code: 0,
      message: '',
      data: null,
      traceId: 'trace-unfollow'
    })

    const pending = getFollowStatus(3, entityId)
    while (!resolveStaleStatus) await Promise.resolve()

    await unfollowUser(3, entityId)
    resolveStaleStatus()

    await expect(pending).resolves.toEqual({ data: false, traceId: 'trace-stale-status' })

    const cached = await getFollowStatus(3, entityId)
    expect(cached.data).toBe(false)
    expect(mock.history.get).toHaveLength(1)
  })

  it('expires cached follow statuses after the TTL', async () => {
    const entityId = 'dddddddd-dddd-7ddd-8ddd-dddddddddddd'
    const now = vi.spyOn(Date, 'now')
    now.mockReturnValue(1_000_000_000)
    let serverValue = true
    mock = new MockAdapter(http)
    mock.onGet('/api/follows/statuses').reply(() => [200, {
      code: 0,
      message: '',
      data: { [entityId]: serverValue },
      traceId: 'trace-statuses'
    }])

    expect((await getFollowStatuses(3, [entityId])).data[entityId]).toBe(true)
    expect((await getFollowStatuses(3, [entityId])).data[entityId]).toBe(true)
    expect(mock.history.get).toHaveLength(1)

    serverValue = false
    now.mockReturnValue(1_000_000_000 + 5 * 60 * 1000 + 1)

    expect((await getFollowStatuses(3, [entityId])).data[entityId]).toBe(false)
    expect(mock.history.get).toHaveLength(2)
  })

  it('evicts the least recently used follow status beyond the capacity bound', async () => {
    const ids = Array.from(
      { length: 501 },
      (_, i) => `00000000-0000-7000-8000-${String(i).padStart(12, '0')}`
    )
    mock = new MockAdapter(http)
    mock.onGet('/api/follows/statuses').reply((config) => {
      const requested = String(config.params.entityIds).split(',')
      return [200, {
        code: 0,
        message: '',
        data: Object.fromEntries(requested.map((id) => [id, true])),
        traceId: 'trace-statuses'
      }]
    })

    // 批量查询单次上限 200，三次写满 500 项容量
    await getFollowStatuses(3, ids.slice(0, 200))
    await getFollowStatuses(3, ids.slice(200, 400))
    await getFollowStatuses(3, ids.slice(400, 500))
    expect(mock.history.get).toHaveLength(3)

    // 读 ids[1] 使其成为最近使用
    await getFollowStatuses(3, [ids[1]])
    expect(mock.history.get).toHaveLength(3)

    // 写入第 501 项，逐出最久未使用的 ids[0]
    await getFollowStatuses(3, [ids[500]])
    expect(mock.history.get).toHaveLength(4)

    // ids[1] 仍在缓存中；ids[0] 已被逐出需要重新拉取
    await getFollowStatuses(3, [ids[1]])
    expect(mock.history.get).toHaveLength(4)
    await getFollowStatuses(3, [ids[0]])
    expect(mock.history.get).toHaveLength(5)
  })

  it('getFollowStatuses should preserve UUID ids and return a complete status map', async () => {
    const entityA = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
    const entityB = 'dddddddd-dddd-7ddd-8ddd-dddddddddddd'
    mock = new MockAdapter(http)
    mock.onGet('/api/follows/statuses').reply((config) => {
      expect(config.params).toMatchObject({
        entityType: 3,
        entityIds: `${entityA},${entityB}`
      })
      return [200, {
        code: 0,
        message: '',
        data: {
          [entityA]: true,
          [entityB]: false
        },
        traceId: 'trace-follow-statuses'
      }]
    })

    const response = await getFollowStatuses(3, [entityA, entityB, entityA])

    expect(response).toEqual({
      data: {
        [entityA]: true,
        [entityB]: false
      },
      traceId: 'trace-follow-statuses'
    })
    expect(mock.history.get).toHaveLength(1)
  })

  it('does not reuse private follow statuses after the authenticated identity changes', async () => {
    const entityId = 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee'
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-user-a',
      me: { userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa' }
    })
    mock = new MockAdapter(http)
    mock.onGet('/api/follows/statuses').replyOnce(200, {
      code: 0,
      message: '',
      data: { [entityId]: true },
      traceId: 'trace-user-a'
    })

    expect((await getFollowStatuses(3, [entityId])).data[entityId]).toBe(true)

    auth.installSession({
      accessToken: 'token-user-b',
      me: { userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb' }
    })
    mock.onGet('/api/follows/statuses').replyOnce(200, {
      code: 0,
      message: '',
      data: { [entityId]: false },
      traceId: 'trace-user-b'
    })

    expect((await getFollowStatuses(3, [entityId])).data[entityId]).toBe(false)
    expect(mock.history.get).toHaveLength(2)
  })

  it('reissues an in-flight follow-status query when the authenticated identity changes', async () => {
    const entityId = 'ffffffff-ffff-7fff-8fff-ffffffffffff'
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-user-a',
      me: { userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa' }
    })

    let resolveOldIdentity
    mock = new MockAdapter(http)
    mock.onGet('/api/follows/statuses').replyOnce(() => new Promise((resolve) => {
      resolveOldIdentity = () => resolve([200, {
        code: 0,
        message: '',
        data: { [entityId]: true },
        traceId: 'trace-user-a'
      }])
    }))

    const pending = getFollowStatuses(3, [entityId])
    while (!resolveOldIdentity) await Promise.resolve()

    auth.installSession({
      accessToken: 'token-user-b',
      me: { userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb' }
    })
    mock.onGet('/api/follows/statuses').replyOnce(200, {
      code: 0,
      message: '',
      data: { [entityId]: false },
      traceId: 'trace-user-b'
    })
    resolveOldIdentity()

    await expect(pending).resolves.toEqual({
      data: { [entityId]: false },
      traceId: 'trace-user-b'
    })
    expect(mock.history.get).toHaveLength(2)
  })
})
