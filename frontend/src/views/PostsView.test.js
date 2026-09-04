// @vitest-environment jsdom

import { defineComponent, h, nextTick, reactive } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'

const routerState = vi.hoisted(() => ({
  route: null,
  replace: vi.fn(),
  push: vi.fn()
}))

routerState.route = reactive({
  name: 'posts',
  path: '/posts',
  fullPath: '/posts',
  query: {}
})

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

vi.mock('../api/services/postService', () => ({
  listGlobalFeed: vi.fn().mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-v1' }, traceId: 'trace-feed' }),
  listBoardFeed: vi.fn().mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-board-v1' }, traceId: 'trace-board-feed' }),
  createPost: vi.fn().mockResolvedValue({ data: { postId: 1 }, traceId: 'trace-create-post' }),
  batchPostSummaries: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-batch-summary' })
}))

vi.mock('../api/services/searchService', () => ({
  searchPosts: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-search-posts' })
}))

vi.mock('../api/services/taxonomyService', () => ({
  suggestTags: vi.fn().mockResolvedValue({ data: [] })
}))

import PostsView from './PostsView.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import PostBlockEditor from '../components/posts/PostBlockEditor.vue'
import FeedToolbar from '../components/posts/FeedToolbar.vue'
import { batchPostSummaries, createPost, listBoardFeed, listGlobalFeed } from '../api/services/postService'
import { searchPosts } from '../api/services/searchService'
import { usePostsFeed } from './posts/usePostsFeed'

const ORDER_TABS = [
  { value: 'latest', label: '最新' },
  { value: 'hot', label: '最热' }
]

