// @vitest-environment jsdom

import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { useInboxUnreadStore } from '../stores/inboxUnread'

const { listImConversationPage, getImUnreadSummary, topicSummary } = vi.hoisted(() => ({
  listImConversationPage: vi.fn(),
  getImUnreadSummary: vi.fn(),
  topicSummary: vi.fn()
}))

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversationPage,
  markImConversationRead: vi.fn().mockResolvedValue({}),
  getImUnreadSummary
}))

vi.mock('../api/services/noticeService', () => ({
  topicSummary
}))

import { useConversationsFeed } from './useConversationsFeed'

const VIEWER_ID = '11111111-1111-7111-8111-111111111111'
const OTHER_USER_ID = '22222222-2222-7222-8222-222222222222'

function conversation(conversationId, { unreadCount = 0 } = {}) {
  return { conversationId, otherUserId: OTHER_USER_ID, unreadCount, lastMessage: null }
}

function page(items, { nextCursor = null, hasMore = false } = {}) {
  return { items, nextCursor, hasMore }
}

function mountFeed({ authed = true } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (authed) {
    useAuthStore().installSession({
      accessToken: 'access-token',
      me: { userId: VIEWER_ID, username: 'viewer' }
    })
  }

  let feed
  const Harness = defineComponent({
    setup() {
      feed = useConversationsFeed()
      return () => null
    }
  })
  mount(Harness, { global: { plugins: [pinia] } })
  return feed
}

