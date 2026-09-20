// @vitest-environment jsdom

import { defineComponent, ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'

const { batchUserSummary, followUser, getFollowStatuses, listFollowees, listFollowers, routerPush, unfollowUser } = vi.hoisted(() => ({
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

vi.mock('../../api/services/userService', () => ({ batchUserSummary }))
vi.mock('../../api/services/socialService', () => ({
  followUser,
  unfollowUser,
  getFollowStatuses,
  listFollowees,
  listFollowers
}))

import { useFollowRelationListState } from './useFollowRelationListState'

const VIEWER_ID = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
const PROFILE_ID = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'

function relation(index) {
  return {
    targetId: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
    followTime: '2026-08-01T00:00:00Z'
  }
}

function relationPage(items, nextCursor = '') {
  return {
    data: {
      items,
      nextCursor,
      hasNext: Boolean(nextCursor)
    }
  }
}

function mountState({ relationKind = 'followees', profileUserId = PROFILE_ID, authed = true } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (authed) {
    useAuthStore().installSession({
      accessToken: 'access-token',
      me: { userId: VIEWER_ID, username: 'viewer' }
    })
  }

  let state
  const Harness = defineComponent({
    setup() {
      state = useFollowRelationListState({
        relationKind: ref(relationKind),
        profileUserId: ref(profileUserId)
      })
      return () => null
    }
  })
  mount(Harness, { global: { plugins: [pinia] } })
  return { state, wrapper: null }
}

describe('useFollowRelationListState', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    batchUserSummary.mockImplementation(async (ids) => ({
      data: ids.map((id) => ({ id, username: `user-${id.slice(-2)}` }))
    }))
    getFollowStatuses.mockResolvedValue({ data: {} })
    followUser.mockResolvedValue({ traceId: 'trace-follow' })
    unfollowUser.mockResolvedValue({ traceId: 'trace-unfollow' })
  })

  it('loads the followees policy page on mount and hydrates viewer follow status', async () => {
    listFollowees.mockResolvedValueOnce(relationPage([relation(0), relation(1)], 'cursor-2'))

    const { state } = mountState({ relationKind: 'followees' })
    await flushPromises()

    expect(listFollowees).toHaveBeenCalledWith(PROFILE_ID, { cursor: '', size: 10 })
    expect(listFollowers).not.toHaveBeenCalled()
    expect(state.policy.value.title).toBe('关注')
    expect(state.items.value).toHaveLength(2)
    expect(state.items.value[0]).toMatchObject({ user: expect.objectContaining({ username: expect.any(String) }) })
    expect(state.hasNext.value).toBe(true)
    expect(state.nextCursor.value).toBe('cursor-2')
    expect(getFollowStatuses).toHaveBeenCalledWith(3, [relation(0).targetId, relation(1).targetId])
  })

  it('resets and refetches with the followers policy when relationKind switches', async () => {
    listFollowees.mockResolvedValue(relationPage([relation(0)], ''))
    listFollowers.mockResolvedValue(relationPage([relation(5)], ''))

    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().installSession({ accessToken: 'access-token', me: { userId: VIEWER_ID, username: 'viewer' } })

    const relationKind = ref('followees')
    const profileUserId = ref(PROFILE_ID)
    let state
    const Harness = defineComponent({
      setup() {
        state = useFollowRelationListState({ relationKind, profileUserId })
        return () => null
      }
    })
    mount(Harness, { global: { plugins: [pinia] } })
    await flushPromises()
    expect(listFollowees).toHaveBeenCalledTimes(1)

    relationKind.value = 'followers'
    await flushPromises()

    expect(listFollowees).toHaveBeenCalledTimes(1)
    expect(listFollowers).toHaveBeenCalledTimes(1)
    expect(listFollowers).toHaveBeenCalledWith(PROFILE_ID, { cursor: '', size: 10 })
    expect(state.policy.value.title).toBe('粉丝')
    expect(state.items.value.map((item) => item.targetId)).toEqual([relation(5).targetId])
    expect(state.error.value).toBe('')
    expect(state.pageError.value).toBe('')
  })

  it('appends pages with the returned cursor until exhausted and dedupes page-shifted entries', async () => {
    const firstPage = Array.from({ length: 10 }, (_, index) => relation(index))
    listFollowees
      .mockResolvedValueOnce(relationPage(firstPage, 'cursor-2'))
      .mockResolvedValueOnce(relationPage([relation(9), relation(10)], ''))

    const { state } = mountState()
    await flushPromises()

    await state.loadMore()
    await flushPromises()

    expect(listFollowees.mock.calls.map(([, request]) => request.cursor)).toEqual(['', 'cursor-2'])
    const ids = state.items.value.map((item) => item.targetId)
    expect(new Set(ids).size).toBe(ids.length)
    expect(state.items.value).toHaveLength(11)
    expect(state.hasNext.value).toBe(false)
  })

  it('keeps loaded items and retries the same cursor after a load-more failure', async () => {
    listFollowees
      .mockResolvedValueOnce(relationPage(Array.from({ length: 10 }, (_, index) => relation(index)), 'cursor-2'))
      .mockRejectedValueOnce(new Error('temporary relation failure'))
      .mockResolvedValueOnce(relationPage([relation(10)], ''))

    const { state } = mountState()
    await flushPromises()

    await state.loadMore()
    await flushPromises()
    expect(state.items.value).toHaveLength(10)
    expect(state.pageError.value).toBe('temporary relation failure')
    expect(state.error.value).toBe('')

    await state.loadMore()
    await flushPromises()
    expect(listFollowees.mock.calls.map(([, request]) => request.cursor)).toEqual(['', 'cursor-2', 'cursor-2'])
    expect(state.items.value).toHaveLength(11)
    expect(state.pageError.value).toBe('')
  })

  it('refuses to load more while a request is running or no next page exists', async () => {
    listFollowees.mockResolvedValue(relationPage([relation(0)], ''))

    const { state } = mountState()
    await flushPromises()

    expect(state.hasNext.value).toBe(false)
    await state.loadMore()
    expect(listFollowees).toHaveBeenCalledTimes(1)
  })

  it('offers reload after the initial load fails', async () => {
    listFollowees
      .mockRejectedValueOnce(new Error('relation service down'))
      .mockResolvedValueOnce(relationPage([relation(0)], ''))

    const { state } = mountState()
    await flushPromises()
    expect(state.error.value).toBe('relation service down')
    expect(state.items.value).toHaveLength(0)

    await state.reload()
    await flushPromises()
    expect(state.error.value).toBe('')
    expect(state.items.value).toHaveLength(1)
  })

  it('discards a stale response when the profile route changes', async () => {
    let resolvePrevious
    listFollowees
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePrevious = resolve }))
      .mockResolvedValueOnce(relationPage([relation(20)], ''))

    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().installSession({ accessToken: 'access-token', me: { userId: VIEWER_ID, username: 'viewer' } })

    const relationKind = ref('followees')
    const profileUserId = ref(PROFILE_ID)
    let state
    const Harness = defineComponent({
      setup() {
        state = useFollowRelationListState({ relationKind, profileUserId })
        return () => null
      }
    })
    mount(Harness, { global: { plugins: [pinia] } })
    await flushPromises()

    profileUserId.value = 'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
    await flushPromises()

    resolvePrevious(relationPage([relation(0)], ''))
    await flushPromises()
    expect(state.items.value.map((item) => item.targetId)).toEqual([relation(20).targetId])
  })

  it('applies follow and unfollow mutations in place and keeps the guard per target', async () => {
    listFollowees.mockResolvedValueOnce(relationPage([relation(0), relation(1)], ''))

    const { state } = mountState()
    await flushPromises()

    const first = state.items.value[0]
    await state.doFollow(first)
    await flushPromises()

    expect(followUser).toHaveBeenCalledWith(3, relation(0).targetId)
    expect(state.items.value[0].hasFollowed).toBe(true)
    expect(state.isMutating(relation(0).targetId)).toBe(false)

    const second = state.items.value[1]
    const inFlight = state.doUnfollow(second)
    expect(state.isMutating(relation(1).targetId)).toBe(true)
    await inFlight
    expect(unfollowUser).toHaveBeenCalledWith(3, relation(1).targetId)
    expect(state.items.value[1].hasFollowed).toBe(false)
  })

  it('ignores duplicate mutations on the same target while one is in flight', async () => {
    let resolveFollow
    followUser.mockImplementationOnce(() => new Promise((resolve) => { resolveFollow = resolve }))
    listFollowees.mockResolvedValueOnce(relationPage([relation(0)], ''))

    const { state } = mountState()
    await flushPromises()

    const first = state.items.value[0]
    const pending = state.doFollow(first)
    await state.doFollow(first)

    expect(followUser).toHaveBeenCalledTimes(1)
    resolveFollow({ traceId: 'trace-follow' })
    await pending
    expect(state.isMutating(relation(0).targetId)).toBe(false)
  })

  it('surfaces the mutation failure message without flipping the relation', async () => {
    followUser.mockRejectedValueOnce(new Error('关注服务不可用'))
    listFollowees.mockResolvedValueOnce(relationPage([relation(0)], ''))

    const { state } = mountState()
    await flushPromises()

    await state.doFollow(state.items.value[0])
    expect(state.items.value[0].hasFollowed).toBe(false)
    expect(state.error.value).toBe('关注服务不可用')
    expect(state.isMutating(relation(0).targetId)).toBe(false)
  })

  it('applies a pending mutation to the reloaded item for the same viewer', async () => {
    let resolveFollow
    followUser.mockImplementation(() => new Promise((resolve) => { resolveFollow = resolve }))
    listFollowees
      .mockResolvedValueOnce(relationPage([relation(0)], ''))
      .mockResolvedValueOnce(relationPage([relation(0)], ''))

    const { state } = mountState()
    await flushPromises()
    const previousItem = state.items.value[0]
    const pendingFollow = state.doFollow(previousItem)

    await state.reload()
    expect(state.items.value[0]).not.toBe(previousItem)
    expect(state.isMutating(relation(0).targetId)).toBe(true)

    resolveFollow({ traceId: 'trace-follow-complete' })
    await pendingFollow
    await flushPromises()
    expect(state.items.value[0].hasFollowed).toBe(true)
    expect(state.isMutating(relation(0).targetId)).toBe(false)
  })

  it('discards mutation results after the account switches', async () => {
    let resolveFollow
    followUser.mockImplementation(() => new Promise((resolve) => { resolveFollow = resolve }))
    listFollowees
      .mockResolvedValueOnce(relationPage([relation(0)], ''))
      .mockResolvedValueOnce(relationPage([relation(1)], ''))

    const { state } = mountState()
    await flushPromises()

    const pendingFollow = state.doFollow(state.items.value[0])

    useAuthStore().installSession({
      accessToken: 'replacement-token',
      me: { userId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd', username: 'replacement viewer' }
    })
    await flushPromises()

    resolveFollow({ traceId: 'trace-stale-follow' })
    await pendingFollow
    expect(state.items.value[0].hasFollowed).toBe(false)
    expect(state.isMutating(relation(0).targetId)).toBe(false)
  })

  it('opens the target profile through the router', async () => {
    listFollowees.mockResolvedValueOnce(relationPage([relation(0)], ''))

    const { state } = mountState()
    await flushPromises()

    state.openProfile(state.items.value[0])
    expect(routerPush).toHaveBeenCalledWith({
      name: 'userProfile',
      params: { userId: relation(0).targetId }
    })
  })

  it('loads the public page for an anonymous viewer without querying follow statuses', async () => {
    const { state } = mountState({ authed: false })
    await flushPromises()

    // 列表是公开数据：匿名也会加载并补水用户摘要，只是不查私密的关注状态。
    expect(listFollowees).toHaveBeenCalledTimes(1)
    expect(batchUserSummary).toHaveBeenCalledWith([relation(0).targetId])
    expect(getFollowStatuses).not.toHaveBeenCalled()
    expect(state.authed.value).toBe(false)
    expect(state.items.value).toHaveLength(1)
  })
})
