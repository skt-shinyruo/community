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

import Topbar from './Topbar.vue'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'

function mountTopbar(props = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const wrapper = mount(Topbar, {
    props,
    attachTo: document.body,
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
  return { wrapper, auth: useAuthStore(), ui: useUiStore() }
}

describe('Topbar', () => {
  beforeEach(() => {
    routerState.route = { name: 'posts', query: {} }
    routerState.push.mockClear()
    routerState.replace.mockClear()
  })

  it('keeps only collapse, workspace eyebrow, shell search and the theme shortcut', () => {
    const { wrapper, auth } = mountTopbar()
    auth.installSession({
      accessToken: 'token-a',
      me: { userId: 'user-1', username: 'aaa', authorities: ['ROLE_USER'] }
    })

    expect(wrapper.get('button[aria-label="折叠或展开侧边栏"]').exists()).toBe(true)
    expect(wrapper.get('.topbar-eyebrow').text()).toBe('社区')
    expect(wrapper.get('input[type="search"]').exists()).toBe(true)
    expect(wrapper.get('button[aria-label="切换到深色主题"]').exists()).toBe(true)

    // 不渲染账户块、溢出菜单、登出或通知铃铛。
    expect(wrapper.find('.topbar-overflow').exists()).toBe(false)
    expect(wrapper.find('.topbar-user-link').exists()).toBe(false)
    expect(wrapper.find('button[aria-label="登出"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('登录')
    expect(wrapper.text()).not.toContain('aaa')
  })

  it('renders the Chinese workspace eyebrow for the current route', () => {
    routerState.route = { name: 'wallet', query: {} }
    const { wrapper } = mountTopbar()
    expect(wrapper.get('.topbar-eyebrow').text()).toBe('个人')

    routerState.route = { name: 'moderation', query: {} }
    const admin = mountTopbar({ mode: 'admin' })
    expect(admin.wrapper.get('.topbar-eyebrow').text()).toBe('运营')
  })

  it('hides the shell search in the admin workspace but keeps the theme shortcut', () => {
    const { wrapper } = mountTopbar({ mode: 'admin' })
    expect(wrapper.find('input[type="search"]').exists()).toBe(false)
    expect(wrapper.get('button[aria-label="切换到深色主题"]').exists()).toBe(true)
  })

  it('toggles the effective theme from the shortcut button', async () => {
    const { wrapper, ui } = mountTopbar()
    expect(ui.effectiveTheme).toBe('light')

    await wrapper.get('button[aria-label="切换到深色主题"]').trigger('click')
    expect(ui.theme).toBe('dark')
    expect(ui.effectiveTheme).toBe('dark')

    await wrapper.get('button[aria-label="切换到浅色主题"]').trigger('click')
    expect(ui.theme).toBe('light')
  })

  it('submits the shell search to the global search route', async () => {
    const { wrapper } = mountTopbar()
    const input = wrapper.get('input[type="search"]')

    await input.setValue('spring')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(routerState.push).toHaveBeenCalledWith({ name: 'search', query: { q: 'spring' } })
  })

  it('replaces the query when already on the search route', async () => {
    routerState.route = { name: 'search', query: { q: 'java' } }
    const { wrapper } = mountTopbar()
    const input = wrapper.get('input[type="search"]')

    await input.setValue('spring')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(routerState.replace).toHaveBeenCalledWith({ name: 'search', query: { q: 'spring' } })
    expect(routerState.push).not.toHaveBeenCalled()
  })
})
