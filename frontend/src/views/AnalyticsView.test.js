// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { uv, dau } = vi.hoisted(() => ({
  uv: vi.fn(),
  dau: vi.fn()
}))

vi.mock('../api/services/analyticsService', () => ({
  uv,
  dau
}))

import { useAuthStore } from '../stores/auth'
import AnalyticsView from './AnalyticsView.vue'

let auth

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  auth = useAuthStore()
  auth.installSession({
    accessToken: 'analytics-token',
    me: {
      userId: 1,
      username: 'admin',
      authorities: ['ROLE_ADMIN']
    }
  })

  return mount(AnalyticsView, {
    global: {
      plugins: [pinia],
      stubs: {
        UiCard: { props: ['flat'], template: '<section><slot /></section>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input v-bind="$attrs" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        UiState: { template: '<div><slot /><slot name="description" /></div>' }
      }
    }
  })
}

describe('AnalyticsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    uv.mockResolvedValue({ data: 1234, traceId: 'trace-uv' })
    dau.mockResolvedValue({ data: 567, traceId: 'trace-dau' })
  })

  it('queries uv/dau for the selected range and renders the readout', async () => {
    const wrapper = mountView()
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('2026-04-01')
    await inputs[1].setValue('2026-04-30')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(uv).toHaveBeenCalledWith({ start: '2026-04-01', end: '2026-04-30' })
    expect(dau).toHaveBeenCalledWith({ start: '2026-04-01', end: '2026-04-30' })
    expect(wrapper.text()).toContain('UV（独立访客）')
    expect(wrapper.text()).toContain('DAU（日活）')
    expect(wrapper.text()).toContain('数据范围')
    expect(wrapper.text()).toContain('数据新鲜度')
    expect(wrapper.text()).toContain('1234')
    expect(wrapper.text()).toContain('567')
    expect(wrapper.text()).not.toContain('占位图表')
  })

  it('keeps the successful metric visible when the sibling metric fails', async () => {
    dau.mockRejectedValueOnce(new Error('DAU unavailable'))
    const wrapper = mountView()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('1234')
    expect(wrapper.text()).toContain('—')
    expect(wrapper.text()).toContain('部分统计加载失败：DAU unavailable')
  })

  it('ignores the earlier date-range response after a newer query completes', async () => {
    const staleUv = deferred()
    const staleDau = deferred()
    uv.mockReset().mockReturnValueOnce(staleUv.promise).mockResolvedValueOnce({ data: 222, traceId: 'trace-new-uv' })
    dau.mockReset().mockReturnValueOnce(staleDau.promise).mockResolvedValueOnce({ data: 22, traceId: 'trace-new-dau' })
    const wrapper = mountView()
    const inputs = wrapper.findAll('input')

    await inputs[0].setValue('2026-04-01')
    await inputs[1].setValue('2026-04-30')
    const firstQuery = wrapper.vm.query()
    wrapper.vm.start = '2026-05-01'
    wrapper.vm.end = '2026-05-31'
    await nextTick()
    await wrapper.vm.query()

    staleUv.resolve({ data: 111, traceId: 'trace-old-uv' })
    staleDau.resolve({ data: 11, traceId: 'trace-old-dau' })
    await firstQuery
    await nextTick()

    expect(wrapper.text()).toContain('222')
    expect(wrapper.text()).toContain('22')
    expect(wrapper.text()).not.toContain('111')
    expect(wrapper.emitted('trace').flat()).toEqual(['trace-new-uv'])
  })

  it('drops pending analytics results after the viewer role changes', async () => {
    const pendingUv = deferred()
    const pendingDau = deferred()
    uv.mockReset().mockReturnValueOnce(pendingUv.promise)
    dau.mockReset().mockReturnValueOnce(pendingDau.promise)
    const wrapper = mountView()

    const queryPromise = wrapper.vm.query()
    auth.setMe({ userId: 1, username: 'former-admin', authorities: ['ROLE_USER'] })
    await nextTick()
    pendingUv.resolve({ data: 999, traceId: 'stale-uv' })
    pendingDau.resolve({ data: 888, traceId: 'stale-dau' })
    await queryPromise

    auth.setMe({ userId: 1, username: 'admin-again', authorities: ['ROLE_ADMIN'] })
    await nextTick()
    expect(wrapper.text()).not.toContain('999')
    expect(wrapper.text()).not.toContain('888')
    expect(wrapper.emitted('trace')).toBeUndefined()
  })
})
