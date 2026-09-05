// @vitest-environment jsdom

import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick, reactive } from 'vue'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'

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
const mountedWrappers = []
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
          props: ['variant', 'title'],
          template: '<div :data-variant="variant"><strong v-if="title">{{ title }}</strong><slot /><slot name="description" /><slot name="actions" /></div>'
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

function mountView(component) {
  const wrapper = mount(component, mountOptions())
  mountedWrappers.push(wrapper)
  return wrapper
}

// UiSelect 浮层 teleport 到 body，选项查询走 document。
function listboxOptions() {
  return [...document.body.querySelectorAll('[role="listbox"] [role="option"]')]
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

  afterEach(() => {
    while (mountedWrappers.length) mountedWrappers.pop().unmount()
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

    const wrapper = mountView(MarketListView)
    await flushPromises()

    expect(listMarketListings).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('虚拟商品')
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('自动交付')
    expect(wrapper.text()).toContain('实物配送')
    expect(wrapper.findAll('.market-listing')).toHaveLength(2)
  })

  it('appends listing pages and retries the same page after a failure', async () => {
    listMarketListings
      .mockResolvedValueOnce({
        data: [{ listingId: 11, goodsType: 'VIRTUAL', title: '第一页', status: 'ACTIVE' }],
        hasNext: true,
        page: 0,
        size: 20
      })
      .mockRejectedValueOnce(new Error('下一页暂不可用'))
      .mockResolvedValueOnce({
        data: [
          { listingId: 11, goodsType: 'VIRTUAL', title: '第一页（更新）', status: 'ACTIVE' },
          { listingId: 12, goodsType: 'PHYSICAL', title: '第二页', status: 'ACTIVE' }
        ],
        hasNext: false,
        page: 1,
        size: 20
      })

    const wrapper = mountView(MarketListView)
    await flushPromises()

    expect(listMarketListings).toHaveBeenNthCalledWith(1, { page: 0, size: 20 })
    await wrapper.get('[data-test="market-load-more"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('下一页暂不可用')
    expect(listMarketListings).toHaveBeenNthCalledWith(2, { page: 1, size: 20 })

    await wrapper.get('[data-test="market-load-more"]').trigger('click')
    await flushPromises()

    expect(listMarketListings).toHaveBeenNthCalledWith(3, { page: 1, size: 20 })
    expect(wrapper.findAll('.market-listing')).toHaveLength(2)
    expect(wrapper.text()).toContain('第一页（更新）')
    expect(wrapper.text()).toContain('第二页')
    expect(wrapper.text()).not.toContain('加载更多')
    expect(wrapper.text()).toContain('已经到底了')
  })

  it('renders trust-oriented empty market copy', async () => {
    listMarketListings.mockResolvedValue({ data: [], traceId: 'trace-market-list' })

    const wrapper = mountView(MarketListView)
    await flushPromises()

    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('履约方式')
    expect(wrapper.text()).toContain('争议可裁定')
    expect(wrapper.text()).not.toContain('前台只按商品类型展示不同的履约语义')
  })

  it('shows a skeleton during the first catalog load', async () => {
    const pending = deferred()
    listMarketListings.mockReturnValueOnce(pending.promise)

    const wrapper = mountView(MarketListView)
    await nextTick()

    expect(wrapper.get('[role="status"]').text()).toContain('正在加载市场商品')
    expect(wrapper.text()).not.toContain('暂无在售商品')

    pending.resolve({ data: [], traceId: 'trace-market-list' })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无在售商品')
  })

  it('offers a retry when the catalog fails to load', async () => {
    listMarketListings
      .mockRejectedValueOnce(new Error('市场服务暂不可用'))
      .mockResolvedValueOnce({
        data: [{ listingId: 11, goodsType: 'VIRTUAL', title: '重试后的商品', status: 'ACTIVE' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mountView(MarketListView)
    await flushPromises()

    expect(wrapper.get('[data-variant="error"]').text()).toContain('市场服务暂不可用')

    await wrapper.get('[data-test="market-list-retry"]').trigger('click')
    await flushPromises()

    expect(listMarketListings).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('重试后的商品')
    expect(wrapper.find('[data-variant="error"]').exists()).toBe(false)
  })

  it('filters the loaded catalog with the in-page search without new requests', async () => {
    listMarketListings.mockResolvedValue({
      data: [
        { listingId: 11, goodsType: 'VIRTUAL', title: 'Steam Key', description: '自动交付', sellerUserId: 'seller-a', status: 'ACTIVE' },
        { listingId: 12, goodsType: 'PHYSICAL', title: '二手键盘', description: '顺手出', sellerUserId: 'seller-b', status: 'ACTIVE' }
      ],
      hasNext: true,
      page: 0,
      size: 20,
      traceId: 'trace-market-list'
    })

    const wrapper = mountView(MarketListView)
    await flushPromises()
    expect(wrapper.findAll('.market-listing')).toHaveLength(2)

    await wrapper.get('[type="search"]').setValue('键盘')
    expect(wrapper.findAll('.market-listing')).toHaveLength(1)
    expect(wrapper.text()).toContain('二手键盘')
    expect(wrapper.text()).not.toContain('Steam Key')
    expect(wrapper.text()).toContain('匹配 1 / 已加载 2 个商品')
    expect(listMarketListings).toHaveBeenCalledTimes(1)

    await wrapper.get('[type="search"]').setValue('不存在的商品')
    expect(wrapper.findAll('.market-listing')).toHaveLength(0)
    expect(wrapper.text()).toContain('没有匹配')

    await wrapper.get('[data-test="market-search-clear"]').trigger('click')
    expect(wrapper.findAll('.market-listing')).toHaveLength(2)
    expect(wrapper.text()).toContain('Steam Key')
  })

  it('shows a detail skeleton during the first load and a retryable error state on failure', async () => {
    const pending = deferred()
    getMarketListingDetail.mockReturnValueOnce(pending.promise)

    const wrapper = mountView(MarketDetailView)
    await nextTick()
    expect(wrapper.get('[role="status"]').text()).toContain('正在加载商品详情')

    pending.reject(new Error('详情服务暂不可用'))
    await flushPromises()
    expect(wrapper.get('[data-variant="error"]').text()).toContain('详情服务暂不可用')

    getMarketListingDetail.mockResolvedValueOnce({
      data: marketListing(LISTING_A, 'VIRTUAL', 'Retry listing'),
      traceId: 'trace-market-detail'
    })
    await wrapper.get('[data-test="market-detail-retry"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Retry listing')
    expect(wrapper.find('[data-variant="error"]').exists()).toBe(false)
  })

  it('keeps the listing visible and reports order failures inline', async () => {
    authenticate()
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'VIRTUAL', 'Inline failure listing'),
      traceId: 'trace-market-detail'
    })
    createMarketOrder.mockRejectedValueOnce(new Error('库存不足'))

    const wrapper = mountView(MarketDetailView)
    await flushPromises()
    await findOrderButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-test="market-order-error"]').text()).toContain('库存不足')
    expect(wrapper.text()).toContain('Inline failure listing')
    expect(wrapper.find('[data-variant="error"]').exists()).toBe(false)
  })

  it('links back to the market catalog instead of a bare breadcrumb', async () => {
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'VIRTUAL', 'Back link listing'),
      traceId: 'trace-market-detail'
    })

    const wrapper = mountView(MarketDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('返回市场')
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

    const wrapper = mountView(MarketDetailView)
    await flushPromises()

    expect(getMarketListingDetail).toHaveBeenCalledWith(LISTING_A)
    expect(listMarketAddresses).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('安全下单')
    expect(wrapper.text()).toContain('库存')
    expect(wrapper.text()).toContain('履约')

    // 收货地址收敛到 UiSelect：默认地址自动选中，显式切换验证 v-model 接线。
    const addressTrigger = wrapper.get('[data-test="market-address-select"]')
    expect(addressTrigger.text()).toContain('张三')
    await addressTrigger.trigger('click')
    await nextTick()
    const addressOption = [...document.body.querySelectorAll('.ui-select__option')]
      .find((el) => el.textContent.includes('张三'))
    await new DOMWrapper(addressOption).trigger('click')
    await vi.waitFor(() => expect(findOrderButton(wrapper).attributes('disabled')).toBeUndefined())
    await findOrderButton(wrapper).trigger('click')
    await vi.waitFor(() => expect(createMarketOrder).toHaveBeenCalledTimes(1))

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

    const wrapper = mountView(MarketDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain(`${goodsType} listing`)
    expect(listMarketAddresses).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="market-address-field"]').exists()).toBe(false)
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

    const wrapper = mountView(MarketDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('Public physical listing')
    expect(wrapper.get('[data-test="market-address-field"]').text()).toContain(failure.message)
    expect(wrapper.find('[data-variant="error"]').exists()).toBe(false)
  })

  it('keeps an empty address state local and blocks a physical order', async () => {
    authenticate()
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'PHYSICAL', 'Physical without address'),
      traceId: 'trace-market-detail'
    })
    listMarketAddresses.mockResolvedValueOnce({ data: [], traceId: 'trace-addresses' })

    const wrapper = mountView(MarketDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('Physical without address')
    expect(wrapper.get('[data-test="market-address-empty"]').exists()).toBe(true)

    await findOrderButton(wrapper).trigger('click')
    await flushPromises()

    expect(createMarketOrder).not.toHaveBeenCalled()
    expect(wrapper.get('[data-test="market-address-field"]').text()).toContain('请选择收货地址')
    expect(wrapper.text()).toContain('Physical without address')
  })

  it('redirects an anonymous order attempt to login without creating an order', async () => {
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'VIRTUAL', 'Anonymous virtual listing'),
      traceId: 'trace-market-detail'
    })

    const wrapper = mountView(MarketDetailView)
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

    const wrapper = mountView(MarketDetailView)
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(1))
    setRouteListing(LISTING_B)
    await nextTick()
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(2))
    await flushPromises()

    expect(wrapper.text()).toContain('Listing B')
    expect(wrapper.get('[data-test="market-address-select"]').text()).toContain('Bob')

    oldAddresses.resolve({ data: [marketAddress(ADDRESS_A, 'Alice')], traceId: 'trace-a' })
    await flushPromises()

    expect(wrapper.text()).toContain('Listing B')
    expect(wrapper.get('[data-test="market-address-select"]').text()).toContain('Bob')
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

    const wrapper = mountView(MarketDetailView)
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(1))
    setRouteListing(LISTING_B)
    await nextTick()
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(2))
    await flushPromises()

    oldAddresses.reject(new Error('old address failure'))
    await flushPromises()

    expect(wrapper.text()).toContain('Listing B')
    expect(wrapper.get('[data-test="market-address-select"]').text()).toContain('Bob')
    expect(wrapper.find('[data-test="market-address-field"] .ui-field-error').exists()).toBe(false)
    expect(wrapper.get('[data-test="market-address-select"]').text()).not.toContain('正在加载')
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

    const wrapper = mountView(MarketDetailView)
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(1))
    authenticate('token-2')
    await nextTick()
    await vi.waitFor(() => expect(listMarketAddresses).toHaveBeenCalledTimes(2))
    await flushPromises()

    expect(getMarketListingDetail).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-test="market-address-select"]').text()).toContain('Bob')

    oldAddresses.resolve({ data: [marketAddress(ADDRESS_A, 'Alice')], traceId: 'trace-a' })
    await flushPromises()

    expect(wrapper.get('[data-test="market-address-select"]').text()).toContain('Bob')
    expect(wrapper.text()).not.toContain('Alice')
  })

  it('discards a late public detail response after navigating to another listing', async () => {
    const oldDetail = deferred()
    getMarketListingDetail
      .mockReturnValueOnce(oldDetail.promise)
      .mockResolvedValueOnce({ data: marketListing(LISTING_B, 'VIRTUAL', 'Listing B') })

    const wrapper = mountView(MarketDetailView)
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

    const wrapper = mountView(MarketDetailView)
    await flushPromises()

    expect(listMarketAddresses).not.toHaveBeenCalled()
    await findOrderButton(wrapper).trigger('click')
    await flushPromises()

    expect(createMarketOrder).toHaveBeenCalledWith({
      listingId: LISTING_A,
      quantity: 1,
      addressId: undefined
    }, expect.objectContaining({ writeAttempt: expect.any(Object) }))
    expect(wrapper.text()).toContain('订单已创建')
    expect(wrapper.text()).toContain(orderId)
    expect(routerPush).toHaveBeenCalledWith({
      name: 'marketOrderDetail',
      params: { orderId }
    })
  })

  it('ignores an order creation response after the authenticated identity changes', async () => {
    authenticate('token-a')
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'VIRTUAL', 'Shared public listing')
    })
    const oldOrder = deferred()
    createMarketOrder.mockReturnValueOnce(oldOrder.promise)

    const wrapper = mountView(MarketDetailView)
    await flushPromises()
    await findOrderButton(wrapper).trigger('click')
    await vi.waitFor(() => expect(createMarketOrder).toHaveBeenCalledTimes(1))

    authenticate('token-b', '88888888-8888-7888-8888-888888888888')
    await nextTick()
    oldOrder.resolve({
      data: { orderId: '99999999-9999-7999-8999-999999999999', status: 'ESCROWED' }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Shared public listing')
    expect(wrapper.text()).not.toContain('99999999-9999-7999-8999-999999999999')
    expect(routerPush).not.toHaveBeenCalled()
    expect(findOrderButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('does not let an old order response commit after the quantity intent changes', async () => {
    authenticate('token-a')
    getMarketListingDetail.mockResolvedValue({
      data: marketListing(LISTING_A, 'VIRTUAL', 'Intent guarded listing')
    })
    const pendingOrder = deferred()
    createMarketOrder.mockReturnValueOnce(pendingOrder.promise)
    const wrapper = mountView(MarketDetailView)
    await flushPromises()

    await findOrderButton(wrapper).trigger('click')
    await vi.waitFor(() => expect(createMarketOrder).toHaveBeenCalledTimes(1))
    const quantityInput = wrapper.get('input')
    expect(quantityInput.attributes('disabled')).toBeDefined()
    quantityInput.element.disabled = false
    await quantityInput.setValue('2')
    pendingOrder.resolve({
      data: { orderId: '99999999-9999-7999-8999-999999999999', status: 'ESCROWED' }
    })
    await flushPromises()

    expect(quantityInput.element.value).toBe('2')
    expect(wrapper.text()).not.toContain('99999999-9999-7999-8999-999999999999')
    expect(routerPush).not.toHaveBeenCalled()
    expect(findOrderButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('publishes a physical listing with goodsType-aware payload', async () => {
    authenticate('token-a')
    const wrapper = mountView(MarketPublishView)

    expect(wrapper.text()).toContain('发布流程')
    expect(wrapper.text()).toContain('交易信息')
    expect(wrapper.text()).toContain('履约信息')
    expect(wrapper.text()).not.toContain('不再拆成独立虚拟市场页面')

    // 商品类型走 UiSelect（APG combobox/listbox）：打开浮层并选中「实物商品」。
    await wrapper.get('[role="combobox"]').trigger('click')
    await nextTick()
    const physicalOption = listboxOptions().find((option) => option.textContent === '实物商品')
    expect(physicalOption).toBeTruthy()
    physicalOption.click()
    await nextTick()

    await wrapper.findAll('input')[0].setValue('二手键盘')
    await wrapper.find('textarea').setValue('顺手出')
    await wrapper.findAll('input')[1].setValue('12900')
    await wrapper.findAll('input')[2].setValue('1')
    await wrapper.get('[data-test="publish-submit"]').trigger('click')
    await flushPromises()

    expect(createMarketListing).toHaveBeenCalledWith(expect.objectContaining({
      goodsType: 'PHYSICAL',
      title: '二手键盘',
      unitPrice: 12900,
      stockTotal: 1
    }))
  })

  it('keeps the preloaded-content validation inline on the field without calling the service', async () => {
    authenticate('token-a')
    const wrapper = mountView(MarketPublishView)

    await wrapper.findAll('input')[0].setValue('Steam 兑换码')
    await wrapper.get('[data-test="publish-submit"]').trigger('click')
    await flushPromises()

    expect(createMarketListing).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('自动交付商品至少需要一条预存内容')

    // 字段错误随内容输入即时清除。
    await wrapper.findAll('textarea')[1].setValue('CODE-001')
    await nextTick()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('reports publish failures inline in the submit area', async () => {
    authenticate('token-a')
    createMarketListing.mockRejectedValueOnce(new Error('发布服务不可用'))
    const wrapper = mountView(MarketPublishView)

    await wrapper.findAll('input')[0].setValue('Steam 兑换码')
    await wrapper.findAll('textarea')[1].setValue('CODE-001')
    await wrapper.get('[data-test="publish-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('发布服务不可用')
    expect(wrapper.text()).not.toContain('发布成功')
  })

  it('clears the publish draft and ignores an old submission after the authenticated identity changes', async () => {
    authenticate('token-a')
    const oldSubmission = deferred()
    createMarketListing.mockReturnValueOnce(oldSubmission.promise)
    const wrapper = mountView(MarketPublishView)

    await wrapper.findAll('input')[0].setValue('A 的私有草稿')
    await wrapper.findAll('textarea')[1].setValue('CODE-A')
    await wrapper.get('[data-test="publish-submit"]').trigger('click')
    await vi.waitFor(() => expect(createMarketListing).toHaveBeenCalledTimes(1))

    authenticate('token-b', '88888888-8888-7888-8888-888888888888')
    await nextTick()

    expect(wrapper.vm.form.title).toBe('')
    expect(wrapper.vm.inventoryText).toBe('')
    expect(wrapper.vm.submitting).toBe(false)
    expect(wrapper.text()).toContain('发布后可从“我的出售”继续管理库存和订单。')

    oldSubmission.resolve({ data: { listingId: LISTING_A } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('发布成功')
    expect(wrapper.vm.form.title).toBe('')
    expect(wrapper.vm.inventoryText).toBe('')
  })

  it('loads disputes and delegates admin resolution through the unified service', async () => {
    authenticateAdmin()
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

    const wrapper = mountView(AdminMarketDisputesView)
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

function authenticate(
  accessToken = 'access-token',
  userId = '77777777-7777-7777-8777-777777777777'
) {
  useAuthStore().installSession({
    accessToken,
    me: { userId, username: 'buyer' }
  })
}

function authenticateAdmin(accessToken = 'admin-access-token') {
  useAuthStore().installSession({
    accessToken,
    me: {
      userId: '88888888-8888-7888-8888-888888888888',
      username: 'market-admin',
      authorities: ['ROLE_ADMIN']
    }
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
