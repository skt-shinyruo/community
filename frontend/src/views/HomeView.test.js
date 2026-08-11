// @vitest-environment jsdom

import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'

const { httpGet, showToast } = vi.hoisted(() => ({
  httpGet: vi.fn(),
  showToast: vi.fn()
}))

vi.mock('../api/http', () => ({
  default: { get: httpGet }
}))

vi.mock('../ui/toastService', () => ({
  showToast,
  showErrorToast: (_error, payload) => showToast(payload)
}))

import HomeView from './HomeView.vue'

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function countResponse(value) {
  return { data: { data: value } }
}

function queueCountLoad() {
  const unread = deferred()
  const following = deferred()
  const followers = deferred()
  httpGet
    .mockReturnValueOnce(unread.promise)
    .mockReturnValueOnce(following.promise)
    .mockReturnValueOnce(followers.promise)
  return { unread, following, followers }
}

function resolveCountLoad(load, { unread, following, followers }) {
  load.unread.resolve(countResponse(unread))
  load.following.resolve(countResponse(following))
  load.followers.resolve(countResponse(followers))
}

function mountView({ token = 'token-user-a', userId = 'user-a' } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: token,
    me: { userId, username: userId, authorities: [] }
  })

  return mount(HomeView, {
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        UiCard: { template: '<section><slot /></section>' },
        UiPageHeader: {
          template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>'
        },
        UiState: { template: '<section><slot /><slot name="actions" /></section>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('HomeView request lifecycle', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
  })

  it('discards an old account response and reloads all counts for the replacement identity', async () => {
    const oldLoad = queueCountLoad()
    const wrapper = mountView()
    await nextTick()

    const newLoad = queueCountLoad()
    useAuthStore().installSession({
      accessToken: 'token-user-b',
      me: { userId: 'user-b', username: 'user-b', authorities: [] }
    })
    await nextTick()

    expect(httpGet.mock.calls.slice(3).map(([url]) => url)).toEqual([
      '/api/notices/unread-count',
      '/api/follows/user-b/followees/count',
      '/api/follows/user-b/followers/count'
    ])
    expect(wrapper.vm.unreadCount).toBe(0)

    resolveCountLoad(newLoad, { unread: 22, following: 23, followers: 24 })
    await flushPromises()
    resolveCountLoad(oldLoad, { unread: 11, following: 12, followers: 13 })
    await flushPromises()

    expect(wrapper.vm.unreadCount).toBe(22)
    expect(wrapper.vm.followingCount).toBe(23)
    expect(wrapper.vm.followerCount).toBe(24)
    expect(showToast).not.toHaveBeenCalled()
  })

  it('invalidates in-flight counts when only the access token changes', async () => {
    const oldTokenLoad = queueCountLoad()
    const wrapper = mountView()
    await nextTick()

    const newTokenLoad = queueCountLoad()
    useAuthStore().installSession({
      accessToken: 'refreshed-token-user-a',
      me: { userId: 'user-a', username: 'user-a', authorities: [] }
    })
    await nextTick()

    resolveCountLoad(newTokenLoad, { unread: 31, following: 32, followers: 33 })
    await flushPromises()
    resolveCountLoad(oldTokenLoad, { unread: 1, following: 2, followers: 3 })
    await flushPromises()

    expect(wrapper.vm.unreadCount).toBe(31)
    expect(wrapper.vm.followingCount).toBe(32)
    expect(wrapper.vm.followerCount).toBe(33)
  })

  it('lets only the latest concurrent refresh commit data and finish loading', async () => {
    const initialLoad = queueCountLoad()
    const wrapper = mountView()
    resolveCountLoad(initialLoad, { unread: 1, following: 2, followers: 3 })
    await flushPromises()

    const olderRefresh = queueCountLoad()
    const olderPromise = wrapper.vm.refreshAll()
    const latestRefresh = queueCountLoad()
    const latestPromise = wrapper.vm.refreshAll()

    resolveCountLoad(olderRefresh, { unread: 11, following: 12, followers: 13 })
    await olderPromise
    await flushPromises()

    expect(wrapper.vm.loading).toBe(true)
    expect(wrapper.vm.unreadCount).toBe(1)
    expect(showToast).not.toHaveBeenCalled()

    resolveCountLoad(latestRefresh, { unread: 21, following: 22, followers: 23 })
    await latestPromise
    await flushPromises()

    expect(wrapper.vm.loading).toBe(false)
    expect(wrapper.vm.unreadCount).toBe(21)
    expect(wrapper.vm.followingCount).toBe(22)
    expect(wrapper.vm.followerCount).toBe(23)
    expect(showToast).toHaveBeenCalledTimes(1)
    expect(showToast).toHaveBeenCalledWith({ type: 'success', text: '已刷新开发检查项' })
  })

  it('commits successful count sections when one request fails', async () => {
    const load = queueCountLoad()
    const wrapper = mountView()
    load.unread.resolve(countResponse(7))
    load.following.reject(new Error('following unavailable'))
    load.followers.resolve(countResponse(9))
    await flushPromises()

    expect(wrapper.vm.unreadCount).toBe(7)
    expect(wrapper.vm.followerCount).toBe(9)
    expect(showToast).toHaveBeenCalledWith({ type: 'error', text: '部分开发检查项加载失败' })
  })

  it('does not commit data or show notifications after unmount', async () => {
    const pendingLoad = queueCountLoad()
    const wrapper = mountView()
    await nextTick()
    const viewModel = wrapper.vm

    wrapper.unmount()
    resolveCountLoad(pendingLoad, { unread: 41, following: 42, followers: 43 })
    await flushPromises()

    expect(viewModel.unreadCount).toBe(0)
    expect(viewModel.followingCount).toBe(0)
    expect(viewModel.followerCount).toBe(0)
    expect(showToast).not.toHaveBeenCalled()
  })
})