describe('PostsView', () => {
  function deferred() {
    let resolve
    let reject
    const promise = new Promise((res, rej) => {
      resolve = res
      reject = rej
    })
    return { promise, resolve, reject }
  }

  function createPostsPinia() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.installSession({ accessToken: 'token' })
    auth.setMe({ userId: 7, username: 'aaa', headerUrl: '', authorities: [] })

    const taxonomy = useTaxonomyStore()
    taxonomy.categories = [{ id: 1, name: '技术' }]
    taxonomy.hotTags = [{ id: 1, name: 'Java' }, { id: 2, name: 'Spring' }]
    taxonomy.ensureCategories = vi.fn()
    taxonomy.ensureHotTags = vi.fn()

    const socialPrefs = useSocialPrefsStore()
    socialPrefs.ensureBlocked = vi.fn().mockResolvedValue()
    socialPrefs.clear = vi.fn()

    const postMetaCache = usePostMetaCacheStore()
    postMetaCache.ensureUserSummaries = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeCounts = vi.fn().mockResolvedValue({})
    postMetaCache.ensureLikeStatuses = vi.fn().mockResolvedValue({})
    postMetaCache.clearLikeStatuses = vi.fn()

    return pinia
  }

  function mountView() {
    const pinia = createPostsPinia()

    return mount(PostsView, {
      global: {
        plugins: [pinia],
        stubs: {
          FeedToolbar: true
        }
      }
    })
  }

  async function openComposer(wrapper) {
    await flushPromises()
    await wrapper.get('.posts-feed-compose-strip').trigger('click')
    await nextTick()
    return wrapper.getComponent(UiAutosuggestInput)
  }

  beforeEach(() => {
    routerState.route.query = {}
    routerState.replace.mockClear()
    routerState.push.mockClear()
    listGlobalFeed.mockClear()
    listGlobalFeed.mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-v1' }, traceId: 'trace-feed' })
    listBoardFeed.mockClear()
    listBoardFeed.mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-board-v1' }, traceId: 'trace-board-feed' })
    createPost.mockClear()
    createPost.mockResolvedValue({ data: { postId: 1 }, traceId: 'trace-create-post' })
    batchPostSummaries.mockClear()
    batchPostSummaries.mockResolvedValue({ data: [], traceId: 'trace-batch-summary' })
    searchPosts.mockClear()
    searchPosts.mockResolvedValue({ data: [], traceId: 'trace-search-posts' })
    window.localStorage.clear()
  })

  it('loads the new global feed contract by default', async () => {
    mountView()
    await flushPromises()

    expect(listGlobalFeed).toHaveBeenCalledWith({ cursor: '', size: 10 })
    expect(listBoardFeed).not.toHaveBeenCalled()
    expect(searchPosts).not.toHaveBeenCalled()
  })

  it('advances through consecutive cursors and stops at the final page', async () => {
    listGlobalFeed
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-1', title: 'first batch' }], nextCursor: 'cursor-2' }
      })
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-2', title: 'second batch' }], nextCursor: 'cursor-3' }
      })
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-3', title: 'final batch' }], nextCursor: '' }
      })

    const wrapper = mountView()
    await flushPromises()
    await wrapper.vm.loadMore()
    await wrapper.vm.loadMore()

    expect(listGlobalFeed.mock.calls.map(([request]) => request.cursor)).toEqual([
      '',
      'cursor-2',
      'cursor-3'
    ])
    expect(wrapper.text()).toContain('first batch')
    expect(wrapper.text()).toContain('second batch')
    expect(wrapper.text()).toContain('final batch')
    expect(wrapper.find('.posts-load-more-btn').exists()).toBe(false)

    await wrapper.vm.loadMore()
    expect(listGlobalFeed).toHaveBeenCalledTimes(3)
  })

  it('exposes page intent groups without feed implementation details', async () => {
    const pinia = createPostsPinia()
    let postsFeed
    const Harness = defineComponent({
      setup() {
        postsFeed = usePostsFeed(vi.fn())
        return () => h('div')
      }
    })
    const wrapper = mount(Harness, { global: { plugins: [pinia] } })
    await flushPromises()

    expect(Object.keys(postsFeed)).toEqual(['session', 'scope', 'feed', 'unread', 'composer'])
    expect(postsFeed.feed).toMatchObject({
      reload: expect.any(Function),
      loadMore: expect.any(Function),
      openUserProfile: expect.any(Function)
    })
    expect(postsFeed.feed).not.toHaveProperty('load')
    expect(postsFeed.feed).not.toHaveProperty('page')
    expect(postsFeed.scope).not.toHaveProperty('taxonomy')
    expect(postsFeed.scope).not.toHaveProperty('boardId')
    expect(postsFeed.session).not.toHaveProperty('auth')

    wrapper.unmount()
  })

  it('keeps the current feed cursor when refresh fails and retries load-more from it', async () => {
    listGlobalFeed
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-1', title: 'current page' }], nextCursor: 'cursor-2' },
        traceId: 'trace-page-1'
      })
      .mockRejectedValueOnce(new Error('temporary feed refresh failure'))
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-2', title: 'next page' }], nextCursor: '' },
        traceId: 'trace-page-2'
      })

    const wrapper = mountView()
    await flushPromises()
    await wrapper.vm.reload()

    expect(wrapper.text()).toContain('current page')
    expect(wrapper.text()).toContain('temporary feed refresh failure')

    await wrapper.vm.loadMore()

    expect(listGlobalFeed.mock.calls.map(([request]) => request.cursor)).toEqual(['', '', 'cursor-2'])
    expect(wrapper.text()).toContain('current page')
    expect(wrapper.text()).toContain('next page')
  })

  it('keeps latest-feed state after the first opaque cursor is issued', async () => {
    window.localStorage.setItem('community.read.posts.v1.7', JSON.stringify({ lastSeenAt: 1, items: {} }))
    listGlobalFeed.mockResolvedValueOnce({
      data: {
        items: [{ id: 'post-1', title: 'current page', createTime: new Date(2000).toISOString() }],
        nextCursor: 'opaque-cursor'
      },
      traceId: 'trace-page-1'
    })

    const wrapper = mountView()
    await flushPromises()

    expect(listGlobalFeed).toHaveBeenCalledWith({ cursor: '', size: 10 })
    expect(wrapper.vm.newSinceLastSeenCount).toBe(1)
  })

  it('does not let a stale load-more completion restore an obsolete cursor', async () => {
    let resolveStalePage
    listGlobalFeed
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-old', title: 'old page' }], nextCursor: 'cursor-old' },
        traceId: 'trace-old-page'
      })
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveStalePage = resolve
      }))
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-current', title: 'current page' }], nextCursor: 'cursor-current' },
        traceId: 'trace-current-page'
      })
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-next', title: 'current next page' }], nextCursor: '' },
        traceId: 'trace-current-next-page'
      })

    const wrapper = mountView()
    await flushPromises()
    const staleLoad = wrapper.vm.loadMore()
    while (!resolveStalePage) await Promise.resolve()

    await wrapper.vm.reload()
    resolveStalePage({
      data: { items: [{ id: 'post-stale', title: 'stale page' }], nextCursor: 'cursor-stale' },
      traceId: 'trace-stale-page'
    })
    await staleLoad
    await wrapper.vm.loadMore()

    expect(listGlobalFeed.mock.calls.map(([request]) => request.cursor)).toEqual([
      '',
      'cursor-old',
      '',
      'cursor-current'
    ])
    expect(wrapper.text()).toContain('current page')
    expect(wrapper.text()).toContain('current next page')
    expect(wrapper.text()).not.toContain('stale page')
  })

  it('does not commit the previous account feed after the session changes', async () => {
    const oldFeed = deferred()
    listGlobalFeed
      .mockReturnValueOnce(oldFeed.promise)
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-b', title: 'account B feed' }], nextCursor: '' },
        traceId: 'trace-b'
      })

    const wrapper = mountView()
    await vi.waitFor(() => expect(listGlobalFeed).toHaveBeenCalledTimes(1))

    useAuthStore().installSession({
      accessToken: 'token-b',
      me: { userId: 8, username: 'bbb', headerUrl: '', authorities: [] }
    })
    await vi.waitFor(() => expect(listGlobalFeed).toHaveBeenCalledTimes(2))
    await flushPromises()

    expect(wrapper.text()).toContain('account B feed')

    oldFeed.resolve({
      data: { items: [{ id: 'post-a', title: 'account A feed' }], nextCursor: '' },
      traceId: 'trace-a'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('account B feed')
    expect(wrapper.text()).not.toContain('account A feed')
  })

  it('clears the previous account composer draft when the session changes', async () => {
    const wrapper = mountView()
    await openComposer(wrapper)
    await wrapper.get('input[name="post-title"]').setValue('private draft from A')
    await wrapper.get('[data-test="block-text-0"]').setValue('private body from A')

    useAuthStore().installSession({
      accessToken: 'token-b',
      me: { userId: 8, username: 'bbb', headerUrl: '', authorities: [] }
    })
    await flushPromises()

    expect(wrapper.find('.posts-composer').exists()).toBe(false)
    await wrapper.get('.posts-feed-compose-strip').trigger('click')
    await nextTick()
    expect(wrapper.get('input[name="post-title"]').element.value).toBe('')
    expect(wrapper.get('[data-test="block-text-0"]').element.value).toBe('')
  })

  it('passes the unified query contract to the toolbar and order tabs', async () => {
    const wrapper = mount(PostsView, {
      global: {
        plugins: [createPinia()]
      }
    })
    await flushPromises()

    const toolbar = wrapper.getComponent(FeedToolbar)
    expect(toolbar.props()).toMatchObject({
      categoryId: '',
      tag: '',
      showClear: false
    })
    expect(toolbar.props()).not.toHaveProperty('boardId')
    expect(toolbar.props()).not.toHaveProperty('order')

    const tabs = wrapper.getComponent(UiTabs)
    expect(tabs.props('modelValue')).toBe('latest')
    expect(tabs.props('tabs')).toEqual(ORDER_TABS)
  })

  it('switches the order tab and writes order=hot into the route query', async () => {
    const wrapper = mountView()
    await flushPromises()

    const tabs = wrapper.getComponent(UiTabs)
    const tabButtons = tabs.findAll('[role="tab"]')
    expect(tabButtons.map((tab) => tab.text())).toEqual(['最新', '最热'])
    expect(tabButtons[0].attributes('aria-selected')).toBe('true')

    await tabButtons[1].trigger('click')

    expect(routerState.replace).toHaveBeenCalledWith({ name: 'posts', query: { order: 'hot' } })

    // 路由落地后按新查询重新加载，tabs 反映受控选中态
    listGlobalFeed.mockClear()
    routerState.route.query = { order: 'hot' }
    await flushPromises()

    expect(listGlobalFeed).toHaveBeenCalledTimes(1)
    expect(tabs.findAll('[role="tab"]')[1].attributes('aria-selected')).toBe('true')
  })

  it('normalizes a legacy boardId link into the categoryId query contract', async () => {
    routerState.route.query = { boardId: 'board-legacy-1' }

    mountView()
    await flushPromises()

    expect(routerState.replace).toHaveBeenCalledWith({ name: 'posts', query: { categoryId: 'board-legacy-1' } })
    expect(listBoardFeed).toHaveBeenCalledWith('board-legacy-1', { cursor: '', size: 10 })
  })

  it('applies the toolbar category filter into the route query and board feed', async () => {
    const wrapper = mountView()
    await flushPromises()

    wrapper.getComponent(FeedToolbar).vm.$emit('update:categoryId', '1')

    expect(routerState.replace).toHaveBeenCalledWith({ name: 'posts', query: { categoryId: '1' } })

    routerState.route.query = { categoryId: '1' }
    await flushPromises()

    expect(listBoardFeed).toHaveBeenCalledWith('1', { cursor: '', size: 10 })
    expect(listGlobalFeed).toHaveBeenCalledTimes(1)
  })

  it('filters by a clicked card tag through the search stack and clears it again', async () => {
    listGlobalFeed.mockResolvedValueOnce({
      data: {
        items: [{ id: 'post-1', title: 'tagged discussion', tags: ['Java'] }],
        nextCursor: ''
      }
    })
    searchPosts.mockResolvedValue({
      data: [{ postId: 'post-1', userId: 'user-1', title: 'tagged discussion', tags: ['Java'] }],
      traceId: 'trace-search-posts'
    })
    batchPostSummaries.mockResolvedValue({
      data: [{ id: 'post-1', userId: 'user-1', title: 'tagged discussion', tags: ['Java'], commentCount: 4 }],
      traceId: 'trace-batch-summary'
    })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('.posts-card-tag').trigger('click')
    expect(routerState.replace).toHaveBeenCalledWith({ name: 'posts', query: { tag: 'Java' } })

    routerState.route.query = { tag: 'Java' }
    await flushPromises()

    expect(searchPosts).toHaveBeenCalledWith(expect.objectContaining({ tag: 'Java', page: 0, size: 10 }))
    expect(searchPosts).toHaveBeenCalledTimes(1)
    expect(listGlobalFeed).toHaveBeenCalledTimes(1)
    expect(batchPostSummaries).toHaveBeenCalledWith(['post-1'])
    expect(wrapper.text()).toContain('4 回复')
    expect(routerState.push).not.toHaveBeenCalled()

    // 清除 tag chip 后回到 feed 栈
    wrapper.getComponent(FeedToolbar).vm.$emit('clearTag')
    expect(routerState.replace).toHaveBeenCalledWith({ name: 'posts', query: {} })

    routerState.route.query = {}
    await flushPromises()
    expect(listGlobalFeed).toHaveBeenCalledTimes(2)
  })

  it('appends search pages through load-more on the tag view', async () => {
    routerState.route.query = { tag: 'Java' }
    const firstPage = Array.from({ length: 10 }, (_, index) => ({
      postId: `post-${index}`,
      userId: 'user-1',
      title: `hit ${index}`,
      tags: ['Java']
    }))
    searchPosts
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockResolvedValueOnce({
        data: [{ postId: 'post-10', userId: 'user-1', title: 'hit 10', tags: ['Java'] }],
        traceId: 'trace-page-1'
      })

    const wrapper = mountView()
    await flushPromises()

    expect(searchPosts).toHaveBeenCalledWith(expect.objectContaining({ tag: 'Java', page: 0, size: 10 }))
    expect(wrapper.text()).toContain('hit 0')
    expect(wrapper.find('.posts-load-more-btn').exists()).toBe(true)

    await wrapper.vm.loadMore()

    expect(searchPosts).toHaveBeenCalledWith(expect.objectContaining({ tag: 'Java', page: 1, size: 10 }))
    expect(wrapper.text()).toContain('hit 10')
    expect(wrapper.find('.posts-load-more-btn').exists()).toBe(false)
  })

  it('keeps unread locating affordances off the filtered views', async () => {
    window.localStorage.setItem('community.read.posts.v1.7', JSON.stringify({ lastSeenAt: 1, items: {} }))
    routerState.route.query = { order: 'hot' }
    listGlobalFeed.mockResolvedValueOnce({
      data: {
        items: [{ id: 'post-1', title: 'hot page', createTime: new Date(2000).toISOString() }],
        nextCursor: ''
      },
      traceId: 'trace-page-1'
    })

    const wrapper = mountView()
    await flushPromises()

    // 新增计数、上次位置分隔线和提示条只属于默认最新视图
    expect(wrapper.vm.newSinceLastSeenCount).toBe(0)
    expect(wrapper.find('.posts-last-seen-divider').exists()).toBe(false)
    expect(wrapper.find('.posts-new-hint').exists()).toBe(false)
  })

  it('shows card skeletons during the first load', async () => {
    const pending = deferred()
    listGlobalFeed.mockReturnValueOnce(pending.promise)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('.ui-skeleton--card')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('当前视图暂无讨论')

    pending.resolve({ data: { items: [], nextCursor: '' }, traceId: 'trace-feed' })
    await flushPromises()

    expect(wrapper.findAll('.ui-skeleton--card')).toHaveLength(0)
    expect(wrapper.text()).toContain('当前视图暂无讨论')
  })

  it('renders the error state with a retry action and recovers', async () => {
    listGlobalFeed
      .mockRejectedValueOnce(new Error('feed unavailable'))
      .mockResolvedValueOnce({
        data: { items: [{ id: 'post-1', title: 'recovered page' }], nextCursor: '' },
        traceId: 'trace-recovered'
      })

    const wrapper = mountView()
    await flushPromises()

    const errorState = wrapper.get('.ui-state--error')
    expect(errorState.text()).toContain('feed unavailable')

    await errorState.get('button').trigger('click')
    await flushPromises()

    expect(listGlobalFeed).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('recovered page')
    expect(wrapper.find('.ui-state--error').exists()).toBe(false)
  })

  it('opens a post from card click and from Enter on the card itself', async () => {
    listGlobalFeed.mockResolvedValueOnce({
      data: { items: [{ id: 'post-1', title: 'keyboard open' }], nextCursor: '' }
    })

    const wrapper = mountView()
    await flushPromises()

    const card = wrapper.get('.posts-card')
    await card.trigger('keydown.enter')
    expect(routerState.push).toHaveBeenCalledWith({ name: 'postDetail', params: { postId: 'post-1' } })

    routerState.push.mockClear()
    await card.trigger('click')
    expect(routerState.push).toHaveBeenCalledWith({ name: 'postDetail', params: { postId: 'post-1' } })
  })

  it('positions the discussion feed before secondary explanation copy', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.posts-workspace').exists()).toBe(true)
    expect(wrapper.find('.posts-main-feed').exists()).toBe(true)
    expect(wrapper.find('.posts-context-panel').exists()).toBe(true)
    expect(wrapper.text()).toContain('社区讨论')
    expect(wrapper.text()).toContain('开始一个讨论')
    expect(wrapper.text()).not.toContain('发帖入口保留在顶部，不把整个首屏变成编辑器')
  })

  it('uses the shared autosuggest input in the composer tag field', async () => {
    const wrapper = mountView()
    const tagInput = await openComposer(wrapper)

    expect(tagInput.exists()).toBe(true)
  })

  it('associates the composer title field label with the input', async () => {
    const wrapper = mountView()
    await openComposer(wrapper)

    const titleInput = wrapper.get('input[name="post-title"]')
    const label = wrapper.get(`label[for="${titleInput.attributes('id')}"]`)
    expect(label.text()).toContain('标题')
  })

  it('adds committed tags and preserves the existing validation path', async () => {
    const wrapper = mountView()
    const tagInput = await openComposer(wrapper)

    await tagInput.vm.$emit('update:modelValue', 'Java')
    await nextTick()
    await tagInput.vm.$emit('commit', 'Java')
    await nextTick()

    expect(wrapper.findAll('.posts-composer-tag').map((node) => node.text())).toContain('#Java')
    expect(wrapper.find('.ui-field-error').exists()).toBe(false)

    await tagInput.vm.$emit('update:modelValue', 'java!')
    await nextTick()
    await tagInput.vm.$emit('commit', 'java!')
    await nextTick()

    expect(wrapper.get('.ui-field-error').text()).toContain('标签格式非法')
    expect(wrapper.findAll('.posts-composer-tag').map((node) => node.text())).toEqual(['#Java'])
  })

  it('preserves comma-delimiter tag insertion through the shared autosuggest input', async () => {
    const wrapper = mountView()
    const tagInput = await openComposer(wrapper)
    const preventDefault = vi.fn()

    await tagInput.vm.$emit('update:modelValue', 'Spring')
    await nextTick()
    await tagInput.vm.$emit('keydown', { key: ',', preventDefault })
    await nextTick()

    expect(preventDefault).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.posts-composer-tag').map((node) => node.text())).toContain('#Spring')
  })

  it('publishes block payload from composer', async () => {
    const wrapper = mountView()
    await openComposer(wrapper)
    await wrapper.get('input[name="post-title"]').setValue('hello')
    await wrapper.get('[data-test="block-text-0"]').setValue('body')
    await wrapper.get('.posts-composer-submit').trigger('click')
    await flushPromises()

    expect(createPost).toHaveBeenCalledWith(expect.objectContaining({
      title: 'hello',
      blocks: [expect.objectContaining({ type: 'paragraph', text: 'body' })]
    }), expect.objectContaining({ writeAttempt: expect.any(Object) }))
  })

  it('does not let an old publish response clear a newer composer intent', async () => {
    const pendingCreate = deferred()
    createPost.mockReturnValueOnce(pendingCreate.promise)
    const wrapper = mountView()
    await openComposer(wrapper)
    const titleInput = wrapper.get('input[name="post-title"]')
    await titleInput.setValue('first title')
    await wrapper.get('[data-test="block-text-0"]').setValue('body')
    await wrapper.get('.posts-composer-submit').trigger('click')
    await vi.waitFor(() => expect(createPost).toHaveBeenCalledTimes(1))

    expect(titleInput.attributes('disabled')).toBeDefined()
    titleInput.element.disabled = false
    await titleInput.setValue('new draft title')
    pendingCreate.resolve({ data: { postId: 'old-post-id' }, traceId: 'trace-old-create' })
    await flushPromises()

    expect(wrapper.get('input[name="post-title"]').element.value).toBe('new draft title')
    expect(wrapper.find('.posts-composer').exists()).toBe(true)
    expect(routerState.push).not.toHaveBeenCalled()
  })

  it.each([
    ['pending', '媒体仍在上传，请等待上传完成后再发布'],
    ['uploading', '媒体仍在上传，请等待上传完成后再发布'],
    ['failed', '媒体上传失败，请重试或移除后再发布']
  ])('blocks publish while a media block is %s', async (uploadState, message) => {
    const wrapper = mountView()
    await openComposer(wrapper)
    await wrapper.get('input[name="post-title"]').setValue('hello')
    await wrapper.getComponent(PostBlockEditor).vm.$emit('update:modelValue', [
      { type: 'paragraph', text: 'body', clientId: 'local-text' },
      { type: 'image', assetId: '', caption: 'caption', uploadState, clientId: 'local-image' }
    ])
    await nextTick()

    await wrapper.get('.posts-composer-submit').trigger('click')
    await flushPromises()

    expect(createPost).not.toHaveBeenCalled()
    expect(wrapper.get('.posts-composer-submit-error').text()).toContain(message)
  })

  it('resets blocked media draft state when the composer is closed and reopened', async () => {
    const wrapper = mountView()
    await openComposer(wrapper)
    await wrapper.get('input[name="post-title"]').setValue('hello')
    await wrapper.getComponent(PostBlockEditor).vm.$emit('update:modelValue', [
      { type: 'paragraph', text: 'body', clientId: 'local-text' },
      { type: 'image', assetId: '', caption: 'caption', uploadState: 'uploading', clientId: 'local-image' }
    ])
    await nextTick()

    await wrapper.get('.posts-composer-close').trigger('click')
    await nextTick()
    await wrapper.get('.posts-feed-compose-strip').trigger('click')
    await nextTick()

    expect(wrapper.get('input[name="post-title"]').element.value).toBe('')
    expect(wrapper.find('.posts-composer-submit-error').text()).toBe('')
    expect(wrapper.getComponent(PostBlockEditor).props('modelValue')).toEqual([{ type: 'paragraph', text: '' }])

    await wrapper.get('input[name="post-title"]').setValue('clean')
    await wrapper.get('[data-test="block-text-0"]').setValue('body')
    await wrapper.get('.posts-composer-submit').trigger('click')
    await flushPromises()

    expect(createPost).toHaveBeenCalledTimes(1)
    expect(createPost).toHaveBeenCalledWith(expect.objectContaining({
      title: 'clean',
      blocks: [expect.objectContaining({ type: 'paragraph', text: 'body' })]
    }), expect.objectContaining({ writeAttempt: expect.any(Object) }))
  })

  it('strips client-only block fields from create payload', async () => {
    const wrapper = mountView()
    await openComposer(wrapper)
    await wrapper.get('input[name="post-title"]').setValue('hello')
    await wrapper.getComponent(PostBlockEditor).vm.$emit('update:modelValue', [
      {
        type: 'image',
        assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        caption: 'caption',
        uploadState: 'completed',
        clientId: 'local-image',
        selectedFile: new File(['image'], 'demo.png', { type: 'image/png' }),
        previewUrl: 'blob:http://localhost/demo',
        uploadError: 'old error',
        error: 'old error'
      }
    ])
    await nextTick()

    await wrapper.get('.posts-composer-submit').trigger('click')
    await flushPromises()

    expect(createPost).toHaveBeenCalledTimes(1)
    expect(createPost).toHaveBeenCalledWith(expect.objectContaining({
      title: 'hello',
      blocks: [
        {
          type: 'image',
          assetId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          caption: 'caption'
        }
      ]
    }), expect.objectContaining({ writeAttempt: expect.any(Object) }))
  })
})
