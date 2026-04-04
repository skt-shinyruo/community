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
      name: 'marketOrderDetail',
      path: '/market/orders/31',
      fullPath: '/market/orders/31'
    })
  }
  return {
    ...actual,
    useRoute: () => routeState.route
  }
})

vi.mock('../api/services/marketService', () => ({
  listBuyingMarketOrders: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-buying' }),
  listSellingMarketOrders: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-selling' }),
  getMarketOrderDetail: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-detail' })
}))

import MarketBuyingOrdersView from './MarketBuyingOrdersView.vue'
import MarketSellingOrdersView from './MarketSellingOrdersView.vue'
import MarketOrderDetailView from './MarketOrderDetailView.vue'
import {
  getMarketOrderDetail,
  listBuyingMarketOrders,
  listSellingMarketOrders
} from '../api/services/marketService'

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

describe('Unified market order views', () => {
  beforeEach(() => {
    routeState.route.params.orderId = '31'
    routeState.route.path = '/market/orders/31'
    routeState.route.fullPath = '/market/orders/31'
    vi.clearAllMocks()
    listBuyingMarketOrders.mockResolvedValue({ data: [], traceId: 'trace-buying' })
    listSellingMarketOrders.mockResolvedValue({ data: [], traceId: 'trace-selling' })
    getMarketOrderDetail.mockResolvedValue({ data: {}, traceId: 'trace-detail' })
  })

  it('loads buying orders on mount and renders goods type and status', async () => {
    listBuyingMarketOrders.mockResolvedValue({
      data: [
        {
          orderId: 31,
          requestId: 'buying:req-1',
          goodsType: 'VIRTUAL',
          listingTitleSnapshot: 'Netflix 卡密',
          status: 'DELIVERED',
          totalAmount: 1500,
          autoConfirmAt: '2026-04-04T12:00:00Z'
        }
      ],
      traceId: 'trace-buying'
    })

    const wrapper = mount(MarketBuyingOrdersView, mountOptions())
    await flushPromises()

    expect(listBuyingMarketOrders).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-order-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('虚拟商品')
    expect(wrapper.text()).toContain('待确认')
  })

  it('loads selling orders on mount and renders physical order rows', async () => {
    listSellingMarketOrders.mockResolvedValue({
      data: [
        {
          orderId: 32,
          requestId: 'selling:req-1',
          goodsType: 'PHYSICAL',
          listingTitleSnapshot: '二手键盘',
          status: 'SHIPPED',
          totalAmount: 12900
        }
      ],
      traceId: 'trace-selling'
    })

    const wrapper = mount(MarketSellingOrdersView, mountOptions())
    await flushPromises()

    expect(listSellingMarketOrders).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-order-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('已发货')
  })

  it('loads physical order detail and renders shipment information', async () => {
    getMarketOrderDetail.mockResolvedValue({
      data: {
        orderId: 31,
        requestId: 'buying:req-1',
        goodsType: 'PHYSICAL',
        listingTitleSnapshot: '二手键盘',
        status: 'SHIPPED',
        totalAmount: 12900,
        shipment: {
          carrierName: '顺丰',
          trackingNo: 'SF1234567890',
          shippingRemark: '工作日派送'
        }
      },
      traceId: 'trace-detail'
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(getMarketOrderDetail).toHaveBeenCalledWith('31')
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('顺丰')
    expect(wrapper.text()).toContain('SF1234567890')
  })

  it('shows the empty detail state when the order detail payload is empty', async () => {
    getMarketOrderDetail.mockResolvedValue({ data: {}, traceId: 'trace-detail' })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
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
    getMarketOrderDetail.mockImplementation((orderId) => {
      if (orderId === '31') return firstPromise
      if (orderId === '32') return secondPromise
      return Promise.resolve({ data: {}, traceId: 'trace-detail' })
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await nextTick()

    routeState.route.params = { orderId: '32' }
    routeState.route.path = '/market/orders/32'
    routeState.route.fullPath = '/market/orders/32'
    await nextTick()

    resolveSecond({
      data: {
        orderId: 32,
        requestId: 'selling:req-1',
        goodsType: 'PHYSICAL',
        listingTitleSnapshot: '二手键盘',
        status: 'SHIPPED',
        totalAmount: 12900
      },
      traceId: 'trace-detail-32'
    })
    await flushPromises()

    resolveFirst({
      data: {
        orderId: 31,
        requestId: 'buying:req-1',
        goodsType: 'VIRTUAL',
        listingTitleSnapshot: 'Netflix 卡密',
        status: 'DELIVERED',
        totalAmount: 1500
      },
      traceId: 'trace-detail-31'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('selling:req-1')
    expect(wrapper.text()).not.toContain('buying:req-1')
  })
})
