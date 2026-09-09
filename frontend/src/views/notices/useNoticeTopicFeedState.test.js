import { describe, expect, it } from 'vitest'

import { describeNoticeContent, noticePostId } from './useNoticeTopicFeedState'

function noticeWith(content) {
  return { content: JSON.stringify(content) }
}

describe('useNoticeTopicFeedState presentation', () => {
  it('hits localized copy for the production PascalCase contract event types', () => {
    expect(describeNoticeContent(noticeWith({ type: 'CommentCreated', payload: {} })).title)
      .toBe('有人回复了你的内容')
    expect(describeNoticeContent(noticeWith({ type: 'LikeCreated', payload: {} })).title)
      .toBe('你的内容收到了新的点赞')
    expect(describeNoticeContent(noticeWith({ type: 'FollowCreated', payload: {} })).title)
      .toBe('你收到了新的关注')
    expect(describeNoticeContent(noticeWith({ type: 'ModerationActionApplied', payload: {} })).title)
      .toBe('治理状态有更新')
  })

  it('falls back for unknown types without rendering the raw event name', () => {
    const presentation = describeNoticeContent(noticeWith({ type: 'SomethingElseHappened', payload: {} }))
    expect(presentation.title).toBe('查看这条通知')
    expect(presentation.body).toBeTruthy()
    expect(presentation.body).not.toContain('SomethingElseHappened')
  })

  it('resolves the post id from moderation payloads only for post targets', () => {
    const postId = '11111111-1111-7111-8111-111111111111'
    expect(noticePostId(noticeWith({
      type: 'ModerationActionApplied',
      payload: { targetType: 1, targetId: postId }
    }))).toBe(postId)
    expect(noticePostId(noticeWith({
      type: 'ModerationActionApplied',
      payload: { targetType: 2, targetId: postId }
    }))).toBe('')
  })
})
