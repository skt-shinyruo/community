import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import { createPinia, setActivePinia } from 'pinia'

import http from '../http'
import { createWriteAttempt, IDEMPOTENCY_HEADER } from '../writeAttempt'
import * as walletService from './walletService'

describe('api/services/walletService', () => {
  let mock

  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    mock?.restore()
    mock = null
  })

  it('createTransfer should POST to the transfers endpoint with the write attempt Idempotency-Key', async () => {
    mock = new MockAdapter(http)
    const writeAttempt = createWriteAttempt()
    mock.onPost('/api/wallet/transfers').reply((config) => {
      expect(config.headers?.[IDEMPOTENCY_HEADER]).toBe(writeAttempt.begin())
      expect(JSON.parse(config.data)).toEqual({
        toUserId: '11111111-1111-7111-8111-111111111111',
        amount: 25
      })
      return [200, {
        code: 0,
        message: 'OK',
        httpStatus: 200,
        data: {
          txnId: '44444444-4444-7444-8444-444444444444',
          status: 'SUCCEEDED'
        },
        traceId: 'trace-create-transfer',
        timestamp: 1774060182920
      }]
    })

    const resp = await walletService.createTransfer(
      { toUserId: '11111111-1111-7111-8111-111111111111', amount: 25 },
      { writeAttempt }
    )

    expect(resp.traceId).toBe('trace-create-transfer')
    expect(/** @type {{ txnId?: string }} */ (resp.data).txnId).toBe('44444444-4444-7444-8444-444444444444')
    expect(/** @type {{ status?: string }} */ (resp.data).status).toBe('SUCCEEDED')
    expect(mock.history.post).toHaveLength(1)
    expect(mock.history.post[0].url).toBe('/api/wallet/transfers')
    expect(mock.history.post[0].method).toBe('post')
  })

  it('createTransfer should reject without sending a request when writeAttempt is missing', async () => {
    mock = new MockAdapter(http)
    mock.onAny().reply((config) => {
      throw new Error(`unexpected HTTP request: ${config.method} ${config.url}`)
    })

    await expect(
      walletService.createTransfer({ toUserId: '11111111-1111-7111-8111-111111111111', amount: 25 })
    ).rejects.toThrow(TypeError)

    expect(mock.history.post).toHaveLength(0)
  })
})
