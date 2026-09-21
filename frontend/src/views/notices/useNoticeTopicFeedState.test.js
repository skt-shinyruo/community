// @vitest-environment jsdom

import { defineComponent, ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/services/noticeService', () => ({
  listNotices: vi.fn(),
  markTopicRead: vi.fn(),
  topicSummary: vi.fn().mockResolvedValue({ data: [] }),
  unreadCount: vi.fn().mockResolvedValue({ data: 0 })
}))

import { listNotices } from '../../api/services/noticeService'
import { useAuthStore } from '../../stores/auth'
import { describeNoticeContent, noticePostId, useNoticeTopicFeedState } from './useNoticeTopicFeedState'

// 类型别名：vi.mock 替换后的服务函数在本测试里只按 Mock 使用（宽松 payload 不再受真实签名约束）。
const listNoticesMock = /** @type {import('vitest').Mock} */ (listNotices)

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

describe('useNoticeTopicFeedState paging', () => {
  function mountFeed() {
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().installSession({ accessToken: 'notice-token' })

    /** @type {ReturnType<typeof useNoticeTopicFeedState> | undefined} */
    let feed
    const Harness = defineComponent({
      setup() {
        feed = useNoticeTopicFeedState({ topic: ref('comment') })
        return () => null
      }
    })
    mount(Harness, { global: { plugins: [pinia] } })
    if (!feed) throw new Error('harness feed not initialized')
    return feed
  }

  function notice(id) {
    return { id, status: 0, content: '{}', createTime: '2026-04-29T00:00:00Z' }
  }

  beforeEach(() => {
    listNoticesMock.mockReset()
    listNoticesMock.mockResolvedValue({ data: [] })
  })

  it('dedupes page-shifted notices by id when a new notice arrives between pages', async () => {
    const firstPage = Array.from({ length: 10 }, (_, index) =>
      notice(`00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`)
    )
    listNoticesMock
      .mockResolvedValueOnce({ data: firstPage })
      .mockResolvedValueOnce({
        data: [
          notice('00000000-0000-7000-8000-000000000010'),
          notice('10000000-0000-7000-8000-000000000001')
        ]
      })

    const feed = mountFeed()
    await flushPromises()

    await feed.loadMore()

    const ids = feed.cards.value.map((card) => card.id)
    expect(new Set(ids).size).toBe(ids.length)
    expect(feed.cards.value).toHaveLength(11)
    expect(vi.mocked(listNotices).mock.calls.map(([, request]) => request?.page)).toEqual([0, 1])
  })
})
