// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerState = vi.hoisted(() => ({
  route: { name: 'posts', query: {} }
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routerState.route
  }
})

import MobileNav from './MobileNav.vue'
import { useAuthStore } from '../../stores/auth'
import { useInboxUnreadStore } from '../../stores/inboxUnread'

function mountMobileNav({ authed = false, noticeUnread = 0, messageUnread = 0 } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  const inboxUnread = useInboxUnreadStore()
  if (authed) {
    auth.installSession({
      accessToken: 'token-a',
      me: { userId: 'user-1', username: 'aaa', authorities: ['ROLE_USER'] }
    })
  }
  inboxUnread.noticeUnread = noticeUnread
  inboxUnread.messageUnread = messageUnread

  const wrapper = mount(MobileNav, {
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: {
          name: 'RouterLink',
          props: ['to'],
          template: '<a :data-to="JSON.stringify(to)"><slot /></a>'
        }
      }
    }
  })
  return { wrapper, auth, inboxUnread }
}

describe('MobileNav', () => {
  beforeEach(() => {
    routerState.route = { name: 'posts', query: {} }
  })

  it('keeps the five fixed entries for anonymous visitors with inbox entries pointing at login', () => {
    const { wrapper } = mountMobileNav()

    const items = wrapper.findAll('.mobile-nav-item')
    expect(items.map((el) => el.get('.mobile-nav-text').text())).toEqual(['帖子', '搜索', '通知', '私信', '我'])
    expect(items[2].attributes('data-to')).toBe('{"name":"login"}')
    expect(items[3].attributes('data-to')).toBe('{"name":"login"}')
    expect(items[4].attributes('data-to')).toBe('{"name":"login"}')
    expect(wrapper.find('.mobile-nav-badge').exists()).toBe(false)
  })

  it('shows unread badges on the notices and messages entries for signed-in users', () => {
    const { wrapper } = mountMobileNav({ authed: true, noticeUnread: 4, messageUnread: 1 })

    const items = wrapper.findAll('.mobile-nav-item')
    expect(items[2].attributes('data-to')).toBe('{"name":"notices"}')
    expect(items[2].get('.mobile-nav-badge').text()).toBe('4')
    expect(items[2].attributes('aria-label')).toBe('通知，4 条未读')
    expect(items[3].get('.mobile-nav-badge').text()).toBe('1')
    expect(items[0].find('.mobile-nav-badge').exists()).toBe(false)
    expect(items[4].find('.mobile-nav-badge').exists()).toBe(false)
  })

  it('keeps badges hidden when the unread counts are zero', () => {
    const { wrapper } = mountMobileNav({ authed: true })

    expect(wrapper.find('.mobile-nav-badge').exists()).toBe(false)
    expect(wrapper.findAll('.mobile-nav-item').map((el) => el.attributes('aria-label'))).toEqual([
      '帖子',
      '搜索',
      '通知',
      '私信',
      '我'
    ])
  })
})
