import { describe, expect, it } from 'vitest'
import { buildProfileTimeline, collectTimelineUserIds } from './userProfileTimeline'

describe('userProfileTimeline', () => {
  it('merges recent posts and comments into one descending timeline', () => {
    const items = buildProfileTimeline({
      posts: [
        { id: 101, title: 'Post A', createTime: '2026-03-22T08:00:00Z', commentCount: 3 }
      ],
      comments: [
        { id: 22, postId: 101, postTitle: 'Post A', entityType: 1, entityId: 22, content: 'comment', createTime: '2026-03-22T09:00:00Z' }
      ]
    })

    expect(items).toHaveLength(2)
    expect(items[0]).toMatchObject({
      kind: 'comment',
      title: '参与了讨论',
      route: { name: 'postDetail', params: { postId: '101' }, query: { commentId: '22' } }
    })
    expect(items[1]).toMatchObject({
      kind: 'post',
      title: '发起了讨论'
    })
  })

  it('maps reply comments to the parent comment anchor', () => {
    const items = buildProfileTimeline({
      posts: [],
      comments: [
        { id: 23, postId: 102, postTitle: 'Post B', entityType: 2, entityId: 88, content: 'reply', createTime: '2026-03-22T10:00:00Z' }
      ]
    })

    expect(items[0]).toMatchObject({
      kind: 'comment',
      route: { name: 'postDetail', params: { postId: '102' }, query: { commentId: '88', replyId: '23' } }
    })
  })

  it('collects related user ids for recent replies and target users', () => {
    const ids = collectTimelineUserIds({
      posts: [{ id: 1, lastReplyUserId: 7 }, { id: 2, lastReplyUserId: 7 }],
      comments: [{ id: 3, targetId: 9 }, { id: 4, targetId: 0 }]
    })

    expect(ids).toEqual([7, 9])
  })

  it('adds reply-user and target-user language when user summaries are available', () => {
    const items = buildProfileTimeline({
      posts: [
        {
          id: 101,
          title: 'Post A',
          createTime: '2026-03-22T08:00:00Z',
          commentCount: 3,
          lastReplyUserId: 7,
          lastReplyPreview: '这条回复把问题拆得更清楚了。'
        }
      ],
      comments: [
        { id: 23, postId: 102, postTitle: 'Post B', entityType: 2, entityId: 88, targetId: 9, content: 'reply', createTime: '2026-03-22T10:00:00Z' }
      ],
      usersById: {
        7: { id: 7, username: 'Mina', headerUrl: '/m.png' },
        9: { id: 9, username: 'Aki', headerUrl: '/a.png' }
      }
    })

    expect(items[0]).toMatchObject({
      kind: 'comment',
      title: '回复了 Aki',
      contextLabel: '回复对象',
      contextUser: { id: 9, username: 'Aki', headerUrl: '/a.png' }
    })
    expect(items[1]).toMatchObject({
      kind: 'post',
      contextLabel: '最近回复',
      contextUser: { id: 7, username: 'Mina', headerUrl: '/m.png' }
    })
    expect(items[1].body).toContain('这条回复')
  })
})
