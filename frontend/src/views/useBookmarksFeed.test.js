// @vitest-environment jsdom

import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'

const { listBookmarks, routerPush } = vi.hoisted(() => ({
  listBookmarks: vi.fn(),
  routerPush: vi.fn()
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({ push: routerPush })
  }
})

vi.mock('../api/services/bookmarkService', () => ({
  listBookmarks
}))

vi.mock('../api/services/blockService', () => ({
  listBlockedUsers: vi.fn().mockResolvedValue({ data: [] })
}))

import { useBookmarksFeed } from './useBookmarksFeed'

const VIEWER_ID = '11111111-1111-7111-8111-111111111111'
const OTHER_USER_ID = '22222222-2222-7222-8222-222222222222'

function bookmark(index, { userId = VIEWER_ID, title } = {}) {
  return {
    id: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
    userId,
    title: title || `bookmark-${index + 1}`,
    categoryId: index % 2 === 0 ? '11111111-1111-7111-8111-111111111111' : '',
    createTime: '2026-08-01T00:00:00Z'
  }
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

  const taxonomy = useTaxonomyStore()
  taxonomy.categories = [{ id: '11111111-1111-7111-8111-111111111111', name: '技术' }]
  taxonomy.ensureCategories = vi.fn()

  const socialPrefs = useSocialPrefsStore()
  socialPrefs.ensureBlocked = vi.fn().mockResolvedValue()

  let feed
  const Harness = defineComponent({
    setup() {
      feed = useBookmarksFeed()
      return () => null
    }
  })
  mount(Harness, { global: { plugins: [pinia] } })
  return { feed, socialPrefs }
}

