// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = reactive({
  params: { listingId: '21' },
  name: 'virtualMarketInventory',
  path: '/market/virtual/my-listings/21/inventory',
  fullPath: '/market/virtual/my-listings/21/inventory'
})

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routeState
  }
})

vi.mock('../api/services/virtualMarketService', () => ({
  listMyVirtualListings: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-my-listings' }),
  listVirtualInventory: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-inventory' }),
  addVirtualInventory: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-add' }),
  invalidateVirtualInventory: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-invalidate' })
}))

import VirtualMarketInventoryView from './VirtualMarketInventoryView.vue'
import VirtualMarketMyListingsView from './VirtualMarketMyListingsView.vue'
import {
  addVirtualInventory,
  invalidateVirtualInventory,
  listMyVirtualListings,
  listVirtualInventory
} from '../api/services/virtualMarketService'

function mountOptions() {
  return {
    global: {
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :data-to="JSON.stringify(to)"><slot /></a>'
        },
        UiBreadcrumb: {
          template: '<div><slot /></div>'
        },
        UiCard: {
          template: '<section><slot /></section>'
        },
        UiPageHeader: {
          template: '<header><slot name="title" /><slot name="subtitle" /><slot /></header>'
        },
        UiEmpty: {
          props: ['type'],
          template: '<div><slot /><slot name="description" /></div>'
        },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        }
      }
    }
  }
}

describe('Virtual market seller views', () => {
  beforeEach(() => {
    routeState.params = { listingId: '21' }
    routeState.path = '/market/virtual/my-listings/21/inventory'
    routeState.fullPath = '/market/virtual/my-listings/21/inventory'
    vi.clearAllMocks()
    listMyVirtualListings.mockResolvedValue({ data: [], traceId: 'trace-my-listings' })
    listVirtualInventory.mockResolvedValue({ data: [], traceId: 'trace-inventory' })
    addVirtualInventory.mockResolvedValue({ data: {}, traceId: 'trace-add' })
    invalidateVirtualInventory.mockResolvedValue({ data: {}, traceId: 'trace-invalidate' })
  })

  it('loads seller listings on mount and renders listing rows with inventory links', async () => {
    listMyVirtualListings.mockResolvedValue({
      data: [
        {
          listingId: 21,
          title: 'Steam 兑换码',
          description: '库存页继续维护卡密',
          unitPrice: 1999,
          deliveryMode: 'PRELOADED',
          stockAvailable: 2,
          status: 'ACTIVE'
        }
      ],
      traceId: 'trace-my-listings'
    })

    const wrapper = mount(VirtualMarketMyListingsView, mountOptions())
    await flushPromises()

    expect(listMyVirtualListings).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('Steam 兑换码')
    expect(wrapper.text()).toContain('自动交付')
    expect(wrapper.text()).toContain('剩余 2')
    expect(wrapper.findAll('a').some((link) => link.text().includes('库存管理'))).toBe(true)
  })

  it('loads inventory on mount and renders payload rows', async () => {
    listVirtualInventory.mockResolvedValue({
      data: [
        {
          inventoryUnitId: 301,
          listingId: 21,
          payloadType: 'CODE',
          payloadContent: 'CODE-001',
          status: 'AVAILABLE'
        }
      ],
      traceId: 'trace-inventory'
    })

    const wrapper = mount(VirtualMarketInventoryView, mountOptions())
    await flushPromises()

    expect(listVirtualInventory).toHaveBeenCalledWith('21')
    expect(wrapper.findAll('.market-order-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('CODE-001')
    expect(wrapper.text()).toContain('AVAILABLE')
  })

  it('submits new inventory batches and invalidates available units', async () => {
    listVirtualInventory.mockResolvedValue({
      data: [
        {
          inventoryUnitId: 301,
          listingId: 21,
          payloadType: 'CODE',
          payloadContent: 'CODE-001',
          status: 'AVAILABLE'
        }
      ],
      traceId: 'trace-inventory'
    })

    const wrapper = mount(VirtualMarketInventoryView, mountOptions())
    await flushPromises()

    await wrapper.find('select').setValue('CODE')
    await wrapper.find('textarea').setValue('CODE-002\nCODE-003')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(addVirtualInventory).toHaveBeenCalledWith('21', {
      payloadType: 'CODE',
      payloads: ['CODE-002', 'CODE-003']
    })

    const invalidateButton = wrapper.findAll('button').at(1)
    await invalidateButton.trigger('click')
    await flushPromises()

    expect(invalidateVirtualInventory).toHaveBeenCalledWith(301)
  })
})
