import { describe, expect, it } from 'vitest'
import {
  applySearchHydration,
  applySearchSummaries,
  collectSearchHydrationIds,
  describeSearchActivity
} from './searchResultSurface'

describe('searchResultSurface', () => {
  it('collects unique user and post ids for hydration', () => {
    const ids = collectSearchHydrationIds([
      {
        postId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        userId: '11111111-1111-7111-8111-111111111111',
        lastReplyUserId: '99999999-9999-7999-8999-999999999999'
      },
      {
        postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
        userId: '11111111-1111-7111-8111-111111111111',
        lastReplyUserId: '99999999-9999-7999-8999-999999999999'
      },
      {
        postId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
        userId: '22222222-2222-7222-8222-222222222222',
        lastReplyUserId: '33333333-3333-7333-8333-333333333333'
      },
      { postId: '', userId: '' }
    ])

    expect(ids).toEqual({
      postIds: [
        'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
        'cccccccc-cccc-7ccc-8ccc-cccccccccccc'
      ],
      userIds: [
        '11111111-1111-7111-8111-111111111111',
        '99999999-9999-7999-8999-999999999999',
        '22222222-2222-7222-8222-222222222222',
        '33333333-3333-7333-8333-333333333333'
      ]
    })
  })

  it('applies author, recent-replier, and like-count hydration onto search items', () => {
    const items = applySearchHydration(
      [
        {
          postId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          userId: '11111111-1111-7111-8111-111111111111',
          lastReplyUserId: '99999999-9999-7999-8999-999999999999',
          title: 'A'
        },
        {
          postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          userId: '22222222-2222-7222-8222-222222222222',
          title: 'B'
        }
      ],
      {
        users: {
          '11111111-1111-7111-8111-111111111111': {
            id: '11111111-1111-7111-8111-111111111111',
            username: 'Mara'
          },
          '99999999-9999-7999-8999-999999999999': {
            id: '99999999-9999-7999-8999-999999999999',
            username: 'Lin'
          }
        },
        likeCounts: {
          'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa': 15,
          'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb': 3
        }
      }
    )

    expect(items[0]).toMatchObject({
      author: { id: '11111111-1111-7111-8111-111111111111', username: 'Mara' },
      lastReplyUser: { id: '99999999-9999-7999-8999-999999999999', username: 'Lin' },
      likeCount: 15
    })
    expect(items[1]).toMatchObject({
      author: null,
      lastReplyUser: null,
      likeCount: 3
    })
  })

  it('merges post summary meta into search items by post id', () => {
    const items = applySearchSummaries(
      [
        { postId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', title: 'A' },
        { postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', title: 'B' }
      ],
      [
        {
          id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          commentCount: 2,
          lastActivityTime: '2026-03-22T09:00:00Z'
        },
        {
          id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          commentCount: 7,
          lastActivityTime: '2026-03-22T10:00:00Z'
        }
      ]
    )

    expect(items[0]).toMatchObject({
      postId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      commentCount: 7,
      lastActivityTime: '2026-03-22T10:00:00Z'
    })
    expect(items[1]).toMatchObject({
      postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      commentCount: 2,
      lastActivityTime: '2026-03-22T09:00:00Z'
    })
  })

  it('builds recent activity copy from preview and trims overly long text', () => {
    const activity = describeSearchActivity({
      commentCount: 3,
      lastReplyPreview:
        '这是一个很长的最近回复摘句，用来验证搜索结果卡片里的活动摘要会在达到上限后自动截断，避免整张卡片因为一段过长的回复而显得拖沓失焦。这里再补一段额外说明，确保长度明显超过卡片活动摘要的展示上限。',
      lastReplyUser: { id: 9, username: 'Lin' }
    })

    expect(activity).toMatchObject({
      label: '最近回复 · Lin'
    })
    expect(activity?.copy.endsWith('…')).toBe(true)
    expect(activity?.copy.length).toBeLessThanOrEqual(73)
  })

  it('falls back to reply-count guidance when no preview is available', () => {
    expect(describeSearchActivity({ commentCount: 4 })).toEqual({
      label: '最近回复',
      copy: '这条讨论目前已有 4 条回复，打开后可继续沿着最新上下文阅读。'
    })
  })

  it('returns null when there is no reply activity to surface', () => {
    expect(describeSearchActivity({ commentCount: 0, lastReplyPreview: '' })).toBeNull()
  })
})
