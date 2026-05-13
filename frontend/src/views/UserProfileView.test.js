// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/http', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    defaults: { baseURL: '' }
  }
}))

const { authState } = vi.hoisted(() => ({
  authState: {
    accessToken: '',
    userId: 0,
    authed: false
  }
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({
    accessToken: authState.accessToken,
    userId: authState.userId,
    authed: authState.authed
  })
}))

vi.mock('../stores/postMetaCache', () => ({
  usePostMetaCacheStore: () => ({
    ensureUserSummaries: vi.fn().mockResolvedValue({})
  })
}))

const socialPrefsState = {
  blockedSet: new Set(),
  ensureBlocked: vi.fn().mockResolvedValue(undefined),
  clear: vi.fn()
}
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
  followUser: vi.fn(),
  unfollowUser: vi.fn(),
  getFollowStatus: vi.fn()
}))

vi.mock('../api/services/blockService', () => ({
  blockUser: vi.fn(),
  unblockUser: vi.fn()
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

describe('UserProfileView route contract', () => {
  const userId = '11111111-1111-7111-8111-111111111111'

  beforeEach(() => {
    vi.clearAllMocks()
    authState.accessToken = ''
    authState.userId = 0
    authState.authed = false
    http.get.mockImplementation((url) => {
      if (url === `/api/users/${userId}`) {
        return Promise.resolve(
          okResult({
            id: userId,
            username: 'alice',
            level: 3,
            score: 250
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
})
