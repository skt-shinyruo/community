// @vitest-environment jsdom

import { nextTick } from 'vue'
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
  listPosts: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-posts' }),
  createPost: vi.fn().mockResolvedValue({ data: { postId: 1 }, traceId: 'trace-create-post' })
}))

vi.mock('../api/services/taxonomyService', () => ({
  suggestTags: vi.fn().mockResolvedValue({ data: [] })
}))

import PostsView from './PostsView.vue'
import UiAutosuggestInput from '../components/ui/UiAutosuggestInput.vue'
import { listPosts } from '../api/services/postService'

describe('PostsView', () => {
  function mountView() {
    const pinia = createPinia()
    setActivePinia(pinia)

    const auth = useAuthStore()
    auth.setAccessToken('token')
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
    listPosts.mockClear()
    listPosts.mockResolvedValue({ data: [], traceId: 'trace-posts' })
    window.localStorage.clear()
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
})
