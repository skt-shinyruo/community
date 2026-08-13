import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { batchUserSummary, clearUserProfileCache, getUserProfile, invalidateUserProfile } from './userService'

describe('api/services/userService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
    clearUserProfileCache()
  })

  afterEach(() => {
    vi.useRealTimers()
    mock?.restore()
    mock = null
  })

  it('expires cached profiles after the cache TTL', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-12T00:00:00Z'))
    const userId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    mock = new MockAdapter(http)
    mock.onGet(`/api/users/${userId}`).replyOnce(200, {
      code: 0, data: { id: userId, username: 'before-expiry' }
    }).onGet(`/api/users/${userId}`).replyOnce(200, {
      code: 0, data: { id: userId, username: 'after-expiry' }
    })

    expect((await getUserProfile(userId)).username).toBe('before-expiry')
    await vi.advanceTimersByTimeAsync(5 * 60 * 1000 + 1)
    expect((await getUserProfile(userId)).username).toBe('after-expiry')
  })

  it('does not let an invalidated in-flight request repopulate the cache', async () => {
    const userId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    let resolveOld
    mock = new MockAdapter(http)
    mock.onGet(`/api/users/${userId}`).replyOnce(() => new Promise((resolve) => {
      resolveOld = resolve
    })).onGet(`/api/users/${userId}`).replyOnce(200, {
      code: 0, data: { id: userId, username: 'fresh' }
    })

    const oldRequest = getUserProfile(userId)
    await Promise.resolve()
    invalidateUserProfile(userId)
    expect((await getUserProfile(userId)).username).toBe('fresh')
    resolveOld([200, { code: 0, data: { id: userId, username: 'stale' } }])
    expect((await oldRequest).username).toBe('stale')
    expect((await getUserProfile(userId)).username).toBe('fresh')
  })

  it('evicts the least recently used profile when the cache reaches its bound', async () => {
    mock = new MockAdapter(http)
    mock.onGet().reply((config) => {
      const id = config.url.split('/').pop()
      return [200, { code: 0, data: { id, username: id } }]
    })
    const ids = Array.from({ length: 101 }, (_, index) =>
      `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`)

    for (const id of ids) await getUserProfile(id)
    await getUserProfile(ids[0])

    expect(mock.history.get).toHaveLength(102)
  })

  it('force refreshes a cached profile', async () => {
    const userId = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
    mock = new MockAdapter(http)
    mock.onGet(`/api/users/${userId}`).replyOnce(200, {
      code: 0, data: { id: userId, username: 'cached' }
    }).onGet(`/api/users/${userId}`).replyOnce(200, {
      code: 0, data: { id: userId, username: 'forced' }
    })

    expect((await getUserProfile(userId)).username).toBe('cached')
    expect((await getUserProfile(userId, { force: true })).username).toBe('forced')
    expect(mock.history.get).toHaveLength(2)
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
    expect(profile.showUserLevel).toBe(false)
    expect(profile.userLevelEnabled).toBe(false)
  })

  it('getUserProfile should show user level only when backend explicitly enables it', async () => {
    const userId = '11111111-1111-7111-8111-111111111112'
    mock = new MockAdapter(http)
    mock.onGet(`/api/users/${userId}`).reply(200, {
      code: 0,
      message: '',
      data: {
        id: userId,
        username: 'level-user',
        userLevel: 2,
        signInDaysInWindow: 12
      },
      traceId: 'trace-user-level'
    })

    const profile = await getUserProfile(userId)

    expect(profile.userLevel).toBe(2)
    expect(profile.signInDaysInWindow).toBe(12)
    expect(profile.userLevelEnabled).toBe(false)
    expect(profile.showUserLevel).toBe(false)
  })

  it('getUserProfile should show complete user level data when explicit flag is enabled', async () => {
    const userId = '11111111-1111-7111-8111-111111111113'
    mock = new MockAdapter(http)
    mock.onGet(`/api/users/${userId}`).reply(200, {
      code: 0,
      message: '',
      data: {
        id: userId,
        username: 'level-user',
        userLevelEnabled: true,
        userLevel: 3,
        signInDaysInWindow: 88
      },
      traceId: 'trace-user-level'
    })

    const profile = await getUserProfile(userId)

    expect(profile.userLevelEnabled).toBe(true)
    expect(profile.showUserLevel).toBe(true)
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
