import { describe, expect, it } from 'vitest'

import { buildMarketState } from './marketState'

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
      statusLabel: '在售'
    })
    expect(state.listings[1]).toMatchObject({
      trustLabel: '钱包托管',
      fulfillmentLabel: '实物配送',
      statusLabel: '在售'
    })
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

  it('preserves every canonical pending order state in operational labels', () => {
    const cases = [
      ['ESCROW_PENDING', '托管处理中', '托管处理中', '等待资金托管'],
      ['RELEASE_PENDING', '放款处理中', '放款处理中', '等待放款完成'],
      ['REFUND_PENDING', '退款处理中', '退款处理中', '等待退款完成'],
      ['ESCROW_CANCEL_PENDING', '取消处理中', '取消托管处理中', '等待取消订单'],
      ['ESCROW_FAILED', '托管失败', '托管失败', '资金托管失败'],
      ['DISPUTE_REFUND_PENDING', '争议退款处理中', '退款处理中', '等待争议退款完成'],
      ['DISPUTE_RELEASE_PENDING', '争议放款处理中', '放款处理中', '等待争议放款完成']
    ]

    for (const [status, statusLabel, fundsLabel, nextActionLabel] of cases) {
      const state = buildMarketState({
        orders: [{
          orderId: `order-${status}`,
          goodsType: 'PHYSICAL',
          deliveryModeSnapshot: 'MANUAL',
          status,
          totalAmount: 88
        }]
      })
      expect(state.orders[0]).toMatchObject({ statusLabel, fundsLabel, nextActionLabel })
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
})
