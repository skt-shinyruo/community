import { describe, expect, it } from 'vitest'

import {
  normalizeOpaqueId,
  requireApiOpaqueId,
  requireOpaqueId
} from './opaqueId'

describe('utils/opaqueId', () => {
  it('keeps optional opaque ids lenient for empty UI state', () => {
    expect(normalizeOpaqueId(null)).toBe('')
    expect(normalizeOpaqueId(undefined)).toBe('')
    expect(normalizeOpaqueId(0)).toBe('')
    expect(normalizeOpaqueId(' undefined ')).toBe('')
  })

  it('throws for required user-operation ids when the value is empty', () => {
    expect(() => requireOpaqueId(null, 'postId')).toThrow('postId 非法')
    expect(() => requireOpaqueId('0', 'postId')).toThrow('postId 非法')
  })

  it('throws for API contract ids when the value is missing or not a UUID', () => {
    expect(() => requireApiOpaqueId(null, 'messageId')).toThrow('messageId 缺失')
    expect(() => requireApiOpaqueId('not-a-uuid', 'messageId')).toThrow('messageId 非法')
    expect(requireApiOpaqueId(' AAAAAAAA-AAAA-7AAA-8AAA-AAAAAAAAAAAA ', 'messageId'))
      .toBe('AAAAAAAA-AAAA-7AAA-8AAA-AAAAAAAAAAAA')
  })
})
