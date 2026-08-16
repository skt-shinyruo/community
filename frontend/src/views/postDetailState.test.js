import { describe, expect, it } from 'vitest'

import {
  buildQuoteMarkdown,
  buildQuotePreview,
  composeReplyContent
} from './postDetailState'

describe('postDetailState', () => {
  it('builds compact quote previews and markdown blocks', () => {
    expect(buildQuotePreview('  hello\nworld  ')).toBe('hello world')
    expect(buildQuoteMarkdown({
      username: 'alice',
      userId: '11111111-1111-7111-8111-111111111111',
      raw: 'line 1\n\nline 2'
    })).toBe('> 引用 @alice\n> line 1\n> line 2')
  })

  it('composes reply content by prepending quote markdown when present', () => {
    expect(composeReplyContent('thanks', {
      username: 'alice',
      userId: '11111111-1111-7111-8111-111111111111',
      raw: 'quoted body'
    })).toBe('> 引用 @alice\n> quoted body\n\nthanks')
  })
})
