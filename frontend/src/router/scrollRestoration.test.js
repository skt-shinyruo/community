import { describe, expect, it } from 'vitest'
import { resolveScrollPosition } from './scrollRestoration'

describe('resolveScrollPosition', () => {
  it('restores the saved position on back / forward navigation', () => {
    const saved = { left: 0, top: 640 }

    const position = resolveScrollPosition({ path: '/posts', hash: '' }, { path: '/posts/1', hash: '' }, saved)

    expect(position).toBe(saved)
  })

  it('prefers the saved position over an anchor target', () => {
    const saved = { left: 0, top: 320 }

    const position = resolveScrollPosition({ path: '/posts', hash: '#comments' }, { path: '/posts/1', hash: '' }, saved)

    expect(position).toBe(saved)
  })

  it('scrolls to the anchor element when the target route carries a hash', () => {
    const position = resolveScrollPosition({ path: '/posts/1', hash: '#comments' }, { path: '/posts', hash: '' }, null)

    expect(position).toEqual({ el: '#comments' })
  })

  it('scrolls to the anchor even when only the hash changes on the same path', () => {
    const position = resolveScrollPosition({ path: '/posts/1', hash: '#reply-2' }, { path: '/posts/1', hash: '' }, null)

    expect(position).toEqual({ el: '#reply-2' })
  })

  it('scrolls to top when navigating across routes', () => {
    const position = resolveScrollPosition({ path: '/posts/1', hash: '' }, { path: '/posts', hash: '' }, null)

    expect(position).toEqual({ top: 0 })
  })

  it('keeps the current position for same-path query switches like tab deep links', () => {
    const position = resolveScrollPosition(
      { path: '/settings', hash: '', query: { section: 'addresses' } },
      { path: '/settings', hash: '', query: { section: 'profile' } },
      null
    )

    expect(position).toBe(false)
  })
})
