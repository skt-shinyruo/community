// @vitest-environment jsdom

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

vi.mock('../api/services/bookmarkService', () => ({ listBookmarks }))

import BookmarksView from './BookmarksView.vue'

function bookmark(index) {
  return {
    id: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
    userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
    title: `bookmark-${index + 1}`,
    createTime: '2026-08-01T00:00:00Z'
  }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'access-token',
    me: { userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb' }
  })
  useTaxonomyStore().ensureCategories = vi.fn().mockResolvedValue()
  useSocialPrefsStore().ensureBlocked = vi.fn().mockResolvedValue()

  return mount(BookmarksView, {
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: { props: ['to'], template: '<a><slot /></a>' },
        UiBadge: true,
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiSkeleton: { template: '<div class="ui-skeleton-stub" />' },
        UiState: {
          props: ['variant', 'title'],
          template: '<div class="ui-state-stub"><span v-if="title">{{ title }}</span><slot /><slot name="description" /><slot name="actions" /></div>'
        },
        UiButton: {
          props: ['disabled', 'variant', 'to'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('BookmarksView pagination', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('keeps bookmarks and retries the same page after load-more fails', async () => {
    const firstPage = Array.from({ length: 10 }, (_, index) => bookmark(index))
    listBookmarks
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockRejectedValueOnce(new Error('temporary bookmark failure'))
      .mockResolvedValueOnce({ data: [bookmark(10)], traceId: 'trace-page-1' })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.vm.loadMore()
    expect(listBookmarks.mock.calls.map(([request]) => request.page)).toEqual([0, 1])
    expect(wrapper.vm.items).toHaveLength(10)
    expect(wrapper.text()).toContain('temporary bookmark failure')

    await wrapper.vm.loadMore()
    expect(listBookmarks.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.vm.items).toHaveLength(11)
  })

  it('discards a previous account response after the session changes', async () => {
    const previousAccountRequest = deferred()
    const currentAccountRequest = deferred()
    listBookmarks
      .mockImplementationOnce(() => previousAccountRequest.promise)
      .mockImplementationOnce(() => currentAccountRequest.promise)

    const wrapper = mountView()
    await flushPromises()

    useAuthStore().installSession({
      accessToken: 'replacement-token',
      me: { userId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc' }
    })
    await flushPromises()
    expect(listBookmarks).toHaveBeenCalledTimes(2)

    currentAccountRequest.resolve({ data: [{ ...bookmark(1), title: 'current account' }] })
    await flushPromises()
    expect(wrapper.text()).toContain('current account')

    previousAccountRequest.resolve({ data: [{ ...bookmark(0), title: 'previous account' }] })
    await flushPromises()
    expect(wrapper.text()).toContain('current account')
    expect(wrapper.text()).not.toContain('previous account')
  })
})

describe('BookmarksView result states', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows a skeleton during the first load instead of bare loading text', async () => {
    const pending = deferred()
    listBookmarks.mockImplementationOnce(() => pending.promise)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('.ui-skeleton-stub')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('正在加载收藏内容')

    pending.resolve({ data: [bookmark(0)] })
    await flushPromises()
    expect(wrapper.findAll('.ui-skeleton-stub')).toHaveLength(0)
    expect(wrapper.text()).toContain('bookmark-1')
  })

  it('offers a retry action when the first load fails', async () => {
    listBookmarks
      .mockRejectedValueOnce(new Error('bookmark service down'))
      .mockResolvedValueOnce({ data: [bookmark(0)] })

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('bookmark service down')
    expect(wrapper.text()).not.toContain('bookmark-1')

    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    expect(retry).toBeTruthy()
    await retry.trigger('click')
    await flushPromises()

    expect(listBookmarks).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('bookmark-1')
    expect(wrapper.text()).not.toContain('bookmark service down')
  })

  it('keeps the loaded list visible when a reload fails afterwards', async () => {
    listBookmarks
      .mockResolvedValueOnce({ data: [bookmark(0)] })
      .mockRejectedValueOnce(new Error('refresh failed'))

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('bookmark-1')

    await wrapper.vm.reload()
    await flushPromises()

    expect(wrapper.text()).toContain('refresh failed')
    expect(wrapper.text()).toContain('bookmark-1')
  })

  it('shows the empty state with a primary next step when nothing is bookmarked', async () => {
    listBookmarks.mockResolvedValueOnce({ data: [] })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无收藏')
    const next = wrapper.findAll('button').find((button) => button.text() === '浏览帖子')
    expect(next).toBeTruthy()
    expect(wrapper.find('.bookmarks-load-more').exists()).toBe(false)
  })
})

describe('BookmarksView card interaction', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens the post from card click and keyboard Enter', async () => {
    const entry = {
      ...bookmark(0),
      categoryId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      tags: ['Java']
    }
    listBookmarks.mockResolvedValueOnce({ data: [entry] })

    const wrapper = mountView()
    await flushPromises()

    const card = wrapper.get('.bookmark-card')
    await card.trigger('click')
    expect(routerPush).toHaveBeenCalledTimes(1)
    expect(routerPush).toHaveBeenLastCalledWith({ name: 'postDetail', params: { postId: entry.id } })

    await card.trigger('keydown.enter')
    expect(routerPush).toHaveBeenCalledTimes(2)
    expect(routerPush).toHaveBeenLastCalledWith({ name: 'postDetail', params: { postId: entry.id } })
  })

  it('does not open the post when Enter or click comes from a nested taxonomy link', async () => {
    const entry = {
      ...bookmark(0),
      categoryId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      tags: ['Java']
    }
    listBookmarks.mockResolvedValueOnce({ data: [entry] })

    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('.bookmark-card-tag').trigger('keydown.enter')
    expect(routerPush).not.toHaveBeenCalled()

    await wrapper.get('.bookmark-card-tag').trigger('click')
    expect(routerPush).not.toHaveBeenCalled()

    await wrapper.get('.bookmark-card-category').trigger('keydown.enter')
    await wrapper.get('.bookmark-card-category').trigger('click')
    expect(routerPush).not.toHaveBeenCalled()
  })
})
