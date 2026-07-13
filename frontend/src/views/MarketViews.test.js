// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = reactive({
  params: { listingId: '22222222-2222-7222-8222-222222222222' },
  name: 'marketDetail',
  path: '/market/listings/22222222-2222-7222-8222-222222222222',
  fullPath: '/market/listings/22222222-2222-7222-8222-222222222222'
})
const routerPush = vi.fn()

vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRoute: () => routeState,
    useRouter: () => ({ push: routerPush })
  }
})

vi.mock('../api/services/marketService', () => ({
  listMarketListings: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-market-list' }),
  getMarketListingDetail: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-market-detail' }),
  createMarketOrder: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-create-order' }),
  createMarketListing: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-create-listing' }),
  listMarketAddresses: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-addresses' }),
  listAdminMarketDisputes: vi.fn().mockResolvedValue({ data: [], traceId: 'trace-disputes' }),
  adminResolveMarketDispute: vi.fn().mockResolvedValue({ data: {}, traceId: 'trace-resolve' })
}))

import MarketListView from './MarketListView.vue'
import MarketDetailView from './MarketDetailView.vue'
import MarketPublishView from './MarketPublishView.vue'
import AdminMarketDisputesView from './AdminMarketDisputesView.vue'
import {
  adminResolveMarketDispute,
  createMarketListing,
  createMarketOrder,
  getMarketListingDetail,
  listAdminMarketDisputes,
  listMarketAddresses,
  listMarketListings
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

describe('Unified market views', () => {
  beforeEach(() => {
    routeState.params = { listingId: '22222222-2222-7222-8222-222222222222' }
    routeState.name = 'marketDetail'
    routeState.path = '/market/listings/22222222-2222-7222-8222-222222222222'
    routeState.fullPath = '/market/listings/22222222-2222-7222-8222-222222222222'
    vi.clearAllMocks()
    listMarketListings.mockResolvedValue({ data: [], traceId: 'trace-market-list' })
    getMarketListingDetail.mockResolvedValue({ data: {}, traceId: 'trace-market-detail' })
    createMarketOrder.mockResolvedValue({ data: {}, traceId: 'trace-create-order' })
    createMarketListing.mockResolvedValue({ data: {}, traceId: 'trace-create-listing' })
    listMarketAddresses.mockResolvedValue({ data: [], traceId: 'trace-addresses' })
    listAdminMarketDisputes.mockResolvedValue({ data: [], traceId: 'trace-disputes' })
    adminResolveMarketDispute.mockResolvedValue({ data: {}, traceId: 'trace-resolve' })
  })

  it('loads unified listings and renders both goods type labels', async () => {
    listMarketListings.mockResolvedValue({
      data: [
        {
          listingId: 11,
          goodsType: 'VIRTUAL',
          title: 'Steam Key',
          description: '自动交付',
          unitPrice: 1999,
          deliveryMode: 'PRELOADED',
          stockAvailable: 2,
          status: 'ACTIVE'
        },
        {
          listingId: '22222222-2222-7222-8222-222222222222',
          goodsType: 'PHYSICAL',
          title: '二手键盘',
          description: '顺手出',
          unitPrice: 12900,
          stockAvailable: 1,
          status: 'ACTIVE'
        }
      ],
      traceId: 'trace-market-list'
    })

    const wrapper = mount(MarketListView, mountOptions())
    await flushPromises()

    expect(listMarketListings).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('虚拟商品')
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('自动交付')
    expect(wrapper.text()).toContain('实物配送')
    expect(wrapper.findAll('.market-row')).toHaveLength(2)
  })

  it('renders trust-oriented empty market copy', async () => {
    listMarketListings.mockResolvedValue({ data: [], traceId: 'trace-market-list' })

    const wrapper = mount(MarketListView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('履约方式')
    expect(wrapper.text()).toContain('争议可裁定')
    expect(wrapper.text()).not.toContain('前台只按商品类型展示不同的履约语义')
  })

  it('loads a physical listing detail and requires an address for order creation', async () => {
    getMarketListingDetail.mockResolvedValue({
      data: {
        listingId: 21,
        goodsType: 'PHYSICAL',
        title: '二手键盘',
        description: '顺手出',
        unitPrice: 12900,
        stockAvailable: 1,
        status: 'ACTIVE'
      },
      traceId: 'trace-market-detail'
    })
    listMarketAddresses.mockResolvedValue({
      data: [
        {
          addressId: '33333333-3333-7333-8333-333333333333',
          receiverName: '张三',
          city: '上海市',
          detailAddress: '世纪大道 100 号',
          defaultAddress: true
        }
      ],
      traceId: 'trace-addresses'
    })

    const wrapper = mount(MarketDetailView, mountOptions())
    await flushPromises()

    expect(getMarketListingDetail).toHaveBeenCalledWith('22222222-2222-7222-8222-222222222222')
    expect(listMarketAddresses).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('安全下单')
    expect(wrapper.text()).toContain('库存')
    expect(wrapper.text()).toContain('履约')

    await wrapper.find('select').setValue('33333333-3333-7333-8333-333333333333')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(createMarketOrder).toHaveBeenCalledTimes(1)
    expect(createMarketOrder.mock.calls[0][0]).toMatchObject({
      listingId: '22222222-2222-7222-8222-222222222222',
      quantity: 1,
      addressId: '33333333-3333-7333-8333-333333333333'
    })
  })

  it('uses the created order response to show the order id and enter order detail', async () => {
    const orderId = '44444444-4444-7444-8444-444444444444'
    getMarketListingDetail.mockResolvedValue({
      data: {
        listingId: '22222222-2222-7222-8222-222222222222',
        goodsType: 'VIRTUAL',
        title: 'Steam Key',
        description: '自动交付',
        unitPrice: 1999,
        stockAvailable: 2,
        status: 'ACTIVE'
      },
      traceId: 'trace-market-detail'
    })
    createMarketOrder.mockResolvedValue({
      data: {
        orderId,
        status: 'ESCROWED'
      },
      traceId: 'trace-create-order'
    })

    const wrapper = mount(MarketDetailView, mountOptions())
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(createMarketOrder).toHaveBeenCalledWith({
      listingId: '22222222-2222-7222-8222-222222222222',
      quantity: 1,
      addressId: undefined
    })
    expect(wrapper.text()).toContain('订单已创建')
    expect(wrapper.text()).toContain(orderId)
    expect(routerPush).toHaveBeenCalledWith({
      name: 'marketOrderDetail',
      params: { orderId }
    })
  })

  it('publishes a physical listing with goodsType-aware payload', async () => {
    const wrapper = mount(MarketPublishView, mountOptions())

    expect(wrapper.text()).toContain('发布流程')
    expect(wrapper.text()).toContain('交易信息')
    expect(wrapper.text()).toContain('履约信息')
    expect(wrapper.text()).not.toContain('不再拆成独立虚拟市场页面')

    await wrapper.find('select').setValue('PHYSICAL')
    await wrapper.findAll('input')[0].setValue('二手键盘')
    await wrapper.find('textarea').setValue('顺手出')
    await wrapper.findAll('input')[1].setValue('12900')
    await wrapper.findAll('input')[2].setValue('1')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(createMarketListing).toHaveBeenCalledWith(expect.objectContaining({
      goodsType: 'PHYSICAL',
      title: '二手键盘',
      unitPrice: 12900,
      stockTotal: 1
    }))
  })

  it('loads disputes and delegates admin resolution through the unified service', async () => {
    listAdminMarketDisputes.mockResolvedValue({
      data: [
        {
          disputeId: 1,
          goodsType: 'PHYSICAL',
          reason: '货不对板',
          status: 'SELLER_REJECTED'
        }
      ],
      traceId: 'trace-disputes'
    })

    const wrapper = mount(AdminMarketDisputesView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('货不对板')
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).not.toContain('资金状态')
    expect(wrapper.text()).toContain('需要管理员裁定')

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(adminResolveMarketDispute).toHaveBeenCalledWith(1, 'refund', { note: 'refund' })
  })
})
