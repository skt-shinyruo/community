// @vitest-environment jsdom

import { defineComponent, reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { useTaxonomyStore } from '../../stores/taxonomy'

const routerState = vi.hoisted(() => ({
  route: /** @type {{ name: string, path: string, fullPath: string, query: Record<string, unknown> } | null} */ (null),
  replace: vi.fn(),
  push: vi.fn()
}))

routerState.route = reactive({
  name: 'posts',
  path: '/posts',
  fullPath: '/posts',
  query: {}
})

/** 取已初始化的路由对象，供测试内统一访问 */
function currentRoute() {
  if (!routerState.route) throw new Error('routerState.route not initialized')
  return routerState.route
}

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routerState.route,
    useRouter: () => ({
      replace: routerState.replace,
      push: routerState.push
    })
  }
})

vi.mock('../../api/services/postService', () => ({
  listGlobalFeed: vi.fn().mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-v1' } }),
  listBoardFeed: vi.fn().mockResolvedValue({ data: { items: [], nextCursor: '' } }),
  createPost: vi.fn().mockResolvedValue({ data: { postId: 1 } }),
  batchPostSummaries: vi.fn().mockResolvedValue({ data: [] })
}))

vi.mock('../../api/services/searchService', () => ({
  searchPosts: vi.fn().mockResolvedValue({ data: [] })
}))

vi.mock('../../api/services/taxonomyService', () => ({
  suggestTags: vi.fn().mockResolvedValue({ data: [] })
}))

vi.mock('../../api/services/socialService', () => ({
  setLike: vi.fn(),
  getLikeCounts: vi.fn().mockResolvedValue({ data: {} }),
  getLikeStatuses: vi.fn().mockResolvedValue({ data: {} })
}))

const { showToast } = vi.hoisted(() => ({
  showToast: vi.fn()
}))

vi.mock('../../ui/toastService', () => ({
  showToast,
  showErrorToast: vi.fn((cause, fallback) => showToast(fallback)),
  setToastHandler: vi.fn()
}))

import { listGlobalFeed, listBoardFeed } from '../../api/services/postService'
import { searchPosts } from '../../api/services/searchService'
import { setLike } from '../../api/services/socialService'
import { usePostsFeed } from './usePostsFeed'
import {
  canJumpToLastSeenDivider,
  findLastSeenDividerIndex,
  hasLastSeenDivider
} from './usePostsFeed'

const USER_ID = '11111111-1111-7111-8111-111111111111'
const OTHER_USER_ID = '22222222-2222-7222-8222-222222222222'

function post(id, { userId = USER_ID, activityAt = 1_700_000_000_000 } = {}) {
  return {
    id,
    userId,
    title: `post-${id}`,
    lastActivityTime: new Date(activityAt).toISOString(),
    createTime: new Date(activityAt).toISOString()
  }
}

function mountFeed({ query = {} } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const auth = useAuthStore()
  auth.installSession({ accessToken: 'token' })
  auth.setMe({ userId: USER_ID, username: 'aaa', authorities: [] })

  const taxonomy = useTaxonomyStore()
  taxonomy.categories = [{ id: 1, name: '技术' }]
  taxonomy.hotTags = [{ id: 1, name: 'Java' }]
  taxonomy.ensureCategories = vi.fn()
  taxonomy.ensureHotTags = vi.fn()

  const socialPrefs = useSocialPrefsStore()
  socialPrefs.ensureBlocked = vi.fn().mockResolvedValue(undefined)

  const postMetaCache = usePostMetaCacheStore()
  postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
  postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})
  postMetaCache.ensureLikeStatuses = vi.fn().mockResolvedValue({})
  postMetaCache.clearLikeStatuses = vi.fn()

  currentRoute().query = query

  /** @type {ReturnType<typeof usePostsFeed> | undefined} */
  let feed
  const Harness = defineComponent({
    setup() {
      feed = usePostsFeed()
      return () => null
    }
  })
  mount(Harness, { global: { plugins: [pinia] } })
  if (!feed) throw new Error('harness feed not initialized')
  return feed
}

describe('usePostsFeed last-seen divider', () => {
  it('enables last-seen jump only when a divider exists inside the current feed', () => {
    const dividerIndex = findLastSeenDividerIndex(
      [
        { activityAt: 300 },
        { activityAt: 180 },
        { activityAt: 120 }
      ],
      200,
      (item) => item.activityAt
    )

    expect(dividerIndex).toBe(1)
    expect(
      canJumpToLastSeenDivider({
        isLatestFeedView: true,
        newSinceLastSeenCount: 2,
        newHintDismissed: false,
        dividerIndex,
        itemsLength: 3
      })
    ).toBe(true)

    expect(
      canJumpToLastSeenDivider({
        isLatestFeedView: true,
        newSinceLastSeenCount: 3,
        newHintDismissed: false,
        dividerIndex: -1,
        itemsLength: 3
      })
    ).toBe(false)

    expect(
      hasLastSeenDivider({
        isLatestFeedView: true,
        dividerIndex: 0,
        itemsLength: 3
      })
    ).toBe(false)
  })

  it('skips zero or non-finite activity values when locating the divider', () => {
    expect(findLastSeenDividerIndex(
      [{ activityAt: 0 }, { activityAt: null }, { activityAt: 100 }],
      150,
      (item) => item.activityAt
    )).toBe(2)

    expect(findLastSeenDividerIndex([], 100, (item) => item.activityAt)).toBe(-1)
    expect(findLastSeenDividerIndex([{ activityAt: 90 }], 0, (item) => item.activityAt)).toBe(-1)
    expect(findLastSeenDividerIndex([{ activityAt: 90 }], Number.NaN, (item) => item.activityAt)).toBe(-1)
  })
})

