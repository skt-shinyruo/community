// @vitest-environment jsdom

import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({
  route: null
}))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  const { reactive } = await vi.importActual('vue')
  if (!routeState.route) {
    routeState.route = reactive({
      params: { orderId: '31' },
      name: 'virtualMarketOrderDetail',
      path: '/market/virtual/orders/31',
      fullPath: '/market/virtual/orders/31'
    })
  }
  return {
    ...actual,
    useRoute: () => routeState.route
  }
})

vi.mock('../api/services/virtualMarketService', () => ({
  listBuyingVirtualOrders: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-buying' }),
  listSellingVirtualOrders: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-selling' }),
  getVirtualOrderDetail: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-detail' })
}))

import VirtualMarketBuyingOrdersView from './VirtualMarketBuyingOrdersView.vue'
import VirtualMarketSellingOrdersView from './VirtualMarketSellingOrdersView.vue'
import VirtualMarketOrderDetailView from './VirtualMarketOrderDetailView.vue'
import {
  getVirtualOrderDetail,
  listBuyingVirtualOrders,
  listSellingVirtualOrders
} from '../api/services/virtualMarketService'

function mountOptions() {
  return {
    global: {
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a><slot /></a>'
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
        }
      }
    }
  }
}

describe('Virtual market order views', () => {
  beforeEach(() => {
    routeState.route.params.orderId = '31'
    routeState.route.path = '/market/virtual/orders/31'
    routeState.route.fullPath = '/market/virtual/orders/31'
    vi.clearAllMocks()
    listBuyingVirtualOrders.mockResolvedValue({ data: [], traceId: 'trace-buying' })
    listSellingVirtualOrders.mockResolvedValue({ data: [], traceId: 'trace-selling' })
    getVirtualOrderDetail.mockResolvedValue({ data: {}, traceId: 'trace-detail' })
  })

  it('loads buying orders on mount and renders request status and amount', async () => {
    listBuyingVirtualOrders.mockResolvedValue({
      data: [
        {
          orderId: 31,
          requestId: 'buying:req-1',
          listingTitleSnapshot: 'Netflix 卡密',
          status: 'DELIVERED',
          totalAmount: 1500,
          autoConfirmAt: '2026-04-04T12:00:00Z'
        }
      ],
      traceId: 'trace-buying'
    })

    const wrapper = mount(VirtualMarketBuyingOrdersView, mountOptions())
    await flushPromises()

    expect(listBuyingVirtualOrders).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-order-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('buying:req-1')
    expect(wrapper.text()).toContain('待确认')
    expect(wrapper.text()).toContain('1500 积分')
  })

  it('loads selling orders on mount and renders seller order rows', async () => {
    listSellingVirtualOrders.mockResolvedValue({
      data: [
        {
          orderId: 32,
          requestId: 'selling:req-1',
          listingTitleSnapshot: '邀请码',
          status: 'ESCROWED',
          totalAmount: 2400
        }
      ],
      traceId: 'trace-selling'
    })

    const wrapper = mount(VirtualMarketSellingOrdersView, mountOptions())
    await flushPromises()

    expect(listSellingVirtualOrders).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-order-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('selling:req-1')
    expect(wrapper.text()).toContain('已托管')
    expect(wrapper.text()).toContain('2400 积分')
  })

  it('loads order detail on mount and renders status amount and delivery contents', async () => {
    getVirtualOrderDetail.mockResolvedValue({
      data: {
        orderId: 31,
        requestId: 'buying:req-1',
        listingTitleSnapshot: 'Netflix 卡密',
        status: 'DELIVERED',
        totalAmount: 1500,
        autoConfirmAt: '2026-04-04T12:00:00Z',
        deliveryContents: ['CODE-001', 'CODE-002']
      },
      traceId: 'trace-detail'
    })

    const wrapper = mount(VirtualMarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(getVirtualOrderDetail).toHaveBeenCalledWith('31')
    expect(wrapper.text()).toContain('待确认')
    expect(wrapper.text()).toContain('1500 积分')
    expect(wrapper.text()).toContain('CODE-001')
    expect(wrapper.text()).toContain('CODE-002')
  })

  it('shows the empty detail state when the order detail payload is empty', async () => {
    getVirtualOrderDetail.mockResolvedValue({ data: {}, traceId: 'trace-detail' })

    const wrapper = mount(VirtualMarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('暂无订单详情')
    expect(wrapper.text()).not.toContain('订单 #1')
    expect(wrapper.findAll('.market-order-row')).toHaveLength(0)
  })

  it('ignores stale order detail responses after route changes', async () => {
    let resolveFirst
    let resolveSecond
    const firstPromise = new Promise((resolve) => { resolveFirst = resolve })
    const secondPromise = new Promise((resolve) => { resolveSecond = resolve })
    getVirtualOrderDetail.mockImplementation((orderId) => {
      if (orderId === '31') return firstPromise
      if (orderId === '32') return secondPromise
      return Promise.resolve({ data: {}, traceId: 'trace-detail' })
    })

    const wrapper = mount(VirtualMarketOrderDetailView, mountOptions())
    await nextTick()

    routeState.route.params = { orderId: '32' }
    routeState.route.path = '/market/virtual/orders/32'
    routeState.route.fullPath = '/market/virtual/orders/32'
    await nextTick()

    expect(getVirtualOrderDetail).toHaveBeenCalledWith('31')
    expect(getVirtualOrderDetail).toHaveBeenCalledWith('32')

    resolveSecond({
      data: {
        orderId: 32,
        requestId: 'selling:req-1',
        listingTitleSnapshot: '邀请码',
        status: 'ESCROWED',
        totalAmount: 2400,
        deliveryContents: ['INVITE-001']
      },
      traceId: 'trace-detail-32'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('selling:req-1')
    expect(wrapper.text()).toContain('2400 积分')

    resolveFirst({
      data: {
        orderId: 31,
        requestId: 'buying:req-1',
        listingTitleSnapshot: 'Netflix 卡密',
        status: 'DELIVERED',
        totalAmount: 1500,
        deliveryContents: ['CODE-001']
      },
      traceId: 'trace-detail-31'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('selling:req-1')
    expect(wrapper.text()).not.toContain('buying:req-1')
  })
})
