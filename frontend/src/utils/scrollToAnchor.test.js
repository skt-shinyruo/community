// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { scrollToAnchor } from './scrollToAnchor'

describe('scrollToAnchor', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('falls back to the legacy native call when scroll options are rejected', () => {
    document.body.innerHTML = '<div id="target"></div>'
    const target = document.getElementById('target')
    target.scrollIntoView = vi.fn()
      .mockImplementationOnce(() => { throw new TypeError('options unsupported') })
      .mockImplementationOnce(() => {})

    expect(scrollToAnchor('target', { highlightClass: '' })).toBe(true)
    expect(target.scrollIntoView).toHaveBeenNthCalledWith(1, { behavior: 'smooth', block: 'center' })
    expect(target.scrollIntoView).toHaveBeenNthCalledWith(2)
  })
})