describe('usePostsFeed feed state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentRoute().query = {}
    window.localStorage.clear()
    vi.mocked(listGlobalFeed).mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-v1' }, traceId: 'trace-feed' })
    vi.mocked(listBoardFeed).mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-board-v1' }, traceId: 'trace-board-feed' })
    vi.mocked(searchPosts).mockResolvedValue({ data: [], traceId: 'trace-search' })
    vi.mocked(showToast).mockClear()
  })

  it('loads the global feed by default and exposes the activity projections', async () => {
    vi.mocked(listGlobalFeed).mockResolvedValueOnce({
      data: {
        items: [
          post('post-1', { activityAt: 3_000 }),
          post('post-2', { userId: OTHER_USER_ID, activityAt: 1_000 })
        ],
        nextCursor: '',
        rankVersion: 'rank-v1'
      },
      traceId: 'trace-page'
    })

    const feed = mountFeed()
    await flushPromises()

    expect(listGlobalFeed).toHaveBeenCalledWith({ cursor: '', size: 10 })
    expect(feed.feed.items.value).toHaveLength(2)
    expect(feed.feed.hasNext.value).toBe(false)

    const first = feed.feed.items.value[0]
    // activityTime 返回原始时间字段：lastActivityTime 优先，其次 lastReplyTime / createTime。
    expect(feed.feed.activityTime(first)).toBe(first.lastActivityTime)
    expect(feed.feed.activityUser(first)).toBe(null)
  })

  it('falls back to createTime then reply attribution for activity time and user', async () => {
    const withReply = {
      id: 'post-r',
      userId: USER_ID,
      createTime: new Date(1_000).toISOString(),
      lastReplyTime: new Date(2_000).toISOString(),
      lastReplyUserId: OTHER_USER_ID,
      lastReplyAuthor: { username: 'replier' },
      author: { username: 'author' }
    }
    const onlyCreate = {
      id: 'post-c',
      userId: USER_ID,
      createTime: new Date(500).toISOString(),
      author: { username: 'author' }
    }
    vi.mocked(listGlobalFeed).mockResolvedValueOnce({ data: { items: [withReply, onlyCreate], nextCursor: '', rankVersion: 'rank-v1' }, traceId: 'trace-page' })

    const feed = mountFeed()
    // 补水前的原始投影断言：activityTime / activityUser 的字段优先级。
    expect(feed.feed.activityTime(withReply)).toBe(withReply.lastReplyTime)
    expect(feed.feed.activityUserId(withReply)).toBe(OTHER_USER_ID)
    expect(feed.feed.activityUser(withReply)).toEqual({ username: 'replier' })

    expect(feed.feed.activityTime(onlyCreate)).toBe(onlyCreate.createTime)
    expect(feed.feed.activityUserId(onlyCreate)).toBe(USER_ID)
    expect(feed.feed.activityUser(onlyCreate)).toEqual({ username: 'author' })
  })

  it('routes the board feed for a category filter and the search stack for a tag filter', async () => {
    vi.mocked(listBoardFeed).mockResolvedValueOnce({ data: { items: [post('board-1')], nextCursor: '', rankVersion: 'rank-board-v1' }, traceId: 'trace-board-page' })
    vi.mocked(searchPosts).mockResolvedValueOnce({ data: [], traceId: 'trace-search-page' })

    const categoryFeed = mountFeed({ query: { categoryId: '7' } })
    await flushPromises()
    expect(listBoardFeed).toHaveBeenCalledWith('7', { cursor: '', size: 10 })
    expect(categoryFeed.feed.items.value).toHaveLength(1)

    mountFeed({ query: { tag: 'Java' } })
    await flushPromises()
    expect(searchPosts).toHaveBeenCalledWith({ categoryId: undefined, tag: 'Java', page: 0, size: 10 })
  })

  it('blocks repeat likes on the same post while a request is in flight and releases after', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolveLike
    vi.mocked(setLike).mockImplementationOnce(() => new Promise((resolve) => { resolveLike = resolve }))
    vi.mocked(listGlobalFeed).mockResolvedValueOnce({ data: { items: [post('post-1')], nextCursor: '', rankVersion: 'rank-v1' }, traceId: 'trace-page' })

    const feed = mountFeed()
    await flushPromises()
    const target = feed.feed.items.value[0]

    expect(feed.feed.isLikePending(target)).toBe(false)
    const pending = feed.feed.togglePostLike(target)
    await Promise.resolve()
    expect(feed.feed.isLikePending(target)).toBe(true)

    // 在途时重复点击被忽略。
    await feed.feed.togglePostLike(target)
    expect(setLike).toHaveBeenCalledTimes(1)

    if (!resolveLike) throw new Error('resolveLike not initialized')
    resolveLike({ data: { likeCount: 5, liked: true } })
    await pending
    expect(feed.feed.isLikePending(target)).toBe(false)
    expect(target.likeCount).toBe(5)
    expect(target.liked).toBe(true)
  })

  it('blocks an anonymous like with a warning toast instead of a request', async () => {
    vi.mocked(listGlobalFeed).mockResolvedValueOnce({ data: { items: [post('post-1')], nextCursor: '', rankVersion: 'rank-v1' }, traceId: 'trace-page' })
    const feed = mountFeed()
    await flushPromises()

    useAuthStore().clear()
    await flushPromises()

    await feed.feed.togglePostLike(feed.feed.items.value[0])
    expect(setLike).not.toHaveBeenCalled()
    expect(showToast).toHaveBeenCalledWith({ type: 'warning', text: '请先登录' })
  })
})
