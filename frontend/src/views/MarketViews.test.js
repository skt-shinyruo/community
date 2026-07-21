// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuthStore } from '../stores/auth'

const LISTING_A = '22222222-2222-7222-8222-222222222222'
const LISTING_B = '55555555-5555-7555-8555-555555555555'
const ADDRESS_A = '33333333-3333-7333-8333-333333333333'
const ADDRESS_B = '66666666-6666-7666-8666-666666666666'

const routeState = reactive({
  params: { listingId: LISTING_A },
  name: 'marketDetail',
  path: `/market/listings/${LISTING_A}`,
  fullPath: `/market/listings/${LISTING_A}`
})
const routerPush = vi.fn()
let pinia

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
          props: ['variant'],
          template: '<div :data-variant="variant"><slot /><slot name="description" /></div>'
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
    pinia = createPinia()
    setActivePinia(pinia)
    setRouteListing(LISTING_A)
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
    authenticate()
    getMarketListingDetail.mockResolvedValue({
      data: {
        listingId: LISTING_A,
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
          addressId: ADDRESS_A,
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

    expect(getMarketListingDetail).toHaveBeenCalledWith(LISTING_A)
    expect(listMarketAddresses).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('安全下单')
    expect(wrapper.text()).toContain('库存')
    expect(wrapper.text()).toContain('履约')

    await wrapper.find('select').setValue(ADDRESS_A)
    await findOrderButton(wrapper).trigger('click')
    await flushPromises()

    expect(createMarketOrder).toHaveBeenCalledTimes(1)
    expect(createMarketOrder.mock.calls[0][0]).toMatchObject({
      listingId: LISTING_A,
      quantity: 1,
      addressId: ADDRESS_A
    })
  })

  it.each(['PHYSICAL', 'VIRTUAL'])('keeps an anonymous %s listing public without loading addresses', async (goodsType) => {
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, goodsType, `${goodsType} listing`),
      traceId: 'trace-market-detail'
    })

    const wrapper = mount(MarketDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain(`${goodsType} listing`)
    expect(listMarketAddresses).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="market-address-error"]').exists()).toBe(false)
  })

  it.each([
    ['401', Object.assign(new Error('登录状态已失效'), { response: { status: 401 } })],
    ['503', Object.assign(new Error('地址服务暂不可用'), { response: { status: 503 } })]
  ])('keeps the authenticated physical listing visible when address loading returns %s', async (_status, failure) => {
    authenticate()
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'PHYSICAL', 'Public physical listing'),
      traceId: 'trace-market-detail'
    })
    listMarketAddresses.mockRejectedValueOnce(failure)

    const wrapper = mount(MarketDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('Public physical listing')
    expect(wrapper.get('[data-test="market-address-error"]').text()).toContain(failure.message)
    expect(wrapper.find('[data-variant="error"]').exists()).toBe(false)
  })

  it('keeps an empty address state local and blocks a physical order', async () => {
    authenticate()
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'PHYSICAL', 'Physical without address'),
      traceId: 'trace-market-detail'
    })
    listMarketAddresses.mockResolvedValueOnce({ data: [], traceId: 'trace-addresses' })

    const wrapper = mount(MarketDetailView, mountOptions())
    await flushPromises()

    expect(wrapper.text()).toContain('Physical without address')
    expect(wrapper.get('[data-test="market-address-empty"]').exists()).toBe(true)

    await findOrderButton(wrapper).trigger('click')
    await flushPromises()

    expect(createMarketOrder).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="market-address-error"]').text()).toContain('请选择收货地址')
    expect(wrapper.text()).toContain('Physical without address')
  })

  it('redirects an anonymous order attempt to login without creating an order', async () => {
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'VIRTUAL', 'Anonymous virtual listing'),
      traceId: 'trace-market-detail'
    })

    const wrapper = mount(MarketDetailView, mountOptions())
    await flushPromises()
    await findOrderButton(wrapper).trigger('click')
    await flushPromises()

    expect(createMarketOrder).not.toHaveBeenCalled()
    expect(routerPush).toHaveBeenCalledWith({
      name: 'login',
      query: { redirect: `/market/listings/${LISTING_A}` }
    })
  })

  it('discards an old address response after navigating to another listing', async () => {
    authenticate()
    getMarketListingDetail
      .mockResolvedValueOnce({ data: marketListing(LISTING_A, 'PHYSICAL', 'Listing A') })
      .mockResolvedValueOnce({ data: marketListing(LISTING_B, 'PHYSICAL', 'Listing B') })
    const oldAddresses = deferred()
    listMarketAddresses
      .mockReturnValueOnce(oldAddresses.promise)
      .mockResolvedValueOnce({ data: [marketAddress(ADDRESS_B, 'Bob')], traceId: 'trace-b' })

    const wrapper = mount(MarketDetailView, mountOptions())
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(1))
    setRouteListing(LISTING_B)
    await nextTick()
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(2))
    await flushPromises()

    expect(wrapper.text()).toContain('Listing B')
    expect(wrapper.get('[data-test="market-address-select"]').element.value).toBe(ADDRESS_B)

    oldAddresses.resolve({ data: [marketAddress(ADDRESS_A, 'Alice')], traceId: 'trace-a' })
    await flushPromises()

    expect(wrapper.text()).toContain('Listing B')
    expect(wrapper.get('[data-test="market-address-select"]').element.value).toBe(ADDRESS_B)
    expect(wrapper.text()).not.toContain('Alice')
  })

  it('discards an old address failure without clearing the new listing address state', async () => {
    authenticate()
    getMarketListingDetail
      .mockResolvedValueOnce({ data: marketListing(LISTING_A, 'PHYSICAL', 'Listing A') })
      .mockResolvedValueOnce({ data: marketListing(LISTING_B, 'PHYSICAL', 'Listing B') })
    const oldAddresses = deferred()
    listMarketAddresses
      .mockReturnValueOnce(oldAddresses.promise)
      .mockResolvedValueOnce({ data: [marketAddress(ADDRESS_B, 'Bob')], traceId: 'trace-b' })

    const wrapper = mount(MarketDetailView, mountOptions())
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(1))
    setRouteListing(LISTING_B)
    await nextTick()
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(2))
    await flushPromises()

    oldAddresses.reject(new Error('old address failure'))
    await flushPromises()

    expect(wrapper.text()).toContain('Listing B')
    expect(wrapper.get('[data-test="market-address-select"]').element.value).toBe(ADDRESS_B)
    expect(wrapper.find('[data-test="market-address-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-test="market-address-loading"]').exists()).toBe(false)
  })

  it('reloads only private addresses on token generation changes and discards the old response', async () => {
    authenticate('token-1')
    getMarketListingDetail.mockResolvedValueOnce({
      data: marketListing(LISTING_A, 'PHYSICAL', 'Generation listing')
    })
    const oldAddresses = deferred()
    listMarketAddresses
      .mockReturnValueOnce(oldAddresses.promise)
      .mockResolvedValueOnce({ data: [marketAddress(ADDRESS_B, 'Bob')], traceId: 'trace-b' })

    const wrapper = mount(MarketDetailView, mountOptions())
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(1))
    authenticate('token-2')
    await nextTick()
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(2))
    await flushPromises()

    expect(getMarketListingDetail).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-test="market-address-select"]').element.value).toBe(ADDRESS_B)

    oldAddresses.resolve({ data: [marketAddress(ADDRESS_A, 'Alice')], traceId: 'trace-a' })
    await flushPromises()

    expect(wrapper.get('[data-test="market-address-select"]').element.value).toBe(ADDRESS_B)
    expect(wrapper.text()).not.toContain('Alice')
  })

  it('discards a late public detail response after navigating to another listing', async () => {
    const oldDetail = deferred()
    getMarketListingDetail
      .mockReturnValueOnce(oldDetail.promise)
      .mockResolvedValueOnce({ data: marketListing(LISTING_B, 'VIRTUAL', 'Listing B') })

    const wrapper = mount(MarketDetailView, mountOptions())
    await vi.waitFor(() => expect(getMarketListingDetail).toHaveBeenCalledTimes(1))
    setRouteListing(LISTING_B)
    await nextTick()
    await flushPromises()
    expect(wrapper.text()).toContain('Listing B')

    oldDetail.resolve({ data: marketListing(LISTING_A, 'PHYSICAL', 'Listing A') })
    await flushPromises()

    expect(wrapper.text()).toContain('Listing B')
    expect(wrapper.text()).not.toContain('Listing A')
    expect(listMarketAddresses).not.toHaveBeenCalled()
  })

  it('uses the created order response to show the order id and enter order detail', async () => {
    authenticate()
    const orderId = '44444444-4444-7444-8444-444444444444'
    getMarketListingDetail.mockResolvedValue({
      data: {
        listingId: LISTING_A,
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

    expect(listMarketAddresses).not.toHaveBeenCalled()
    await findOrderButton(wrapper).trigger('click')
    await flushPromises()

    expect(createMarketOrder).toHaveBeenCalledWith({
      listingId: LISTING_A,
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

function authenticate(accessToken = 'access-token') {
  useAuthStore().installSession({
    accessToken,
    me: { userId: '77777777-7777-7777-8777-777777777777', username: 'buyer' }
  })
}

function setRouteListing(listingId) {
  routeState.params = { listingId }
  routeState.name = 'marketDetail'
  routeState.path = `/market/listings/${listingId}`
  routeState.fullPath = `/market/listings/${listingId}`
}

function marketListing(listingId, goodsType, title) {
  return {
    listingId,
    goodsType,
    title,
    description: `${title} description`,
    unitPrice: 12900,
    stockAvailable: 1,
    status: 'ACTIVE'
  }
}

function marketAddress(addressId, receiverName) {
  return {
    addressId,
    receiverName,
    city: '上海市',
    detailAddress: `${receiverName} road 100`,
    defaultAddress: true
  }
}

function findOrderButton(wrapper) {
  return wrapper.findAll('button').find((button) => button.text().includes('安全下单'))
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
