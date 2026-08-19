import { describe, expect, it } from 'vitest'

import {
  buildQuoteMarkdown,
  buildQuotePreview,
  composeReplyContent,
  hydrateCommentItem,
  hydrateReplyItem
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

  it('assembles API comments and replies with named interaction state', () => {
    const users = {
      author: { username: 'alice' },
      target: { username: 'bob' }
    }
    const comment = hydrateCommentItem({ id: 'comment', userId: 'author', content: 'root' }, {
      users,
      counts: { comment: 3 },
      statuses: { comment: true }
    })
    const reply = hydrateReplyItem({ id: 'reply', userId: 'author', replyToUserId: 'target' }, {
      users,
      counts: { reply: 2 },
      statuses: { reply: false }
    })

    expect(comment.ui).toEqual({
      replyEditor: {
        open: false,
        draft: '',
        error: '',
        submitting: false,
        parentCommentId: '',
        quote: null
      },
      replyList: {
        expanded: false,
        items: [],
        page: 0,
        size: 5,
        nextCursor: '',
        cursorHistory: [''],
        loading: false,
        error: ''
      },
      like: { liked: true, count: 3, loading: false, error: '' }
    })
    expect(reply).toMatchObject({
      targetUser: users.target,
      ui: { like: { liked: false, count: 2, loading: false, error: '' } }
    })
    expect(Object.keys(comment).some((key) => key.startsWith('_'))).toBe(false)
    expect(Object.keys(reply).some((key) => key.startsWith('_'))).toBe(false)
  })
})
