import { describe, expect, it } from 'vitest'
import { parsePointsAmount } from './pointsAmount'

describe('parsePointsAmount', () => {
  it('accepts positive integers from number and string input', () => {
    expect(parsePointsAmount(25)).toEqual({ valid: true, amount: 25, message: '' })
    expect(parsePointsAmount('25')).toEqual({ valid: true, amount: 25, message: '' })
    expect(parsePointsAmount(' 25 ')).toEqual({ valid: true, amount: 25, message: '' })
  })

  it('rejects decimal input so a confirmed amount cannot be truncated by the backend', () => {
    const result = parsePointsAmount(1.5)
    expect(result.valid).toBe(false)
    expect(result.message).toBe('积分金额必须是整数，不支持小数')
    expect(parsePointsAmount('1.5').valid).toBe(false)
    expect(parsePointsAmount('0.1').valid).toBe(false)
  })

  it('rejects empty, non-numeric, zero and negative input', () => {
    for (const raw of ['', '   ', 'abc', null, undefined, Number.NaN, 0, -2, '-2']) {
      const result = parsePointsAmount(raw)
      expect(result.valid).toBe(false)
      expect(result.message).toBe('请输入正整数积分金额')
    }
  })

  it('rejects amounts beyond the safe integer range', () => {
    const result = parsePointsAmount(Number.MAX_SAFE_INTEGER + 1)
    expect(result.valid).toBe(false)
    expect(result.message).toBe('积分金额超出支持的范围')
  })
})
