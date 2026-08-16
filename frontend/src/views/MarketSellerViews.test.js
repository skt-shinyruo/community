// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'

const routeState = reactive({
  params: { listingId: '21' },
  name: 'marketInventory',
  path: '/market/my-listings/21/inventory',
  fullPath: '/market/my-listings/21/inventory'
})
let pinia

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
      plugins: [pinia],
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
        }
      }
    }
  }
}

describe('Unified market seller views', () => {
  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    authenticate('seller-a', 'token-a')
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

  it('discards seller listings returned for a previous authenticated identity', async () => {
    const oldListings = deferred()
    listMyMarketListings
      .mockReturnValueOnce(oldListings.promise)
      .mockResolvedValueOnce({
        data: [{ listingId: 22, goodsType: 'PHYSICAL', title: 'B 的商品', status: 'ACTIVE' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mount(MarketMyListingsView, mountOptions())
    await vi.waitFor(() => expect(listMyMarketListings).toHaveBeenCalledTimes(1))
    authenticate('seller-b', 'token-b')
    await vi.waitFor(() => expect(listMyMarketListings).toHaveBeenCalledTimes(2))
    await flushPromises()

    oldListings.resolve({
      data: [{ listingId: 21, goodsType: 'VIRTUAL', title: 'A 的私有商品', status: 'ACTIVE' }],
      hasNext: false,
      page: 0,
      size: 20
    })
    await flushPromises()

    expect(wrapper.text()).toContain('B 的商品')
    expect(wrapper.text()).not.toContain('A 的私有商品')
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

    expect(listMarketInventory).toHaveBeenCalledWith('21', { page: 0, size: 20 })
    expect(wrapper.findAll('.market-order-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('CODE-001')
    expect(wrapper.text()).toContain('可售')
    expect(wrapper.text()).not.toContain('AVAILABLE')
  })

  it('does not let an old listing response replace inventory after navigation', async () => {
    let resolveOldRequest
    listMarketInventory
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveOldRequest = resolve
      }))
      .mockResolvedValueOnce({
        data: [{ inventoryUnitId: 302, payloadContent: 'NEW-LISTING', status: 'AVAILABLE' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mount(MarketInventoryView, mountOptions())
    await nextTick()
    routeState.params = { listingId: '22' }
    await flushPromises()

    resolveOldRequest({
      data: [{ inventoryUnitId: 301, payloadContent: 'OLD-LISTING', status: 'AVAILABLE' }],
      hasNext: false,
      page: 0,
      size: 20
    })
    await flushPromises()

    expect(listMarketInventory).toHaveBeenNthCalledWith(2, '22', { page: 0, size: 20 })
    expect(wrapper.text()).toContain('NEW-LISTING')
    expect(wrapper.text()).not.toContain('OLD-LISTING')
  })

  it('discards inventory returned for a previous authenticated identity', async () => {
    const oldInventory = deferred()
    listMarketInventory
      .mockReturnValueOnce(oldInventory.promise)
      .mockResolvedValueOnce({
        data: [{ inventoryUnitId: 302, payloadContent: 'B-SECRET', status: 'AVAILABLE' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mount(MarketInventoryView, mountOptions())
    await vi.waitFor(() => expect(listMarketInventory).toHaveBeenCalledTimes(1))
    authenticate('seller-b', 'token-b')
    await vi.waitFor(() => expect(listMarketInventory).toHaveBeenCalledTimes(2))
    await flushPromises()

    oldInventory.resolve({
      data: [{ inventoryUnitId: 301, payloadContent: 'A-SECRET', status: 'AVAILABLE' }],
      hasNext: false,
      page: 0,
      size: 20
    })
    await flushPromises()

    expect(wrapper.text()).toContain('B-SECRET')
    expect(wrapper.text()).not.toContain('A-SECRET')
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

function authenticate(userId, accessToken) {
  useAuthStore().installSession({
    accessToken,
    me: { userId, username: userId }
  })
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}