describe('useBookmarksFeed', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routerPush.mockClear()
  })

  it('loads the first page on mount with categories and blocklist ensured', async () => {
    listBookmarks.mockResolvedValueOnce({ data: [bookmark(0), bookmark(1)] })

    const { feed, socialPrefs } = mountFeed()
    await flushPromises()

    expect(listBookmarks).toHaveBeenCalledWith({ page: 0, size: 10 })
    expect(feed.items.value).toHaveLength(2)
    expect(feed.hasNext.value).toBe(false)
    expect(feed.loading.value).toBe(false)
    expect(feed.error.value).toBe('')
    // 列表请求在拉黑与分类就绪之后发起。
    expect(socialPrefs.ensureBlocked).toHaveBeenCalled()
  })

  it('appends the next page until a short page ends the feed', async () => {
    listBookmarks
      .mockResolvedValueOnce({ data: Array.from({ length: 10 }, (_, index) => bookmark(index)) })
      .mockResolvedValueOnce({ data: [bookmark(10)] })

    const { feed } = mountFeed()
    await flushPromises()

    await feed.loadMore()
    await flushPromises()

    expect(listBookmarks.mock.calls.map(([request]) => request.page)).toEqual([0, 1])
    expect(feed.items.value).toHaveLength(11)
    expect(feed.hasNext.value).toBe(false)
  })

  it('keeps the loaded page and retries the same page number after a load-more failure', async () => {
    listBookmarks
      .mockResolvedValueOnce({ data: Array.from({ length: 10 }, (_, index) => bookmark(index)) })
      .mockRejectedValueOnce(new Error('temporary bookmark failure'))
      .mockResolvedValueOnce({ data: [bookmark(10)] })

    const { feed } = mountFeed()
    await flushPromises()

    await feed.loadMore()
    await flushPromises()
    expect(feed.items.value).toHaveLength(10)
    expect(feed.pageError.value).toBe('temporary bookmark failure')
    expect(feed.error.value).toBe('')

    await feed.loadMore()
    await flushPromises()
    expect(listBookmarks.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(feed.items.value).toHaveLength(11)
    expect(feed.pageError.value).toBe('')
  })

  it('dedupes page-shifted bookmarks by id when appending the next page', async () => {
    listBookmarks
      .mockResolvedValueOnce({ data: Array.from({ length: 10 }, (_, index) => bookmark(index)) })
      .mockResolvedValueOnce({ data: [{ ...bookmark(9), title: 'shifted-copy' }, bookmark(10)] })

    const { feed } = mountFeed()
    await flushPromises()
    await feed.loadMore()
    await flushPromises()

    const ids = feed.items.value.map((item) => item.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(feed.items.value).toHaveLength(11)
  })

  it('does not commit a new page when append returns empty rows and ends the feed', async () => {
    listBookmarks
      .mockResolvedValueOnce({ data: Array.from({ length: 10 }, (_, index) => bookmark(index)) })
      .mockResolvedValueOnce({ data: [] })
    const { feed } = mountFeed()
    await flushPromises()

    await feed.loadMore()
    await flushPromises()

    // 空页：已加载列表保持不变，page 不推进（下次仍从第 1 页重试），hasNext 由空页判定关闭。
    expect(feed.items.value).toHaveLength(10)
    expect(feed.page.value).toBe(0)
    expect(feed.hasNext.value).toBe(false)
  })

  it('hides bookmarks from blocked authors while keeping unblocked ones', async () => {
    const { feed, socialPrefs } = mountFeed()
    socialPrefs.blockedUserIds = [OTHER_USER_ID]

    listBookmarks.mockResolvedValueOnce({
      data: [bookmark(0, { userId: OTHER_USER_ID }), bookmark(1)]
    })
    await feed.reload()
    await flushPromises()

    expect(feed.items.value).toHaveLength(1)
    expect(feed.items.value[0].userId).toBe(VIEWER_ID)
  })

  it('resolves the category label from the taxonomy or falls back to a stable id', async () => {
    const { feed } = mountFeed()

    expect(feed.categoryLabel('11111111-1111-7111-8111-111111111111')).toBe('技术')
    expect(feed.categoryLabel('99999999-9999-7999-8999-999999999999')).toBe('分类#99999999-9999-7999-8999-999999999999')
    expect(feed.categoryLabel('')).toBe('')
    expect(feed.categoryLabel(null)).toBe('')
  })

  it('opens the post through the router', async () => {
    listBookmarks.mockResolvedValueOnce({ data: [bookmark(0)] })
    const { feed } = mountFeed()
    await flushPromises()

    feed.openPost(feed.items.value[0])
    expect(routerPush).toHaveBeenCalledWith({
      name: 'postDetail',
      params: { postId: bookmark(0).id }
    })
  })

  it('offers reload after the initial load fails and clears the error on success', async () => {
    listBookmarks
      .mockRejectedValueOnce(new Error('bookmark service down'))
      .mockResolvedValueOnce({ data: [bookmark(0)] })

    const { feed } = mountFeed()
    await flushPromises()
    expect(feed.error.value).toBe('bookmark service down')
    expect(feed.items.value).toHaveLength(0)

    await feed.reload()
    await flushPromises()
    expect(feed.error.value).toBe('')
    expect(feed.items.value).toHaveLength(1)
  })

  it('discards a stale response after the account switches and reloads for the new identity', async () => {
    let resolvePrevious
    listBookmarks
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePrevious = resolve }))
      .mockResolvedValueOnce({ data: [{ ...bookmark(0), title: 'current account' }] })

    const { feed } = mountFeed()
    await flushPromises()

    useAuthStore().installSession({
      accessToken: 'replacement-token',
      me: { userId: OTHER_USER_ID, username: 'other' }
    })
    await flushPromises()
    expect(listBookmarks).toHaveBeenCalledTimes(2)
    expect(feed.items.value[0].title).toBe('current account')

    resolvePrevious({ data: [{ ...bookmark(0), title: 'previous account' }] })
    await flushPromises()
    expect(feed.items.value[0].title).toBe('current account')
    expect(feed.error.value).toBe('')
  })

  it('refuses to load anything when the viewer is anonymous', async () => {
    const { feed } = mountFeed({ authed: false })
    await flushPromises()

    expect(listBookmarks).not.toHaveBeenCalled()
    expect(feed.items.value).toHaveLength(0)
    expect(feed.loading.value).toBe(false)
  })
})
