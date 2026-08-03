// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from 'vitest'
import { getPostReadAt, markPostRead } from './readTracker'

describe('readTracker identity scope', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('keeps per-post read history isolated between accounts', () => {
    markPostRead('post-1', { at: 100, identityId: 'user-a' })
    markPostRead('post-1', { at: 200, identityId: 'user-b' })

    expect(getPostReadAt('post-1', { identityId: 'user-a' })).toBe(100)
    expect(getPostReadAt('post-1', { identityId: 'user-b' })).toBe(200)
    expect(getPostReadAt('post-1')).toBe(0)
  })
})
