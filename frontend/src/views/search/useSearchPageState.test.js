// @vitest-environment jsdom

import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { usePostMetaCacheStore } from '../../stores/postMetaCache'
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

  function mountState() {
    const pinia = createPinia()
    setActivePinia(pinia)
    const taxonomy = useTaxonomyStore()
    taxonomy.categories = [{ id: categoryId, name: '公告' }]
    taxonomy.ensureCategories = vi.fn()
    taxonomy.ensureHotTags = vi.fn()
    const postMetaCache = usePostMetaCacheStore()
    postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})

    let state
    const Harness = defineComponent({
      setup() {
        state = useSearchPageState()
        return () => null
      }
    })
    const wrapper = mount(Harness, { global: { plugins: [pinia] } })
    return { state, wrapper }
  }

  function searchItem(id, title) {
    return {
      postId: id,
      userId: '11111111-1111-7111-8111-111111111111',
      title
    }
  }

  beforeEach(() => {
    routerState.route.query = {}
    routerState.replace.mockClear()
    searchPosts.mockReset()
    searchPosts.mockResolvedValue({ data: [] })
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
    searchPosts
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
    expect(searchPosts.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 2])
    expect(state.page.value).toBe(1)
    expect(state.items.value).toHaveLength(20)
    expect(state.hasNext.value).toBe(false)
  })

  it('keeps appended results and surfaces pageError when loading more fails', async () => {
    routerState.route.query = { q: 'paging' }
    const firstPage = Array.from({ length: 10 }, (_, index) =>
      searchItem(`00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`, `first-${index}`)
    )
    searchPosts
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
    expect(searchPosts.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(state.pageError.value).toBe('')
    expect(state.page.value).toBe(1)
    expect(state.items.value).toHaveLength(11)
    expect(state.items.value[10].title).toBe('second-0')
  })

  it('refuses to load more while a request is running or no next page exists', async () => {
    routerState.route.query = { q: 'guarded' }
    searchPosts.mockResolvedValue({ data: [] })
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
    searchPosts.mockResolvedValueOnce({
      data: [searchItem('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', 'stable result')]
    })
    const { state } = mountState()
    await flushPromises()
    searchPosts.mockRejectedValueOnce(new Error('search unavailable'))
    state.keyword.value = 'retry'

    await state.submitSearch()

    expect(state.page.value).toBe(0)
    expect(state.items.value[0].title).toBe('stable result')
    expect(state.error.value).toBe('search unavailable')
  })
})
