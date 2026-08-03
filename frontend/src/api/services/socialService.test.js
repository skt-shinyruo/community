import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { followUser, getFollowStatuses, getLikeCounts, getLikeStatuses, setLike } from './socialService'
import { useAuthStore } from '../../stores/auth'

describe('api/services/socialService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
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
