// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerState = vi.hoisted(() => ({
  route: { name: 'posts', query: {} },
  push: vi.fn(),
  replace: vi.fn()
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routerState.route,
    useRouter: () => ({ push: routerState.push, replace: routerState.replace })
  }
})

const { post } = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('../../api/http', () => ({ default: { post } }))

import SidebarNav from './SidebarNav.vue'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import { useInboxUnreadStore } from '../../stores/inboxUnread'

function mountSidebar({
  props = {},
  routeName,
  authed = false,
  authorities = ['ROLE_USER'],
  collapsed = false,
  noticeUnread = 0,
  messageUnread = 0
} = {}) {
  if (routeName) routerState.route = { name: routeName, query: {} }
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  const ui = useUiStore()
  const inboxUnread = useInboxUnreadStore()
  if (authed) {
    auth.installSession({
      accessToken: 'token-a',
      me: { userId: 'user-1', username: 'aaa', authorities }
    })
  }
  if (collapsed) ui.sidebarCollapsed = true
  inboxUnread.noticeUnread = noticeUnread
  inboxUnread.messageUnread = messageUnread

  const wrapper = mount(SidebarNav, {
    props,
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
  return { wrapper, auth, ui, inboxUnread }
}

function navItems(wrapper) {
  return wrapper.findAll('.nav-item').map((el) => ({
    text: el.text(),
    label: el.attributes('aria-label'),
    to: el.attributes('data-to')
  }))
}

describe('SidebarNav', () => {
  beforeEach(() => {
    routerState.route = { name: 'posts', query: {} }
    routerState.push.mockClear()
    routerState.replace.mockClear()
    post.mockReset()
  })

  it('renders only the community, market and account groups for anonymous visitors', () => {
    const { wrapper } = mountSidebar()

    expect(wrapper.findAll('.nav-group-title').map((el) => el.text())).toEqual(['社区', '市场', '账户'])
    const items = navItems(wrapper)
    expect(items.map((it) => it.label)).toEqual(['帖子', '搜索', '市场', '登录'])
    expect(wrapper.find('.sidebar-footer').exists()).toBe(false)
  })

  it('renders the three first-level domains for signed-in users without market sub-destinations', () => {
    const { wrapper } = mountSidebar({ authed: true })

    expect(wrapper.findAll('.nav-group-title').map((el) => el.text())).toEqual(['社区', '市场', '个人'])
    const labels = navItems(wrapper).map((it) => it.label)
    expect(labels).toEqual(['帖子', '搜索', '收藏', '我的主页', '市场', '积分钱包', '网盘', '通知', '私信', '设置'])
    expect(labels).not.toContain('发布商品')
    expect(labels).not.toContain('我的出售')
    expect(labels).not.toContain('我的购买')
    expect(labels).not.toContain('出售订单')
    expect(labels).not.toContain('收货地址')
  })

  it('marks the current route active through the market domain family', () => {
    const { wrapper } = mountSidebar({ authed: true, routeName: 'marketSellingOrders' })

    const market = wrapper
      .findAll('.nav-item')
      .find((el) => el.attributes('aria-label') === '市场')
    expect(market?.classes()).toContain('is-active')
  })

  it('shows unread badges only on the notices and messages entries', () => {
    const { wrapper } = mountSidebar({ authed: true, noticeUnread: 2, messageUnread: 120 })

    const items = wrapper.findAll('.nav-item')
    const notices = items.find((el) => el.text().includes('通知'))
    const messages = items.find((el) => el.text().includes('私信'))
    const wallet = items.find((el) => el.text().includes('积分钱包'))

    expect(notices?.get('.nav-badge').text()).toBe('2')
    expect(notices?.attributes('aria-label')).toBe('通知，2 条未读')
    expect(messages?.get('.nav-badge').text()).toBe('99+')
    expect(wallet?.find('.nav-badge').exists()).toBe(false)
  })

  it('concentrates the account area at the sidebar footer with profile, settings and logout', async () => {
    const { wrapper, auth } = mountSidebar({ authed: true })
    post.mockResolvedValueOnce({})

    const footer = wrapper.get('.sidebar-footer')
    expect(footer.text()).toContain('aaa')
    expect(footer.get('.sidebar-user-link').attributes('data-to')).toBe('{"name":"userProfile","params":{"userId":"user-1"}}')

    const actions = footer.findAll('.sidebar-account-action')
    expect(actions.map((el) => el.text())).toEqual(['设置', '登出'])
    expect(actions[0].attributes('data-to')).toBe('{"name":"settings"}')

    await actions[1].trigger('click')
    await flushPromises()

    expect(post).toHaveBeenCalledWith('/api/auth/logout')
    expect(auth.authed).toBe(false)
    expect(routerState.replace).toHaveBeenCalledWith({ name: 'login' })
  })

  it('keeps settings and logout reachable as labelled icon buttons when collapsed', () => {
    const { wrapper } = mountSidebar({ authed: true, collapsed: true })

    const labels = wrapper.findAll('.sidebar-footer button').map((el) => el.attributes('aria-label'))
    expect(labels).toEqual(['设置', '登出'])
  })

  it('still renders the account area for the admin workspace', () => {
    const { wrapper } = mountSidebar({ props: { mode: 'admin' }, authed: true, authorities: ['ROLE_ADMIN'] })

    expect(wrapper.findAll('.nav-group-title').map((el) => el.text())).toContain('管理')
    const footer = wrapper.get('.sidebar-footer')
    expect(footer.text()).toContain('治理视图已启用')
    expect(footer.text()).toContain('登出')
  })
})
