import { beforeEach, describe, expect, it, vi } from 'vitest'

import { setToastHandler, showToast } from './toastService'

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
})
