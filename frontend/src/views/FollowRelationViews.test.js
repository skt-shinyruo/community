// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'

const {
  batchUserSummary,
  followUser,
  getFollowStatuses,
  listFollowees,
  listFollowers,
  unfollowUser
} = vi.hoisted(() => ({
  batchUserSummary: vi.fn(),
  followUser: vi.fn(),
  getFollowStatuses: vi.fn(),
  listFollowees: vi.fn(),
  listFollowers: vi.fn(),
  unfollowUser: vi.fn()
}))

vi.mock('../api/services/userService', () => ({ batchUserSummary }))
vi.mock('../api/services/socialService', () => ({
  followUser,
  unfollowUser,
  getFollowStatuses,
  listFollowees,
  listFollowers
}))

import FolloweesView from './FolloweesView.vue'
import FollowersView from './FollowersView.vue'

const VIEWER_ID = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
const PROFILE_ID = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
const NEXT_PROFILE_ID = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'

function relation(index) {
  return {
    targetId: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
    followTime: '2026-08-01T00:00:00Z'
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

function mountView(component) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'access-token',
    me: { userId: VIEWER_ID, username: 'viewer' }
  })

  return mount(component, {
    props: { userId: PROFILE_ID },
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        UiAvatar: true,
        UiBreadcrumb: true,
        UiCard: { template: '<section><slot /></section>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiPagination: true
      }
    }
  })
}

