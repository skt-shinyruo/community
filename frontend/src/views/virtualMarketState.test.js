import { describe, expect, it } from 'vitest'

import { buildVirtualMarketState } from './virtualMarketState'

describe('views/virtualMarketState', () => {
  it('should label delivery and order status clearly', () => {
    const state = buildVirtualMarketState({
      listings: [{ id: 1, title: 'Steam Key', unitPrice: 1999, deliveryMode: 'PRELOADED', status: 'ACTIVE', stockAvailable: 2 }],
      orders: [{ orderId: 9, status: 'DELIVERED', totalAmount: 3998, autoConfirmAt: '2026-04-04T12:00:00Z' }]
    })

    expect(state.listings[0].deliveryLabel).toBe('自动交付')
    expect(state.orders[0].statusLabel).toBe('待确认')
  })
})
