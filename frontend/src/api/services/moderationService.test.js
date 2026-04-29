import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import * as moderationService from './moderationService'

describe('api/services/moderationService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('listReports should keep reporterId as an opaque UUID query parameter', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/moderation/reports').reply((config) => {
      expect(config.params).toMatchObject({
        reporterId: '11111111-1111-7111-8111-111111111111',
        page: 0,
        size: 20
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: [],
        traceId: 'trace-reports',
        timestamp: 1774060182920
      }]
    })

    const resp = await moderationService.listReports({
      reporterId: '11111111-1111-7111-8111-111111111111'
    })

    expect(resp.traceId).toBe('trace-reports')
  })

  it('takeAction should post reportId as an opaque UUID string', async () => {
    mock = new MockAdapter(http)
    mock.onPost('/api/moderation/actions').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        reportId: '22222222-2222-7222-8222-222222222222',
        action: 'hide',
        reason: 'spam'
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: '33333333-3333-7333-8333-333333333333',
        traceId: 'trace-action',
        timestamp: 1774060182920
      }]
    })

    const resp = await moderationService.takeAction({
      reportId: '22222222-2222-7222-8222-222222222222',
      action: 'hide',
      reason: 'spam'
    })

    expect(resp.traceId).toBe('trace-action')
  })

  it('listActions should keep actorId as an opaque UUID query parameter', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/moderation/actions').reply((config) => {
      expect(config.params).toMatchObject({
        actorId: '33333333-3333-7333-8333-333333333333',
        page: 0,
        size: 20
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: [],
        traceId: 'trace-actions',
        timestamp: 1774060182920
      }]
    })

    const resp = await moderationService.listActions({
      actorId: '33333333-3333-7333-8333-333333333333'
    })

    expect(resp.traceId).toBe('trace-actions')
  })
})
