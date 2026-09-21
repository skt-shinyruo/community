// @vitest-environment jsdom

import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/** @typedef {{ params: Record<string, unknown>, name: string, path: string, fullPath: string }} MarketRouteLike */
const authState = vi.hoisted(() => ({ state: /** @type {{ userId: string, accessToken: string, authed: boolean, tokenGeneration: number, identityEpoch: number, identityUserId: string } | null} */ (null) }))

const routeState = vi.hoisted(() => ({
  route: /** @type {MarketRouteLike | null} */ (null)
}))

const routerState = vi.hoisted(() => ({ push: /** @type {import('vitest').Mock | null} */ (null) }))

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  const vueActual = /** @type {typeof import('vue')} */ (await vi.importActual('vue'))
  const reactive = vueActual.reactive
  if (!routeState.route) {
    routeState.route = reactive({
      params: { orderId: '31' },
      name: 'marketOrderDetail',
      path: '/market/orders/31',
      fullPath: '/market/orders/31'
    })
  }
  if (!routerState.push) {
    routerState.push = vi.fn()
  }
  return {
    ...actual,
    useRoute: () => routeState.route,
    useRouter: () => ({ push: routerState.push })
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

vi.mock('../stores/auth', async () => {
  const { reactive } = /** @type {{ reactive: typeof import('vue').reactive }} */ (await vi.importActual('vue'))
  if (!authState.state) {
    authState.state = reactive({
      accessToken: 'token',
      authed: true,
      tokenGeneration: 1,
      userId: '11111111-1111-7111-8111-111111111111',
      identityEpoch: 1,
      identityUserId: '11111111-1111-7111-8111-111111111111'
    })
  }
  return { useAuthStore: () => authState.state }
})

import MarketOrderListView from './MarketOrderListView.vue'
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

// 类型别名：vi.mock 替换后的服务函数在本测试里只按 Mock 使用（宽松 payload 不再受真实签名约束）。
const cancelMarketOrderMock = /** @type {import('vitest').Mock} */ (cancelMarketOrder)
const confirmMarketOrderMock = /** @type {import('vitest').Mock} */ (confirmMarketOrder)
const deliverMarketOrderMock = /** @type {import('vitest').Mock} */ (deliverMarketOrder)
const getMarketOrderDetailMock = /** @type {import('vitest').Mock} */ (getMarketOrderDetail)
const listBuyingMarketOrdersMock = /** @type {import('vitest').Mock} */ (listBuyingMarketOrders)
const listSellingMarketOrdersMock = /** @type {import('vitest').Mock} */ (listSellingMarketOrders)
const openMarketOrderDisputeMock = /** @type {import('vitest').Mock} */ (openMarketOrderDispute)
const shipMarketOrderMock = /** @type {import('vitest').Mock} */ (shipMarketOrder)

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
          props: ['variant', 'title'],
          template: '<div :data-variant="variant"><strong v-if="title">{{ title }}</strong><slot /><slot name="description" /><slot name="actions" /></div>'
        },
        UiButton: {
          props: ['disabled', 'variant', 'to'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /></button>'
        },
        UiModalConfirm: {
          props: ['title', 'message', 'confirmText', 'confirmVariant'],
          emits: ['confirm', 'cancel'],
          template: '<div data-test="order-confirm"><p>{{ message }}</p><button data-test="order-confirm-cancel" @click="$emit(\'cancel\')">取消</button><button data-test="order-confirm-ok" @click="$emit(\'confirm\')">{{ confirmText }}</button></div>'
        }
      }
    }
  }
}

function mountOrderList(side) {
  return mount(MarketOrderListView, {
    props: { side },
    ...mountOptions()
  })
}

