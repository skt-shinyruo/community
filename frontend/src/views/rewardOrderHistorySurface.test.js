import { describe, expect, it } from 'vitest'

import { buildRewardOrderHistorySurface } from './rewardOrderHistorySurface'

describe('rewardOrderHistorySurface', () => {
  it('keeps page chrome available for empty and error states', () => {
    expect(buildRewardOrderHistorySurface({ loading: false, error: '加载失败', orders: [] })).toMatchObject({
      showHeader: true,
      canReturnToShop: true,
      bodyState: 'error'
    })

    expect(buildRewardOrderHistorySurface({ loading: false, error: '', orders: [] })).toMatchObject({
      showHeader: true,
      canReturnToShop: true,
      bodyState: 'empty'
    })
  })

  it('shows loading only before the first page of orders arrives', () => {
    expect(buildRewardOrderHistorySurface({ loading: true, error: '', orders: [] }).bodyState).toBe('loading')
    expect(buildRewardOrderHistorySurface({ loading: true, error: '', orders: [{ id: 1 }] }).bodyState).toBe('list')
  })
})
