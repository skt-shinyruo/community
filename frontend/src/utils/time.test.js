import { describe, expect, it } from 'vitest'
import { formatLocalDate } from './time'

describe('formatLocalDate', () => {
  it('formats the calendar day from local date parts instead of UTC', () => {
    // 按本地时区构造的 2026-01-02 03:04:05：UTC 口径在不同时区会落到 01-01 或 01-02，
    // 本地日历日必须与 toISOString() 的 UTC 结果区分开。
    const localEarlyMorning = new Date(2026, 0, 2, 3, 4, 5)
    expect(formatLocalDate(localEarlyMorning)).toBe('2026-01-02')
  })

  it('zero-pads month and day', () => {
    expect(formatLocalDate(new Date(2026, 8, 9))).toBe('2026-09-09')
  })

  it('defaults to the current local day', () => {
    const now = new Date()
    const expected = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    expect(formatLocalDate()).toBe(expected)
  })

  it('returns an empty string for invalid input', () => {
    expect(formatLocalDate('not-a-date')).toBe('')
    expect(formatLocalDate(new Date('not-a-date'))).toBe('')
  })
})
