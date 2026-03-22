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
      { postId: 10, userId: 7, lastReplyUserId: 9 },
      { postId: 11, userId: 7, lastReplyUserId: 9 },
      { postId: 12, userId: 8, lastReplyUserId: 10 },
      { postId: 0, userId: 0 }
    ])

    expect(ids).toEqual({
      postIds: [10, 11, 12],
      userIds: [7, 9, 8, 10]
    })
  })

  it('applies author, recent-replier, and like-count hydration onto search items', () => {
    const items = applySearchHydration(
      [
        { postId: 10, userId: 7, lastReplyUserId: 9, title: 'A' },
        { postId: 11, userId: 8, title: 'B' }
      ],
      {
        users: {
          7: { id: 7, username: 'Mara' },
          9: { id: 9, username: 'Lin' }
        },
        likeCounts: {
          10: 15,
          11: 3
        }
      }
    )

    expect(items[0]).toMatchObject({
      author: { id: 7, username: 'Mara' },
      lastReplyUser: { id: 9, username: 'Lin' },
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
        { postId: 10, title: 'A' },
        { postId: 11, title: 'B' }
      ],
      [
        { id: 11, commentCount: 2, lastActivityTime: '2026-03-22T09:00:00Z' },
        { id: 10, commentCount: 7, lastActivityTime: '2026-03-22T10:00:00Z' }
      ]
    )

    expect(items[0]).toMatchObject({
      postId: 10,
      commentCount: 7,
      lastActivityTime: '2026-03-22T10:00:00Z'
    })
    expect(items[1]).toMatchObject({
      postId: 11,
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
