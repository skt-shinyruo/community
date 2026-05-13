// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = reactive({
  params: { listingId: '21' },
  name: 'marketInventory',
  path: '/market/my-listings/21/inventory',
  fullPath: '/market/my-listings/21/inventory'
})

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routeState
  }
})

vi.mock('../api/services/marketService', () => ({
  listMyMarketListings: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-my-listings' }),
  listMarketInventory: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-inventory' }),
  addMarketInventory: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-add' }),
  invalidateMarketInventory: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-invalidate' })
}))

import MarketInventoryView from './MarketInventoryView.vue'
import MarketMyListingsView from './MarketMyListingsView.vue'
import {
  addMarketInventory,
  invalidateMarketInventory,
  listMarketInventory,
  listMyMarketListings
} from '../api/services/marketService'

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
        UiState: {
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

describe('Unified market seller views', () => {
  beforeEach(() => {
    routeState.params = { listingId: '21' }
    routeState.path = '/market/my-listings/21/inventory'
    routeState.fullPath = '/market/my-listings/21/inventory'
    vi.clearAllMocks()
    listMyMarketListings.mockResolvedValue({ data: [], traceId: 'trace-my-listings' })
    listMarketInventory.mockResolvedValue({ data: [], traceId: 'trace-inventory' })
    addMarketInventory.mockResolvedValue({ data: {}, traceId: 'trace-add' })
    invalidateMarketInventory.mockResolvedValue({ data: {}, traceId: 'trace-invalidate' })
  })

  it('loads seller listings on mount and renders goods type labels with inventory links', async () => {
    listMyMarketListings.mockResolvedValue({
      data: [
        {
          listingId: 21,
          goodsType: 'VIRTUAL',
          title: 'Steam 兑换码',
          description: '库存页继续维护卡密',
          unitPrice: 1999,
          deliveryMode: 'PRELOADED',
          stockAvailable: 2,
          status: 'ACTIVE'
        },
        {
          listingId: 22,
          goodsType: 'PHYSICAL',
          title: '二手键盘',
          description: '顺手出',
          unitPrice: 12900,
          stockAvailable: 1,
          status: 'ACTIVE'
        }
      ],
      traceId: 'trace-my-listings'
    })

    const wrapper = mount(MarketMyListingsView, mountOptions())
    await flushPromises()

    expect(listMyMarketListings).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-row')).toHaveLength(2)
    expect(wrapper.text()).toContain('虚拟商品')
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('自动交付')
    expect(wrapper.findAll('a').some((link) => link.text().includes('库存管理'))).toBe(true)
  })

  it('loads inventory on mount and renders payload rows', async () => {
    listMarketInventory.mockResolvedValue({
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

    const wrapper = mount(MarketInventoryView, mountOptions())
    await flushPromises()

    expect(listMarketInventory).toHaveBeenCalledWith('21')
    expect(wrapper.findAll('.market-order-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('CODE-001')
    expect(wrapper.text()).toContain('可售')
    expect(wrapper.text()).not.toContain('AVAILABLE')
  })

  it('submits new inventory batches and invalidates available units', async () => {
    listMarketInventory.mockResolvedValue({
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

    const wrapper = mount(MarketInventoryView, mountOptions())
    await flushPromises()

    await wrapper.find('select').setValue('CODE')
    await wrapper.find('textarea').setValue('CODE-002\nCODE-003')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(addMarketInventory).toHaveBeenCalledWith('21', {
      payloadType: 'CODE',
      payloads: ['CODE-002', 'CODE-003']
    })

    const invalidateButton = wrapper.findAll('button').at(1)
    await invalidateButton.trigger('click')
    await flushPromises()

    expect(invalidateMarketInventory).toHaveBeenCalledWith(301)
  })
})