describe('Unified market order views', () => {
  beforeEach(() => {
    marketRoute().params.orderId = '31'
    marketRoute().path = '/market/orders/31'
    marketRoute().fullPath = '/market/orders/31'
    installIdentity('11111111-1111-7111-8111-111111111111', 'token')
    vi.clearAllMocks()
    cancelMarketOrderMock.mockResolvedValue({ data: {}, traceId: 'trace-cancel' })
    confirmMarketOrderMock.mockResolvedValue({ data: {}, traceId: 'trace-confirm' })
    deliverMarketOrderMock.mockResolvedValue({ data: {}, traceId: 'trace-deliver' })
    openMarketOrderDisputeMock.mockResolvedValue({ data: {}, traceId: 'trace-dispute' })
    shipMarketOrderMock.mockResolvedValue({ data: {}, traceId: 'trace-ship' })
    listBuyingMarketOrdersMock.mockResolvedValue({ data: [], traceId: 'trace-buying' })
    listSellingMarketOrdersMock.mockResolvedValue({ data: [], traceId: 'trace-selling' })
    getMarketOrderDetailMock.mockResolvedValue({ data: {}, traceId: 'trace-detail' })
  })

  it('loads buying orders on mount and renders goods type and status', async () => {
    listBuyingMarketOrdersMock.mockResolvedValue({
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

    const wrapper = mountOrderList('buying')
    await flushPromises()

    expect(listBuyingMarketOrders).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-order-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('虚拟商品')
    expect(wrapper.text()).toContain('待确认')
    expect(wrapper.text()).toContain('托管中')
    expect(wrapper.text()).toContain('已交付')
    expect(wrapper.text()).toContain('等待买家确认完成')
  })

  it('loads selling orders on mount and renders physical order rows', async () => {
    listSellingMarketOrdersMock.mockResolvedValue({
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

    const wrapper = mountOrderList('selling')
    await flushPromises()

    expect(listSellingMarketOrders).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-order-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('已发货')
    expect(wrapper.text()).toContain('已发货')
    expect(wrapper.text()).toContain('等待买家确认收货')
  })

  it.each([
    ['buying', listBuyingMarketOrders],
    ['selling', listSellingMarketOrders]
  ])('discards old %s orders after the authenticated identity changes', async (side, listOrders) => {
    const listOrdersMock = /** @type {import('vitest').Mock} */ (listOrders)
    const oldOrders = deferred()
    listOrdersMock
      .mockReturnValueOnce(oldOrders.promise)
      .mockResolvedValueOnce({
        data: [{ orderId: 42, goodsType: 'PHYSICAL', listingTitleSnapshot: 'B 的私有订单', status: 'ESCROWED' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mountOrderList(side)
    await vi.waitFor(() => expect(listOrdersMock).toHaveBeenCalledTimes(1))
    installIdentity('22222222-2222-7222-8222-222222222222', 'token-b')
    await vi.waitFor(() => expect(listOrdersMock).toHaveBeenCalledTimes(2))
    await flushPromises()

    oldOrders.resolve({
      data: [{ orderId: 31, goodsType: 'VIRTUAL', listingTitleSnapshot: 'A 的私有订单', status: 'DELIVERED' }],
      hasNext: false,
      page: 0,
      size: 20
    })
    await flushPromises()

    expect(wrapper.text()).toContain('B 的私有订单')
    expect(wrapper.text()).not.toContain('A 的私有订单')
  })

  it('renders in-domain buying/selling tabs and deep-links the sibling tab by route', async () => {
    listBuyingMarketOrdersMock.mockResolvedValue({
      data: [
        {
          orderId: 31,
          requestId: 'buying:req-1',
          goodsType: 'VIRTUAL',
          listingTitleSnapshot: 'Netflix 卡密',
          status: 'DELIVERED',
          totalAmount: 1500
        }
      ],
      traceId: 'trace-buying'
    })

    const wrapper = mountOrderList('buying')
    await flushPromises()

    const tablist = wrapper.find('[role="tablist"]')
    expect(tablist.exists()).toBe(true)
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toBe('买入')
    expect(tabs[1].text()).toBe('卖出')
    expect(tabs[0].attributes('aria-selected')).toBe('true')
    expect(tabs[1].attributes('aria-selected')).toBe('false')

    await tabs[1].trigger('click')
    expect(marketPush()).toHaveBeenCalledWith({ name: 'marketSellingOrders' })
  })

  it('activates the sibling tab with arrow keys for keyboard users', async () => {
    const wrapper = mountOrderList('buying')
    await flushPromises()

    await wrapper.find('[role="tablist"]').trigger('keydown', { key: 'ArrowRight' })
    expect(marketPush()).toHaveBeenCalledWith({ name: 'marketSellingOrders' })

    marketPush().mockClear()
    await wrapper.setProps({ side: 'selling' })
    await flushPromises()
    await wrapper.find('[role="tablist"]').trigger('keydown', { key: 'ArrowLeft' })
    expect(marketPush()).toHaveBeenCalledWith({ name: 'marketBuyingOrders' })
  })

  it('keeps buying and selling state isolated when the route reuses the component', async () => {
    listBuyingMarketOrdersMock.mockResolvedValue({
      data: [
        {
          orderId: 31,
          requestId: 'buying:req-1',
          goodsType: 'VIRTUAL',
          listingTitleSnapshot: '买入的卡密',
          status: 'DELIVERED',
          totalAmount: 1500
        }
      ],
      hasNext: false,
      page: 0,
      size: 20
    })

    const wrapper = mountOrderList('buying')
    await flushPromises()
    expect(wrapper.text()).toContain('买入的卡密')

    // 路由复用组件实例：side prop 切换后按新 scope 重取，旧买单列表不得残留在卖单视图。
    listSellingMarketOrdersMock.mockResolvedValue({
      data: [
        {
          orderId: 41,
          requestId: 'selling:req-1',
          goodsType: 'PHYSICAL',
          listingTitleSnapshot: '卖出的键盘',
          status: 'ESCROWED',
          totalAmount: 12900
        }
      ],
      hasNext: false,
      page: 0,
      size: 20
    })
    await wrapper.setProps({ side: 'selling' })
    await flushPromises()

    expect(listSellingMarketOrders).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('卖出的键盘')
    expect(wrapper.text()).not.toContain('买入的卡密')
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs[1].attributes('aria-selected')).toBe('true')
  })

  it('marks in-flight escrow states with a processing badge and text label', async () => {
    listBuyingMarketOrdersMock.mockResolvedValue({
      data: [
        {
          orderId: 36,
          requestId: 'buying:req-pending',
          goodsType: 'VIRTUAL',
          listingTitleSnapshot: '待托管订单',
          status: 'ESCROW_PENDING',
          totalAmount: 800
        }
      ],
      traceId: 'trace-buying'
    })

    const wrapper = mountOrderList('buying')
    await flushPromises()

    const badge = wrapper.find('.market-order-card .badge')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('托管处理中')
    expect(badge.classes()).toContain('badge-pending')
    expect(wrapper.text()).toContain('等待资金托管')
  })

  it('offers a primary next step on empty buying and selling lists', async () => {
    const buying = mountOrderList('buying')
    await flushPromises()
    expect(buying.text()).toContain('暂无购买订单')
    expect(buying.text()).toContain('去市场逛逛')
    buying.unmount()

    const selling = mountOrderList('selling')
    await flushPromises()
    expect(selling.text()).toContain('暂无出售订单')
    expect(selling.text()).toContain('查看我的出售')
    selling.unmount()
  })

  it('shows a retryable error state when the initial order load fails', async () => {
    listBuyingMarketOrdersMock.mockRejectedValueOnce(new Error('网络异常'))

    const wrapper = mountOrderList('buying')
    await flushPromises()
    expect(wrapper.text()).toContain('网络异常')

    listBuyingMarketOrdersMock.mockResolvedValue({
      data: [
        {
          orderId: 31,
          requestId: 'buying:req-1',
          goodsType: 'VIRTUAL',
          listingTitleSnapshot: 'Netflix 卡密',
          status: 'DELIVERED',
          totalAmount: 1500
        }
      ],
      hasNext: false,
      page: 0,
      size: 20
    })
    await wrapper.find('[data-test="market-orders-retry"]').trigger('click')
    await flushPromises()

    expect(listBuyingMarketOrders).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Netflix 卡密')
    expect(wrapper.text()).not.toContain('网络异常')
  })

  it('loads physical order detail and renders shipment information', async () => {
    getMarketOrderDetailMock.mockResolvedValue({
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
    expect(wrapper.find('.market-order-lifecycle').exists()).toBe(true)
    expect(wrapper.text()).toContain('已创建')
    expect(wrapper.text()).toContain('资金托管')
    expect(wrapper.text()).toContain('履约')
    expect(wrapper.text()).toContain('确认')
    expect(wrapper.text()).toContain('争议')
  })

  it('shows the empty detail state when the order detail payload is empty', async () => {
    getMarketOrderDetailMock.mockResolvedValue({ data: {}, traceId: 'trace-detail' })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('暂无订单详情')
    expect(wrapper.text()).not.toContain('订单 #1')
    expect(wrapper.findAll('.market-order-summary')).toHaveLength(0)
  })

  it('ignores stale order detail responses after route changes', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolveFirst
    /** @type {((value: unknown) => void) | undefined} */
    let resolveSecond
    const firstPromise = new Promise((resolve) => { resolveFirst = resolve })
    const secondPromise = new Promise((resolve) => { resolveSecond = resolve })
    getMarketOrderDetailMock.mockImplementation((orderId) => {
      if (orderId === '31') return firstPromise
      if (orderId === '32') return secondPromise
      return Promise.resolve({ data: {}, traceId: 'trace-detail' })
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await nextTick()

    marketRoute().params = { orderId: '32' }
    marketRoute().path = '/market/orders/32'
    marketRoute().fullPath = '/market/orders/32'
    await nextTick()
    if (!resolveSecond) throw new Error('second detail resolve not captured')
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

    if (!resolveFirst) throw new Error('first detail resolve not captured')
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

  it('discards private order detail returned for a previous identity', async () => {
    const oldDetail = deferred()
    getMarketOrderDetailMock
      .mockReturnValueOnce(oldDetail.promise)
      .mockResolvedValueOnce({
        data: {
          orderId: 31,
          requestId: 'user-b-request',
          goodsType: 'VIRTUAL',
          listingTitleSnapshot: 'B 的订单',
          status: 'DELIVERED',
          deliveryContents: ['B-SECRET']
        }
      })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await vi.waitFor(() => expect(getMarketOrderDetail).toHaveBeenCalledTimes(1))
    installIdentity('22222222-2222-7222-8222-222222222222', 'token-b')
    await vi.waitFor(() => expect(getMarketOrderDetail).toHaveBeenCalledTimes(2))
    await flushPromises()

    oldDetail.resolve({
      data: {
        orderId: 31,
        requestId: 'user-a-request',
        goodsType: 'VIRTUAL',
        listingTitleSnapshot: 'A 的订单',
        status: 'DELIVERED',
        deliveryContents: ['A-SECRET']
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('B-SECRET')
    expect(wrapper.text()).not.toContain('A-SECRET')
    expect(wrapper.text()).not.toContain('user-a-request')
  })

  it('lets a seller deliver a manual virtual order and reloads detail', async () => {
    authedState().userId = '22222222-2222-7222-8222-222222222222'
    getMarketOrderDetailMock.mockResolvedValue({
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
    const b = wrapper.findAll('button').find((item) => item.text() === '提交交付')
    if (!b) throw new Error('nf')
    await b.trigger('click')
    await flushPromises()

    expect(deliverMarketOrder).toHaveBeenCalledWith('31', { deliveryContent: 'card-secret-123' })
    expect(getMarketOrderDetail).toHaveBeenCalledTimes(2)
  })

  it('lets a seller ship a physical order and reloads detail', async () => {
    authedState().userId = '22222222-2222-7222-8222-222222222222'
    marketRoute().params.orderId = '32'
    marketRoute().path = '/market/orders/32'
    marketRoute().fullPath = '/market/orders/32'
    getMarketOrderDetailMock.mockResolvedValue({
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
    const button1 = wrapper.findAll('button').find((item) => item.text() === '确认发货')
    if (!button1) throw new Error('button not found: 确认发货')
    await button1.trigger('click')
    await flushPromises()

    expect(shipMarketOrder).toHaveBeenCalledWith('32', {
      carrierName: '顺丰',
      trackingNo: 'SF1234567890',
      shippingRemark: '工作日派送'
    })
    expect(getMarketOrderDetail).toHaveBeenCalledTimes(2)
  })

  it('does not let an old order action reload or clear a newly routed order', async () => {
    authedState().userId = '22222222-2222-7222-8222-222222222222'
    const oldDelivery = deferred()
    deliverMarketOrderMock.mockReturnValueOnce(oldDelivery.promise)
    getMarketOrderDetailMock
      .mockResolvedValueOnce({
        data: sellerManualOrder(31, 'A order')
      })
      .mockResolvedValueOnce({
        data: sellerManualOrder(32, 'B order')
      })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()
    await wrapper.find('textarea').setValue('A-DELIVERY')
    const button2 = wrapper.findAll('button').find((item) => item.text() === '提交交付')
    if (!button2) throw new Error('button not found: 提交交付')
    await button2.trigger('click')
    await vi.waitFor(() => expect(deliverMarketOrder).toHaveBeenCalledWith('31', { deliveryContent: 'A-DELIVERY' }))

    marketRoute().params = { orderId: '32' }
    marketRoute().path = '/market/orders/32'
    marketRoute().fullPath = '/market/orders/32'
    await flushPromises()
    await wrapper.find('textarea').setValue('B-DRAFT')

    oldDelivery.resolve({ data: {}, traceId: 'old-action' })
    await flushPromises()

    expect(getMarketOrderDetail).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('B order')
    expect(wrapper.find('textarea').element.value).toBe('B-DRAFT')
  })

  it('lets a buyer confirm and cancel eligible orders after capital-loss confirmation', async () => {
    marketRoute().params.orderId = '33'
    marketRoute().path = '/market/orders/33'
    marketRoute().fullPath = '/market/orders/33'
    getMarketOrderDetailMock.mockResolvedValue({
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
    const button3 = wrapper.findAll('button').find((item) => item.text() === '确认收货')
    if (!button3) throw new Error('button not found: 确认收货')
    await button3.trigger('click')
    await flushPromises()

    // 确认收货是放款给卖家的资损动作：先经确认弹窗复述金额与后果，确认前不调用接口。
    expect(confirmMarketOrder).not.toHaveBeenCalled()
    const confirmDialog = wrapper.find('[data-test="order-confirm"]')
    expect(confirmDialog.exists()).toBe(true)
    expect(confirmDialog.text()).toContain('12900 积分')
    expect(confirmDialog.text()).toContain('放款给卖家')
    await confirmDialog.find('[data-test="order-confirm-ok"]').trigger('click')
    await flushPromises()

    expect(confirmMarketOrder).toHaveBeenCalledWith('33')
    expect(cancelMarketOrder).not.toHaveBeenCalled()

    getMarketOrderDetailMock.mockClear()
    getMarketOrderDetailMock.mockResolvedValue({
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

    marketRoute().params = { orderId: '34' }
    marketRoute().path = '/market/orders/34'
    marketRoute().fullPath = '/market/orders/34'
    await flushPromises()

    expect(wrapper.text()).toContain('取消订单')
    const button4 = wrapper.findAll('button').find((item) => item.text() === '取消订单')
    if (!button4) throw new Error('button not found: 取消订单')
    await button4.trigger('click')
    await flushPromises()

    // 取消订单中止卖家履约并触发退款，同样需要二次确认。
    expect(cancelMarketOrder).not.toHaveBeenCalled()
    const cancelDialog = wrapper.find('[data-test="order-confirm"]')
    expect(cancelDialog.exists()).toBe(true)
    expect(cancelDialog.text()).toContain('500 积分')
    expect(cancelDialog.text()).toContain('退回')
    await cancelDialog.find('[data-test="order-confirm-ok"]').trigger('click')
    await flushPromises()

    expect(cancelMarketOrder).toHaveBeenCalledWith('34')
  })

  it('dismisses the capital-loss confirmation without calling the order APIs', async () => {
    marketRoute().params.orderId = '33'
    marketRoute().path = '/market/orders/33'
    marketRoute().fullPath = '/market/orders/33'
    getMarketOrderDetailMock.mockResolvedValue({
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

    const button5 = wrapper.findAll('button').find((item) => item.text() === '确认收货')
    if (!button5) throw new Error('button not found: 确认收货')
    await button5.trigger('click')
    await flushPromises()
    const dialog = wrapper.find('[data-test="order-confirm"]')
    expect(dialog.exists()).toBe(true)

    await dialog.find('[data-test="order-confirm-cancel"]').trigger('click')
    await flushPromises()

    expect(confirmMarketOrder).not.toHaveBeenCalled()
    expect(cancelMarketOrder).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="order-confirm"]').exists()).toBe(false)
  })

  it('closes a pending capital-loss confirmation when the route or identity changes', async () => {
    marketRoute().params.orderId = '33'
    marketRoute().path = '/market/orders/33'
    marketRoute().fullPath = '/market/orders/33'
    getMarketOrderDetailMock.mockResolvedValue({
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
    const button6 = wrapper.findAll('button').find((item) => item.text() === '确认收货')
    if (!button6) throw new Error('button not found: 确认收货')
    await button6.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="order-confirm"]').exists()).toBe(true)

    marketRoute().params = { orderId: '36' }
    marketRoute().path = '/market/orders/36'
    marketRoute().fullPath = '/market/orders/36'
    await flushPromises()

    expect(wrapper.find('[data-test="order-confirm"]').exists()).toBe(false)
    expect(confirmMarketOrder).not.toHaveBeenCalled()
  })

  it('labels the viewer role and pending processing state with text, not color alone', async () => {
    getMarketOrderDetailMock.mockResolvedValue({
      data: {
        orderId: 31,
        requestId: 'selling:req-role',
        goodsType: 'VIRTUAL',
        deliveryModeSnapshot: 'MANUAL',
        sellerUserId: '11111111-1111-7111-8111-111111111111',
        buyerUserId: '22222222-2222-7222-8222-222222222222',
        listingTitleSnapshot: 'Netflix 卡密',
        status: 'ESCROW_PENDING',
        totalAmount: 1500
      },
      traceId: 'trace-detail'
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('我是卖家')
    const badge = wrapper.find('.market-order-summary .badge')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('托管处理中')
    expect(badge.classes()).toContain('badge-pending')
    expect(wrapper.text()).toContain('等待资金托管')
  })

  it('shows conservative facts and no controls for an unknown order status', async () => {
    getMarketOrderDetailMock.mockResolvedValue({
      data: {
        orderId: 31,
        requestId: 'future:req-1',
        goodsType: 'VIRTUAL',
        deliveryModeSnapshot: 'MANUAL',
        sellerUserId: '11111111-1111-7111-8111-111111111111',
        buyerUserId: '11111111-1111-7111-8111-111111111111',
        listingTitleSnapshot: '未来状态订单',
        status: 'FUTURE_STATUS',
        totalAmount: 500
      }
    })

    const wrapper = mount(MarketOrderDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('状态待确认')
    expect(wrapper.text()).toContain('资金状态待确认')
    expect(wrapper.text()).toContain('查看订单详情')
    expect(wrapper.text()).not.toContain('订单操作')
    expect(deliverMarketOrder).not.toHaveBeenCalled()
    expect(shipMarketOrder).not.toHaveBeenCalled()
    expect(confirmMarketOrder).not.toHaveBeenCalled()
    expect(cancelMarketOrder).not.toHaveBeenCalled()
    expect(openMarketOrderDispute).not.toHaveBeenCalled()
  })

  it('lets a buyer open a dispute for a delivered order and reloads detail', async () => {
    marketRoute().params.orderId = '35'
    marketRoute().path = '/market/orders/35'
    marketRoute().fullPath = '/market/orders/35'
    getMarketOrderDetailMock.mockResolvedValue({
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
    const button7 = wrapper.findAll('button').find((item) => item.text() === '发起申诉')
    if (!button7) throw new Error('button not found: 发起申诉')
    await button7.trigger('click')
    await flushPromises()

    expect(openMarketOrderDispute).toHaveBeenCalledWith('35', {
      reason: '交付内容无效',
      buyerNote: '邀请码无法使用'
    })
    expect(getMarketOrderDetail).toHaveBeenCalledTimes(2)
  })
})

function installIdentity(userId, accessToken) {
  if (!authState.state) throw new Error('auth state not initialized')
  authedState().userId = userId
  authState.state.accessToken = accessToken
  authState.state.authed = !!accessToken
  authState.state.identityEpoch += 1
  authState.state.identityUserId = userId
}

// 路由与推送 mock 在工厂内惰性初始化，读取处统一经 getter 收窄。
function marketRoute() {
  if (!routeState.route) throw new Error('route state not initialized')
  return routeState.route
}

function marketPush() {
  if (!routerState.push) throw new Error('router push not initialized')
  return routerState.push
}

function authedState() {
  if (!authState.state) throw new Error('auth state not initialized')
  return authState.state
}

function sellerManualOrder(orderId, title) {
  return {
    orderId,
    requestId: `selling:${orderId}`,
    goodsType: 'VIRTUAL',
    deliveryModeSnapshot: 'MANUAL',
    sellerUserId: '22222222-2222-7222-8222-222222222222',
    buyerUserId: '11111111-1111-7111-8111-111111111111',
    listingTitleSnapshot: title,
    status: 'ESCROWED',
    totalAmount: 1500
  }
}

function deferred() {
  /** @type {((value: unknown) => void) | undefined} */
  let resolve
  /** @type {((reason?: unknown) => void) | undefined} */
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  if (!resolve || !reject) throw new Error('deferred controls not captured')
  return { promise, resolve, reject }
}

