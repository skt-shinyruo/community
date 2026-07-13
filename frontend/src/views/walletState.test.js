import { describe, expect, it } from 'vitest'
import { buildWalletState } from './walletState'

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
})
