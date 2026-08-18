// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    defaults: { baseURL: '' }
  }
}))

const {
  authData,
  authStoreHolder,
  blockUser,
  ensureUserSummaries,
  followUser,
  getFollowStatus,
  showToast,
  unblockUser,
  unfollowUser
} = vi.hoisted(() => ({
  authData: {
    accessToken: '',
    userId: 0,
    authed: false,
    tokenGeneration: 0
  },
  authStoreHolder: { current: null },
  blockUser: vi.fn(),
  ensureUserSummaries: vi.fn(),
  followUser: vi.fn(),
  getFollowStatus: vi.fn(),
  showToast: vi.fn(),
  unblockUser: vi.fn(),
  unfollowUser: vi.fn()
}))

const authState = reactive(authData)
authStoreHolder.current = authState

vi.mock('../stores/auth', () => ({
  useAuthStore: () => authStoreHolder.current
}))

vi.mock('../stores/postMetaCache', () => ({
  usePostMetaCacheStore: () => ({
    ensureUserSummaries
  })
}))

const socialPrefsState = reactive({
  blockedSet: new Set(),
  ensureBlocked: vi.fn().mockResolvedValue(undefined),
  clear: vi.fn()
})
vi.mock('../stores/socialPrefs', () => ({
  useSocialPrefsStore: () => socialPrefsState
}))

vi.mock('../stores/taxonomy', () => ({
  useTaxonomyStore: () => ({
    categoriesById: new Map(),
    ensureCategories: vi.fn()
  })
}))

vi.mock('../api/services/socialService', () => ({
  followUser,
  unfollowUser,
  getFollowStatus
}))

vi.mock('../api/services/blockService', () => ({
  blockUser,
  unblockUser
}))

vi.mock('../ui/toastService', () => ({
  showToast,
  showErrorToast: (_error, payload) => showToast(payload)
}))

vi.mock('../utils/time', () => ({
  formatTime: vi.fn(() => ''),
  formatTimeAgo: vi.fn(() => '')
}))

import UserProfileView from './UserProfileView.vue'
import http from '../api/http'

function okResult(data, traceId = 'trace-user') {
  return {
    data: {
      code: 0,
      message: '',
      data,
      traceId
    }
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

function mountProfile(userId) {
  return mount(UserProfileView, {
    props: { userId },
    global: {
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :data-to="typeof to === \'string\' ? to : JSON.stringify(to)"><slot /></a>'
        },
        UiBreadcrumb: true,
        ReportModal: true
      }
    }
  })
}

