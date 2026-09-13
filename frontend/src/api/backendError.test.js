import { describe, expect, it } from 'vitest'

import { backendErrorCode, backendErrorMessage, isCaptchaRejected } from './backendError'

describe('api/backendError', () => {
  it('returns the numeric backend code from the Result body', () => {
    expect(backendErrorCode({ response: { data: { code: 10005 } } })).toBe(10005)
  })

  it('parses numeric string codes from the Result body', () => {
    expect(backendErrorCode({ response: { data: { code: '10006' } } })).toBe(10006)
  })

  it('falls back to 0 for axios network and timeout error codes instead of NaN', () => {
    expect(backendErrorCode({ code: 'ERR_NETWORK', message: 'Network Error' })).toBe(0)
    expect(backendErrorCode({ code: 'ECONNABORTED', message: 'timeout of 15000ms exceeded' })).toBe(0)
    expect(backendErrorCode({ code: 'ERR_CANCELED', message: 'canceled' })).toBe(0)
  })

  it('returns 0 when no code is present at all', () => {
    expect(backendErrorCode({})).toBe(0)
    expect(backendErrorCode(null)).toBe(0)
    expect(backendErrorCode(undefined)).toBe(0)
  })

  it('does not treat network errors as captcha rejections', () => {
    expect(isCaptchaRejected({ code: 'ERR_NETWORK' })).toBe(false)
    expect(isCaptchaRejected({ response: { data: { code: 10005 } } })).toBe(true)
    expect(isCaptchaRejected({ response: { data: { code: 10006 } } })).toBe(true)
  })

  it('keeps preferring the backend message over the transport message', () => {
    expect(backendErrorMessage({ response: { data: { message: '用户名或密码错误' } }, message: 'Request failed' })).toBe('用户名或密码错误')
    expect(backendErrorMessage({ message: 'Network Error' }, '兜底')).toBe('Network Error')
    expect(backendErrorMessage({}, '兜底')).toBe('兜底')
  })
})
