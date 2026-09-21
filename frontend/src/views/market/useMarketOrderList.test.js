// @vitest-environment jsdom

import { defineComponent, reactive, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useMarketOrderList } from './useMarketOrderList'

const USER_ID = '11111111-1111-7111-8111-111111111111'
const OTHER_USER_ID = '22222222-2222-7222-8222-222222222222'

function order(orderId, title) {
  return { orderId, goodsType: 'VIRTUAL', listingTitleSnapshot: title, status: 'DELIVERED', totalAmount: 100 }
}

function pageResponse(data, { hasNext = false, page = 0 } = {}) {
  return { data, hasNext, page, size: 20 }
}

function createSubject({ side = 'buying', auth, pages } = /** @type {{ side?: string, auth?: Record<string, unknown>, pages?: Array<unknown> }} */ ({})) {
  const listOrders = vi.fn()
  if (pages) {
    for (const page of pages) {
      if (page instanceof Error) listOrders.mockRejectedValueOnce(page)
      else listOrders.mockResolvedValueOnce(page)
    }
  }
  listOrders.mockResolvedValue(pageResponse([]))
  const sideRef = ref(side)
  const authReactive = auth ?? {
    authed: true,
    identityEpoch: 1,
    identityUserId: USER_ID
  }

  /** @type {ReturnType<typeof useMarketOrderList> | undefined} */
  let subject
  const Harness = defineComponent({
    setup() {
      subject = useMarketOrderList({
        auth: authReactive,
        listOrders,
        initialError: '加载订单失败',
        moreError: '加载更多订单失败',
        side: sideRef
      })
      return () => null
    }
  })
  mount(Harness)
  if (!subject) throw new Error('harness subject not initialized')
  return { subject, listOrders, sideRef }
}

