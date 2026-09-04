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

function sectionTabs(wrapper) {
  return wrapper.findAll('[role="tab"]')
}

function activeSectionTab(wrapper) {
  return sectionTabs(wrapper).find((tab) => tab.attributes('aria-selected') === 'true')
}

describe('SettingsView section contract', () => {
  let pinia
  let router

  async function mountAt(path, options = {}) {
    await router.push(path)
    await router.isReady()
    const wrapper = mount(SettingsView, {
      ...options,
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

  it('exposes accessible section tabs wired to their panels', async () => {
    const wrapper = await mountAt('/settings?section=appearance')

    const tablist = wrapper.get('[role="tablist"]')
    expect(tablist.attributes('aria-label')).toBe('设置分区')

    const tabs = sectionTabs(wrapper)
    expect(tabs.map((tab) => tab.text())).toEqual(['公开资料', '外观', '收货地址'])

    expect(activeSectionTab(wrapper)?.text()).toBe('外观')
    for (const tab of tabs) {
      const isActive = tab.attributes('aria-selected') === 'true'
      expect(tab.attributes('tabindex')).toBe(isActive ? '0' : '-1')
      const panel = wrapper.get(`#${tab.attributes('aria-controls')}`)
      expect(panel.attributes('role')).toBe('tabpanel')
      expect(panel.attributes('aria-labelledby')).toBe(tab.attributes('id'))
      expect(panel.element.style.display).toBe(isActive ? '' : 'none')
    }
  })

  it('defaults to the profile section and canonicalizes a missing section query', async () => {
    const wrapper = await mountAt('/settings')

    expect(wrapper.text()).toContain('头像上传')
    expect(activeSectionTab(wrapper)?.text()).toBe('公开资料')
    expect(router.currentRoute.value.query).toEqual({ section: 'profile' })
  })

  it('falls back to the profile section and canonicalizes an invalid section query', async () => {
    const wrapper = await mountAt('/settings?section=bogus')

    expect(wrapper.text()).toContain('头像上传')
    expect(wrapper.text()).not.toContain('正在加载地址簿')
    expect(activeSectionTab(wrapper)?.text()).toBe('公开资料')
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
    expect(activeSectionTab(wrapper)?.text()).toBe('收货地址')
    expect(router.currentRoute.value.query).toEqual({ section: 'addresses' })
  })

  it('switches sections through the tabs and updates the URL', async () => {
    const wrapper = await mountAt('/settings?section=profile')

    await sectionTabs(wrapper).find((tab) => tab.text() === '外观').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ section: 'appearance' })
    expect(wrapper.text()).toContain('跟随系统')
    expect(activeSectionTab(wrapper)?.text()).toBe('外观')

    await sectionTabs(wrapper).find((tab) => tab.text() === '收货地址').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ section: 'addresses' })
    expect(listMarketAddresses).toHaveBeenCalledTimes(1)
    expect(activeSectionTab(wrapper)?.text()).toBe('收货地址')
  })

  it('drives the deep link through the tablist keyboard model', async () => {
    const wrapper = await mountAt('/settings?section=profile', { attachTo: document.body })
    const tablist = wrapper.get('[role="tablist"]')

    await tablist.trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ section: 'appearance' })
    expect(activeSectionTab(wrapper)?.text()).toBe('外观')
    expect(document.activeElement?.textContent).toBe('外观')

    await tablist.trigger('keydown', { key: 'End' })
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ section: 'addresses' })
    expect(listMarketAddresses).toHaveBeenCalledTimes(1)

    await tablist.trigger('keydown', { key: 'ArrowRight' })
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ section: 'profile' })

    await tablist.trigger('keydown', { key: 'ArrowLeft' })
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ section: 'addresses' })

    await tablist.trigger('keydown', { key: 'Home' })
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ section: 'profile' })
    expect(activeSectionTab(wrapper)?.text()).toBe('公开资料')
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
