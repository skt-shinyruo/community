import { describe, expect, it } from 'vitest'

import {
  buildQuoteMarkdown,
  buildQuotePreview,
  collectCommentHydrationIds,
  collectReplyHydrationIds,
  composeReplyContent,
  hydratePostComment,
  hydratePostReply
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

  it('collects bounded hydration ids for comments and replies', () => {
    expect(collectCommentHydrationIds([
      {
        id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        userId: '11111111-1111-7111-8111-111111111111'
      },
      {
        id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
        userId: '22222222-2222-7222-8222-222222222222'
      }
    ])).toEqual({
      userIds: [
        '11111111-1111-7111-8111-111111111111',
        '22222222-2222-7222-8222-222222222222'
      ],
      entityIds: [
        'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
      ]
    })

    expect(collectReplyHydrationIds([
      {
        id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
        userId: '22222222-2222-7222-8222-222222222222',
        targetId: '11111111-1111-7111-8111-111111111111'
      }
    ])).toEqual({
      userIds: [
        '22222222-2222-7222-8222-222222222222',
        '11111111-1111-7111-8111-111111111111'
      ],
      entityIds: ['cccccccc-cccc-7ccc-8ccc-cccccccccccc']
    })
  })

  it('hydrates comments and replies with user and like overlays', () => {
    expect(hydratePostComment({
      id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      userId: '11111111-1111-7111-8111-111111111111'
    }, {
      users: {
        '11111111-1111-7111-8111-111111111111': { username: 'alice' }
      },
      counts: {
        'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa': 3
      },
      statuses: {
        'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa': true
      }
    })).toMatchObject({
      user: { username: 'alice' },
      likeCount: 3,
      liked: true,
      _replies: []
    })

    expect(hydratePostReply({
      id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      userId: '22222222-2222-7222-8222-222222222222',
      targetId: '11111111-1111-7111-8111-111111111111'
    }, {
      users: {
        '22222222-2222-7222-8222-222222222222': { username: 'bob' },
        '11111111-1111-7111-8111-111111111111': { username: 'alice' }
      },
      counts: {
        'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb': 2
      },
      statuses: {
        'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb': false
      }
    })).toMatchObject({
      user: { username: 'bob' },
      targetUser: { username: 'alice' },
      likeCount: 2,
      liked: false
    })
  })
})
