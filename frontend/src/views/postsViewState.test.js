import { describe, expect, it } from 'vitest'

import {
  collectPostsHydrationIds,
  commitComposerTagDraft,
  parsePostsRouteQuery,
  resolvePostsFeedPlan,
  serializePostsRouteQuery
} from './postsViewState'

describe('postsViewState', () => {
  it('commits deduplicated composer tags and keeps invalid drafts as errors', () => {
    expect(commitComposerTagDraft(['Java'], '#Spring Java')).toEqual({
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

  it('parses the unified route query with boardId compatibility and order fallback', () => {
    expect(parsePostsRouteQuery({})).toEqual({ categoryId: '', tag: '', order: 'latest' })
    expect(
      parsePostsRouteQuery({ categoryId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', tag: '#Java', order: 'hot' })
    ).toEqual({
      categoryId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      tag: 'Java',
      order: 'hot'
    })
    // 旧链接的 boardId 归一为 categoryId，categoryId 优先
    expect(parsePostsRouteQuery({ boardId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb' }).categoryId)
      .toBe('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb')
    expect(
      parsePostsRouteQuery({
        categoryId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        boardId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
      }).categoryId
    ).toBe('aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa')
    expect(parsePostsRouteQuery({ order: 'bogus' }).order).toBe('latest')
  })

  it('serializes query changes by dropping empty values, the default order and boardId', () => {
    expect(
      serializePostsRouteQuery(
        { boardId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', order: 'hot', tag: 'Java' },
        { categoryId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa' }
      )
    ).toEqual({
      categoryId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      order: 'hot',
      tag: 'Java'
    })

    expect(serializePostsRouteQuery({ order: 'hot', tag: 'Java' }, { order: 'latest', tag: '' })).toEqual({})
    expect(serializePostsRouteQuery({}, { tag: '#  Spring  ' })).toEqual({ tag: 'Spring' })
    // 旧 boardId 链接在序列化时归一为 categoryId
    expect(serializePostsRouteQuery({ boardId: 'board-1' }, {})).toEqual({ categoryId: 'board-1' })
    expect(serializePostsRouteQuery({ boardId: 'board-1' }, { categoryId: '' })).toEqual({})
  })

  it('routes tag-filtered views to the search stack and the rest to the cursor feed', () => {
    expect(resolvePostsFeedPlan({})).toEqual({ source: 'feed', scope: 'global' })
    expect(resolvePostsFeedPlan({ categoryId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa' }))
      .toEqual({ source: 'feed', scope: 'category' })
    expect(resolvePostsFeedPlan({ tag: 'Java' })).toEqual({ source: 'search' })
    expect(resolvePostsFeedPlan({ tag: '#Java', categoryId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa' }))
      .toEqual({ source: 'search' })
  })
})
