import { describe, it, expect } from 'vitest'

import { unwrapResultBody, BusinessError } from './result'

describe('result', () => {
  it('unwrapResultBody should return data/traceId when code=0', () => {
    const body = { code: 0, message: '', traceId: 't-1', data: { ok: true } }
    const out = unwrapResultBody(body, 'hint')
    expect(out).toEqual({ data: { ok: true }, traceId: 't-1' })
  })

  it('unwrapResultBody should throw BusinessError when code!=0', () => {
    const body = { code: 10001, message: 'bad', traceId: 't-2', data: { foo: 'bar' } }
    expect(() => unwrapResultBody(body, 'hint')).toThrow(BusinessError)
    try {
      unwrapResultBody(body, 'hint')
      throw new Error('should not reach')
    } catch (e) {
      expect(e).toBeInstanceOf(BusinessError)
      expect(e.name).toBe('BusinessError')
      expect(e.message).toBe('bad')
      expect(e.code).toBe(10001)
      expect(e.traceId).toBe('t-2')
      expect(e.data).toEqual({ foo: 'bar' })
    }
  })

  it('unwrapResultBody should use hint when message is empty', () => {
    const body = { code: 1, message: '', traceId: 't-3', data: null }
    expect(() => unwrapResultBody(body, '登录')).toThrow('登录 失败')
  })
})

