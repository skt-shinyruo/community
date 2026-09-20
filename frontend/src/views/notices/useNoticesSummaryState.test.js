// @vitest-environment jsdom

import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/auth'

const { topicSummary } = vi.hoisted(() => ({
  topicSummary: vi.fn()
}))

vi.mock('../../api/services/noticeService', () => ({
  topicSummary
}))

import { noticeTopicPresentation, noticeUnreadCount, useNoticesSummaryState } from './useNoticesSummaryState'

function mountState({ authed = true } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  if (authed) {
    useAuthStore().installSession({
      accessToken: 'access-token',
      me: { userId: '11111111-1111-7111-8111-111111111111', username: 'viewer' }
    })
  }

  let state
  const Harness = defineComponent({
    setup() {
      state = useNoticesSummaryState()
      return () => null
    }
  })
  mount(Harness, { global: { plugins: [pinia] } })
  return state
}

describe('useNoticesSummaryState presentation', () => {
  it('maps the production topics to localized copy', () => {
    expect(noticeTopicPresentation('like')).toMatchObject({ title: '点赞' })
    expect(noticeTopicPresentation('comment')).toMatchObject({ title: '评论' })
    expect(noticeTopicPresentation('follow')).toMatchObject({ title: '关注' })
    expect(noticeTopicPresentation('moderation')).toMatchObject({ title: '治理' })
  })

  it('echoes unknown topic keys as-is and defaults empty or null to 其他', () => {
    expect(noticeTopicPresentation('unknown-topic')).toMatchObject({ title: 'unknown-topic' })
    expect(noticeTopicPresentation('')).toMatchObject({ title: '其他' })
    expect(noticeTopicPresentation(null)).toMatchObject({ title: '其他' })
  })

  it('normalizes unread counts to non-negative finite numbers', () => {
    expect(noticeUnreadCount({ unreadCount: 3 })).toBe(3)
    expect(noticeUnreadCount({ unreadCount: 0 })).toBe(0)
    expect(noticeUnreadCount({ unreadCount: -2 })).toBe(0)
    expect(noticeUnreadCount({ unreadCount: 'not-a-number' })).toBe(0)
    expect(noticeUnreadCount(null)).toBe(0)
  })
})

describe('useNoticesSummaryState loading', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the grouped inbox on mount and counts topics with pending unread', async () => {
    topicSummary.mockResolvedValueOnce({
      data: [
        { topic: 'comment', unreadCount: 2 },
        { topic: 'like', unreadCount: 0 },
        { topic: 'follow', unreadCount: 5 }
      ]
    })

    const state = mountState()
    await flushPromises()

    expect(topicSummary).toHaveBeenCalledTimes(1)
    expect(state.loading.value).toBe(false)
    expect(state.error.value).toBe('')
    expect(state.items.value).toHaveLength(3)
    expect(state.pendingTopicCount.value).toBe(2)
  })

  it('keeps the error surface retryable after a failed load', async () => {
    topicSummary
      .mockRejectedValueOnce(new Error('通知服务不可用'))
      .mockResolvedValueOnce({ data: [] })

    const state = mountState()
    await flushPromises()
    expect(state.error.value).toBe('通知服务不可用')
    expect(state.items.value).toHaveLength(0)

    await state.reload()
    await flushPromises()
    expect(state.error.value).toBe('')
    expect(state.loading.value).toBe(false)
  })

  it('treats a non-array payload as an empty inbox', async () => {
    topicSummary.mockResolvedValueOnce({ data: null })

    const state = mountState()
    await flushPromises()

    expect(state.items.value).toEqual([])
    expect(state.error.value).toBe('')
  })

  it('does not load for an anonymous viewer and stays quiet', async () => {
    const state = mountState({ authed: false })
    await flushPromises()

    expect(topicSummary).not.toHaveBeenCalled()
    expect(state.loading.value).toBe(false)
    expect(state.items.value).toHaveLength(0)
  })

  it('discards a stale response after the account switches and reloads for the new identity', async () => {
    let resolvePrevious
    topicSummary
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePrevious = resolve }))
      .mockResolvedValueOnce({ data: [{ topic: 'comment', unreadCount: 1 }] })

    const state = mountState()
    await flushPromises()

    useAuthStore().installSession({
      accessToken: 'replacement-token',
      me: { userId: '22222222-2222-7222-8222-222222222222', username: 'other' }
    })
    await flushPromises()
    expect(topicSummary).toHaveBeenCalledTimes(2)
    expect(state.items.value).toHaveLength(1)

    resolvePrevious({ data: [{ topic: 'like', unreadCount: 9 }] })
    await flushPromises()
    expect(state.items.value).toHaveLength(1)
    expect(state.items.value[0].topic).toBe('comment')
  })

  it('clears private rows when the session ends', async () => {
    topicSummary.mockResolvedValue({ data: [{ topic: 'comment', unreadCount: 1 }] })

    const state = mountState()
    await flushPromises()
    expect(state.items.value).toHaveLength(1)

    useAuthStore().clear()
    await flushPromises()

    expect(state.items.value).toHaveLength(0)
    expect(topicSummary).toHaveBeenCalledTimes(1)
  })
})
