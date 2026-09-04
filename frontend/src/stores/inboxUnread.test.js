import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { topicSummary, getImUnreadSummary } = vi.hoisted(() => ({
  topicSummary: vi.fn(),
  getImUnreadSummary: vi.fn()
}))

vi.mock('../api/services/noticeService', () => ({ topicSummary }))
vi.mock('../api/services/imCoreChatService', () => ({ getImUnreadSummary }))

import { useAuthStore } from './auth'
import { formatUnreadCount, useInboxUnreadStore } from './inboxUnread'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function login(auth, { token = 'token-a', userId = 'user-a' } = {}) {
  auth.installSession({ accessToken: token, me: { userId } })
}

describe('inboxUnread store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    topicSummary.mockResolvedValue({ data: [] })
    getImUnreadSummary.mockResolvedValue({ rooms: [], conversations: [] })
  })

  it('formats unread counts for compact badges', () => {
    expect(formatUnreadCount(0)).toBe('')
    expect(formatUnreadCount(-3)).toBe('')
    expect(formatUnreadCount('abc')).toBe('')
    expect(formatUnreadCount(5)).toBe('5')
    expect(formatUnreadCount(99)).toBe('99')
    expect(formatUnreadCount(120)).toBe('99+')
  })

  it('stays empty and skips requests while anonymous', async () => {
    const store = useInboxUnreadStore()
    await store.refresh()

    expect(store.noticeUnread).toBe(0)
    expect(store.messageUnread).toBe(0)
    expect(topicSummary).not.toHaveBeenCalled()
    expect(getImUnreadSummary).not.toHaveBeenCalled()
  })

  it('aggregates notice and private-message unread counts after login', async () => {
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth)
    topicSummary.mockResolvedValueOnce({
      data: [
        { topic: 'comment', unreadCount: 2 },
        { topic: 'like', unreadCount: 1 },
        { topic: 'follow', unreadCount: 0 }
      ]
    })
    getImUnreadSummary.mockResolvedValueOnce({
      rooms: [{ roomId: 'r1', unreadCount: 4 }],
      conversations: [
        { conversationId: 'c1', unreadCount: 3 },
        { conversationId: 'c2', unreadCount: 0 }
      ]
    })

    await store.refresh()

    expect(store.noticeUnread).toBe(3)
    // 群聊未读不计入私信角标。
    expect(store.messageUnread).toBe(3)
    expect(topicSummary).toHaveBeenCalledWith({ silent: true })
  })

  it('keeps the last known count of a channel when its refresh fails silently', async () => {
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth)
    topicSummary.mockResolvedValueOnce({ data: [{ topic: 'comment', unreadCount: 2 }] })
    await store.refresh()
    expect(store.noticeUnread).toBe(2)

    topicSummary.mockRejectedValueOnce(new Error('network down'))
    getImUnreadSummary.mockResolvedValueOnce({ rooms: [], conversations: [{ conversationId: 'c1', unreadCount: 5 }] })

    await store.refresh()

    expect(store.noticeUnread).toBe(2)
    expect(store.messageUnread).toBe(5)
  })

  it('resets counts on logout and discards in-flight results', async () => {
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth)
    const pendingNotices = deferred()
    topicSummary.mockReturnValueOnce(pendingNotices.promise)
    const inflight = store.refresh()

    auth.clear()
    store.reset()
    pendingNotices.resolve({ data: [{ topic: 'like', unreadCount: 9 }] })
    await inflight

    expect(store.noticeUnread).toBe(0)
    expect(store.messageUnread).toBe(0)
  })

  it('does not let a previous account refresh replace the current account counts', async () => {
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth, { token: 'token-a', userId: 'user-a' })
    const staleNotices = deferred()
    topicSummary.mockReturnValueOnce(staleNotices.promise)
    const staleRefresh = store.refresh()

    login(auth, { token: 'token-b', userId: 'user-b' })
    topicSummary.mockResolvedValueOnce({ data: [{ topic: 'comment', unreadCount: 1 }] })
    getImUnreadSummary.mockResolvedValueOnce({ rooms: [], conversations: [{ conversationId: 'c9', unreadCount: 2 }] })
    await store.refresh()

    staleNotices.resolve({ data: [{ topic: 'like', unreadCount: 8 }] })
    await staleRefresh

    expect(store.noticeUnread).toBe(1)
    expect(store.messageUnread).toBe(2)
  })
})
