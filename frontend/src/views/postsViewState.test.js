import { describe, expect, it } from 'vitest'

import {
  collectPostsHydrationIds,
  commitComposerTagDraft,
  normalizeComposerTagToken
} from './postsViewState'

describe('postsViewState', () => {
  it('normalizes composer tag tokens by trimming hash prefixes and whitespace', () => {
    expect(normalizeComposerTagToken(' #Java Spring ')).toBe('Java-Spring')
  })

  it('commits deduplicated composer tags and keeps invalid drafts as errors', () => {
    expect(commitComposerTagDraft(['Java'], 'Spring Java')).toEqual({
      tags: ['Java', 'Spring'],
      error: '',
      draft: ''
    })

    expect(commitComposerTagDraft(['Java'], 'java!')).toEqual({
      tags: ['Java'],
      error: '标签格式非法（仅允许中英文、数字、_、-）',
      draft: 'java!'
    })
  })

  it('collects unique user and post ids for hydration with a defensive cap', () => {
    const ids = collectPostsHydrationIds([
      {
        id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        userId: '11111111-1111-7111-8111-111111111111',
        lastReplyUserId: '22222222-2222-7222-8222-222222222222'
      },
      {
        id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
        userId: '11111111-1111-7111-8111-111111111111',
        lastReplyUserId: '33333333-3333-7333-8333-333333333333'
      }
    ])

    expect(ids).toEqual({
      userIds: [
        '11111111-1111-7111-8111-111111111111',
        '22222222-2222-7222-8222-222222222222',
        '33333333-3333-7333-8333-333333333333'
      ],
      postIds: [
        'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
      ]
    })
  })
})
