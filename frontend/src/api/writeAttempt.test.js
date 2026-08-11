import { describe, expect, it, vi } from 'vitest'

import { createWriteAttempt, IDEMPOTENCY_HEADER, writeAttemptConfig } from './writeAttempt'

describe('writeAttempt', () => {
  it('reuses one key after failure until the caller explicitly ends the attempt', () => {
    const keyFactory = vi.fn()
      .mockReturnValueOnce('attempt-1')
      .mockReturnValueOnce('attempt-2')
      .mockReturnValueOnce('attempt-3')
    const attempt = createWriteAttempt({ keyFactory })

    expect(writeAttemptConfig(attempt).headers[IDEMPOTENCY_HEADER]).toBe('attempt-1')
    expect(writeAttemptConfig(attempt).headers[IDEMPOTENCY_HEADER]).toBe('attempt-1')
    expect(keyFactory).toHaveBeenCalledTimes(1)

    attempt.changeIntent()
    expect(writeAttemptConfig(attempt).headers[IDEMPOTENCY_HEADER]).toBe('attempt-2')

    attempt.succeed()
    expect(writeAttemptConfig(attempt).headers[IDEMPOTENCY_HEADER]).toBe('attempt-3')
  })

  it('ends a cancelled attempt without retaining its key', () => {
    const keyFactory = vi.fn().mockReturnValueOnce('attempt-1').mockReturnValueOnce('attempt-2')
    const attempt = createWriteAttempt({ keyFactory })

    expect(attempt.begin()).toBe('attempt-1')
    attempt.cancel()
    expect(attempt.key).toBe('')
    expect(attempt.begin()).toBe('attempt-2')
  })
})
