import { describe, expect, it } from 'vitest'
import { buildRewardShopState } from './rewardShopState'

describe('rewardShopState', () => {
  it('derives canRedeem, insufficientBalance, and soldOut for reward items', () => {
    const state = buildRewardShopState({
      rewardBalance: 12,
      items: [
        { id: 1, itemName: '头像框周卡', itemDesc: '自动发放', costBalance: 8, stock: 5, perUserLimit: 1, fulfillmentMode: 'AUTO', status: 'ACTIVE' },
        { id: 2, itemName: '社群资格', itemDesc: '人工发放', costBalance: 18, stock: 5, perUserLimit: 1, fulfillmentMode: 'MANUAL', status: 'ACTIVE' },
        { id: 3, itemName: '限定贴纸', itemDesc: '库存用尽', costBalance: 6, stock: 0, perUserLimit: 1, fulfillmentMode: 'AUTO', status: 'ACTIVE' }
      ],
      orders: []
    })

    expect(state.items[0]).toMatchObject({
      id: 1,
      canRedeem: true,
      insufficientBalance: false,
      soldOut: false
    })
    expect(state.items[1]).toMatchObject({
      id: 2,
      canRedeem: false,
      insufficientBalance: true,
      soldOut: false
    })
    expect(state.items[2]).toMatchObject({
      id: 3,
      canRedeem: false,
      insufficientBalance: false,
      soldOut: true
    })
  })

  it('maps order statuses to distinct ui labels', () => {
    const state = buildRewardShopState({
      rewardBalance: 20,
      items: [],
      orders: [
        { id: 101, status: 'PENDING', itemNameSnapshot: '社群资格', costBalanceSnapshot: 15, fulfillmentModeSnapshot: 'MANUAL' },
        { id: 102, status: 'PROCESSING', itemNameSnapshot: '人工徽章', costBalanceSnapshot: 10, fulfillmentModeSnapshot: 'MANUAL' },
        { id: 103, status: 'FULFILLED', itemNameSnapshot: '头像框周卡', costBalanceSnapshot: 8, fulfillmentModeSnapshot: 'AUTO' },
        { id: 104, status: 'REFUNDED', itemNameSnapshot: '限定贴纸', costBalanceSnapshot: 6, fulfillmentModeSnapshot: 'AUTO' },
        { id: 105, status: 'CANCELLED', itemNameSnapshot: '社群资格', costBalanceSnapshot: 15, fulfillmentModeSnapshot: 'MANUAL' }
      ]
    })

    expect(state.orders.map((order) => order.statusLabel)).toEqual([
      '待处理',
      '处理中',
      '已发放',
      '已退款',
      '已取消'
    ])
  })
})
