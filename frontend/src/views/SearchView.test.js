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
  function mountView() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.clear()

    const taxonomy = useTaxonomyStore()
    taxonomy.categories = [{ id: 1, name: '公告' }]
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
          UiEmpty: true
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
      categoryId: 0,
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
})