describe('UserProfileView route contract', () => {
  const userId = '11111111-1111-7111-8111-111111111111'

  beforeEach(() => {
    http.get.mockReset()
    http.post.mockReset()
    blockUser.mockReset()
    ensureUserSummaries.mockReset()
    followUser.mockReset()
    getFollowStatus.mockReset()
    showToast.mockReset()
    unblockUser.mockReset()
    unfollowUser.mockReset()
    socialPrefsState.ensureBlocked.mockReset()
    socialPrefsState.clear.mockReset()
    authState.accessToken = ''
    authState.userId = 0
    authState.authed = false
    authState.tokenGeneration = 0
    socialPrefsState.blockedSet = new Set()
    ensureUserSummaries.mockResolvedValue({})
    getFollowStatus.mockResolvedValue({ data: false, traceId: 'trace-follow-status' })
    followUser.mockResolvedValue({ traceId: 'trace-follow' })
    unfollowUser.mockResolvedValue({ traceId: 'trace-unfollow' })
    blockUser.mockResolvedValue({ traceId: 'trace-block' })
    unblockUser.mockResolvedValue({ traceId: 'trace-unblock' })
    socialPrefsState.ensureBlocked.mockResolvedValue(undefined)
    http.get.mockImplementation((url) => {
      if (url === `/api/users/${userId}`) {
        return Promise.resolve(
          okResult({
            id: userId,
            username: 'alice'
          })
        )
      }
      if (url === `/api/users/${userId}/recent-posts`) return Promise.resolve(okResult([]))
      if (url === `/api/users/${userId}/recent-comments`) return Promise.resolve(okResult([]))
      return Promise.resolve(okResult({}))
    })
  })

  it('declares userId as an explicit prop for route-prop pages', () => {
    expect(UserProfileView.props).toBeTruthy()
    expect(UserProfileView.props.userId).toBeTruthy()
  })

  it('publishes only the page model, intent actions and lifecycle', () => {
    const wrapper = mountProfile(userId)

    expect(Object.keys(wrapper.vm.actions).sort()).toEqual([
      'closeReport',
      'follow',
      'openReport',
      'reload',
      'toggleBlocked',
      'unfollow'
    ])
    expect(Object.keys(wrapper.vm.lifecycle).sort()).toEqual(['mount', 'unmount'])
    expect(wrapper.vm.model).toMatchObject({ userId, loading: true })
  })

  it('keeps the profile usable when optional activity and relationship requests fail', async () => {
    authState.accessToken = 'viewer-token'
    authState.userId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    authState.authed = true
    authState.tokenGeneration = 1
    http.get.mockImplementation((url) => {
      if (url === `/api/users/${userId}`) {
        return Promise.resolve(okResult({ id: userId, username: 'alice' }))
      }
      if (url === `/api/users/${userId}/recent-posts`) return Promise.reject(new Error('posts unavailable'))
      if (url === `/api/users/${userId}/recent-comments`) return Promise.resolve(okResult([]))
      return Promise.resolve(okResult({}))
    })
    getFollowStatus.mockRejectedValue(new Error('relationship unavailable'))

    const wrapper = mountProfile(userId)
    await flushPromises()

    expect(wrapper.vm.model.profile).toMatchObject({ id: userId, username: 'alice' })
    expect(wrapper.vm.model.recentPosts).toEqual([])
    expect(wrapper.vm.model.followStatusState).toBe('error')
    expect(wrapper.vm.model.error).toBe('')
  })

  it('hides sign-in user-level ui and falls back to wallet-oriented public profile copy when new fields are absent', async () => {
    const wrapper = mount(UserProfileView, {
      props: {
        userId
      },
      global: {
        stubs: {
          RouterLink: {
            template: '<a><slot /></a>'
          },
          UiBreadcrumb: true,
          ReportModal: true
        }
      }
    })

    await flushPromises()
    await flushPromises()

    expect(http.get.mock.calls.map(([url]) => url)).toEqual([
      `/api/users/${userId}`,
      `/api/users/${userId}/recent-posts`,
      `/api/users/${userId}/recent-comments`
    ])
    expect(wrapper.text()).toContain('钱包资产')
    expect(wrapper.text()).toContain('未公开')
    expect(wrapper.text()).not.toContain('钱包页为准')
    expect(wrapper.text()).not.toContain('当前主页还未接入真实钱包余额')
    expect(wrapper.text()).not.toContain('用户等级 LV')
    expect(wrapper.text()).not.toContain('签到用户等级')
    expect(wrapper.text()).not.toContain('250 分')
    expect(wrapper.text()).not.toContain('LV 3')
    expect(wrapper.text()).not.toContain('积分等级')
    expect(wrapper.text()).not.toContain('NaN')
  })

  it('renders a compact identity header with bounded long profile text', async () => {
    const longUsername = '1654388696@qq.com'
    http.get.mockImplementation((url) => {
      if (url === `/api/users/${userId}`) {
        return Promise.resolve(
          okResult({
            id: userId,
            username: longUsername,
            showUserLevel: true,
            userLevel: 1,
            signInDaysInWindow: 0
          })
        )
      }
      if (url === `/api/users/${userId}/recent-posts`) return Promise.resolve(okResult([]))
      if (url === `/api/users/${userId}/recent-comments`) return Promise.resolve(okResult([]))
      return Promise.resolve(okResult({}))
    })

    const wrapper = mount(UserProfileView, {
      props: {
        userId
      },
      global: {
        stubs: {
          RouterLink: {
            template: '<a><slot /></a>'
          },
          UiBreadcrumb: true,
          ReportModal: true
        }
      }
    })

    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.profile-cover-sheet').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Member Snapshot')

    const name = wrapper.find('.profile-name')
    expect(name.text()).toBe(longUsername)
    expect(name.attributes('title')).toBe(longUsername)
    expect(name.classes()).toContain('profile-text-wrap')

    const meta = wrapper.find('.profile-id-value')
    expect(meta.text()).toBe('11111111...1111')
    expect(meta.text()).not.toBe(userId)
    expect(meta.attributes('title')).toBe(userId)
    expect(meta.classes()).toContain('profile-text-wrap')

    expect(wrapper.text()).toContain('暂无公开动态')
    expect(wrapper.findAll('.profile-signal-card')).toHaveLength(0)
  })

  it('routes private messages to the canonical conversation id from current and profile UUIDs', async () => {
    const profileUserId = '00000000-0000-0000-0000-000000000000'
    const me = '80000000-0000-0000-0000-000000000000'
    authState.accessToken = 'token'
    authState.userId = me
    authState.authed = true

    const wrapper = mount(UserProfileView, {
      props: {
        userId: profileUserId
      },
      global: {
        stubs: {
          RouterLink: {
            props: ['to'],
            template: '<a :data-to="typeof to === \'string\' ? to : JSON.stringify(to)"><slot /></a>'
          },
          UiBreadcrumb: true,
          ReportModal: true
        }
      }
    })

    await flushPromises()
    await flushPromises()

    const messageLink = wrapper.find('.profile-message-link')
    expect(messageLink.attributes('data-to')).toBe(`/messages/${me}_${profileUserId}`)
  })

  it('commits profile, recent activity and follow status from one route snapshot', async () => {
    const previousUserId = '22222222-2222-7222-8222-222222222222'
    const currentUserId = '33333333-3333-7333-8333-333333333333'
    const viewerId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const previousProfileRequest = deferred()
    const previousFollowRequest = deferred()
    authState.accessToken = 'viewer-token'
    authState.userId = viewerId
    authState.authed = true
    authState.tokenGeneration = 1

    http.get.mockImplementation((url) => {
      if (url === `/api/users/${previousUserId}`) return previousProfileRequest.promise
      if (url === `/api/users/${previousUserId}/recent-posts`) {
        return Promise.resolve(okResult([{ id: 'aaaaaaaa-1111-7111-8111-111111111111', title: 'previous post' }], 'trace-previous-posts'))
      }
      if (url === `/api/users/${previousUserId}/recent-comments`) {
        return Promise.resolve(okResult([], 'trace-previous-comments'))
      }
      if (url === `/api/users/${currentUserId}`) {
        return Promise.resolve(okResult({ id: currentUserId, username: 'current profile' }, 'trace-current-profile'))
      }
      if (url === `/api/users/${currentUserId}/recent-posts`) {
        return Promise.resolve(okResult([{ id: 'bbbbbbbb-2222-7222-8222-222222222222', title: 'current post' }], 'trace-current-posts'))
      }
      if (url === `/api/users/${currentUserId}/recent-comments`) {
        return Promise.resolve(okResult([], 'trace-current-comments'))
      }
      return Promise.resolve(okResult({}))
    })
    getFollowStatus.mockImplementation((_entityType, targetId) => {
      if (targetId === previousUserId) return previousFollowRequest.promise
      return Promise.resolve({ data: true, traceId: 'trace-current-follow' })
    })

    const wrapper = mountProfile(previousUserId)
    await flushPromises()
    const requestedBeforeRouteChange = http.get.mock.calls.map(([url]) => url)
    expect(requestedBeforeRouteChange).toContain(`/api/users/${previousUserId}/recent-posts`)
    expect(requestedBeforeRouteChange).toContain(`/api/users/${previousUserId}/recent-comments`)

    await wrapper.setProps({ userId: currentUserId })
    await flushPromises()
    expect(wrapper.vm.model.profile).toMatchObject({ id: currentUserId, username: 'current profile' })
    expect(wrapper.vm.model.recentPosts.map((post) => post.title)).toEqual(['current post'])
    expect(wrapper.vm.model.followStatus).toBe(true)

    previousProfileRequest.resolve(okResult({ id: previousUserId, username: 'previous profile' }, 'trace-previous-profile'))
    previousFollowRequest.resolve({ data: false, traceId: 'trace-previous-follow' })
    await flushPromises()

    expect(wrapper.vm.model.profile).toMatchObject({ id: currentUserId, username: 'current profile' })
    expect(wrapper.vm.model.recentPosts.map((post) => post.title)).toEqual(['current post'])
    expect(wrapper.vm.model.followStatus).toBe(true)
  })

  it('discards an old viewer follow-status response after the account changes', async () => {
    const profileUserId = '44444444-4444-7444-8444-444444444444'
    const previousViewerId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const currentViewerId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    const previousFollowRequest = deferred()
    authState.accessToken = 'previous-token'
    authState.userId = previousViewerId
    authState.authed = true
    authState.tokenGeneration = 1
    http.get.mockImplementation((url) => {
      if (url === `/api/users/${profileUserId}`) {
        return Promise.resolve(okResult({ id: profileUserId, username: 'profile' }, 'trace-profile'))
      }
      if (url === `/api/users/${profileUserId}/recent-posts`) return Promise.resolve(okResult([]))
      if (url === `/api/users/${profileUserId}/recent-comments`) return Promise.resolve(okResult([]))
      return Promise.resolve(okResult({}))
    })
    getFollowStatus
      .mockImplementationOnce(() => previousFollowRequest.promise)
      .mockResolvedValue({ data: true, traceId: 'trace-current-viewer-follow' })

    const wrapper = mountProfile(profileUserId)
    await flushPromises()

    authState.accessToken = 'current-token'
    authState.userId = currentViewerId
    authState.authed = true
    authState.tokenGeneration = 2
    await flushPromises()
    expect(wrapper.vm.model.followStatus).toBe(true)

    previousFollowRequest.resolve({ data: false, traceId: 'trace-previous-viewer-follow' })
    await flushPromises()
    expect(wrapper.vm.model.followStatus).toBe(true)
  })

  it('does not refresh a new route with an old follow mutation result', async () => {
    const previousUserId = '55555555-5555-7555-8555-555555555555'
    const currentUserId = '66666666-6666-7666-8666-666666666666'
    const viewerId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const followRequest = deferred()
    authState.accessToken = 'viewer-token'
    authState.userId = viewerId
    authState.authed = true
    authState.tokenGeneration = 1
    http.get.mockImplementation((url) => {
      if (url === `/api/users/${previousUserId}`) {
        return Promise.resolve(okResult({ id: previousUserId, username: 'previous profile' }, 'trace-previous-profile'))
      }
      if (url === `/api/users/${currentUserId}`) {
        return Promise.resolve(okResult({ id: currentUserId, username: 'current profile' }, 'trace-current-profile'))
      }
      if (url.includes('/recent-posts') || url.includes('/recent-comments')) return Promise.resolve(okResult([]))
      return Promise.resolve(okResult({}))
    })
    getFollowStatus.mockImplementation((_entityType, targetId) => Promise.resolve({
      data: targetId === currentUserId,
      traceId: `trace-status-${targetId}`
    }))
    followUser.mockImplementation(() => followRequest.promise)

    const wrapper = mountProfile(previousUserId)
    await flushPromises()
    const pendingFollow = wrapper.vm.actions.follow()
    await wrapper.vm.actions.follow()
    expect(followUser).toHaveBeenCalledTimes(1)
    expect(followUser).toHaveBeenCalledWith(3, previousUserId)

    await wrapper.setProps({ userId: currentUserId })
    await flushPromises()
    followRequest.resolve({ traceId: 'trace-stale-follow-mutation' })
    await pendingFollow
    await flushPromises()

    expect(wrapper.vm.model.profile).toMatchObject({ id: currentUserId, username: 'current profile' })
    expect(wrapper.vm.model.followStatus).toBe(true)
    expect(wrapper.vm.model.actionLoading).toBe(false)
    const profileRequests = http.get.mock.calls
      .map(([url]) => url)
      .filter((url) => url === `/api/users/${previousUserId}` || url === `/api/users/${currentUserId}`)
    expect(profileRequests).toEqual([`/api/users/${previousUserId}`, `/api/users/${currentUserId}`])
  })

  it('does not apply an old block mutation to a replacement account', async () => {
    const profileUserId = '77777777-7777-7777-8777-777777777777'
    const previousViewerId = 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa'
    const currentViewerId = 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    const blockRequest = deferred()
    authState.accessToken = 'previous-token'
    authState.userId = previousViewerId
    authState.authed = true
    authState.tokenGeneration = 1
    http.get.mockImplementation((url) => {
      if (url === `/api/users/${profileUserId}`) {
        return Promise.resolve(okResult({ id: profileUserId, username: 'profile' }))
      }
      if (url.includes('/recent-posts') || url.includes('/recent-comments')) return Promise.resolve(okResult([]))
      return Promise.resolve(okResult({}))
    })
    blockUser.mockImplementation(() => blockRequest.promise)

    const wrapper = mountProfile(profileUserId)
    await flushPromises()
    const blockedLoadsBeforeSwitch = socialPrefsState.ensureBlocked.mock.calls.length
    const pendingBlock = wrapper.vm.actions.toggleBlocked()
    expect(blockUser).toHaveBeenCalledWith(profileUserId)

    authState.accessToken = 'current-token'
    authState.userId = currentViewerId
    authState.authed = true
    authState.tokenGeneration = 2
    await flushPromises()
    expect(socialPrefsState.ensureBlocked).toHaveBeenCalledTimes(blockedLoadsBeforeSwitch + 1)

    blockRequest.resolve({ traceId: 'trace-stale-block' })
    await pendingBlock
    await flushPromises()
    expect(socialPrefsState.ensureBlocked).toHaveBeenCalledTimes(blockedLoadsBeforeSwitch + 1)
    expect(showToast).not.toHaveBeenCalled()
    expect(wrapper.vm.model.actionLoading).toBe(false)
    expect(wrapper.vm.model.error).toBe('')
  })
})
