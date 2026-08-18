// @vitest-environment jsdom

import { defineComponent, h, nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { usePostMetaCacheStore } from '../stores/postMetaCache'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'

const routerState = vi.hoisted(() => ({
  route: {
    name: 'posts',
    path: '/posts',
    fullPath: '/posts',
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

vi.mock('../api/services/postService', () => ({
  listGlobalFeed: vi.fn().mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-v1' }, traceId: 'trace-feed' }),
  listBoardFeed: vi.fn().mockResolvedValue({ data: { items: [], nextCursor: '', rankVersion: 'rank-board-v1' }, traceId: 'trace-board-feed' }),
  createPost: vi.fn().mockResolvedValue({ data: { postId: 1 }, traceId: 'trace-create-post' })
}))

vi.mock('../api/services/taxonomyService', () => ({
  suggestTags: vi.fn().mockResolvedValue({ data: [] })
}))

import PostsView from './PostsView.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import PostBlockEditor from '../components/posts/PostBlockEditor.vue'
import FeedToolbar from '../components/posts/FeedToolbar.vue'
import { createPost, listBoardFeed, listGlobalFeed } from '../api/services/postService'
import { usePostsFeed } from './posts/usePostsFeed'

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
    window.localStorage.clear()
  })

  it('loads the new global feed contract by default', async () => {
    mountView()
    await flushPromises()

    expect(listGlobalFeed).toHaveBeenCalledWith({ cursor: '', size: 10 })
    expect(listBoardFeed).not.toHaveBeenCalled()
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

  it('passes the simplified feed toolbar contract to the posts shell', async () => {
    const wrapper = mount(PostsView, {
      global: {
        plugins: [createPinia()]
      }
    })
    await flushPromises()

    const toolbar = wrapper.getComponent(FeedToolbar)
    expect(toolbar.props()).toMatchObject({
      boardId: '',
      showClear: false
    })
    expect(toolbar.props()).not.toHaveProperty('order')
    expect(toolbar.props()).not.toHaveProperty('filter')
    expect(toolbar.props()).not.toHaveProperty('subscribed')
    expect(toolbar.props()).not.toHaveProperty('tag')
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

  it('adds committed tags and preserves the existing validation path', async () => {
    const wrapper = mountView()
    const tagInput = await openComposer(wrapper)

    await tagInput.vm.$emit('update:modelValue', 'Java')
    await nextTick()
    await tagInput.vm.$emit('commit', 'Java')
    await nextTick()

    expect(wrapper.findAll('.tag').map((node) => node.text())).toContain('#Java')
    expect(wrapper.find('.posts-composer-error').exists()).toBe(false)

    await tagInput.vm.$emit('update:modelValue', 'java!')
    await nextTick()
    await tagInput.vm.$emit('commit', 'java!')
    await nextTick()

    expect(wrapper.get('.posts-composer-error').text()).toContain('标签格式非法')
    expect(wrapper.findAll('.tag').map((node) => node.text())).toEqual(['#Java'])
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
    expect(wrapper.findAll('.tag').map((node) => node.text())).toContain('#Spring')
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
