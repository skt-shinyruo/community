// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick, reactive } from 'vue'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'

const routeState = reactive({
  params: { listingId: '21' },
  name: 'marketInventory',
  path: '/market/my-listings/21/inventory',
  fullPath: '/market/my-listings/21/inventory'
})
const mountedWrappers = []
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


// 类型别名：vi.mock 替换后的服务函数在本测试里只按 Mock 使用（宽松 payload 不再受真实签名约束）。
const listMyMarketListingsMock = /** @type {import('vitest').Mock} */ (listMyMarketListings)
const listMarketInventoryMock = /** @type {import('vitest').Mock} */ (listMarketInventory)
const addMarketInventoryMock = /** @type {import('vitest').Mock} */ (addMarketInventory)
const invalidateMarketInventoryMock = /** @type {import('vitest').Mock} */ (invalidateMarketInventory)
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
          template: '<nav><slot /></nav>'
        },
        UiPageHeader: {
          template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /><slot /></header>'
        },
        UiState: {
          props: ['variant', 'title'],
          template: '<div :data-variant="variant"><strong v-if="title">{{ title }}</strong><slot /><slot name="description" /><slot name="actions" /></div>'
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
function listboxOptions() {
  return /** @type {HTMLElement[]} */ ([...document.body.querySelectorAll('[role="listbox"] [role="option"]')])
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
    listMyMarketListingsMock.mockResolvedValue({ data: [], traceId: 'trace-my-listings' })
    listMarketInventoryMock.mockResolvedValue({ data: [], traceId: 'trace-inventory' })
    addMarketInventoryMock.mockResolvedValue({ data: {}, traceId: 'trace-add' })
    invalidateMarketInventoryMock.mockResolvedValue({ data: {}, traceId: 'trace-invalidate' })
  })

  afterEach(() => {
    while (mountedWrappers.length) mountedWrappers.pop().unmount()
  })

  it('loads seller listings on mount and renders goods type labels with inventory links', async () => {
    listMyMarketListingsMock.mockResolvedValue({
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

    const wrapper = mountView(MarketMyListingsView)
    await flushPromises()

    expect(listMyMarketListings).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('[data-test="my-listing-row"]')).toHaveLength(2)
    expect(wrapper.text()).toContain('虚拟商品')
    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('钱包托管')
    expect(wrapper.text()).toContain('自动交付')
    const inventoryLink = wrapper.findAll('a').find((link) => link.text().includes('库存管理'))
    if (!inventoryLink) throw new Error('inventory link not found')
    expect(inventoryLink).toBeTruthy()
    const inventoryRoute = inventoryLink.attributes('data-to')
    if (!inventoryRoute) throw new Error('inventory route attribute missing')
    expect(JSON.parse(inventoryRoute)).toEqual({
      name: 'marketInventory',
      params: { listingId: 21 }
    })
  })

  it('shows a skeleton during the first seller listings load and a retryable error state on failure', async () => {
    const pending = deferred()
    listMyMarketListingsMock.mockReturnValueOnce(pending.promise)
    const wrapper = mountView(MarketMyListingsView)
    await nextTick()

    expect(wrapper.get('[role="status"]').text()).toContain('正在加载我的出售商品')

    pending.reject(new Error('网络错误'))
    await flushPromises()

    const errorState = wrapper.get('[data-variant="error"]')
    expect(errorState.text()).toContain('网络错误')
    await wrapper.get('[data-test="my-listings-retry"]').trigger('click')
    await flushPromises()

    expect(listMyMarketListings).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('暂无出售商品')
    // 空态承担主要下一步：从空态直接进发布。
    const publishLink = wrapper.findAll('a').find((link) => link.text().includes('发布商品'))
    expect(publishLink).toBeTruthy()
  })

  it('discards seller listings returned for a previous authenticated identity', async () => {
    const oldListings = deferred()
    listMyMarketListingsMock
      .mockReturnValueOnce(oldListings.promise)
      .mockResolvedValueOnce({
        data: [{ listingId: 22, goodsType: 'PHYSICAL', title: 'B 的商品', status: 'ACTIVE' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mountView(MarketMyListingsView)
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

  it('loads inventory on mount and renders the payload table with status badges', async () => {
    listMarketInventoryMock.mockResolvedValue({
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

    const wrapper = mountView(MarketInventoryView)
    await flushPromises()

    expect(listMarketInventory).toHaveBeenCalledWith('21', { page: 0, size: 20 })
    const table = wrapper.get('[data-test="inventory-table"]')
    expect(table.findAll('tbody tr')).toHaveLength(1)
    expect(table.text()).toContain('CODE-001')
    expect(table.text()).toContain('兑换码')
    expect(table.text()).toContain('可售')
    expect(table.text()).not.toContain('AVAILABLE')
  })

  it('sorts inventory rows through the table sort hooks', async () => {
    listMarketInventoryMock.mockResolvedValue({
      data: [
        { inventoryUnitId: 301, listingId: 21, payloadType: 'CODE', payloadContent: 'SOLD-1', status: 'SOLD' },
        { inventoryUnitId: 302, listingId: 21, payloadType: 'CODE', payloadContent: 'FREE-1', status: 'AVAILABLE' },
        { inventoryUnitId: 303, listingId: 21, payloadType: 'CODE', payloadContent: 'DEAD-1', status: 'INVALIDATED' }
      ],
      traceId: 'trace-inventory'
    })

    const wrapper = mountView(MarketInventoryView)
    await flushPromises()

    const firstCellTexts = () => wrapper.findAll('tbody tr').map((row) => row.findAll('td')[0].text())
    expect(firstCellTexts()).toEqual(['SOLD-1', 'FREE-1', 'DEAD-1'])

    const statusHeader = wrapper.findAll('th')[2]
    await statusHeader.get('button').trigger('click')
    expect(statusHeader.attributes('aria-sort')).toBe('ascending')
    expect(firstCellTexts()).toEqual(['FREE-1', 'SOLD-1', 'DEAD-1'])

    await statusHeader.get('button').trigger('click')
    expect(statusHeader.attributes('aria-sort')).toBe('descending')
    expect(firstCellTexts()).toEqual(['DEAD-1', 'SOLD-1', 'FREE-1'])
  })

  it('does not let an old listing response replace inventory after navigation', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolveOldRequest
    listMarketInventoryMock
      .mockImplementationOnce(() => new Promise((resolve) => {
        resolveOldRequest = resolve
      }))
      .mockResolvedValueOnce({
        data: [{ inventoryUnitId: 302, payloadContent: 'NEW-LISTING', status: 'AVAILABLE' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mountView(MarketInventoryView)
    await nextTick()
    routeState.params = { listingId: '22' }
    await flushPromises()

    if (!resolveOldRequest) throw new Error('old inventory resolve not captured')
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
    listMarketInventoryMock
      .mockReturnValueOnce(oldInventory.promise)
      .mockResolvedValueOnce({
        data: [{ inventoryUnitId: 302, payloadContent: 'B-SECRET', status: 'AVAILABLE' }],
        hasNext: false,
        page: 0,
        size: 20
      })

    const wrapper = mountView(MarketInventoryView)
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

  it('keeps the empty-add validation inline on the inventory field without calling the service', async () => {
    const wrapper = mountView(MarketInventoryView)
    await flushPromises()

    await wrapper.get('[data-test="inventory-add-submit"]').trigger('click')
    await flushPromises()

    expect(addMarketInventory).not.toHaveBeenCalled()
    expect(wrapper.get('[role="alert"]').text()).toContain('请至少输入一条库存内容')
  })

  it('submits new inventory batches through the payload type select and reports failures inline', async () => {
    const wrapper = mountView(MarketInventoryView)
    await flushPromises()

    // 内容类型走 UiSelect（APG combobox/listbox）：打开浮层并选中「链接」。
    await wrapper.get('[role="combobox"]').trigger('click')
    await nextTick()
    const linkOption = listboxOptions().find((option) => option.textContent === '链接')
    if (!linkOption) throw new Error('link option not found')
    expect(linkOption).toBeTruthy()
    linkOption.click()
    await nextTick()

    await wrapper.get('textarea').setValue('https://example.com/key-1\nhttps://example.com/key-2')
    await wrapper.get('[data-test="inventory-add-submit"]').trigger('click')
    await flushPromises()

    expect(addMarketInventory).toHaveBeenCalledWith('21', {
      payloadType: 'LINK',
      payloads: ['https://example.com/key-1', 'https://example.com/key-2']
    }, expect.objectContaining({ writeAttempt: expect.any(Object) }))
    expect(wrapper.text()).toContain('库存已追加。')

    addMarketInventoryMock.mockRejectedValueOnce(new Error('库存服务不可用'))
    await wrapper.get('textarea').setValue('https://example.com/key-3')
    await wrapper.get('[data-test="inventory-add-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('库存服务不可用')
  })

  it('reuses the write-attempt key when a failed inventory append is manually retried and renews it after success', async () => {
    const observedKeys = []
    addMarketInventoryMock
      .mockImplementationOnce((_listingId, _payload, { writeAttempt }) => {
        observedKeys.push(writeAttempt.begin())
        return Promise.reject(new Error('网关超时'))
      })
      .mockImplementation((_listingId, _payload, { writeAttempt }) => {
        observedKeys.push(writeAttempt.begin())
        return Promise.resolve({ data: { appended: 1 }, traceId: '' })
      })
    const wrapper = mountView(MarketInventoryView)
    await flushPromises()

    await wrapper.get('textarea').setValue('CODE-1')
    await wrapper.get('[data-test="inventory-add-submit"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('网关超时')

    // 模糊失败后人工重试：同一批卡密复用同一幂等键，服务端按重放去重，不产生重复库存。
    await wrapper.get('[data-test="inventory-add-submit"]').trigger('click')
    await flushPromises()
    expect(observedKeys[1]).toBe(observedKeys[0])
    expect(wrapper.text()).toContain('库存已追加。')

    // 成功后追加下一批属于新的业务尝试，必须生成新 key。
    await wrapper.get('textarea').setValue('CODE-2')
    await wrapper.get('[data-test="inventory-add-submit"]').trigger('click')
    await flushPromises()
    expect(observedKeys[2]).not.toBe(observedKeys[1])
  })

  it('requires a confirmation before invalidating an available unit', async () => {
    listMarketInventoryMock.mockResolvedValue({
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

    const wrapper = mountView(MarketInventoryView)
    await flushPromises()

    // 先取消：高风险动作不进入服务调用。
    const invalidateTrigger = () => {
      const button = wrapper
        .get('[data-test="inventory-table"]')
        .findAll('button')
        .find((item) => item.text() === '失效')
      if (!button) throw new Error('invalidate button not found')
      return button
    }
    await invalidateTrigger().trigger('click')
    await nextTick()
    const dialog = wrapper.get('[role="dialog"]')
    expect(dialog.text()).toContain('CODE-001')
    expect(dialog.text()).toContain('不再可售')
    const cancelButton = dialog.findAll('button').find((button) => button.text() === '取消')
    if (!cancelButton) throw new Error('cancel button not found')
    await cancelButton.trigger('click')
    await nextTick()
    expect(invalidateMarketInventory).not.toHaveBeenCalled()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    // 再确认：经 UiModalConfirm 复述后真正失效并刷新列表。
    await invalidateTrigger().trigger('click')
    await nextTick()
    const confirmButton = wrapper
      .get('[role="dialog"]')
      .findAll('button')
      .find((button) => button.text() === '确认失效')
    if (!confirmButton) throw new Error('confirm button not found')
    await confirmButton.trigger('click')
    await flushPromises()

    expect(invalidateMarketInventory).toHaveBeenCalledWith(301)
    expect(wrapper.text()).toContain('库存已失效。')
    expect(listMarketInventory).toHaveBeenCalledTimes(2)
  })
})

function authenticate(userId, accessToken) {
  useAuthStore().installSession({
    accessToken,
    me: { userId, username: userId }
  })
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
