import { describe, expect, it } from 'vitest'

import { buildMarketState } from './marketState'

describe('views/marketState', () => {
  it('should derive type labels and mixed fulfillment labels from goodsType', () => {
    const state = buildMarketState({
      listings: [
        {
          listingId: 11,
          goodsType: 'VIRTUAL',
          title: 'Steam Key',
          unitPrice: 1999,
          deliveryMode: 'PRELOADED',
          status: 'ACTIVE',
          stockAvailable: 2
        },
        {
          listingId: 21,
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
    expect(state.listings[1].shipmentLabel).toBe('等待卖家发货')
  })

  it('adds trust labels to active listings', () => {
    const state = buildMarketState({
      listings: [
        { listingId: 1, goodsType: 'VIRTUAL', deliveryMode: 'PRELOADED', status: 'ACTIVE', unitPrice: 10, stockAvailable: 2 },
        { listingId: 2, goodsType: 'PHYSICAL', status: 'ACTIVE', unitPrice: 20, stockAvailable: 1 }
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
          orderId: 9,
          goodsType: 'PHYSICAL',
          status: 'SHIPPED',
          escrowStatus: 'ESCROWED',
          fulfillmentStatus: 'SHIPPED',
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

  it('labels address, inventory, and dispute operational states', () => {
    const state = buildMarketState({
      disputes: [
        { disputeId: 3, goodsType: 'VIRTUAL', status: 'SELLER_REJECTED', reason: '未收到', fundState: 'ESCROWED' }
      ],
      addresses: [
        { addressId: 7, receiverName: '李四', city: '北京', detailAddress: '中关村 1 号', defaultAddress: true }
      ],
      inventory: [
        { inventoryUnitId: 1, status: 'AVAILABLE' },
        { inventoryUnitId: 2, status: 'INVALIDATED' }
      ]
    })

    expect(state.disputes[0]).toMatchObject({
      fundStateLabel: '资金托管中',
      nextActionLabel: '需要管理员裁定'
    })
    expect(state.addresses[0].defaultLabel).toBe('默认地址')
    expect(state.inventory[0].statusLabel).toBe('可售')
    expect(state.inventory[1].statusLabel).toBe('已失效')
  })
})
