import { describe, expect, it } from 'vitest'

import { buildMarketState, filterMarketListings, marketOrderCancelConfirmation, marketOrderConfirmConfirmation, nextTableSort, sortMarketInventory } from './marketState'

describe('views/marketState', () => {
  it('should derive type labels and mixed fulfillment labels from goodsType', () => {
    const state = buildMarketState({
      listings: [
        {
          listingId: '11111111-1111-7111-8111-111111111111',
          sellerUserId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          goodsType: 'VIRTUAL',
          title: 'Steam Key',
          unitPrice: 1999,
          deliveryMode: 'PRELOADED',
          status: 'ACTIVE',
          stockAvailable: 2
        },
        {
          listingId: '22222222-2222-7222-8222-222222222222',
          sellerUserId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          goodsType: 'PHYSICAL',
          title: '二手键盘',
          unitPrice: 12900,
          status: 'ACTIVE',
          stockAvailable: 3
        }
      ]
    })

    expect(state.listings[0].goodsTypeLabel).toBe('虚拟商品')
    expect(state.listings[0].deliveryLabel).toBe('自动交付')
    expect(state.listings[0].fulfillmentLabel).toBe('自动交付')
    expect(state.listings[0].trustLabel).toBe('钱包托管')
    expect(state.listings[1].goodsTypeLabel).toBe('实物商品')
    expect(state.listings[1].fulfillmentLabel).toBe('实物配送')
    expect(state.listings[1].sellerLabel).toBe('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb')
  })

  it('adds trust labels to active listings', () => {
    const state = buildMarketState({
      listings: [
        { listingId: '11111111-1111-7111-8111-111111111111', goodsType: 'VIRTUAL', deliveryMode: 'PRELOADED', status: 'ACTIVE', unitPrice: 10, stockAvailable: 2 },
        { listingId: '22222222-2222-7222-8222-222222222222', goodsType: 'PHYSICAL', status: 'ACTIVE', unitPrice: 20, stockAvailable: 1 }
      ]
    })

    expect(state.listings[0]).toMatchObject({
      trustLabel: '钱包托管',
      fulfillmentLabel: '自动交付',
      statusLabel: '在售',
      statusVariant: 'success'
    })
    expect(state.listings[1]).toMatchObject({
      trustLabel: '钱包托管',
      fulfillmentLabel: '实物配送',
      statusLabel: '在售',
      statusVariant: 'success'
    })
  })

  it('maps listing status to distinguishable badge variants', () => {
    const state = buildMarketState({
      listings: [
        { listingId: '1', goodsType: 'VIRTUAL', status: 'ACTIVE' },
        { listingId: '2', goodsType: 'VIRTUAL', status: 'SOLD_OUT' },
        { listingId: '3', goodsType: 'VIRTUAL', status: 'PAUSED' },
        { listingId: '4', goodsType: 'VIRTUAL', status: 'CLOSED' },
        { listingId: '5', goodsType: 'VIRTUAL', status: '' }
      ]
    })

    expect(state.listings.map((item) => item.statusVariant)).toEqual([
      'success',
      'warning',
      'default',
      'default',
      'default'
    ])
  })

  it('filters loaded listings by the in-page search keyword', () => {
    const state = buildMarketState({
      listings: [
        { listingId: '1', goodsType: 'VIRTUAL', title: 'Steam Key', description: '自动交付', sellerUserId: 'seller-a', status: 'ACTIVE' },
        { listingId: '2', goodsType: 'PHYSICAL', title: '二手键盘', description: '顺手出', sellerUserId: 'seller-b', status: 'ACTIVE' }
      ]
    })

    expect(filterMarketListings(state.listings, '')).toHaveLength(2)
    expect(filterMarketListings(state.listings, '  ')).toHaveLength(2)
    expect(filterMarketListings(state.listings, '键盘').map((item) => item.listingId)).toEqual(['2'])
    expect(filterMarketListings(state.listings, 'STEAM').map((item) => item.listingId)).toEqual(['1'])
    expect(filterMarketListings(state.listings, 'seller-b').map((item) => item.listingId)).toEqual(['2'])
    expect(filterMarketListings(state.listings, '不存在')).toHaveLength(0)
    expect(filterMarketListings(null, '键盘')).toHaveLength(0)
  })

  it('builds order lifecycle and next-action copy for operational rows', () => {
    const state = buildMarketState({
      orders: [
        {
          orderId: '99999999-9999-7999-8999-999999999999',
          goodsType: 'PHYSICAL',
          status: 'SHIPPED',
          totalAmount: 88,
          listingTitleSnapshot: '键盘'
        }
      ]
    })

    expect(state.orders[0]).toMatchObject({
      fundsLabel: '托管中',
      fulfillmentLabel: '已发货',
      nextActionLabel: '等待买家确认收货'
    })
    expect(state.orders[0].lifecycleSteps.map((it) => it.label)).toEqual([
      '已创建',
      '资金托管',
      '已发货',
      '待确认',
      '无争议'
    ])
  })

  it('derives every backend order status and an unknown status from one fact set', () => {
    const cases = [
      ['ESCROW_PENDING', '托管处理中', '托管处理中', '等待履约', '等待资金托管', ['cancel']],
      ['ESCROWED', '已托管', '托管中', '等待履约', '等待卖家履约', ['fulfill', 'cancel']],
      ['DELIVERED', '待确认', '托管中', '已交付', '等待买家确认完成', ['confirm', 'dispute']],
      ['SHIPPED', '已发货', '托管中', '已发货', '等待买家确认收货', ['confirm', 'dispute']],
      ['RELEASE_PENDING', '放款处理中', '放款处理中', '已发货', '等待放款完成', []],
      ['COMPLETED', '已完成', '已放款', '已完成', '订单已完成', []],
      ['REFUND_PENDING', '退款处理中', '退款处理中', '等待履约', '等待退款完成', []],
      ['CANCELLED', '已取消', '已取消', '等待履约', '订单已取消', []],
      ['ESCROW_CANCEL_PENDING', '取消处理中', '取消托管处理中', '等待履约', '等待取消订单', []],
      ['ESCROW_FAILED', '托管失败', '托管失败', '等待履约', '资金托管失败', []],
      ['DISPUTED', '申诉中', '托管中', '争议处理中', '等待争议处理', []],
      ['DISPUTE_REFUND_PENDING', '争议退款处理中', '退款处理中', '争议处理中', '等待争议退款完成', []],
      ['DISPUTE_RELEASE_PENDING', '争议放款处理中', '放款处理中', '争议处理中', '等待争议放款完成', []],
      ['REFUNDED', '已退款', '已退款', '等待履约', '退款已完成', []],
      ['FUTURE_STATUS', '状态待确认', '资金状态待确认', '等待履约', '查看订单详情', []]
    ]

    for (const [status, statusLabel, fundsLabel, fulfillmentLabel, nextActionLabel, allowedActions] of cases) {
      const state = buildMarketState({
        orders: [{
          orderId: `order-${status}`,
          goodsType: 'PHYSICAL',
          deliveryModeSnapshot: 'MANUAL',
          status,
          totalAmount: 88
        }]
      })
      expect(state.orders[0]).toMatchObject({
        statusLabel,
        fundsLabel,
        fulfillmentLabel,
        nextActionLabel,
        allowedActions
      })
    }

    const escrowPending = buildMarketState({ orders: [{ status: 'ESCROW_PENDING', goodsType: 'PHYSICAL' }] }).orders[0]
    expect(escrowPending.lifecycleSteps[1]).toMatchObject({ label: '托管处理中', state: 'active' })

    const releasePending = buildMarketState({ orders: [{ status: 'RELEASE_PENDING', goodsType: 'PHYSICAL' }] }).orders[0]
    expect(releasePending.lifecycleSteps[1]).toMatchObject({ label: '资金托管', state: 'complete' })
    expect(releasePending.lifecycleSteps[2]).toMatchObject({ label: '已发货', state: 'complete' })
    expect(releasePending.lifecycleSteps[3]).toMatchObject({ label: '待确认', state: 'active' })

    const disputePending = buildMarketState({ orders: [{ status: 'DISPUTE_REFUND_PENDING', goodsType: 'VIRTUAL' }] }).orders[0]
    expect(disputePending.lifecycleSteps[2]).toMatchObject({ label: '争议处理中', state: 'complete' })
    expect(disputePending.lifecycleSteps[4]).toMatchObject({ label: '争议处理中', state: 'active' })

    const unknown = buildMarketState({ orders: [{ status: 'FUTURE_STATUS', goodsType: 'PHYSICAL' }] }).orders[0]
    expect(unknown.lifecycleSteps).toEqual([
      { key: 'created', label: '已创建', state: 'complete' },
      { key: 'funds', label: '等待托管', state: 'pending' },
      { key: 'fulfillment', label: '等待履约', state: 'pending' },
      { key: 'confirmation', label: '待确认', state: 'pending' },
      { key: 'dispute', label: '无争议', state: 'pending' }
    ])
  })

  it('labels address, inventory, and dispute operational states', () => {
    const state = buildMarketState({
      disputes: [
        { disputeId: '33333333-3333-7333-8333-333333333333', goodsType: 'VIRTUAL', status: 'SELLER_REJECTED', reason: '未收到', buyerNote: '兑换码无效', sellerNote: '不同意退款' }
      ],
      addresses: [
        { addressId: '77777777-7777-7777-8777-777777777777', receiverName: '李四', city: '北京', detailAddress: '中关村 1 号', defaultAddress: true }
      ],
      inventory: [
        { inventoryUnitId: '11111111-aaaa-7111-8111-111111111111', status: 'AVAILABLE' },
        { inventoryUnitId: '22222222-bbbb-7222-8222-222222222222', status: 'INVALIDATED' }
      ]
    })

    expect(state.disputes[0]).toMatchObject({
      buyerNote: '兑换码无效',
      sellerNote: '不同意退款',
      nextActionLabel: '需要管理员裁定'
    })
    expect(state.addresses[0].defaultLabel).toBe('默认地址')
    expect(state.inventory[0].statusLabel).toBe('可售')
    expect(state.inventory[1].statusLabel).toBe('已失效')
  })

  it('projects inventory status rank, badge variant and payload type label for the seller table', () => {
    const state = buildMarketState({
      inventory: [
        { inventoryUnitId: 'u1', status: 'AVAILABLE', payloadType: 'CODE' },
        { inventoryUnitId: 'u2', status: 'LOCKED', payloadType: 'TEXT' },
        { inventoryUnitId: 'u3', status: 'RESERVED', payloadType: 'LINK' },
        { inventoryUnitId: 'u4', status: 'SOLD', payloadType: 'CODE' },
        { inventoryUnitId: 'u5', status: 'INVALIDATED', payloadType: 'CODE' },
        { inventoryUnitId: 'u6', status: 'ARCHIVED', payloadType: 'FILE' },
        { inventoryUnitId: 'u7', status: '', payloadType: '' }
      ]
    })

    expect(state.inventory.map((item) => item.statusRank)).toEqual([0, 1, 1, 2, 3, 4, 4])
    expect(state.inventory.map((item) => item.statusVariant)).toEqual([
      'success', 'pending', 'pending', 'warning', 'default', 'default', 'default'
    ])
    expect(state.inventory.map((item) => item.payloadTypeLabel)).toEqual([
      '兑换码', '文本', '链接', '兑换码', '兑换码', 'FILE', '未知类型'
    ])
  })

  it('toggles table sort state with direction flip on the same column and asc reset on a new column', () => {
    expect(nextTableSort(undefined, 'status')).toEqual({ key: 'status', direction: 'asc' })
    expect(nextTableSort({ key: '', direction: 'asc' }, 'status')).toEqual({ key: 'status', direction: 'asc' })

    const descending = nextTableSort({ key: 'status', direction: 'asc' }, 'status')
    expect(descending).toEqual({ key: 'status', direction: 'desc' })
    expect(nextTableSort(descending, 'status')).toEqual({ key: 'status', direction: 'asc' })

    expect(nextTableSort({ key: 'status', direction: 'desc' }, 'payloadType')).toEqual({
      key: 'payloadType',
      direction: 'asc'
    })
    expect(nextTableSort({ key: 'status', direction: 'asc' }, '')).toEqual({ key: '', direction: 'asc' })
  })

  it('sorts inventory projections by status rank and payload type label without mutating input', () => {
    const state = buildMarketState({
      inventory: [
        { inventoryUnitId: 'u1', status: 'INVALIDATED', payloadType: 'CODE' },
        { inventoryUnitId: 'u2', status: 'AVAILABLE', payloadType: 'LINK' },
        { inventoryUnitId: 'u3', status: 'SOLD', payloadType: 'TEXT' },
        { inventoryUnitId: 'u4', status: 'AVAILABLE', payloadType: 'CODE' }
      ]
    })

    expect(sortMarketInventory(state.inventory, { key: 'status', direction: 'asc' }).map((item) => item.inventoryUnitId))
      .toEqual(['u2', 'u4', 'u3', 'u1'])
    expect(sortMarketInventory(state.inventory, { key: 'status', direction: 'desc' }).map((item) => item.inventoryUnitId))
      .toEqual(['u1', 'u3', 'u2', 'u4'])
    expect(sortMarketInventory(state.inventory, { key: 'payloadType', direction: 'asc' }).map((item) => item.inventoryUnitId))
      .toEqual(['u1', 'u4', 'u2', 'u3'])

    // 未知排序键与空输入保持原顺序、返回新数组且不改动入参。
    const untouched = state.inventory
    expect(sortMarketInventory(untouched, { key: 'payloadContent', direction: 'asc' })).toEqual(untouched)
    expect(sortMarketInventory(untouched, { key: 'payloadContent', direction: 'asc' })).not.toBe(untouched)
    expect(sortMarketInventory(null, { key: 'status', direction: 'asc' })).toEqual([])
    expect(untouched.map((item) => item.inventoryUnitId)).toEqual(['u1', 'u2', 'u3', 'u4'])
  })

  it('does not synthesize entity identifiers from aliases or array indexes', () => {
    const state = buildMarketState({
      listings: [{ id: 'legacy-listing' }],
      orders: [{}],
      disputes: [{}],
      addresses: [{}],
      inventory: [{ id: 'legacy-inventory' }]
    })

    expect(state.listings[0]).not.toHaveProperty('listingId')
    expect(state.orders[0]).not.toHaveProperty('orderId')
    expect(state.disputes[0]).not.toHaveProperty('disputeId')
    expect(state.addresses[0]).not.toHaveProperty('addressId')
    expect(state.inventory[0]).not.toHaveProperty('inventoryUnitId')
  })

  it('maps order status to distinguishable badge variants, with processing states as pending', () => {
    const state = buildMarketState({
      orders: [
        { orderId: '1', status: 'COMPLETED' },
        { orderId: '2', status: 'DISPUTED' },
        { orderId: '3', status: 'DISPUTE_REFUND_PENDING' },
        { orderId: '4', status: 'ESCROW_FAILED' },
        { orderId: '5', status: 'ESCROW_PENDING' },
        { orderId: '6', status: 'RELEASE_PENDING' },
        { orderId: '7', status: 'REFUND_PENDING' },
        { orderId: '8', status: 'ESCROW_CANCEL_PENDING' },
        { orderId: '9', status: 'ESCROWED' },
        { orderId: '10', status: 'DELIVERED' },
        { orderId: '11', status: 'SHIPPED' },
        { orderId: '12', status: 'CANCELLED' },
        { orderId: '13', status: 'REFUNDED' },
        { orderId: '14', status: 'FUTURE_STATUS' }
      ]
    })

    expect(state.orders.map((item) => item.statusVariant)).toEqual([
      'success',
      'danger',
      'danger',
      'danger',
      'pending',
      'pending',
      'pending',
      'pending',
      'accent',
      'accent',
      'accent',
      'default',
      'default',
      'default'
    ])
    // 未知状态继续保守回落：文字仍承担状态语义，不给可误读的颜色。
    expect(state.orders[13].statusLabel).toBe('状态待确认')
  })

  it('builds capital-loss confirmation copy that restates the escrowed amount and consequence', () => {
    const confirm = marketOrderConfirmConfirmation({ totalAmountText: '12900 积分', confirmButtonText: '确认收货' })
    expect(confirm).toMatchObject({
      title: '确认收货',
      confirmText: '确认收货',
      variant: 'danger'
    })
    expect(confirm.message).toContain('12900 积分')
    expect(confirm.message).toContain('放款给卖家')
    expect(confirm.message).toContain('不可撤销')

    const fallback = marketOrderConfirmConfirmation({})
    expect(fallback.title).toBe('确认完成')
    expect(fallback.message).toContain('托管资金')

    const cancel = marketOrderCancelConfirmation({ totalAmountText: '500 积分' })
    expect(cancel).toMatchObject({
      title: '取消订单',
      confirmText: '取消订单',
      variant: 'danger'
    })
    expect(cancel.message).toContain('500 积分')
    expect(cancel.message).toContain('退回')
    expect(cancel.message).toContain('不可撤销')
  })
})