describe('useMarketOrderList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the first page on mount and exposes the projected market state', async () => {
    const { subject, listOrders } = createSubject()
    await flushPromises()

    expect(listOrders).toHaveBeenCalledWith({ page: 0, size: 20 })
    expect(subject.loading.value).toBe(false)
    expect(subject.state.value.orders).toEqual([])

    listOrders.mockResolvedValueOnce(pageResponse([order(31, '卡密')]))
    await subject.reload()
    await flushPromises()
    expect(subject.state.value.orders).toHaveLength(1)
    expect(subject.state.value.orders[0]).toMatchObject({ orderId: 31, statusLabel: expect.any(String) })
  })

  it('appends pages by page number until hasNext is false', async () => {
    const { subject, listOrders } = createSubject({
      pages: [
        pageResponse([order(31, '第一页')], { hasNext: true, page: 0 }),
        pageResponse([order(41, '第二页')], { hasNext: false, page: 1 })
      ]
    })

    await flushPromises()
    expect(listOrders).toHaveBeenCalledTimes(1)
    expect(subject.hasNext.value).toBe(true)

    await subject.loadMore()
    await flushPromises()

    expect(listOrders.mock.calls.map(([request]) => request.page)).toEqual([0, 1])
    expect(subject.state.value.orders).toHaveLength(2)
    expect(subject.hasNext.value).toBe(false)

    await subject.loadMore()
    expect(listOrders).toHaveBeenCalledTimes(2)
  })

  it('dedupes page-shifted orders by orderId when the index updates between pages', async () => {
    const { subject } = createSubject({
      pages: [
        pageResponse([order(31, 'A'), order(32, 'B')], { hasNext: true, page: 0 }),
        pageResponse([order(32, 'B 移位'), order(33, 'C')], { hasNext: false, page: 1 })
      ]
    })
    await flushPromises()
    await subject.loadMore()
    await flushPromises()

    // 视图投影展开原始字段，类型上用宽Record承载读取。
    const projectedOrders = /** @type {Array<Record<string, unknown>>} */ (subject.state.value.orders)
    const ids = projectedOrders.map((item) => item.orderId)
    expect(new Set(ids).size).toBe(ids.length)
    expect(subject.state.value.orders).toHaveLength(3)
    // orderId 相同的移位条目按后到覆盖：B 移位替换第一页的 B。
    expect(projectedOrders.find((item) => item.orderId === 32)?.listingTitleSnapshot).toBe('B 移位')
  })


  it('keeps loaded orders and reports append failures in pageError for the same-side retry', async () => {
    const { subject, listOrders } = createSubject({
      pages: [
        pageResponse([order(31, '第一页')], { hasNext: true, page: 0 }),
        new Error('temporary order failure'),
        pageResponse([order(41, '第二页')], { hasNext: false, page: 1 })
      ]
    })
    await flushPromises()

    await subject.loadMore()
    await flushPromises()
    expect(subject.pageError.value).toBe('temporary order failure')
    expect(subject.state.value.orders).toHaveLength(1)
    expect(subject.error.value).toBe('')
    expect(subject.loadingMore.value).toBe(false)

    await subject.loadMore()
    await flushPromises()
    expect(listOrders.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(subject.state.value.orders).toHaveLength(2)
    expect(subject.pageError.value).toBe('')
  })
  it('refuses load-more while a first load is in flight', async () => {
    const listOrders = vi.fn().mockImplementation(() => new Promise(() => {}))
    /** @type {ReturnType<typeof useMarketOrderList> | undefined} */
    let subject
    const Harness = defineComponent({
      setup() {
        subject = useMarketOrderList({
          auth: { authed: true, identityEpoch: 1, identityUserId: USER_ID },
          listOrders,
          side: ref('buying')
        })
        return () => null
      }
    })
    mount(Harness)
    if (!subject) throw new Error('harness subject not initialized')
    await vi.waitFor(() => expect(listOrders).toHaveBeenCalledTimes(1))
    expect(subject.loading.value).toBe(true)

    await subject.loadMore()
    expect(listOrders).toHaveBeenCalledTimes(1)
  })

  it('resets and refetches with the new scope when side switches on the same instance', async () => {
    const { subject, listOrders, sideRef } = createSubject({
      side: 'buying',
      pages: [
        pageResponse([order(31, '买单')], { hasNext: false, page: 0 }),
        pageResponse([order(41, '卖单')], { hasNext: false, page: 0 })
      ]
    })
    await flushPromises()
    expect(/** @type {Array<Record<string, unknown>>} */ (subject.state.value.orders).map((item) => item.orderId)).toEqual([31])
    expect(listOrders).toHaveBeenCalledTimes(1)

    sideRef.value = 'selling'
    await vi.waitFor(() => expect(listOrders).toHaveBeenCalledTimes(2))
    await flushPromises()
    expect(/** @type {Array<Record<string, unknown>>} */ (subject.state.value.orders).map((item) => item.orderId)).toEqual([41])
    expect(listOrders).toHaveBeenLastCalledWith({ page: 0, size: 20 })
    expect(subject.error.value).toBe('')
    expect(subject.pageError.value).toBe('')
  })


  it('discards in-flight responses after the identity changes', async () => {
    /** @type {((value: unknown) => void) | undefined} */
    let resolvePrevious
    const listOrders = vi.fn()
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePrevious = resolve }))
      .mockResolvedValueOnce(pageResponse([order(41, '当前身份订单')], { hasNext: false, page: 0 }))
    const auth = reactive({
      authed: true,
      identityEpoch: 1,
      identityUserId: USER_ID
    })
    /** @type {ReturnType<typeof useMarketOrderList> | undefined} */
    let subject
    const Harness = defineComponent({
      setup() {
        subject = useMarketOrderList({ auth, listOrders, side: ref('buying') })
        return () => null
      }
    })
    mount(Harness)
    if (!subject) throw new Error('harness subject not initialized')
    await vi.waitFor(() => expect(listOrders).toHaveBeenCalledTimes(1))


    // 换账号：identityEpoch 推进，watch 触发 reset + reload。
    auth.identityEpoch = 2
    auth.identityUserId = OTHER_USER_ID
    await vi.waitFor(() => expect(listOrders).toHaveBeenCalledTimes(2))
    await flushPromises()
    expect(/** @type {Array<Record<string, unknown>>} */ (subject.state.value.orders).map((item) => item.listingTitleSnapshot)).toEqual(['当前身份订单'])
    await flushPromises()
    if (!resolvePrevious) throw new Error('previous identity resolve not captured')
    resolvePrevious(pageResponse([order(31, '旧身份订单')], { hasNext: false, page: 0 }))
    await flushPromises()
    expect(/** @type {Array<Record<string, unknown>>} */ (subject.state.value.orders).map((item) => item.listingTitleSnapshot)).toEqual(['当前身份订单'])
    await flushPromises()
  })

  it('does not request anything when the viewer is anonymous', async () => {
    const { subject, listOrders, sideRef } = createSubject({
      auth: { authed: false, identityEpoch: 1, identityUserId: USER_ID }
    })

    await flushPromises()
    expect(listOrders).not.toHaveBeenCalled()
    expect(subject.loading.value).toBe(false)
    expect(subject.state.value.orders).toEqual([])

    sideRef.value = 'selling'
    await flushPromises()
    expect(listOrders).not.toHaveBeenCalled()
  })

  it('surfaces the injected fallback copy when the API error carries no message', async () => {
    const { subject, listOrders } = createSubject({
      pages: [
        new Error(),
        pageResponse([order(31, '重试成功')], { hasNext: false, page: 0 })
      ]
    })
    await flushPromises()
    expect(listOrders).toHaveBeenCalledTimes(1)
    expect(subject.error.value).toBe('加载订单失败')
    expect(subject.state.value.orders).toEqual([])

    await subject.reload()
    await flushPromises()
    expect(subject.state.value.orders).toHaveLength(1)
    expect(subject.error.value).toBe('')
  })
})