describe('follow relation pagination', () => {
  beforeEach(() => {
    batchUserSummary.mockReset()
    followUser.mockReset()
    getFollowStatuses.mockReset()
    listFollowees.mockReset()
    listFollowers.mockReset()
    unfollowUser.mockReset()
    batchUserSummary.mockImplementation(async (ids) => ({
      data: ids.map((id) => ({ id, username: `user-${id.slice(-2)}` }))
    }))
    getFollowStatuses.mockResolvedValue({ data: {} })
    followUser.mockResolvedValue({ traceId: 'trace-follow' })
    unfollowUser.mockResolvedValue({ traceId: 'trace-unfollow' })
  })

  it.each([
    ['followees', FolloweesView, listFollowees],
    ['followers', FollowersView, listFollowers]
  ])('keeps %s page data and retries the same page after failure', async (_label, component, listRelations) => {
    const firstPage = Array.from({ length: 10 }, (_, index) => relation(index))
    const secondPage = [relation(10)]
    listRelations
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockRejectedValueOnce(new Error('temporary relation failure'))
      .mockResolvedValueOnce({ data: secondPage, traceId: 'trace-page-1' })

    const wrapper = mountView(component)
    await flushPromises()

    await wrapper.vm.nextPage()
    expect(wrapper.vm.page).toBe(0)
    expect(wrapper.vm.items).toHaveLength(10)
    expect(wrapper.text()).toContain('temporary relation failure')

    await wrapper.vm.nextPage()
    expect(listRelations.mock.calls.map(([, request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.vm.page).toBe(1)
    expect(wrapper.vm.items).toHaveLength(1)
  })

  it.each([
    ['followees', FolloweesView, listFollowees],
    ['followers', FollowersView, listFollowers]
  ])('discards stale %s data when the profile route changes', async (_label, component, listRelations) => {
    const previousProfileRequest = deferred()
    const currentProfileRequest = deferred()
    listRelations
      .mockImplementationOnce(() => previousProfileRequest.promise)
      .mockImplementationOnce(() => currentProfileRequest.promise)

    const wrapper = mountView(component)
    await flushPromises()

    await wrapper.setProps({ userId: NEXT_PROFILE_ID })
    await flushPromises()
    expect(listRelations).toHaveBeenCalledTimes(2)

    currentProfileRequest.resolve({ data: [relation(20)], traceId: 'trace-current-profile' })
    await flushPromises()
    expect(wrapper.vm.items[0].targetId).toBe(relation(20).targetId)

    previousProfileRequest.resolve({ data: [relation(0)], traceId: 'trace-previous-profile' })
    await flushPromises()
    expect(wrapper.vm.items[0].targetId).toBe(relation(20).targetId)
    expect(batchUserSummary).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('trace').flat()).not.toContain('trace-previous-profile')
  })

  it.each([
    ['followees', FolloweesView, listFollowees],
    ['followers', FollowersView, listFollowers]
  ])('keeps the latest %s refresh when an older page request finishes last', async (_label, component, listRelations) => {
    const pageRequest = deferred()
    const refreshRequest = deferred()
    const initialPage = Array.from({ length: 10 }, (_, index) => relation(index))
    listRelations
      .mockResolvedValueOnce({ data: initialPage, traceId: 'trace-initial' })
      .mockImplementationOnce(() => pageRequest.promise)
      .mockImplementationOnce(() => refreshRequest.promise)

    const wrapper = mountView(component)
    await flushPromises()

    const pendingPage = wrapper.vm.load(1)
    const pendingRefresh = wrapper.vm.refresh()
    refreshRequest.resolve({ data: [{ ...relation(30), followTime: '2026-08-02T00:00:00Z' }], traceId: 'trace-refresh' })
    await pendingRefresh
    await flushPromises()

    pageRequest.resolve({ data: [relation(10)], traceId: 'trace-page' })
    await pendingPage
    await flushPromises()
    expect(wrapper.vm.page).toBe(0)
    expect(wrapper.vm.items[0].targetId).toBe(relation(30).targetId)
    expect(wrapper.emitted('trace').flat()).not.toContain('trace-page')
  })

  it.each([
    ['followees', FolloweesView, listFollowees],
    ['followers', FollowersView, listFollowers]
  ])('discards stale %s hydration and mutation results after an account switch', async (_label, component, listRelations) => {
    const previousHydration = deferred()
    const followRequest = deferred()
    batchUserSummary
      .mockImplementationOnce(() => previousHydration.promise)
      .mockImplementation(async (ids) => ({ data: ids.map((id) => ({ id, username: 'current viewer' })) }))
    listRelations
      .mockResolvedValueOnce({ data: [relation(0)], traceId: 'trace-previous-viewer' })
      .mockResolvedValueOnce({ data: [relation(1)], traceId: 'trace-current-viewer' })
      .mockResolvedValueOnce({ data: [relation(1)], traceId: 'trace-third-viewer' })
    followUser.mockImplementation(() => followRequest.promise)

    const wrapper = mountView(component)
    await flushPromises()

    useAuthStore().installSession({
      accessToken: 'replacement-token',
      me: { userId: NEXT_PROFILE_ID, username: 'replacement viewer' }
    })
    await flushPromises()
    expect(wrapper.vm.items[0].targetId).toBe(relation(1).targetId)

    previousHydration.resolve({ data: [{ id: relation(0).targetId, username: 'previous viewer result' }] })
    await flushPromises()
    expect(wrapper.vm.items[0].targetId).toBe(relation(1).targetId)

    const currentItem = wrapper.vm.items[0]
    const pendingFollow = wrapper.vm.doFollow(currentItem)
    await wrapper.vm.doFollow(currentItem)
    expect(followUser).toHaveBeenCalledTimes(1)

    useAuthStore().installSession({
      accessToken: 'third-token',
      me: { userId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd', username: 'third viewer' }
    })
    await flushPromises()
    followRequest.resolve({ traceId: 'trace-stale-follow' })
    await pendingFollow
    await flushPromises()

    expect(wrapper.vm.items[0].hasFollowed).toBe(false)
    expect(wrapper.emitted('trace').flat()).not.toContain('trace-stale-follow')
  })

  it.each([
    ['followees', FolloweesView, listFollowees],
    ['followers', FollowersView, listFollowers]
  ])('applies a pending %s mutation to the refreshed item for the same viewer', async (_label, component, listRelations) => {
    const followRequest = deferred()
    listRelations
      .mockResolvedValueOnce({ data: [relation(0)], traceId: 'trace-initial' })
      .mockResolvedValueOnce({ data: [relation(0)], traceId: 'trace-refresh' })
    followUser.mockImplementation(() => followRequest.promise)

    const wrapper = mountView(component)
    await flushPromises()
    const previousItem = wrapper.vm.items[0]
    const pendingFollow = wrapper.vm.doFollow(previousItem)

    await wrapper.vm.refresh()
    expect(wrapper.vm.items[0]).not.toBe(previousItem)
    expect(wrapper.vm.isMutating(relation(0).targetId)).toBe(true)

    followRequest.resolve({ traceId: 'trace-follow-complete' })
    await pendingFollow
    await flushPromises()
    expect(wrapper.vm.items[0].hasFollowed).toBe(true)
    expect(wrapper.vm.isMutating(relation(0).targetId)).toBe(false)
  })
})
