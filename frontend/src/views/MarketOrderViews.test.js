// @vitest-environment jsdom

import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const authState = vi.hoisted(() => ({
  userId: '11111111-1111-7111-8111-111111111111',
  accessToken: 'token'
}))

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
  cancelMarketOrder: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-cancel' }),
  confirmMarketOrder: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-confirm' }),
  deliverMarketOrder: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-deliver' }),
  openMarketOrderDispute: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-dispute' }),
  shipMarketOrder: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-ship' }),
  listBuyingMarketOrders: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-buying' }),
  listSellingMarketOrders: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-selling' }),
  getMarketOrderDetail: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-detail' })
}))

vi.mock('../stores/auth', () => ({
  useAuthStore: () => ({
    accessToken: authState.accessToken,
    authed: !!authState.accessToken,
    userId: authState.userId
  })
}))

import MarketBuyingOrdersView from './MarketBuyingOrdersView.vue'
import MarketSellingOrdersView from './MarketSellingOrdersView.vue'
import MarketOrderDetailView from './MarketOrderDetailView.vue'
import {
  cancelMarketOrder,
  confirmMarketOrder,
  deliverMarketOrder,
  getMarketOrderDetail,
  listBuyingMarketOrders,
  listSellingMarketOrders,
  openMarketOrderDispute,
  shipMarketOrder
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
        UiState: {
          props: ['type'],
          template: '<div><slot /><slot name="description" /></div>'
        },
        UiButton: {
          props: ['disabled'],
          template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>'
        },
        UiInput: {
          props: ['modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        UiTextarea: {
          props: ['modelValue'],
          template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
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
    authState.userId = '11111111-1111-7111-8111-111111111111'
    authState.accessToken = 'token'
    vi.clearAllMocks()
    cancelMarketOrder.mockResolvedValue({ data: {}, traceId: 'trace-cancel' })
    confirmMarketOrder.mockResolvedValue({ data: {}, traceId: 'trace-confirm' })
    deliverMarketOrder.mockResolvedValue({ data: {}, traceId: 'trace-deliver' })
    openMarketOrderDispute.mockResolvedValue({ data: {}, traceId: 'trace-dispute' })
    shipMarketOrder.mockResolvedValue({ data: {}, traceId: 'trace-ship' })
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
    expect(wrapper.text()).toContain('资金状态待确认')
    expect(wrapper.text()).toContain('已交付')
    expect(wrapper.text()).toContain('等待买家确认完成')
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
    expect(wrapper.text()).toContain('已发货')
    expect(wrapper.text()).toContain('等待买家确认收货')
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
    expect(wrapper.find('.market-lifecycle').exists()).toBe(true)
    expect(wrapper.text()).toContain('已创建')
    expect(wrapper.text()).toContain('资金托管')
    expect(wrapper.text()).toContain('履约')
    expect(wrapper.text()).toContain('确认')
    expect(wrapper.text()).toContain('争议')
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

  it('lets a seller deliver a manual virtual order and reloads detail', async () => {
    authState.userId = '22222222-2222-7222-8222-222222222222'
    getMarketOrderDetail.mockResolvedValue({
      data: {
        orderId: 31,
        requestId: 'selling:req-virtual',
        goodsType: 'VIRTUAL',
        deliveryModeSnapshot: 'MANUAL',
        sellerUserId: '22222222-2222-7222-8222-222222222222',
        buyerUserId: '11111111-1111-7111-8111-111111111111',
        listingTitleSnapshot: 'Netflix 卡密',
        status: 'ESCROWED',
        totalAmount: 1500
      },
      traceId: 'trace-detail'
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('提交交付')
    await wrapper.find('textarea').setValue('card-secret-123')
    await wrapper.findAll('button').find((button) => button.text() === '提交交付').trigger('click')
    await flushPromises()

    expect(deliverMarketOrder).toHaveBeenCalledWith('31', { deliveryContent: 'card-secret-123' })
    expect(getMarketOrderDetail).toHaveBeenCalledTimes(2)
  })

  it('lets a seller ship a physical order and reloads detail', async () => {
    authState.userId = '22222222-2222-7222-8222-222222222222'
    routeState.route.params.orderId = '32'
    routeState.route.path = '/market/orders/32'
    routeState.route.fullPath = '/market/orders/32'
    getMarketOrderDetail.mockResolvedValue({
      data: {
        orderId: 32,
        requestId: 'selling:req-physical',
        goodsType: 'PHYSICAL',
        sellerUserId: '22222222-2222-7222-8222-222222222222',
        buyerUserId: '11111111-1111-7111-8111-111111111111',
        listingTitleSnapshot: '二手键盘',
        status: 'ESCROWED',
        totalAmount: 12900
      },
      traceId: 'trace-detail'
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('确认发货')
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('顺丰')
    await inputs[1].setValue('SF1234567890')
    await wrapper.find('textarea').setValue('工作日派送')
    await wrapper.findAll('button').find((button) => button.text() === '确认发货').trigger('click')
    await flushPromises()

    expect(shipMarketOrder).toHaveBeenCalledWith('32', {
      carrierName: '顺丰',
      trackingNo: 'SF1234567890',
      shippingRemark: '工作日派送'
    })
    expect(getMarketOrderDetail).toHaveBeenCalledTimes(2)
  })

  it('lets a buyer confirm and cancel eligible orders', async () => {
    routeState.route.params.orderId = '33'
    routeState.route.path = '/market/orders/33'
    routeState.route.fullPath = '/market/orders/33'
    getMarketOrderDetail.mockResolvedValue({
      data: {
        orderId: 33,
        requestId: 'buying:req-confirm',
        goodsType: 'PHYSICAL',
        sellerUserId: '22222222-2222-7222-8222-222222222222',
        buyerUserId: '11111111-1111-7111-8111-111111111111',
        listingTitleSnapshot: '二手键盘',
        status: 'SHIPPED',
        totalAmount: 12900
      },
      traceId: 'trace-detail'
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('确认收货')
    await wrapper.findAll('button').find((button) => button.text() === '确认收货').trigger('click')
    await flushPromises()

    expect(confirmMarketOrder).toHaveBeenCalledWith('33')
    expect(cancelMarketOrder).not.toHaveBeenCalled()

    getMarketOrderDetail.mockClear()
    getMarketOrderDetail.mockResolvedValue({
      data: {
        orderId: 34,
        requestId: 'buying:req-cancel',
        goodsType: 'VIRTUAL',
        sellerUserId: '22222222-2222-7222-8222-222222222222',
        buyerUserId: '11111111-1111-7111-8111-111111111111',
        listingTitleSnapshot: '会员卡',
        status: 'ESCROWED',
        totalAmount: 500
      },
      traceId: 'trace-detail-cancel'
    })

    routeState.route.params = { orderId: '34' }
    routeState.route.path = '/market/orders/34'
    routeState.route.fullPath = '/market/orders/34'
    await flushPromises()

    expect(wrapper.text()).toContain('取消订单')
    await wrapper.findAll('button').find((button) => button.text() === '取消订单').trigger('click')
    await flushPromises()

    expect(cancelMarketOrder).toHaveBeenCalledWith('34')
  })

  it('lets a buyer open a dispute for a delivered order and reloads detail', async () => {
    routeState.route.params.orderId = '35'
    routeState.route.path = '/market/orders/35'
    routeState.route.fullPath = '/market/orders/35'
    getMarketOrderDetail.mockResolvedValue({
      data: {
        orderId: 35,
        requestId: 'buying:req-dispute',
        goodsType: 'VIRTUAL',
        sellerUserId: '22222222-2222-7222-8222-222222222222',
        buyerUserId: '11111111-1111-7111-8111-111111111111',
        listingTitleSnapshot: '邀请码',
        status: 'DELIVERED',
        totalAmount: 700
      },
      traceId: 'trace-detail'
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('发起申诉')
    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('交付内容无效')
    await wrapper.find('textarea').setValue('邀请码无法使用')
    await wrapper.findAll('button').find((button) => button.text() === '发起申诉').trigger('click')
    await flushPromises()

    expect(openMarketOrderDispute).toHaveBeenCalledWith('35', {
      reason: '交付内容无效',
      buyerNote: '邀请码无法使用'
    })
    expect(getMarketOrderDetail).toHaveBeenCalledTimes(2)
  })
})
