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
})
