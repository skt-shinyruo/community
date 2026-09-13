import { createPinia, setActivePinia } from 'pinia'
import { flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { topicSummary, getImUnreadSummary } = vi.hoisted(() => ({
  topicSummary: vi.fn(),
  getImUnreadSummary: vi.fn()
}))

vi.mock('../api/services/noticeService', () => ({ topicSummary }))
vi.mock('../api/services/imCoreChatService', () => ({ getImUnreadSummary }))

import { useAuthStore } from './auth'
import {
  formatUnreadCount,
  INBOX_UNREAD_REFRESH_DEBOUNCE_MS,
  shouldRefreshUnreadForPrivateMessage,
  useInboxUnreadStore
} from './inboxUnread'

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

  afterEach(() => {
    vi.useRealTimers()
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

  it('coalesces a burst of scheduled refreshes into a single trailing refresh', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth)
    topicSummary.mockResolvedValue({ data: [{ topic: 'comment', unreadCount: 2 }] })
    getImUnreadSummary.mockResolvedValue({ rooms: [], conversations: [{ conversationId: 'c1', unreadCount: 5 }] })

    store.scheduleRefresh()
    store.scheduleRefresh()
    store.scheduleRefresh()
    await vi.advanceTimersByTimeAsync(INBOX_UNREAD_REFRESH_DEBOUNCE_MS - 1)

    expect(topicSummary).not.toHaveBeenCalled()
    expect(getImUnreadSummary).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()

    expect(topicSummary).toHaveBeenCalledTimes(1)
    expect(getImUnreadSummary).toHaveBeenCalledTimes(1)
    expect(store.noticeUnread).toBe(2)
    expect(store.messageUnread).toBe(5)
  })

  it('cancels a scheduled refresh when the badge state resets', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth)

    store.scheduleRefresh()
    store.reset()
    await vi.advanceTimersByTimeAsync(INBOX_UNREAD_REFRESH_DEBOUNCE_MS + 100)

    expect(topicSummary).not.toHaveBeenCalled()
    expect(getImUnreadSummary).not.toHaveBeenCalled()
    expect(store.noticeUnread).toBe(0)
    expect(store.messageUnread).toBe(0)
  })

  it('only schedules badge refreshes for incoming peer messages, not own echoes', () => {
    const meId = '11111111-1111-7111-8111-111111111111'
    const peerId = '22222222-2222-7222-8222-222222222222'

    expect(shouldRefreshUnreadForPrivateMessage({ fromUserId: peerId, toUserId: meId }, meId)).toBe(true)
    // 自己消息的服务端回声（多端同步）不改变我的未读计数。
    expect(shouldRefreshUnreadForPrivateMessage({ fromUserId: meId, toUserId: peerId }, meId)).toBe(false)
    // 身份未解析时不臆断发送者归属，保留刷新。
    expect(shouldRefreshUnreadForPrivateMessage({ fromUserId: peerId }, '')).toBe(true)
    expect(shouldRefreshUnreadForPrivateMessage(null, meId)).toBe(true)
  })

  it('shares one in-flight refresh round when login triggers fire at once', async () => {
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth)
    topicSummary.mockResolvedValueOnce({ data: [{ topic: 'comment', unreadCount: 2 }] })
    getImUnreadSummary.mockResolvedValueOnce({ rooms: [], conversations: [{ conversationId: 'c1', unreadCount: 5 }] })

    // 身份 watcher、窗口聚焦与落地页首载在同一瞬间同时触发：只发起一轮请求。
    await Promise.all([store.refresh(), store.refresh()])

    expect(topicSummary).toHaveBeenCalledTimes(1)
    expect(getImUnreadSummary).toHaveBeenCalledTimes(1)
    expect(store.noticeUnread).toBe(2)
    expect(store.messageUnread).toBe(5)

    // 在途刷新完成后，新的触发（如已读操作）正常再刷一轮。
    await store.refresh()
    expect(topicSummary).toHaveBeenCalledTimes(2)
  })

  it('does not share in-flight refreshes across identity scopes', async () => {
    const auth = useAuthStore()
    const store = useInboxUnreadStore()
    login(auth, { token: 'token-a', userId: 'user-a' })
    const staleNotices = deferred()
    topicSummary.mockReturnValueOnce(staleNotices.promise)
    const staleRefresh = store.refresh()

    // 换账号后触发的是新身份 scope 的刷新，不复用旧账号的在途请求。
    login(auth, { token: 'token-b', userId: 'user-b' })
    topicSummary.mockResolvedValueOnce({ data: [{ topic: 'comment', unreadCount: 1 }] })
    getImUnreadSummary.mockResolvedValueOnce({ rooms: [], conversations: [{ conversationId: 'c9', unreadCount: 2 }] })
    await store.refresh()

    staleNotices.resolve({ data: [{ topic: 'like', unreadCount: 8 }] })
    await staleRefresh

    expect(topicSummary).toHaveBeenCalledTimes(2)
    expect(store.noticeUnread).toBe(1)
    expect(store.messageUnread).toBe(2)
  })
})
