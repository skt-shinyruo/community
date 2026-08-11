import { describe, expect, it, vi } from 'vitest'
import { settleNamedRequests } from './settledRequests'

describe('settleNamedRequests', () => {
  it('keeps fulfilled sections available when another section fails', async () => {
    const failure = new Error('transactions unavailable')
    const outcome = await settleNamedRequests({
      summary: () => Promise.resolve({ balance: 10 }),
      transactions: () => Promise.reject(failure),
      capabilities: () => ({ enabled: true })
    })

    expect(outcome.anySucceeded).toBe(true)
    expect(outcome.allSucceeded).toBe(false)
    expect(outcome.succeededKeys).toEqual(['summary', 'capabilities'])
    expect(outcome.failedKeys).toEqual(['transactions'])
    expect(outcome.results.summary.value).toEqual({ balance: 10 })
    expect(outcome.results.transactions.error).toBe(failure)
  })

  it('captures synchronous factory failures', async () => {
    const outcome = await settleNamedRequests({ broken: vi.fn(() => { throw new Error('broken') }) })
    expect(outcome.anySucceeded).toBe(false)
    expect(outcome.results.broken.error.message).toBe('broken')
  })
})
