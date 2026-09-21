// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { markImConversationRead, getImUnreadSummary } from '../api/services/imCoreChatService'
import { topicSummary } from '../api/services/noticeService'
import { useAuthStore } from '../stores/auth'
import { INBOX_UNREAD_REFRESH_DEBOUNCE_MS, useInboxUnreadStore } from '../stores/inboxUnread'
import { createConversationReadMarker } from './conversationDetailReadMarker'

vi.mock('../api/services/imCoreChatService', () => ({
  markImConversationRead: vi.fn().mockResolvedValue({}),
  getImUnreadSummary: vi.fn().mockResolvedValue({ conversations: [] })
}))

vi.mock('../api/services/noticeService', () => ({
  topicSummary: vi.fn().mockResolvedValue({ data: [] })
}))

function message(seq, { fromId = 'other' } = {}) {
  return { id: `m-${seq}`, seq, fromId, toId: 'me', content: `msg-${seq}`, clientMsgId: '', createTime: seq }
}

describe('createConversationReadMarker', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // 源函数返回 Promise<void>：测试只需一个已完成的值，运行时占位与 {} 等价。
    vi.mocked(markImConversationRead).mockResolvedValue(/** @type {void} */ (/** @type {unknown} */ ({})))
    setActivePinia(createPinia())
  })

  afterEach(() => {
    useInboxUnreadStore().reset()
  })

  it('anchors the baseline from the confirmed HTTP page and reports the contiguous waterline', async () => {
    const marker = createConversationReadMarker()

    await marker.anchorAndReport('conv-1', 5, [message(1), message(3), message(5), message(7)])

    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenCalledWith('conv-1', 5)
  })

  it('advances past the anchor when realtime frames during the load are contiguous', async () => {
    const marker = createConversationReadMarker()

    // HTTP 页确认到 3，加载期间到达的实时帧 4、5 连续覆盖，一次性推进。
    await marker.anchorAndReport('conv-1', 3, [message(1), message(2), message(3), message(4), message(5)])

    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenCalledWith('conv-1', 5)
  })

  it('does not advance past the confirmed anchor while a gap remains above it', async () => {
    const marker = createConversationReadMarker()

    // 确认水位 5；seq 7 缺 6，缺口之后的消息不提前标读。
    await marker.anchorAndReport('conv-1', 5, [message(1), message(2), message(5), message(7)])

    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenCalledWith('conv-1', 5)
  })

  it('does not report when the HTTP page confirms no contiguous waterline', async () => {
    const marker = createConversationReadMarker()

    await marker.anchorAndReport('conv-1', 0, [message(2), message(4)])
    await marker.anchorAndReport('conv-1', Number.NaN, [message(1)])

    expect(markImConversationRead).not.toHaveBeenCalled()
  })

  it('advances only across realtime gaps and jumps after the gap is filled', async () => {
    const marker = createConversationReadMarker()

    await marker.advanceAndReport('conv-1', [message(1), message(2), message(3)])
    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenCalledWith('conv-1', 3)
    vi.mocked(markImConversationRead).mockClear()

    // 乱序帧：先到 seq 5（缺 4），水位不推进。
    await marker.advanceAndReport('conv-1', [message(1), message(2), message(3), message(5)])
    expect(markImConversationRead).not.toHaveBeenCalled()

    // 缺口补齐（seq 4 到达），一次性推进到 5。
    await marker.advanceAndReport('conv-1', [message(1), message(2), message(3), message(4), message(5)])
    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenCalledWith('conv-1', 5)
  })

  it('ignores an anchor lower than the already-tracked waterline', async () => {
    const marker = createConversationReadMarker()

    await marker.advanceAndReport('conv-1', [message(1), message(2), message(3)])
    vi.mocked(markImConversationRead).mockClear()

    // 迟到的低水位 HTTP 页不回退基线。
    await marker.anchorAndReport('conv-1', 1, [message(1)])
    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenCalledWith('conv-1', 3)
  })

  it('reset drops the waterline so a conversation switch restarts from the page anchor', async () => {
    const marker = createConversationReadMarker()

    await marker.advanceAndReport('conv-1', [message(1), message(2)])
    marker.reset()

    await marker.anchorAndReport('conv-2', 1, [message(1)])
    expect(markImConversationRead).toHaveBeenLastCalledWith('conv-2', 1)
  })

  it('schedules the shell unread badge refresh after the read marker lands', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    try {
      const marker = createConversationReadMarker()
      const auth = useAuthStore()
      auth.installSession({
        accessToken: 'token',
        me: { userId: '11111111-1111-7111-8111-111111111111', username: 'me' }
      })
      const inboxUnread = useInboxUnreadStore()

      await marker.advanceAndReport('conv-1', [message(1)])
      await vi.advanceTimersByTimeAsync(INBOX_UNREAD_REFRESH_DEBOUNCE_MS)

      // 已登录防抖窗口后发起真实角标刷新（通知 + 私信汇总各一次）。
      expect(topicSummary).toHaveBeenCalledTimes(1)
      expect(getImUnreadSummary).toHaveBeenCalledTimes(1)
      expect(inboxUnread.requestId).toBeGreaterThan(0)
    } finally {
      useAuthStore().clear()
      vi.useRealTimers()
    }
  })

  it('keeps the conversation flow unaffected when the read report fails', async () => {
    vi.mocked(markImConversationRead).mockRejectedValueOnce(new Error('标记已读失败'))
    const marker = createConversationReadMarker()

    await expect(marker.advanceAndReport('conv-1', [message(1)])).resolves.toBeUndefined()
  })
})
