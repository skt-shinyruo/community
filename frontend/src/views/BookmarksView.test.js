// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'
import { useSocialPrefsStore } from '../stores/socialPrefs'
import { useTaxonomyStore } from '../stores/taxonomy'

const { listBookmarks } = vi.hoisted(() => ({ listBookmarks: vi.fn() }))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn() })
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
        RouterLink: { template: '<a><slot /></a>' },
        UiBadge: true,
        UiBreadcrumb: true,
        UiCard: { template: '<section><slot /></section>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiButton: {
          props: ['disabled', 'variant'],
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
    expect(wrapper.vm.page).toBe(0)
    expect(wrapper.vm.items).toHaveLength(10)
    expect(wrapper.text()).toContain('temporary bookmark failure')

    await wrapper.vm.loadMore()
    expect(listBookmarks.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.vm.page).toBe(1)
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
