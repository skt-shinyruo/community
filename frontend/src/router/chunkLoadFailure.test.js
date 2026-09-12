import { describe, expect, it, vi } from 'vitest'
import { handleChunkLoadFailure, isChunkLoadFailure } from './chunkLoadFailure'

describe('isChunkLoadFailure', () => {
  it('recognizes dynamic import failure messages across browsers', () => {
    expect(
      isChunkLoadFailure(new TypeError('Failed to fetch dynamically imported module: http://localhost/assets/PostsView-a1b2.js'))
    ).toBe(true)
    expect(
      isChunkLoadFailure(new Error('error loading dynamically imported module: http://localhost/assets/PostsView-a1b2.js'))
    ).toBe(true)
    expect(isChunkLoadFailure(new Error('Importing a module script failed.'))).toBe(true)
  })

  it('recognizes webpack-style chunk errors by name and message', () => {
    const named = new Error('loading failed')
    named.name = 'ChunkLoadError'
    expect(isChunkLoadFailure(named)).toBe(true)
    expect(isChunkLoadFailure(new Error('Loading chunk 42 failed.'))).toBe(true)
    expect(isChunkLoadFailure(new Error('Loading CSS chunk vendors failed.'))).toBe(true)
  })

  it('rejects unrelated errors and empty values', () => {
    expect(isChunkLoadFailure(new Error('Network request failed'))).toBe(false)
    expect(isChunkLoadFailure(new TypeError('Cannot read properties of undefined'))).toBe(false)
    expect(isChunkLoadFailure(null)).toBe(false)
    expect(isChunkLoadFailure(undefined)).toBe(false)
    expect(isChunkLoadFailure({})).toBe(false)
  })
})

describe('handleChunkLoadFailure', () => {
  it('shows a sticky refresh-guidance toast for chunk failures and reports handled', () => {
    const toast = vi.fn()
    const reload = vi.fn()

    const handled = handleChunkLoadFailure(new TypeError('Failed to fetch dynamically imported module: http://x/a.js'), {
      toast,
      reload
    })

    expect(handled).toBe(true)
    expect(toast).toHaveBeenCalledTimes(1)
    const payload = toast.mock.calls[0][0]
    expect(payload.type).toBe('warning')
    expect(payload.duration).toBe(0)
    expect(payload.title).toBeTruthy()
    expect(payload.text).toContain('刷新')
    expect(payload.actionText).toBe('刷新页面')
    expect(typeof payload.onAction).toBe('function')

    payload.onAction()
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('ignores unrelated errors without showing feedback', () => {
    const toast = vi.fn()

    const handled = handleChunkLoadFailure(new Error('boom'), { toast, reload: vi.fn() })

    expect(handled).toBe(false)
    expect(toast).not.toHaveBeenCalled()
  })

  it('still reports handled when the toast service has no registered handler', () => {
    const handled = handleChunkLoadFailure(new TypeError('Failed to fetch dynamically imported module: http://x/a.js'))

    expect(handled).toBe(true)
  })
})
