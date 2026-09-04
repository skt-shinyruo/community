// @vitest-environment jsdom

import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
  searchPosts: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-search' })
}))

vi.mock('../api/services/postService', () => ({
  batchPostSummaries: vi.fn().mockResolvedValue({ data: [] })
}))

import SearchView from './SearchView.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import { searchPosts } from '../api/services/searchService'

describe('SearchView', () => {
  const categoryId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
  const wrappers = []

  function createDeferred() {
    let resolve
    let reject
    const promise = new Promise((res, rej) => {
      resolve = res
      reject = rej
    })
    return { promise, resolve, reject }
  }

  function mountView({ admin = false } = {}) {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.clear()
    if (admin) {
      auth.setMe({
        userId: 1,
        username: 'admin',
        authorities: ['ROLE_ADMIN']
      })
    }

    const taxonomy = useTaxonomyStore()
    taxonomy.categories = [{ id: categoryId, name: '公告' }]
    taxonomy.hotTags = [{ id: 1, name: 'Java' }, { id: 2, name: 'Spring' }]
    taxonomy.ensureCategories = vi.fn()
    taxonomy.ensureHotTags = vi.fn()

    const postMetaCache = usePostMetaCacheStore()
    postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})

    const wrapper = mount(SearchView, {
      attachTo: document.body,
      global: {
        plugins: [pinia]
      }
    })
    wrappers.push(wrapper)
    return wrapper
  }

  function categoryTrigger(wrapper) {
    return wrapper.getComponent(UiSelect).get('[role="combobox"]')
  }

  function listboxOptions() {
    return [...document.body.querySelectorAll('[role="listbox"] [role="option"]')]
  }

  function searchItem(id, title) {
    return {
      postId: id,
      userId: '11111111-1111-7111-8111-111111111111',
      title,
      highlightedTitle: title,
      createTime: Date.now(),
      lastActivityTime: Date.now()
    }
  }

  beforeEach(() => {
    routerState.route.query = {}
    routerState.replace.mockClear()
    routerState.push.mockClear()
    searchPosts.mockClear()
    searchPosts.mockResolvedValue({ data: [], traceId: 'trace-search' })
  })

  afterEach(() => {
    while (wrappers.length) wrappers.pop().unmount()
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

  it('renders the category filter as a UiSelect with the category options', () => {
    const wrapper = mountView()

    const select = wrapper.getComponent(UiSelect)
    expect(select.props('options')).toEqual([
      { label: '全部分类', value: '' },
      { label: '公告', value: categoryId }
    ])
  })

  it('selects a category with the keyboard and sends the UUID id to search', async () => {
    const wrapper = mountView()
    const trigger = categoryTrigger(wrapper)

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await nextTick()
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(listboxOptions().map((option) => option.textContent)).toEqual(['全部分类', '公告'])

    await trigger.trigger('keydown', { key: 'ArrowDown' })
    await trigger.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(routerState.replace).toHaveBeenLastCalledWith({
      name: 'search',
      query: { categoryId }
    })
    expect(searchPosts).toHaveBeenLastCalledWith({
      keyword: '',
      categoryId,
      tag: '',
      page: 0,
      size: 10
    })
    expect(wrapper.text()).toContain('公告')
  })

  it('clears the selected category through the UiSelect clear button', async () => {
    routerState.route.query = { categoryId }
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

    await wrapper.getComponent(UiSelect).get('button.ui-select__clear').trigger('click')
    await flushPromises()

    expect(routerState.replace).toHaveBeenLastCalledWith({ name: 'search', query: {} })
    expect(searchPosts).toHaveBeenLastCalledWith({
      keyword: '',
      categoryId: '',
      tag: '',
      page: 0,
      size: 10
    })
  })

  it('clears category and tag filters through the clear-filters action', async () => {
    routerState.route.query = { categoryId, tag: 'Java' }
    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(searchPosts).toHaveBeenLastCalledWith({
      keyword: '',
      categoryId,
      tag: 'Java',
      page: 0,
      size: 10
    })

    const clear = wrapper.findAll('button').find((button) => button.text() === '清空筛选')
    expect(clear).toBeTruthy()
    await clear.trigger('click')
    await flushPromises()

    expect(routerState.replace).toHaveBeenLastCalledWith({ name: 'search', query: {} })
    expect(searchPosts).toHaveBeenLastCalledWith({
      keyword: '',
      categoryId: '',
      tag: '',
      page: 0,
      size: 10
    })
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
    const firstRun = wrapper.vm.submitSearch()

    await keywordInput.setValue('second')
    const secondRun = wrapper.vm.submitSearch()

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

  it('appends the next page through load more and retries the same page after a failure', async () => {
    routerState.route.query = { q: 'paging' }
    const firstPage = Array.from({ length: 10 }, (_, index) =>
      searchItem(`00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`, `First page ${index + 1}`)
    )
    const secondPage = [searchItem('22222222-2222-7222-8222-222222222222', 'Second page result')]
    searchPosts
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockRejectedValueOnce(new Error('temporary search failure'))
      .mockResolvedValueOnce({ data: secondPage, traceId: 'trace-page-1' })

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()
    const loadMore = () => wrapper.findAll('button').find((button) => button.text() === '加载更多')

    expect(loadMore()).toBeTruthy()
    await loadMore().trigger('click')
    await flushPromises()
    await flushPromises()
    expect(wrapper.text()).toContain('First page 1')
    expect(wrapper.text()).toContain('temporary search failure')

    await loadMore().trigger('click')
    await flushPromises()
    await flushPromises()

    expect(searchPosts.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.text()).toContain('First page 1')
    expect(wrapper.text()).toContain('Second page result')
    expect(wrapper.text()).not.toContain('temporary search failure')
  })

  it('shows a retryable error state when the first search load fails', async () => {
    routerState.route.query = { q: 'unstable' }
    searchPosts
      .mockRejectedValueOnce(new Error('search unavailable'))
      .mockResolvedValueOnce({
        data: [searchItem('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', 'Recovered result')],
        traceId: 'trace-retry'
      })

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(wrapper.text()).toContain('search unavailable')
    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    expect(retry).toBeTruthy()

    await retry.trigger('click')
    await flushPromises()
    await flushPromises()

    expect(searchPosts).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Recovered result')
    expect(wrapper.text()).not.toContain('search unavailable')
  })

  it('renders skeleton placeholders instead of a bare loading text on first load', async () => {
    routerState.route.query = { q: 'slow' }
    const pending = createDeferred()
    searchPosts.mockReturnValueOnce(pending.promise)

    const wrapper = mountView()
    await nextTick()

    expect(wrapper.findAllComponents(UiSkeleton).length).toBeGreaterThan(0)

    pending.resolve({ data: [], traceId: 'trace-slow' })
    await flushPromises()
    await flushPromises()
    expect(wrapper.findAllComponents(UiSkeleton)).toHaveLength(0)
    expect(wrapper.text()).toContain('暂无结果')
  })

  it('does not render the retired admin reindex action', () => {
    const wrapper = mountView({ admin: true })

    expect(wrapper.find('.search-reindex-btn').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('重建索引')
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})