describe('useConversationsFeed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    topicSummary.mockResolvedValue({ data: [] })
    getImUnreadSummary.mockResolvedValue({ conversations: [] })
  })

  it('loads the first page on mount and counts unread conversations', async () => {
    listImConversationPage.mockResolvedValueOnce(page([
      conversation('conv-a', { unreadCount: 2 }),
      conversation('conv-b'),
      conversation('conv-c', { unreadCount: 1 })
    ]))

    const feed = mountFeed()
    await flushPromises()

    expect(listImConversationPage).toHaveBeenCalledWith({ cursor: '', size: 20 })
    expect(feed.items.value).toHaveLength(3)
    expect(feed.pendingCount.value).toBe(2)
    expect(feed.loading.value).toBe(false)
    expect(feed.error.value).toBe('')
  })

  it('appends cursor pages, dedupes by conversationId, and stops at the end', async () => {
    listImConversationPage
      .mockResolvedValueOnce(page([conversation('conv-a'), conversation('conv-b')], { nextCursor: 'cursor-2', hasMore: true }))
      .mockResolvedValueOnce(page([conversation('conv-b'), conversation('conv-c')], { nextCursor: null, hasMore: false }))

    const feed = mountFeed()
    await flushPromises()

    await feed.loadMore()
    await flushPromises()

    expect(listImConversationPage.mock.calls.map(([request]) => request.cursor)).toEqual(['', 'cursor-2'])
    const ids = feed.items.value.map((item) => item.conversationId)
    expect(new Set(ids).size).toBe(ids.length)
    expect(feed.items.value).toHaveLength(3)
    expect(feed.hasMore.value).toBe(false)
  })

  it('keeps loaded rows and reports append failures in pageError with the same retry cursor', async () => {
    listImConversationPage
      .mockResolvedValueOnce(page([conversation('conv-a')], { nextCursor: 'cursor-2', hasMore: true }))
      .mockRejectedValueOnce(new Error('追加失败'))
      .mockResolvedValueOnce(page([conversation('conv-b')], { nextCursor: null, hasMore: false }))

    const feed = mountFeed()
    await flushPromises()

    await feed.loadMore()
    await flushPromises()
    expect(feed.items.value).toHaveLength(1)
    expect(feed.pageError.value).toBe('追加失败')
    expect(feed.error.value).toBe('')

    await feed.loadMore()
    await flushPromises()
    expect(listImConversationPage.mock.calls.map(([request]) => request.cursor)).toEqual(['', 'cursor-2', 'cursor-2'])
    expect(feed.items.value).toHaveLength(2)
    expect(feed.pageError.value).toBe('')
  })

  it('refuses load-more while a request is running or no cursor remains', async () => {
    listImConversationPage.mockResolvedValue(page([conversation('conv-a')], { nextCursor: null, hasMore: false }))

    const feed = mountFeed()
    await flushPromises()

    expect(feed.hasMore.value).toBe(false)
    await feed.loadMore()
    expect(listImConversationPage).toHaveBeenCalledTimes(1)
  })

  it('refreshes the shell unread badge after a successful first load, not after append pages', async () => {
    listImConversationPage
      .mockResolvedValueOnce(page([conversation('conv-a')], { nextCursor: 'cursor-2', hasMore: true }))
      .mockResolvedValueOnce(page([conversation('conv-b')], { nextCursor: null, hasMore: false }))

    const feed = mountFeed()
    await flushPromises()
    expect(getImUnreadSummary).toHaveBeenCalledTimes(1)
    expect(useInboxUnreadStore().requestId).toBeGreaterThan(0)

    await feed.loadMore()
    await flushPromises()
    expect(getImUnreadSummary).toHaveBeenCalledTimes(1)
  })

  it('offers reload after the initial load fails and recovers', async () => {
    listImConversationPage
      .mockRejectedValueOnce(new Error('会话服务不可用'))
      .mockResolvedValueOnce(page([conversation('conv-a')]))

    const feed = mountFeed()
    await flushPromises()
    expect(feed.error.value).toBe('会话服务不可用')
    expect(feed.items.value).toHaveLength(0)

    await feed.reload()
    await flushPromises()
    expect(feed.error.value).toBe('')
    expect(feed.items.value).toHaveLength(1)
  })

  it('ignores a stale load-more response after a refresh from the empty cursor', async () => {
    let resolveStaleAppend
    listImConversationPage
      .mockResolvedValueOnce(page([conversation('conv-a')], { nextCursor: 'cursor-2', hasMore: true }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveStaleAppend = resolve }))
      .mockResolvedValueOnce(page([conversation('conv-refreshed')], { nextCursor: null, hasMore: false }))

    const feed = mountFeed()
    await flushPromises()

    const pendingMore = feed.loadMore()
    await feed.reload()
    await flushPromises()

    resolveStaleAppend(page([conversation('conv-stale')], { nextCursor: null, hasMore: false }))
    await pendingMore
    await flushPromises()

    expect(feed.items.value.map((item) => item.conversationId)).toEqual(['conv-refreshed'])
    expect(feed.error.value).toBe('')
    expect(feed.pageError.value).toBe('')
  })

  it('clears rows and ignores the previous identity response after the account switches', async () => {
    let resolvePrevious
    listImConversationPage
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePrevious = resolve }))
      .mockResolvedValueOnce(page([conversation('conv-current')]))

    const feed = mountFeed()
    await flushPromises()

    useAuthStore().installSession({
      accessToken: 'replacement-token',
      me: { userId: OTHER_USER_ID, username: 'other' }
    })
    await flushPromises()
    expect(listImConversationPage).toHaveBeenCalledTimes(2)
    expect(feed.items.value.map((item) => item.conversationId)).toEqual(['conv-current'])

    resolvePrevious(page([conversation('conv-previous')]))
    await flushPromises()
    expect(feed.items.value.map((item) => item.conversationId)).toEqual(['conv-current'])
  })

  it('keeps loaded rows and pagination across access token rotation', async () => {
    listImConversationPage.mockResolvedValue(page([conversation('conv-a')], { nextCursor: 'cursor-2', hasMore: true }))

    const feed = mountFeed()
    await flushPromises()

    // token 轮换：identityEpoch 不推进，scope 不变，列表与游标保持。
    useAuthStore().installSession({
      accessToken: 'rotated-token',
      me: { userId: VIEWER_ID, username: 'viewer' }
    })
    await flushPromises()

    expect(feed.items.value).toHaveLength(1)
    expect(feed.hasMore.value).toBe(true)
    expect(listImConversationPage).toHaveBeenCalledTimes(1)
  })

  it('does not load for an anonymous viewer', async () => {
    const feed = mountFeed({ authed: false })
    await flushPromises()

    expect(listImConversationPage).not.toHaveBeenCalled()
    expect(feed.items.value).toHaveLength(0)
    expect(feed.loading.value).toBe(false)
  })
})
