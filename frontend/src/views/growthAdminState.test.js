import { describe, expect, it } from 'vitest'
import { buildGrowthAdminState } from './growthAdminState'

describe('growthAdminState', () => {
  it('account rows derive adjustment affordances and risk labels correctly', () => {
    const state = buildGrowthAdminState({
      accounts: [
        { userId: 1, username: 'u1', score: 320, level: 4, rewardBalance: 15, frozenBalance: 0 },
        { userId: 2, username: 'u2', score: 0, level: 1, rewardBalance: 5, frozenBalance: 9 }
      ],
      items: [],
      orders: []
    })

    expect(state.accounts[0]).toMatchObject({
      canAdjustScore: true,
      canAdjustRewardBalance: true,
      riskLabel: '正常'
    })
    expect(state.accounts[1]).toMatchObject({
      canAdjustScore: true,
      canAdjustRewardBalance: true,
      riskLabel: '冻结中'
    })
  })

  it('order rows map operator actions and item warnings correctly', () => {
    const state = buildGrowthAdminState({
      accounts: [],
      items: [
        { id: 11, itemName: '社群资格', costBalance: 120, stock: 1, status: 'ACTIVE' },
        { id: 12, itemName: '头像框周卡', costBalance: 8, stock: 5, status: 'INACTIVE' }
      ],
      orders: [
        { id: 101, status: 'PENDING', itemNameSnapshot: '社群资格' },
        { id: 102, status: 'FULFILLED', itemNameSnapshot: '头像框周卡' },
        { id: 103, status: 'REFUNDED', itemNameSnapshot: '限定贴纸' }
      ]
    })

    expect(state.orders[0]).toMatchObject({
      statusLabel: '待处理',
      canFulfill: true,
      canCancel: true,
      canRefund: false
    })
    expect(state.orders[1]).toMatchObject({
      statusLabel: '已发放',
      canFulfill: false,
      canCancel: false,
      canRefund: true
    })
    expect(state.orders[2]).toMatchObject({
      statusLabel: '已退款',
      canRefund: false
    })

    expect(state.items[0]).toMatchObject({
      warningLabel: '高成本 · 低库存'
    })
    expect(state.items[1]).toMatchObject({
      warningLabel: '已停用'
    })
  })
})
