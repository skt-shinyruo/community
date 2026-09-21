// @vitest-environment jsdom

import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
import { useSocialPrefsStore } from '../../stores/socialPrefs'
import { useTaxonomyStore } from '../../stores/taxonomy'

const routerState = vi.hoisted(() => ({
  route: { query: {} },
  replace: vi.fn()
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routerState.route,
    useRouter: () => ({ replace: routerState.replace })
  }
})

vi.mock('../../api/services/searchService', () => ({
  searchPosts: vi.fn()
}))

vi.mock('../../api/services/postService', () => ({
  batchPostSummaries: vi.fn().mockResolvedValue({ data: [] })
}))

vi.mock('../../api/services/taxonomyService', () => ({
  suggestTags: vi.fn().mockResolvedValue({ data: [] })
}))

import { searchPosts } from '../../api/services/searchService'
import {
  parseSearchRouteQuery,
  serializeSearchRouteQuery,
  useSearchPageState
} from './useSearchPageState'

describe('useSearchPageState', () => {
  const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'

  function mountState({ authed = false } = {}) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const taxonomy = useTaxonomyStore()
    taxonomy.categories = [{ id: categoryId, name: '公告' }]
    taxonomy.ensureCategories = vi.fn()
    taxonomy.ensureHotTags = vi.fn()
    const postMetaCache = usePostMetaCacheStore()
    postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})

    const socialPrefs = useSocialPrefsStore()
    if (authed) {
      const auth = useAuthStore()
      auth.installSession({ accessToken: 'token' })
      socialPrefs.ensureBlocked = vi.fn().mockResolvedValue(undefined)
    }

    /** @type {ReturnType<typeof useSearchPageState> | undefined} */
    let state
    const Harness = defineComponent({
      setup() {
        state = useSearchPageState()
        return () => null
      }
    })
    const wrapper = mount(Harness, { global: { plugins: [pinia] } })
    if (!state) throw new Error('harness state not initialized')
    return { state, wrapper, socialPrefs }
  }

  // searchPosts 的模块 mock；测试载荷不含 traceId，用裸 Mock 形参类型承载。
  /** @type {import('vitest').Mock} */
  const mockedSearchPosts = vi.mocked(searchPosts)

  function searchItem(id, title, userId = '11111111-1111-7111-8111-111111111111') {
    return {
      postId: id,
      userId,
      title
    }
  }

  beforeEach(() => {
    routerState.route.query = {}
    routerState.replace.mockClear()
    vi.mocked(searchPosts).mockReset()
    mockedSearchPosts.mockResolvedValue({ data: [] })
  })

  it('parses and serializes the public search query fields without private delimiters', () => {
    expect(parseSearchRouteQuery({ q: 'java', categoryId, tag: '#Spring' })).toEqual({
      keyword: 'java',
      categoryId,
      tag: 'Spring'
    })
    expect(serializeSearchRouteQuery({ keep: 'yes', q: 'old' }, {
      keyword: 'new',
      categoryId,
      tag: '#Vue'
    })).toEqual({ keep: 'yes', q: 'new', categoryId, tag: 'Vue' })
  })

  it('applies route initialization and later route criteria changes', async () => {
    routerState.route.query = { q: 'first', categoryId, tag: '#Java' }
    const { state } = mountState()
    await flushPromises()

    expect(state.keyword.value).toBe('first')
    expect(state.categoryId.value).toBe(categoryId)
    expect(state.tagDraft.value).toBe('Java')
    expect(searchPosts).toHaveBeenLastCalledWith({
      keyword: 'first', categoryId, tag: 'Java', page: 0, size: 10
    })

    routerState.route.query = { q: 'second' }
    state.applyRouteSearch()
    await flushPromises()
    expect(state.keyword.value).toBe('second')
    expect(state.categoryId.value).toBe('')
    expect(state.tagDraft.value).toBe('')

    routerState.route.query = {}
    state.applyRouteSearch()
    expect(state.keyword.value).toBe('')
    expect(state.items.value).toEqual([])
  })

  it('appends full pages in order and keeps loaded results when the next page is empty', async () => {
    routerState.route.query = { q: 'paging' }
    const firstPage = Array.from({ length: 10 }, (_, index) =>
      searchItem(`00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`, `first-${index}`)
    )
    const secondPage = Array.from({ length: 10 }, (_, index) =>
      searchItem(`10000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`, `second-${index}`)
    )
    mockedSearchPosts
      .mockResolvedValueOnce({ data: firstPage })
      .mockResolvedValueOnce({ data: secondPage })
      .mockResolvedValueOnce({ data: [] })
    const { state } = mountState()
    await flushPromises()

    await state.loadMore()
    expect(state.page.value).toBe(1)
    expect(state.items.value).toHaveLength(20)
    expect(state.items.value[0].title).toBe('first-0')
    expect(state.items.value[10].title).toBe('second-0')

    await state.loadMore()
    expect(mockedSearchPosts.mock.calls.map(([request]) => request?.page)).toEqual([0, 1, 2])
    expect(state.page.value).toBe(1)
    expect(state.items.value).toHaveLength(20)
    expect(state.hasNext.value).toBe(false)
  })

  it('dedupes page-shifted hits by postId when appending the next page', async () => {
    routerState.route.query = { q: 'paging' }
    const firstPage = Array.from({ length: 10 }, (_, index) =>
      searchItem(`00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`, `first-${index}`)
    )
    mockedSearchPosts
      .mockResolvedValueOnce({ data: firstPage })
      .mockResolvedValueOnce({
        data: [
          searchItem('00000000-0000-7000-8000-000000000010', 'shifted-copy-of-first-9'),
          searchItem('10000000-0000-7000-8000-000000000001', 'second-0')
        ]
      })
    const { state } = mountState()
    await flushPromises()

    await state.loadMore()

    const ids = state.items.value.map((item) => item.postId)
    expect(new Set(ids).size).toBe(ids.length)
    expect(state.items.value).toHaveLength(11)
    expect(state.items.value[9].title).toBe('first-9')
    expect(state.items.value[10].title).toBe('second-0')
  })

  it('keeps appended results and surfaces pageError when loading more fails', async () => {
    routerState.route.query = { q: 'paging' }
    const firstPage = Array.from({ length: 10 }, (_, index) =>
      searchItem(`00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`, `first-${index}`)
    )
    mockedSearchPosts
      .mockResolvedValueOnce({ data: firstPage })
      .mockRejectedValueOnce(new Error('temporary search failure'))
      .mockResolvedValueOnce({ data: [searchItem('22222222-2222-7222-8222-222222222222', 'second-0')] })
    const { state } = mountState()
    await flushPromises()

    await state.loadMore()
    expect(state.items.value).toHaveLength(10)
    expect(state.page.value).toBe(0)
    expect(state.pageError.value).toBe('temporary search failure')
    expect(state.error.value).toBe('')

    await state.loadMore()
    expect(mockedSearchPosts.mock.calls.map(([request]) => request?.page)).toEqual([0, 1, 1])
    expect(state.pageError.value).toBe('')
    expect(state.page.value).toBe(1)
    expect(state.items.value).toHaveLength(11)
    expect(state.items.value[10].title).toBe('second-0')
  })

  it('refuses to load more while a request is running or no next page exists', async () => {
    routerState.route.query = { q: 'guarded' }
    mockedSearchPosts.mockResolvedValue({ data: [] })
    const { state } = mountState()
    await flushPromises()

    expect(state.hasNext.value).toBe(false)
    await state.loadMore()
    expect(searchPosts).toHaveBeenCalledTimes(1)

    state.hasNext.value = true
    state.loadingMore.value = true
    await state.loadMore()
    expect(searchPosts).toHaveBeenCalledTimes(1)
  })

  it('keeps committed results and page state when a new request fails', async () => {
    routerState.route.query = { q: 'stable' }
    mockedSearchPosts.mockResolvedValueOnce({
      data: [searchItem('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', 'stable result')]
    })
    const { state } = mountState()
    await flushPromises()
    mockedSearchPosts.mockRejectedValueOnce(new Error('search unavailable'))
    state.keyword.value = 'retry'

    await state.submitSearch()

    expect(state.page.value).toBe(0)
    expect(state.items.value[0].title).toBe('stable result')
    expect(state.error.value).toBe('search unavailable')
  })

  it('hides hits authored by blocked users', async () => {
    routerState.route.query = { q: 'blocked' }
    mockedSearchPosts.mockResolvedValueOnce({
      data: [
        searchItem('33333333-3333-7333-8333-333333333333', 'visible result'),
        searchItem('44444444-4444-7444-8444-444444444444', 'blocked author result', 'blocked-user')
      ]
    })
    const { state } = mountState({ authed: true })
    useSocialPrefsStore().blockedUserIds = ['blocked-user']
    await flushPromises()

    expect(state.items.value.map((item) => item.title)).toEqual(['visible result'])
    expect(state.blockedHiddenCount.value).toBe(1)
  })

  it('accumulates the hidden blocked count across pages without double counting page-shifted hits', async () => {
    routerState.route.query = { q: 'paging' }
    const visiblePage = (prefix, count) =>
      Array.from({ length: count }, (_, index) =>
        searchItem(`${prefix}-0000-7000-8000-${String(index).padStart(12, '0')}`, `visible-${prefix}-${index}`)
      )
    mockedSearchPosts
      .mockResolvedValueOnce({
        data: [
          searchItem('00000000-0000-7000-8000-0000000000b1', 'hidden one', 'blocked-user'),
          ...visiblePage('10000000', 9)
        ]
      })
      .mockResolvedValueOnce({
        data: [
          searchItem('00000000-0000-7000-8000-0000000000b1', 'hidden one shifted', 'blocked-user'),
          searchItem('00000000-0000-7000-8000-0000000000b2', 'hidden two', 'blocked-user'),
          searchItem('20000000-0000-7000-8000-000000000000', 'visible last')
        ]
      })
    const { state } = mountState({ authed: true })
    useSocialPrefsStore().blockedUserIds = ['blocked-user']
    await flushPromises()

    expect(state.items.value).toHaveLength(9)
    expect(state.blockedHiddenCount.value).toBe(1)

    await state.loadMore()
    expect(state.items.value).toHaveLength(10)
    expect(state.items.value[9].title).toBe('visible last')
    expect(state.blockedHiddenCount.value).toBe(2)
    expect(state.hasNext.value).toBe(false)
  })

  it('resets the hidden blocked count on a fresh reload', async () => {
    routerState.route.query = { q: 'reload' }
    mockedSearchPosts
      .mockResolvedValueOnce({
        data: [searchItem('55555555-5555-7555-8555-555555555555', 'hidden', 'blocked-user')]
      })
      .mockResolvedValueOnce({
        data: [searchItem('66666666-6666-7666-8666-666666666666', 'fresh visible')]
      })
    const { state } = mountState({ authed: true })
    useSocialPrefsStore().blockedUserIds = ['blocked-user']
    await flushPromises()
    expect(state.blockedHiddenCount.value).toBe(1)

    await state.reload()
    expect(state.items.value.map((item) => item.title)).toEqual(['fresh visible'])
    expect(state.blockedHiddenCount.value).toBe(0)
  })

  it('does not filter hits for anonymous viewers', async () => {
    routerState.route.query = { q: 'anon' }
    mockedSearchPosts.mockResolvedValueOnce({
      data: [searchItem('77777777-7777-7777-8777-777777777777', 'anonymous visible', 'blocked-user')]
    })
    const { state } = mountState()
    await flushPromises()

    expect(state.items.value).toHaveLength(1)
    expect(state.blockedHiddenCount.value).toBe(0)
  })

  it('still shows search results when loading the blocklist fails', async () => {
    routerState.route.query = { q: 'resilient' }
    mockedSearchPosts.mockResolvedValueOnce({
      data: [searchItem('88888888-8888-7888-8888-888888888888', 'still visible')]
    })
    const { state, socialPrefs } = mountState({ authed: true })
    vi.mocked(socialPrefs.ensureBlocked).mockRejectedValueOnce(new Error('blocklist unavailable'))
    await flushPromises()

    expect(state.items.value).toHaveLength(1)
    expect(state.error.value).toBe('')
  })

  it('clears the hidden blocked count when the search state resets', async () => {
    routerState.route.query = { q: 'blocked' }
    mockedSearchPosts.mockResolvedValueOnce({
      data: [searchItem('99999999-9999-7999-8999-999999999999', 'hidden', 'blocked-user')]
    })
    const { state } = mountState({ authed: true })
    useSocialPrefsStore().blockedUserIds = ['blocked-user']
    await flushPromises()
    expect(state.blockedHiddenCount.value).toBe(1)

    state.clearSearch()
    expect(state.items.value).toEqual([])
    expect(state.blockedHiddenCount.value).toBe(0)

    routerState.route.query = { q: 'again' }
    mockedSearchPosts.mockResolvedValueOnce({
      data: [searchItem('99999999-9999-7999-8999-999999999999', 'hidden again', 'blocked-user')]
    })
    state.applyRouteSearch()
    await flushPromises()
    expect(state.blockedHiddenCount.value).toBe(1)

    routerState.route.query = {}
    state.applyRouteSearch()
    expect(state.blockedHiddenCount.value).toBe(0)
  })
})
