import { describe, expect, it } from 'vitest'
import { formatConversationTime, formatLocalDate, formatTime } from './time'

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

describe('formatTime', () => {
  it('falls back to a placeholder instead of rendering "Invalid Date"', () => {
    expect(formatTime('not-a-date')).toBe('-')
    expect(formatTime(NaN)).toBe('-')
    expect(formatTime('')).toBe('-')
    expect(formatTime(null)).toBe('-')
  })

  it('formats valid timestamps', () => {
    expect(formatTime('2026-08-01T00:00:00Z')).not.toBe('-')
  })
})

describe('formatConversationTime', () => {
  it('falls back to a placeholder for invalid input instead of "Invalid Date"', () => {
    expect(formatConversationTime('not-a-date')).toBe('-')
    expect(formatConversationTime('garbage-string')).toBe('-')
  })

  it('keeps the empty-string fallback for missing input', () => {
    expect(formatConversationTime('')).toBe('')
    expect(formatConversationTime(0)).toBe('')
    expect(formatConversationTime(NaN)).toBe('')
    expect(formatConversationTime(undefined)).toBe('')
  })

  it('renders same-day times as clock time and older ones as a date', () => {
    const now = Date.now()
    expect(formatConversationTime(now)).toMatch(/\d{1,2}:\d{2}/)
    expect(formatConversationTime('2020-01-01T00:00:00Z')).not.toBe('-')
  })
})
