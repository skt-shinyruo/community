import { describe, expect, it } from 'vitest'
import { applyDocumentTitle, resolveDocumentTitle } from './documentTitle'

describe('resolveDocumentTitle', () => {
  it('combines the route meta title with the base title from index.html', () => {
    expect(resolveDocumentTitle({ meta: { title: '讨论首页' } })).toBe('讨论首页 - Community')
    expect(resolveDocumentTitle({ meta: { title: '登录' } })).toBe('登录 - Community')
  })

  it('falls back to the base title when the route has no usable meta title', () => {
    expect(resolveDocumentTitle({ meta: {} })).toBe('Community')
    expect(resolveDocumentTitle({ meta: { title: '' } })).toBe('Community')
    expect(resolveDocumentTitle({ meta: { title: '   ' } })).toBe('Community')
    expect(resolveDocumentTitle({ meta: { title: 42 } })).toBe('Community')
    expect(resolveDocumentTitle({})).toBe('Community')
  })
})

describe('applyDocumentTitle', () => {
  it('writes the resolved title onto the document', () => {
    const doc = { title: '' }

    // 测试环境无完整 DOM Document：以仅含 title 的最小对象驱动写入。
    applyDocumentTitle({ meta: { title: '帖子详情' } }, /** @type {Document} */ (doc))

    expect(doc.title).toBe('帖子详情 - Community')
  })

  it('does nothing without a document', () => {
    expect(() => applyDocumentTitle({ meta: { title: '帖子详情' } }, null)).not.toThrow()
  })
})
