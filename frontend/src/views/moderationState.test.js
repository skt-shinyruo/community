import { describe, expect, it } from 'vitest'

import { moderationActionNeedsDuration, resolveModerationDurationSeconds } from './moderationState'

describe('moderationActionNeedsDuration', () => {
  it.each(['mute', 'ban'])('requires a duration for %s', (action) => {
    expect(moderationActionNeedsDuration(action)).toBe(true)
  })

  it.each(['reject', 'hide', 'delete', 'warn', ''])('does not require a duration for %j', (action) => {
    expect(moderationActionNeedsDuration(action)).toBe(false)
  })
})

describe('resolveModerationDurationSeconds', () => {
  it.each(['reject', 'hide', 'delete', 'warn'])('ignores duration fields for %s actions', (action) => {
    expect(resolveModerationDurationSeconds({ action, durationPreset: 'custom', durationSeconds: 'abc' })).toEqual({
      valid: true,
      message: '',
      durationSeconds: undefined
    })
  })

  it.each(['mute', 'ban'])('resolves preset durations for %s actions', (action) => {
    expect(resolveModerationDurationSeconds({ action, durationPreset: '3600', durationSeconds: '' })).toEqual({
      valid: true,
      message: '',
      durationSeconds: 3600
    })
    expect(resolveModerationDurationSeconds({ action, durationPreset: '2592000', durationSeconds: '' })).toEqual({
      valid: true,
      message: '',
      durationSeconds: 2592000
    })
  })

  it('rejects an unusable preset instead of dropping the duration silently', () => {
    expect(resolveModerationDurationSeconds({ action: 'mute', durationPreset: '', durationSeconds: '' })).toEqual({
      valid: false,
      message: '请选择处置时长',
      durationSeconds: undefined
    })
  })

  it('accepts a positive integer custom duration', () => {
    expect(resolveModerationDurationSeconds({ action: 'ban', durationPreset: 'custom', durationSeconds: '600' })).toEqual({
      valid: true,
      message: '',
      durationSeconds: 600
    })
  })

  it('trims surrounding whitespace in a custom duration', () => {
    expect(
      resolveModerationDurationSeconds({ action: 'ban', durationPreset: 'custom', durationSeconds: '  600  ' })
    ).toEqual({
      valid: true,
      message: '',
      durationSeconds: 600
    })
  })

  it.each(['', '   '])('requires a custom duration instead of falling back to backend defaults: %j', (raw) => {
    const result = resolveModerationDurationSeconds({ action: 'mute', durationPreset: 'custom', durationSeconds: raw })
    expect(result).toEqual({
      valid: false,
      message: '请输入自定义时长（秒）',
      durationSeconds: undefined
    })
  })

  it.each(['0', '000', '-5', 'abc', '10秒', '1.5', '1e3', '0x10', '10abc'])(
    'rejects invalid custom duration %j instead of falling back to backend defaults',
    (raw) => {
      const result = resolveModerationDurationSeconds({ action: 'mute', durationPreset: 'custom', durationSeconds: raw })
      expect(result).toEqual({
        valid: false,
        message: '自定义时长必须是正整数（秒）',
        durationSeconds: undefined
      })
    }
  )

  it('rejects a custom duration beyond the safe integer range', () => {
    const result = resolveModerationDurationSeconds({
      action: 'ban',
      durationPreset: 'custom',
      durationSeconds: '9'.repeat(20)
    })
    expect(result).toEqual({
      valid: false,
      message: '自定义时长超出支持的范围',
      durationSeconds: undefined
    })
  })
})
