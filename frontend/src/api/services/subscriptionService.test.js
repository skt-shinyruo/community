import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { subscribeCategory, unsubscribeCategory } from './subscriptionService'

describe('api/services/subscriptionService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('subscribeCategory and unsubscribeCategory should preserve UUID category ids in the route path', async () => {
    const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onPut(`/api/categories/${categoryId}/subscribe`).reply(200, {
      code: 0,
      message: '',
      data: null,
      traceId: 'trace-subscribe-category'
    })
    mock.onDelete(`/api/categories/${categoryId}/subscribe`).reply(200, {
      code: 0,
      message: '',
      data: null,
      traceId: 'trace-unsubscribe-category'
    })

    const subscribed = await subscribeCategory(categoryId)
    const unsubscribed = await unsubscribeCategory(categoryId)

    expect(subscribed.traceId).toBe('trace-subscribe-category')
    expect(unsubscribed.traceId).toBe('trace-unsubscribe-category')
  })
})
