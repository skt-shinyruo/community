import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listBlockedUsers, listSubscribedCategories } = vi.hoisted(() => ({
  listBlockedUsers: vi.fn(),
  listSubscribedCategories: vi.fn()
}))

vi.mock('../api/services/blockService', () => ({ listBlockedUsers }))
vi.mock('../api/services/subscriptionService', () => ({ listSubscribedCategories }))

import { useAuthStore } from './auth'
import { useSocialPrefsStore } from './socialPrefs'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('socialPrefs identity scope', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('does not let a previous account blocked-list response replace the current account cache', async () => {
    const auth = useAuthStore()
    const prefs = useSocialPrefsStore()
    const oldRequest = deferred()
    listBlockedUsers
      .mockReturnValueOnce(oldRequest.promise)
      .mockResolvedValueOnce({ data: ['blocked-by-b'] })

    auth.installSession({ accessToken: 'token-a', me: { userId: 'user-a' } })
    const staleLoad = prefs.ensureBlocked()

    auth.installSession({ accessToken: 'token-b', me: { userId: 'user-b' } })
    await prefs.ensureBlocked()
    oldRequest.resolve({ data: ['blocked-by-a'] })
    await staleLoad

    expect(prefs.blockedUserIds).toEqual(['blocked-by-b'])
    expect(listBlockedUsers).toHaveBeenCalledTimes(2)
  })

  it('does not let a previous account subscription response replace the current account cache', async () => {
    const auth = useAuthStore()
    const prefs = useSocialPrefsStore()
    const oldRequest = deferred()
    listSubscribedCategories
      .mockReturnValueOnce(oldRequest.promise)
      .mockResolvedValueOnce({ data: ['category-b'] })

    auth.installSession({ accessToken: 'token-a', me: { userId: 'user-a' } })
    const staleLoad = prefs.ensureSubscribedCategories()

    auth.installSession({ accessToken: 'token-b', me: { userId: 'user-b' } })
    await prefs.ensureSubscribedCategories()
    oldRequest.resolve({ data: ['category-a'] })
    await staleLoad

    expect(prefs.subscribedCategoryIds).toEqual(['category-b'])
    expect(listSubscribedCategories).toHaveBeenCalledTimes(2)
  })
})
