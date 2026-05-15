import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { getLikeCounts, getLikeStatuses } from './socialService'

describe('api/services/socialService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
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
})
