// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../stores/auth'
import { useUiStore } from '../stores/ui'

vi.mock('../api/services/marketService', () => ({
  listMarketAddresses: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-list' }),
  createMarketAddress: vi.fn(),
  updateMarketAddress: vi.fn(),
  deleteMarketAddress: vi.fn()
}))

import SettingsView from './SettingsView.vue'
import { listMarketAddresses } from '../api/services/marketService'

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/settings', name: 'settings', component: SettingsView },
      { path: '/:pathMatch(.*)*', name: 'notFound', component: { template: '<div />' } }
    ]
  })
}

function sectionLinks(wrapper) {
  return wrapper.findAll('.settings-sections a')
}

function activeSectionLink(wrapper) {
  return sectionLinks(wrapper).find((link) => link.attributes('aria-current') === 'true')
}

describe('SettingsView section contract', () => {
  let pinia
  let router

  async function mountAt(path) {
    await router.push(path)
    await router.isReady()
    const wrapper = mount(SettingsView, {
      global: {
        plugins: [pinia, router]
      }
    })
    await flushPromises()
    return wrapper
  }

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().installSession({
      accessToken: 'token',
      me: { userId: 7, username: 'aaa', headerUrl: '', authorities: [] }
    })
    window.localStorage.clear()
    vi.clearAllMocks()
    listMarketAddresses.mockResolvedValue({ data: [], traceId: 'trace-list' })
    router = createTestRouter()
  })

  it('exposes keyboard-operable section navigation with correct aria state', async () => {
    const wrapper = await mountAt('/settings?section=appearance')

    const nav = wrapper.get('nav.settings-sections')
    expect(nav.attributes('aria-label')).toBe('设置分区')

    const links = sectionLinks(wrapper)
    expect(links.map((link) => link.text())).toEqual(['公开资料', '外观', '收货地址'])
    for (const link of links) {
      expect(link.attributes('href')).toMatch(/^\/settings\?section=(profile|appearance|addresses)$/)
    }
    expect(activeSectionLink(wrapper)?.text()).toBe('外观')
  })

  it('defaults to the profile section and canonicalizes a missing section query', async () => {
    const wrapper = await mountAt('/settings')

    expect(wrapper.text()).toContain('头像上传')
    expect(activeSectionLink(wrapper)?.text()).toBe('公开资料')
    expect(router.currentRoute.value.query).toEqual({ section: 'profile' })
  })

  it('falls back to the profile section and canonicalizes an invalid section query', async () => {
    const wrapper = await mountAt('/settings?section=bogus')

    expect(wrapper.text()).toContain('头像上传')
    expect(wrapper.text()).not.toContain('正在加载地址簿')
    expect(activeSectionLink(wrapper)?.text()).toBe('公开资料')
    expect(router.currentRoute.value.query).toEqual({ section: 'profile' })
  })

  it('keeps the profile section for a repeated (array) section query', async () => {
    await router.push('/settings?section=appearance&section=addresses')
    await router.isReady()
    const wrapper = mount(SettingsView, {
      global: { plugins: [pinia, router] }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('头像上传')
    expect(router.currentRoute.value.query).toEqual({ section: 'profile' })
  })

  it('renders the appearance section for its deep link and preserves the query', async () => {
    const wrapper = await mountAt('/settings?section=appearance')

    expect(wrapper.text()).toContain('主题')
    expect(wrapper.text()).toContain('密度')
    expect(wrapper.text()).not.toContain('头像上传')
    expect(router.currentRoute.value.query).toEqual({ section: 'appearance' })
    expect(listMarketAddresses).not.toHaveBeenCalled()
  })

  it('renders the addresses section for its deep link and loads the address book', async () => {
    const wrapper = await mountAt('/settings?section=addresses')

    expect(listMarketAddresses).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('收货地址')
    expect(wrapper.text()).not.toContain('头像上传')
    expect(activeSectionLink(wrapper)?.text()).toBe('收货地址')
    expect(router.currentRoute.value.query).toEqual({ section: 'addresses' })
  })

  it('switches sections through the navigation links and updates the URL', async () => {
    const wrapper = await mountAt('/settings?section=profile')

    const appearanceLink = sectionLinks(wrapper).find((link) => link.text() === '外观')
    await appearanceLink.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ section: 'appearance' })
    expect(wrapper.text()).toContain('跟随系统')
    expect(activeSectionLink(wrapper)?.text()).toBe('外观')

    const addressesLink = sectionLinks(wrapper).find((link) => link.text() === '收货地址')
    await addressesLink.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ section: 'addresses' })
    expect(listMarketAddresses).toHaveBeenCalledTimes(1)
    expect(activeSectionLink(wrapper)?.text()).toBe('收货地址')
  })

  it('reads and writes the three-state theme through the appearance section', async () => {
    const wrapper = await mountAt('/settings?section=appearance')
    const ui = useUiStore()
    expect(ui.theme).toBe('system')
    expect(wrapper.text()).toContain('正在跟随系统')

    await wrapper.get('input[name="settings-theme"][value="dark"]').setValue(true)
    expect(ui.theme).toBe('dark')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(JSON.parse(window.localStorage.getItem('community.ui')).theme).toBe('dark')
    expect(wrapper.text()).not.toContain('正在跟随系统')

    await wrapper.get('input[name="settings-theme"][value="system"]').setValue(true)
    expect(ui.theme).toBe('system')
    expect(JSON.parse(window.localStorage.getItem('community.ui')).theme).toBe('system')
  })

  it('reads and writes the density through the appearance section', async () => {
    const wrapper = await mountAt('/settings?section=appearance')
    const ui = useUiStore()
    expect(ui.density).toBe('compact')

    await wrapper.get('input[name="settings-density"][value="comfortable"]').setValue(true)
    expect(ui.density).toBe('comfortable')
    expect(document.documentElement.dataset.density).toBe('comfortable')
    expect(JSON.parse(window.localStorage.getItem('community.ui')).density).toBe('comfortable')

    await wrapper.get('input[name="settings-density"][value="compact"]').setValue(true)
    expect(ui.density).toBe('compact')
    expect(document.documentElement.dataset.density).toBe('compact')
  })
})
