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
  routerPush,
  unfollowUser
} = vi.hoisted(() => ({
  batchUserSummary: vi.fn(),
  followUser: vi.fn(),
  getFollowStatuses: vi.fn(),
  listFollowees: vi.fn(),
  listFollowers: vi.fn(),
  routerPush: vi.fn(),
  unfollowUser: vi.fn()
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => ({ push: routerPush })
  }
})

vi.mock('../api/services/userService', () => ({ batchUserSummary }))
vi.mock('../api/services/socialService', () => ({
  followUser,
  unfollowUser,
  getFollowStatuses,
  listFollowees,
  listFollowers
}))

import FollowRelationListView from './FollowRelationListView.vue'

const VIEWER_ID = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
const PROFILE_ID = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
const NEXT_PROFILE_ID = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'

function relation(index) {
  return {
    targetId: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
    followTime: '2026-08-01T00:00:00Z'
  }
}

function relationPage(items, nextCursor = '', traceId = '') {
  return {
    data: {
      items,
      nextCursor,
      hasNext: Boolean(nextCursor)
    },
    traceId
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

function mountView(relationKind) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'access-token',
    me: { userId: VIEWER_ID, username: 'viewer' }
  })

  return mount(FollowRelationListView, {
    props: { relationKind, userId: PROFILE_ID },
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        UiAvatar: true,
        UiBadge: true,
        UiBreadcrumb: true,
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiSkeleton: true,
        UiState: { template: '<div><slot /><slot name="description" /><slot name="actions" /></div>' },
        UiButton: {
          props: ['disabled', 'variant', 'to'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('follow relation load-more feed', () => {
  beforeEach(() => {
    batchUserSummary.mockReset()
    followUser.mockReset()
    getFollowStatuses.mockReset()
    listFollowees.mockReset()
    listFollowers.mockReset()
    routerPush.mockReset()
    unfollowUser.mockReset()
    batchUserSummary.mockImplementation(async (ids) => ({
      data: ids.map((id) => ({ id, username: `user-${id.slice(-2)}` }))
    }))
    getFollowStatuses.mockResolvedValue({ data: {} })
    followUser.mockResolvedValue({ traceId: 'trace-follow' })
    unfollowUser.mockResolvedValue({ traceId: 'trace-unfollow' })
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('appends %s pages with the returned cursor until the feed is exhausted', async (relationKind, listRelations) => {
    const firstPage = Array.from({ length: 10 }, (_, index) => relation(index))
    listRelations
      .mockResolvedValueOnce(relationPage(firstPage, 'cursor-page-1', 'trace-page-0'))
      .mockResolvedValueOnce(relationPage([relation(10)], '', 'trace-page-1'))

    const wrapper = mountView(relationKind)
    await flushPromises()
    expect(wrapper.vm.items).toHaveLength(10)
    expect(wrapper.vm.hasNext).toBe(true)

    await wrapper.vm.loadMore()
    expect(listRelations.mock.calls.map(([, request]) => request.cursor)).toEqual([
      '',
      'cursor-page-1'
    ])
    expect(wrapper.vm.items).toHaveLength(11)
    expect(wrapper.vm.hasNext).toBe(false)
    expect(wrapper.text()).toContain('已经到底了')
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('keeps loaded %s items and retries the same cursor after a load-more failure', async (relationKind, listRelations) => {
    const firstPage = Array.from({ length: 10 }, (_, index) => relation(index))
    listRelations
      .mockResolvedValueOnce(relationPage(firstPage, 'cursor-page-1', 'trace-page-0'))
      .mockRejectedValueOnce(new Error('temporary relation failure'))
      .mockResolvedValueOnce(relationPage([relation(10)], '', 'trace-page-1'))

    const wrapper = mountView(relationKind)
    await flushPromises()

    await wrapper.vm.loadMore()
    expect(wrapper.vm.items).toHaveLength(10)
    expect(wrapper.vm.pageError).toBe('temporary relation failure')
    expect(wrapper.text()).toContain('temporary relation failure')

    await wrapper.vm.loadMore()
    expect(listRelations.mock.calls.map(([, request]) => request.cursor)).toEqual([
      '',
      'cursor-page-1',
      'cursor-page-1'
    ])
    expect(wrapper.vm.items).toHaveLength(11)
    expect(wrapper.vm.pageError).toBe('')
  })

  it.each([
    ['followees', listFollowees, '暂无关注'],
    ['followers', listFollowers, '暂无粉丝']
  ])('shows the %s empty state with a primary next step', async (relationKind, listRelations, emptyTitle) => {
    listRelations.mockResolvedValueOnce(relationPage([], '', 'trace-empty'))

    const wrapper = mountView(relationKind)
    await flushPromises()

    expect(wrapper.text()).toContain(emptyTitle)
    expect(wrapper.text()).toContain('回到讨论区')
    expect(wrapper.text()).toContain('返回主页')
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('offers a retry after the initial %s load fails', async (relationKind, listRelations) => {
    listRelations
      .mockRejectedValueOnce(new Error('relation service down'))
      .mockResolvedValueOnce(relationPage([relation(0)], '', 'trace-retry'))

    const wrapper = mountView(relationKind)
    await flushPromises()
    expect(wrapper.vm.error).toBe('relation service down')
    expect(wrapper.text()).toContain('relation service down')

    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    expect(retry).toBeTruthy()
    await retry.trigger('click')
    await flushPromises()

    expect(wrapper.vm.error).toBe('')
    expect(wrapper.vm.items).toHaveLength(1)
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('opens a %s card with click or Enter while nested controls stay independent', async (relationKind, listRelations) => {
    listRelations.mockResolvedValue(relationPage([relation(0)], '', 'trace-open'))

    const wrapper = mountView(relationKind)
    await flushPromises()

    const card = wrapper.find('.relation-card')
    expect(card.attributes('role')).toBe('link')
    expect(card.attributes('tabindex')).toBe('0')

    await card.trigger('click')
    expect(routerPush).toHaveBeenCalledTimes(1)
    expect(routerPush).toHaveBeenLastCalledWith({
      name: 'userProfile',
      params: { userId: relation(0).targetId }
    })

    routerPush.mockClear()
    await card.trigger('keydown.enter')
    expect(routerPush).toHaveBeenCalledTimes(1)

    routerPush.mockClear()
    await wrapper.find('.relation-name').trigger('keydown.enter')
    expect(routerPush).not.toHaveBeenCalled()

    await wrapper.find('.relation-actions').trigger('click')
    expect(routerPush).not.toHaveBeenCalled()
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('keeps the %s mutation buttons usable without triggering card navigation', async (relationKind, listRelations) => {
    listRelations.mockResolvedValue(relationPage([relation(0)], '', 'trace-mutate'))

    const wrapper = mountView(relationKind)
    await flushPromises()

    const followButton = wrapper.findAll('.relation-actions button')
      .find((button) => button.text() === '关注')
    expect(followButton).toBeTruthy()
    await followButton.trigger('click')
    await flushPromises()

    expect(followUser).toHaveBeenCalledTimes(1)
    expect(followUser).toHaveBeenCalledWith(3, relation(0).targetId)
    expect(routerPush).not.toHaveBeenCalled()
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('discards stale %s data when the profile route changes', async (relationKind, listRelations) => {
    const previousProfileRequest = deferred()
    const currentProfileRequest = deferred()
    listRelations
      .mockImplementationOnce(() => previousProfileRequest.promise)
      .mockImplementationOnce(() => currentProfileRequest.promise)

    const wrapper = mountView(relationKind)
    await flushPromises()

    await wrapper.setProps({ userId: NEXT_PROFILE_ID })
    await flushPromises()
    expect(listRelations).toHaveBeenCalledTimes(2)

    currentProfileRequest.resolve(relationPage([relation(20)], '', 'trace-current-profile'))
    await flushPromises()
    expect(wrapper.vm.items[0].targetId).toBe(relation(20).targetId)

    previousProfileRequest.resolve(relationPage([relation(0)], '', 'trace-previous-profile'))
    await flushPromises()
    expect(wrapper.vm.items[0].targetId).toBe(relation(20).targetId)
    expect(batchUserSummary).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('keeps the latest %s reload when an older load-more request finishes last', async (relationKind, listRelations) => {
    const pageRequest = deferred()
    const refreshRequest = deferred()
    const initialPage = Array.from({ length: 10 }, (_, index) => relation(index))
    listRelations
      .mockResolvedValueOnce(relationPage(initialPage, 'cursor-page-1', 'trace-initial'))
      .mockImplementationOnce(() => pageRequest.promise)
      .mockImplementationOnce(() => refreshRequest.promise)

    const wrapper = mountView(relationKind)
    await flushPromises()

    const pendingMore = wrapper.vm.loadMore()
    const pendingReload = wrapper.vm.reload()
    refreshRequest.resolve(relationPage(
      [{ ...relation(30), followTime: '2026-08-02T00:00:00Z' }],
      'cursor-refreshed',
      'trace-refresh'
    ))
    await pendingReload
    await flushPromises()

    pageRequest.resolve(relationPage([relation(10)], '', 'trace-page'))
    await pendingMore
    await flushPromises()
    expect(wrapper.vm.items).toHaveLength(1)
    expect(wrapper.vm.items[0].targetId).toBe(relation(30).targetId)
    expect(wrapper.vm.nextCursor).toBe('cursor-refreshed')
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('discards stale %s hydration and mutation results after an account switch', async (relationKind, listRelations) => {
    const previousHydration = deferred()
    const followRequest = deferred()
    batchUserSummary
      .mockImplementationOnce(() => previousHydration.promise)
      .mockImplementation(async (ids) => ({ data: ids.map((id) => ({ id, username: 'current viewer' })) }))
    listRelations
      .mockResolvedValueOnce(relationPage([relation(0)], '', 'trace-previous-viewer'))
      .mockResolvedValueOnce(relationPage([relation(1)], '', 'trace-current-viewer'))
      .mockResolvedValueOnce(relationPage([relation(1)], '', 'trace-third-viewer'))
    followUser.mockImplementation(() => followRequest.promise)

    const wrapper = mountView(relationKind)
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
  })

  it.each([
    ['followees', listFollowees],
    ['followers', listFollowers]
  ])('applies a pending %s mutation to the reloaded item for the same viewer', async (relationKind, listRelations) => {
    const followRequest = deferred()
    listRelations
      .mockResolvedValueOnce(relationPage([relation(0)], '', 'trace-initial'))
      .mockResolvedValueOnce(relationPage([relation(0)], '', 'trace-reload'))
    followUser.mockImplementation(() => followRequest.promise)

    const wrapper = mountView(relationKind)
    await flushPromises()
    const previousItem = wrapper.vm.items[0]
    const pendingFollow = wrapper.vm.doFollow(previousItem)

    await wrapper.vm.reload()
    expect(wrapper.vm.items[0]).not.toBe(previousItem)
    expect(wrapper.vm.isMutating(relation(0).targetId)).toBe(true)

    followRequest.resolve({ traceId: 'trace-follow-complete' })
    await pendingFollow
    await flushPromises()
    expect(wrapper.vm.items[0].hasFollowed).toBe(true)
    expect(wrapper.vm.isMutating(relation(0).targetId)).toBe(false)
  })
})
