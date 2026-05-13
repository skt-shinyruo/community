// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
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

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)

  const auth = useAuthStore()
  auth.setMe({
    userId: 1,
    username: 'admin',
    authorities: ['ROLE_ADMIN']
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
})
