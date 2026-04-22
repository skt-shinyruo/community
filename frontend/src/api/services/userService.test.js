import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { batchUserSummary, getUserProfile } from './userService'

describe('api/services/userService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('getUserProfile should request the UUID route without numeric coercion', async () => {
    const userId = '11111111-1111-7111-8111-111111111111'
    mock = new MockAdapter(http)
    mock.onGet().reply((config) => {
      expect(config.url).toBe(`/api/users/${userId}`)
      return [200, {
        code: 0,
        message: '',
        data: {
          id: userId,
          username: 'alice'
        },
        traceId: 'trace-user'
      }]
    })

    const profile = await getUserProfile(userId)

    expect(profile).toMatchObject({
      id: userId,
      username: 'alice',
      _traceId: 'trace-user'
    })
  })

  it('batchUserSummary should preserve UUID ids and dedupe by original string value', async () => {
    const userA = '11111111-1111-7111-8111-111111111111'
    const userB = '22222222-2222-7222-8222-222222222222'
    mock = new MockAdapter(http)
    mock.onPost('/api/users/batch-summary').reply((config) => {
      expect(JSON.parse(config.data)).toEqual({
        userIds: [userA, userB]
      })
      return [200, {
        code: 0,
        message: '',
        data: [
          { id: userA, username: 'alice' },
          { id: userB, username: 'bob' }
        ],
        traceId: 'trace-batch-user'
      }]
    })

    const resp = await batchUserSummary([userA, userB, userA, '', null])

    expect(resp.traceId).toBe('trace-batch-user')
    expect(resp.data).toEqual([
      { id: userA, username: 'alice' },
      { id: userB, username: 'bob' }
    ])
  })
})
