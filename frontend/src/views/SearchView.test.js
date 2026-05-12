// @vitest-environment jsdom

import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import { useTaxonomyStore } from '../stores/taxonomy'

const routerState = vi.hoisted(() => ({
  route: {
    name: 'search',
    path: '/search',
    fullPath: '/search',
    query: {}
  },
  replace: vi.fn(),
  push: vi.fn()
}))

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

vi.mock('../api/services/searchService', () => ({
  searchPosts: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-search' }),
  reindex: vi.fn().mockResolvedValue({ data: { indexedCount: 0, jobId: '' }, traceId: 'trace-reindex' })
}))

vi.mock('../api/services/postService', () => ({
  batchPostSummaries: vi.fn().mockResolvedValue({ data: [] })
}))

import SearchView from './SearchView.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import { searchPosts } from '../api/services/searchService'

describe('SearchView', () => {
  const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'

  function createDeferred() {
    let resolve
    let reject
    const promise = new Promise((res, rej) => {
      resolve = res
      reject = rej
    })
    return { promise, resolve, reject }
  }

  function mountView() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.clear()

    const taxonomy = useTaxonomyStore()
    taxonomy.categories = [{ id: categoryId, name: '公告' }]
    taxonomy.hotTags = [{ id: 1, name: 'Java' }, { id: 2, name: 'Spring' }]
    taxonomy.ensureCategories = vi.fn()
    taxonomy.ensureHotTags = vi.fn()

    const postMetaCache = usePostMetaCacheStore()
    postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})

    return mount(SearchView, {
      global: {
        plugins: [pinia],
        stubs: {
          UiState: true
        }
      }
    })
  }

  beforeEach(() => {
    routerState.route.query = {}
    routerState.replace.mockClear()
    routerState.push.mockClear()
    searchPosts.mockClear()
    searchPosts.mockResolvedValue({ data: [], traceId: 'trace-search' })
  })

  it('commits tag filter changes through query-driving state', async () => {
    const wrapper = mountView()
    const tagInput = wrapper.getComponent(UiAutosuggestInput)

    await tagInput.vm.$emit('update:modelValue', '#Java')
    await nextTick()
    await tagInput.vm.$emit('commit', '#Java')
    await flushPromises()

    expect(routerState.replace).toHaveBeenLastCalledWith({
      name: 'search',
      query: { tag: 'Java' }
    })
    expect(searchPosts).toHaveBeenLastCalledWith({
      keyword: '',
      categoryId: '',
      tag: 'Java',
      page: 0,
      size: 10
    })
    expect(wrapper.text()).toContain('#Java')
  })

  it('uses the shared autosuggest input for tag filtering', () => {
    const wrapper = mountView()

    expect(wrapper.findComponent(UiAutosuggestInput).exists()).toBe(true)
  })

  it('renders category filters and sends UUID category ids to search', async () => {
    routerState.route.query = { categoryId }
    searchPosts.mockResolvedValue({
      data: [
        {
          postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          userId: '11111111-1111-7111-8111-111111111111',
          title: 'Post A',
          categoryId
        }
      ],
      traceId: 'trace-search-category'
    })

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(searchPosts).toHaveBeenLastCalledWith({
      keyword: '',
      categoryId,
      tag: '',
      page: 0,
      size: 10
    })
    expect(wrapper.text()).toContain('公告')
  })

  it('keeps the newest search result when an older request resolves later', async () => {
    const first = createDeferred()
    const second = createDeferred()
    let callCount = 0

    searchPosts.mockImplementation(({ keyword }) => {
      callCount += 1
      if (keyword === 'first' && callCount === 1) return first.promise
      if (keyword === 'second' && callCount === 2) return second.promise
      return Promise.resolve({ data: [], traceId: `trace-${keyword}` })
    })

    const wrapper = mountView()
    const keywordInput = wrapper.get('input[name="search-keyword"]')

    await keywordInput.setValue('first')
    const firstRun = wrapper.vm.onSearch()

    await keywordInput.setValue('second')
    const secondRun = wrapper.vm.onSearch()

    second.resolve({
      data: [
        {
          postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          userId: '11111111-1111-7111-8111-111111111111',
          title: 'Second Result',
          highlightedTitle: 'Second Result',
          createTime: Date.now(),
          lastActivityTime: Date.now()
        }
      ],
      traceId: 'trace-second'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Second Result')

    first.resolve({
      data: [
        {
          postId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
          userId: '22222222-2222-7222-8222-222222222222',
          title: 'First Result',
          highlightedTitle: 'First Result',
          createTime: Date.now(),
          lastActivityTime: Date.now()
        }
      ],
      traceId: 'trace-first'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Second Result')
    expect(wrapper.text()).not.toContain('First Result')

    await Promise.allSettled([firstRun, secondRun])
  })
})
