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
    expect(state.hero.statusText).toMatch(/待同步|暂未返回/)
  })
})
