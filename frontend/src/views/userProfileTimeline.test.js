import { describe, expect, it } from 'vitest'
import { buildProfileTimeline, collectTimelineUserIds } from './userProfileTimeline'

describe('userProfileTimeline', () => {
  it('merges recent posts and comments into one descending timeline', () => {
    const items = buildProfileTimeline({
      posts: [
        {
          id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          title: 'Post A',
          createTime: '2026-03-22T08:00:00Z',
          commentCount: 3
        }
      ],
      comments: [
        {
          id: '22222222-2222-7222-8222-222222222222',
          postId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          postTitle: 'Post A',
          entityType: 1,
          entityId: '22222222-2222-7222-8222-222222222222',
          content: 'comment',
          createTime: '2026-03-22T09:00:00Z'
        }
      ]
    })

    expect(items).toHaveLength(2)
    expect(items[0]).toMatchObject({
      kind: 'comment',
      title: '参与了讨论',
      route: {
        name: 'postDetail',
        params: { postId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa' },
        query: { commentId: '22222222-2222-7222-8222-222222222222' }
      }
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
        {
          id: '33333333-3333-7333-8333-333333333333',
          postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          postTitle: 'Post B',
          entityType: 2,
          entityId: '88888888-8888-7888-8888-888888888888',
          content: 'reply',
          createTime: '2026-03-22T10:00:00Z'
        }
      ]
    })

    expect(items[0]).toMatchObject({
      kind: 'comment',
      route: {
        name: 'postDetail',
        params: { postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb' },
        query: {
          commentId: '88888888-8888-7888-8888-888888888888',
          replyId: '33333333-3333-7333-8333-333333333333'
        }
      }
    })
  })

  it('collects related user ids for recent replies and target users', () => {
    const ids = collectTimelineUserIds({
      posts: [
        { id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', lastReplyUserId: '77777777-7777-7777-8777-777777777777' },
        { id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', lastReplyUserId: '77777777-7777-7777-8777-777777777777' }
      ],
      comments: [
        { id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc', targetId: '99999999-9999-7999-8999-999999999999' },
        { id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd', targetId: '' }
      ]
    })

    expect(ids).toEqual([
      '77777777-7777-7777-8777-777777777777',
      '99999999-9999-7999-8999-999999999999'
    ])
  })

  it('adds reply-user and target-user language when user summaries are available', () => {
    const items = buildProfileTimeline({
      posts: [
        {
          id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          title: 'Post A',
          createTime: '2026-03-22T08:00:00Z',
          commentCount: 3,
          lastReplyUserId: '77777777-7777-7777-8777-777777777777',
          lastReplyPreview: '这条回复把问题拆得更清楚了。'
        }
      ],
      comments: [
        {
          id: '33333333-3333-7333-8333-333333333333',
          postId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          postTitle: 'Post B',
          entityType: 2,
          entityId: '88888888-8888-7888-8888-888888888888',
          targetId: '99999999-9999-7999-8999-999999999999',
          content: 'reply',
          createTime: '2026-03-22T10:00:00Z'
        }
      ],
      usersById: {
        '77777777-7777-7777-8777-777777777777': {
          id: '77777777-7777-7777-8777-777777777777',
          username: 'Mina',
          headerUrl: '/m.png'
        },
        '99999999-9999-7999-8999-999999999999': {
          id: '99999999-9999-7999-8999-999999999999',
          username: 'Aki',
          headerUrl: '/a.png'
        }
      }
    })

    expect(items[0]).toMatchObject({
      kind: 'comment',
      title: '回复了 Aki',
      contextLabel: '回复对象',
      contextUser: {
        id: '99999999-9999-7999-8999-999999999999',
        username: 'Aki',
        headerUrl: '/a.png'
      }
    })
    expect(items[1]).toMatchObject({
      kind: 'post',
      contextLabel: '最近回复',
      contextUser: {
        id: '77777777-7777-7777-8777-777777777777',
        username: 'Mina',
        headerUrl: '/m.png'
      }
    })
    expect(items[1].body).toContain('这条回复')
  })
})
