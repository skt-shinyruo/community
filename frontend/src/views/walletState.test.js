import { describe, expect, it } from 'vitest'
import {
  WALLET_FEED_MAX_LIMIT,
  WALLET_FEED_PAGE_SIZE,
  buildWalletState,
  nextWalletFeedLimit,
  walletDiscardConfirmation,
  walletFeedExhausted,
  walletFeedHasMore,
  walletTransferConfirmation
} from './walletState'

describe('walletState', () => {
  it('maps summary and txn items into one-wallet surface', () => {
    const state = buildWalletState({
      summary: { balance: 1200, status: 'ACTIVE' },
      txns: [{ txnType: 'TRANSFER', amount: -300, counterpartLabel: '用户 202' }]
    })

    expect(state.hero.balance).toBe(1200)
    expect(state.hero.statusText).toMatch(/正常/)
    expect(state.feed[0].label).toMatch(/转账/)
  })

  it('degrades honestly when wallet status is not returned yet', () => {
    const state = buildWalletState({
      summary: { balance: 900 },
      txns: []
    })

    expect(state.hero.balance).toBe(900)
    expect(state.hero.status).toBe('UNKNOWN')
    expect(state.hero.statusText).toBe('钱包状态暂不可用，余额以当前可见数据为准。')
  })

  it('describes unknown wallet summary as unavailable without future-work copy', () => {
    const state = buildWalletState({ summary: { balance: 0 }, txns: [] })

    expect(state.hero.status).toBe('UNKNOWN')
    expect(state.hero.statusText).toBe('钱包状态暂不可用，余额以当前可见数据为准。')
    expect(state.hero.statusText).not.toContain('后续')
    expect(state.hero.statusText).not.toContain('待同步')
  })

  it('uses backend transaction references and signed amounts', () => {
    const state = buildWalletState({
      summary: { balance: 975, status: 'ACTIVE' },
      txns: [
        {
          txnId: '0198f4b6-9ad4-7a22-8df4-3c680e0d0d01',
          txnRef: 'wallet:transfer:history',
          txnType: 'TRANSFER',
          amount: -25,
          balanceAfter: 975,
          counterpartLabel: '用户 202',
          status: 'SUCCEEDED'
        }
      ]
    })

    expect(state.feed[0].key).toBe('wallet:transfer:history')
    expect(state.feed[0].label).toBe('转账转出')
    expect(state.feed[0].amountText).toBe('-25 积分')
    expect(state.feed[0].meta).toBe('用户 202')
  })

  it('does not use requestId as a transaction key', () => {
    const state = buildWalletState({
      summary: { balance: 10, status: 'ACTIVE' },
      txns: [{ txnId: '11111111-1111-7111-8111-111111111111', requestId: 'legacy-request', txnType: 'RECHARGE', amount: 10 }]
    })

    expect(state.feed[0].key).toBe('11111111-1111-7111-8111-111111111111')
  })

  it('distinguishes test-credit entries from real recharge and withdrawal entries', () => {
    const state = buildWalletState({
      summary: { balance: 10, status: 'ACTIVE' },
      txns: [
        { txnId: '1', txnType: 'TEST_CREDIT_GRANT', amount: 10 },
        { txnId: '2', txnType: 'TEST_CREDIT_DISCARD', amount: -5 },
        { txnId: '3', txnType: 'RECHARGE', amount: 20 },
        { txnId: '4', txnType: 'WITHDRAW', amount: -10 }
      ]
    })

    expect(state.feed.map((item) => item.label)).toEqual([
      '测试积分发放',
      '测试积分销毁',
      '充值入账',
      '提现'
    ])
  })
})

describe('wallet feed append pagination', () => {
  it('grows the request window by one page up to the backend limit cap', () => {
    expect(WALLET_FEED_PAGE_SIZE).toBe(12)
    expect(nextWalletFeedLimit(12)).toBe(24)
    expect(nextWalletFeedLimit(24)).toBe(36)
    expect(nextWalletFeedLimit(48)).toBe(WALLET_FEED_MAX_LIMIT)
    expect(nextWalletFeedLimit(WALLET_FEED_MAX_LIMIT)).toBe(WALLET_FEED_MAX_LIMIT)
  })

  it('treats a full window below the cap as having more items', () => {
    expect(walletFeedHasMore({ count: 12, limit: 12 })).toBe(true)
    expect(walletFeedHasMore({ count: 18, limit: 24 })).toBe(false)
    expect(walletFeedHasMore({ count: 0, limit: 12 })).toBe(false)
  })

  it('stops offering more pages once the request window reaches the backend cap', () => {
    expect(walletFeedHasMore({ count: 50, limit: WALLET_FEED_MAX_LIMIT })).toBe(false)
    expect(walletFeedExhausted({ count: 50, limit: WALLET_FEED_MAX_LIMIT })).toBe(false)
  })

  it('proves exhaustion only when the backend returns fewer items than requested', () => {
    expect(walletFeedExhausted({ count: 7, limit: 12 })).toBe(true)
    expect(walletFeedExhausted({ count: 12, limit: 12 })).toBe(false)
    expect(walletFeedExhausted({ count: 12, limit: 24 })).toBe(true)
  })
})

describe('wallet capital-loss confirmation copy', () => {
  it('restates transfer target and amount before the write attempt runs', () => {
    const copy = walletTransferConfirmation({ toUserId: ' 11111111-1111-7111-8111-111111111111 ', amount: 25 })

    expect(copy.title).toBe('确认转账')
    expect(copy.confirmText).toBe('确认转账')
    expect(copy.message).toContain('11111111-1111-7111-8111-111111111111')
    expect(copy.message).toContain('25')
    expect(copy.message).toContain('不可撤销')
  })

  it('restates the discard amount and its permanent balance effect', () => {
    const copy = walletDiscardConfirmation({ amount: 7 })

    expect(copy.title).toBe('确认销毁测试积分')
    expect(copy.confirmText).toBe('确认销毁')
    expect(copy.message).toContain('7')
    expect(copy.message).toContain('不可撤销')
  })
})
