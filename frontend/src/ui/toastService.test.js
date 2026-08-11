import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setToastHandler, showErrorToast, showToast } from './toastService'

describe('toastService', () => {
  beforeEach(() => {
    setToastHandler(null)
  })

  it('routes toast payloads through the registered handler', () => {
    const handler = vi.fn()
    setToastHandler(handler)

    showToast({ type: 'error', text: '请求失败' })

    expect(handler).toHaveBeenCalledWith({ type: 'error', text: '请求失败' })
  })

  it('is a no-op when no handler is registered', () => {
    vi.stubGlobal('window', {})

    expect(showToast({ type: 'success', text: '已保存' })).toBe(false)
  })

  it('lets only the first owner show a toast for the same error object', () => {
    const handler = vi.fn()
    setToastHandler(handler)
    const error = new Error('request failed')

    expect(showErrorToast(error, { type: 'error', text: '系统错误' })).toBe(true)
    expect(showErrorToast(error, { type: 'error', text: '页面错误' })).toBe(false)

    expect(handler).toHaveBeenCalledTimes(1)
    expect(handler).toHaveBeenCalledWith({ type: 'error', text: '系统错误' })
  })

  it('does not claim an error when there is no active toast handler', () => {
    const error = new Error('request failed')
    expect(showErrorToast(error, { type: 'error', text: 'first' })).toBe(false)

    const handler = vi.fn()
    setToastHandler(handler)
    expect(showErrorToast(error, { type: 'error', text: 'second' })).toBe(true)
    expect(handler).toHaveBeenCalledTimes(1)
  })
})
