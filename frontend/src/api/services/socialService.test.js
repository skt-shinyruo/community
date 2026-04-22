import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { getLikeCounts } from './socialService'

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
})
