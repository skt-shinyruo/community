import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { listSubscribedCategories } from './subscriptionService'

describe('api/services/subscriptionService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('listSubscribedCategories should read the subscribed categories endpoint', async () => {
    mock = new MockAdapter(http)
    mock.onGet('/api/subscriptions/categories').reply(200, {
      code: 0,
      message: 'OK',
      data: [
        '11111111-1111-7111-8111-111111111111',
        '22222222-2222-7222-8222-222222222222'
      ],
      traceId: 'trace-subscribed-categories'
    })

    const resp = await listSubscribedCategories()

    expect(resp.traceId).toBe('trace-subscribed-categories')
    expect(resp.data).toHaveLength(2)
  })
})
