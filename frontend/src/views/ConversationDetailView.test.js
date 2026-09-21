// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
const {
  listeners,
  listImConversationHistory,
  listImConversationMessages,
  markImConversationRead,
  getImUnreadSummary,
  topicSummary,
  sendPrivateText,
  imRealtimeClient
} = vi.hoisted(() => {
  const listenersLocal = {}
  const client = {
    state: { connected: true },
    on: vi.fn((event, handler) => {
      listenersLocal[event] = handler
      return vi.fn()
    }),
    sendPrivateText: vi.fn(() => 'client-msg-1')
  }

  return {
    listeners: listenersLocal,
    listImConversationHistory: vi.fn(),
    listImConversationMessages: vi.fn(),
    markImConversationRead: vi.fn(),
    getImUnreadSummary: vi.fn(),
    topicSummary: vi.fn(),
    sendPrivateText: client.sendPrivateText,
    imRealtimeClient: client
  }
})

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversationHistory,
  listImConversationMessages,
  markImConversationRead,
  getImUnreadSummary
}))

vi.mock('../api/services/noticeService', () => ({
  topicSummary
}))

vi.mock('../im/imRealtimeClient', () => ({
  imRealtimeClient
}))

import { useAuthStore } from '../stores/auth'
import { INBOX_UNREAD_REFRESH_DEBOUNCE_MS, useInboxUnreadStore } from '../stores/inboxUnread'
import ConversationDetailView from './ConversationDetailView.vue'

// vitest 默认连 setImmediate 一起 fake 会让 flushPromises 挂起，这里只 fake 超时器相关的四类。
function useViewFakeTimers() {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] })
}

function mountView(conversationId) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const auth = useAuthStore()
  auth.installSession({
    accessToken: 'token-user-a',
    me: {
      userId: '11111111-1111-7111-8111-111111111111',
      username: 'me',
      authorities: []
    }
  })

  return mount(ConversationDetailView, {
    props: { conversationId },
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :href="to"><slot /></a>'
        },
        UiCard: { template: '<section><slot /></section>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiIconButton: {
          inheritAttrs: false,
          props: ['disabled'],
          emits: ['click'],
          template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('ConversationDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    listImConversationHistory.mockResolvedValue({
      items: [
        {
          messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          seq: 3,
          fromUserId: '22222222-2222-7222-8222-222222222222',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '第一条消息',
          clientMsgId: 'client-a',
          createdAtEpochMs: 1774060182920
        },
        {
          messageId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          seq: 8,
          fromUserId: '11111111-1111-7111-8111-111111111111',
          toUserId: '22222222-2222-7222-8222-222222222222',
          content: '第二条消息',
          clientMsgId: 'client-b',
          createdAtEpochMs: 1774060183920
        }
      ],
      nextBeforeSeq: 3,
      hasMore: true,
      lastReadSeq: 0
    })
    listImConversationMessages.mockResolvedValue({ items: [] })
    markImConversationRead.mockResolvedValue({})
    getImUnreadSummary.mockResolvedValue({ rooms: [], conversations: [] })
    topicSummary.mockResolvedValue({ data: [] })
    sendPrivateText.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads the latest history, marks its maximum seq read, scrolls bottom, and sends to the participant', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 640 })

    await flushPromises()

    expect(wrapper.findAll('.chat-divider')).toHaveLength(2)
    expect(listImConversationHistory).toHaveBeenCalledWith(conversationId, { limit: 50 })
    expect(markImConversationRead).toHaveBeenCalledWith(conversationId, 8)
    expect(chatArea.scrollTop).toBe(640)
    expect(wrapper.text()).toContain('消息时间线')
    expect(wrapper.text()).toContain('第一条消息')
    expect(wrapper.text()).toContain('第二条消息')
    expect(wrapper.text()).toContain('实时已就绪')

    await wrapper.get('textarea').setValue('继续聊')
    await flushPromises()
    const sendButton = wrapper.get('button[aria-label="发送消息"]')
    expect(sendButton.attributes('disabled')).toBeUndefined()
    await sendButton.trigger('click')
    await flushPromises()

    expect(sendPrivateText).toHaveBeenCalledWith({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '继续聊'
    })
  })

  it('refreshes the shell unread badge after the loaded tail is marked read', async () => {
    useViewFakeTimers()
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    topicSummary.mockResolvedValue({ data: [{ topic: 'comment', unreadCount: 2 }] })
    getImUnreadSummary.mockResolvedValue({
      rooms: [],
      conversations: [{ conversationId: 'another-conversation', unreadCount: 6 }]
    })

    mountView(conversationId)
    await flushPromises()

    expect(markImConversationRead).toHaveBeenCalledWith(conversationId, 8)

    // 角标刷新走防抖合并，标记已读后的一个防抖窗口内发出。
    await vi.advanceTimersByTimeAsync(INBOX_UNREAD_REFRESH_DEBOUNCE_MS)
    await flushPromises()

    const inboxUnread = useInboxUnreadStore()
    expect(inboxUnread.noticeUnread).toBe(2)
    expect(inboxUnread.messageUnread).toBe(6)
  })

  it('retains realtime messages that arrive while the latest history is loading', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    /** @type {((value: unknown) => void) | undefined} */
    let resolveHistory
    listImConversationHistory.mockImplementationOnce(() => new Promise((resolve) => { resolveHistory = resolve }))

    const wrapper = mountView(conversationId)
    await listeners.privateMessage({
      conversationId,
      messageId: '99999999-9999-7999-8999-999999999999',
      seq: 9,
      fromUserId: '11111111-1111-7111-8111-111111111111',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '加载期间到达',
      clientMsgId: 'client-live',
      createdAtEpochMs: 1774060187920
    })
    if (!resolveHistory) throw new Error('resolver not captured')
    resolveHistory({
      items: [{
        messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
        seq: 3,
        fromUserId: '22222222-2222-7222-8222-222222222222',
        toUserId: '11111111-1111-7111-8111-111111111111',
        content: '历史响应',
        clientMsgId: 'client-history',
        createdAtEpochMs: 1774060182920
      }],
      nextBeforeSeq: null,
      hasMore: false
    })
    await flushPromises()

    expect(wrapper.text()).toContain('历史响应')
    expect(wrapper.text()).toContain('加载期间到达')
    expect(wrapper.findAll('.message-row')).toHaveLength(2)
    // 加载期间到达的实时帧（seq 9）与 HTTP 基线（seq 3）之间有缺口：
    // 已读只按连续水位 3 上报，缺口由重连 backfill 补齐后再推进。
    expect(markImConversationRead).toHaveBeenLastCalledWith(conversationId, 3)
  })

  it('keeps a successful history load usable when marking it read fails', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    markImConversationRead.mockRejectedValueOnce(new Error('标记已读失败'))

    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 640 })
    await flushPromises()

    expect(wrapper.text()).toContain('第一条消息')
    expect(wrapper.text()).toContain('第二条消息')
    expect(wrapper.text()).not.toContain('标记已读失败')
    expect(chatArea.scrollTop).toBe(640)
  })

  it('loads earlier history and preserves the current scroll anchor', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    /** @type {((value: unknown) => void) | undefined} */
    let resolveEarlier
    listImConversationHistory
      .mockResolvedValueOnce({
        items: [
          {
            messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
            seq: 4,
            fromUserId: '22222222-2222-7222-8222-222222222222',
            toUserId: '11111111-1111-7111-8111-111111111111',
            content: '当前第一页',
            clientMsgId: 'client-4',
            createdAtEpochMs: 1774060184920
          }
        ],
        nextBeforeSeq: 4,
        hasMore: true,
        lastReadSeq: 0
      })
      .mockImplementationOnce(() => new Promise((resolve) => { resolveEarlier = resolve }))

    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    let scrollHeight = 300
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, get: () => scrollHeight })
    await flushPromises()
    chatArea.scrollTop = 40

    const loadEarlier = wrapper.get('[data-testid="load-earlier-messages"]')
    await loadEarlier.trigger('click')
    expect(listImConversationHistory).toHaveBeenLastCalledWith(conversationId, { beforeSeq: 4, limit: 50 })
    expect(wrapper.get('[data-testid="load-earlier-messages"]').attributes('disabled')).toBeDefined()

    scrollHeight = 500
    if (!resolveEarlier) throw new Error('resolver not captured')
    resolveEarlier({
      items: [
        {
          messageId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
          seq: 2,
          fromUserId: '22222222-2222-7222-8222-222222222222',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '更早的消息',
          clientMsgId: 'client-2',
          createdAtEpochMs: 1774060181920
        }
      ],
      nextBeforeSeq: null,
      hasMore: false,
      lastReadSeq: 0
    })
    await flushPromises()

    expect(wrapper.text()).toContain('更早的消息')
    expect(wrapper.text()).toContain('当前第一页')
    expect(chatArea.scrollTop).toBe(240)
    expect(wrapper.find('[data-testid="load-earlier-messages"]').exists()).toBe(false)
    expect(markImConversationRead).toHaveBeenCalledTimes(1)
  })

  it('refreshes back to the latest history page and scrolls bottom', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    listImConversationHistory
      .mockResolvedValueOnce({
        items: [{
          messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          seq: 3,
          fromUserId: '22222222-2222-7222-8222-222222222222',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '旧页面',
          clientMsgId: 'client-old',
          createdAtEpochMs: 1774060182920
        }],
        nextBeforeSeq: 3,
        hasMore: true
      })
      .mockResolvedValueOnce({
        items: [{
          messageId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
          seq: 9,
          fromUserId: '22222222-2222-7222-8222-222222222222',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '刷新后的最新消息',
          clientMsgId: 'client-new',
          createdAtEpochMs: 1774060185920
        }],
        nextBeforeSeq: null,
        hasMore: false
      })

    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 720 })
    await flushPromises()
    await wrapper.find('.chat-header-actions button').trigger('click')
    await flushPromises()

    expect(listImConversationHistory).toHaveBeenLastCalledWith(conversationId, { limit: 50 })
    expect(wrapper.text()).not.toContain('旧页面')
    expect(wrapper.text()).toContain('刷新后的最新消息')
    expect(chatArea.scrollTop).toBe(720)
    expect(wrapper.find('[data-testid="load-earlier-messages"]').exists()).toBe(false)
  })

  it('deduplicates realtime identities without scrolling unless a new tail arrives', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 800 })
    await flushPromises()
    chatArea.scrollTop = 25

    await listeners.privateMessage({
      conversationId,
      messageId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      seq: 3,
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '按 seq 重复',
      clientMsgId: 'client-c',
      createdAtEpochMs: 1774060184920
    })
    await listeners.privateMessage({
      conversationId,
      messageId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
      seq: 7,
      fromUserId: '11111111-1111-7111-8111-111111111111',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '按 messageId 重复',
      clientMsgId: 'client-d',
      createdAtEpochMs: 1774060185920
    })
    await listeners.privateMessage({
      conversationId,
      messageId: 'ffffffff-ffff-7fff-8fff-ffffffffffff',
      seq: 6,
      fromUserId: '11111111-1111-7111-8111-111111111111',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '按 clientMsgId 重复',
      clientMsgId: 'client-b',
      createdAtEpochMs: 1774060186920
    })
    await flushPromises()

    expect(wrapper.findAll('.message-row')).toHaveLength(2)
    expect(chatArea.scrollTop).toBe(25)

    await listeners.privateMessage({
      conversationId,
      messageId: '99999999-9999-7999-8999-999999999999',
      seq: 9,
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '新的尾消息',
      clientMsgId: 'client-9',
      createdAtEpochMs: 1774060187920
    })
    await flushPromises()

    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(chatArea.scrollTop).toBe(800)
  })

  it('clears the stale inline send error after a later send succeeds', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('会被拒绝的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    listeners.sendRejected({ cmd: 'sendPrivateText', clientMsgId: 'client-msg-1', message: '发送频率过高' })
    await flushPromises()
    expect(wrapper.get('.chat-inline-error').text()).toContain('发送频率过高')

    // 下一次发送成功后，上一次残留的行内错误文案必须清除。
    await wrapper.get('textarea').setValue('随后成功的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('.chat-inline-error').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('发送频率过高')
    expect(wrapper.text()).toContain('随后成功的消息')
  })

  it('reports read markers by contiguous seq waterline when realtime frames arrive out of order', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()
    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenLastCalledWith(conversationId, 8)

    // 帧乱序：seq 10 先到但 9 尚未收到，不能把中间消息提前标读。
    await listeners.privateMessage({
      type: 'privateMessage',
      conversationId,
      seq: 10,
      messageId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '乱序先到的 10',
      createdAtEpochMs: 1774060188920
    })
    await flushPromises()
    expect(wrapper.text()).toContain('乱序先到的 10')
    expect(markImConversationRead).toHaveBeenCalledTimes(1)

    // 缺口 9 补齐后，连续水位一次性推进到 10。
    await listeners.privateMessage({
      type: 'privateMessage',
      conversationId,
      seq: 9,
      messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '补回缺口的 9',
      createdAtEpochMs: 1774060187920
    })
    await flushPromises()
    expect(markImConversationRead).toHaveBeenCalledTimes(2)
    expect(markImConversationRead).toHaveBeenLastCalledWith(conversationId, 10)
  })

  it('claims the pending bubble when the server echo arrives before the committed frame', async () => {
    useViewFakeTimers()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('回声先到的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(wrapper.text()).toContain('发送中')

    // 生产 privateMessage 帧不携带 clientMsgId：回声先于 committed 回执到达。
    await listeners.privateMessage({
      type: 'privateMessage',
      conversationId,
      seq: 9,
      messageId: '99999999-9999-7999-8999-999999999999',
      fromUserId: '11111111-1111-7111-8111-111111111111',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '回声先到的消息',
      createdAtEpochMs: 1774060187920
    })
    await flushPromises()

    // 回声认领 pending 气泡：不出现短暂的重复气泡，且按已提交呈现。
    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(wrapper.text()).toContain('回声先到的消息')
    expect(wrapper.text()).not.toContain('发送中')

    // 回声本身即持久化事实：兜底计时器已解除，超时不能再把它误转失败。
    await vi.advanceTimersByTimeAsync(20_000)
    await flushPromises()
    expect(wrapper.text()).not.toContain('发送失败')
    expect(wrapper.text()).not.toContain('发送超时')

    // 迟到的 committed 回执幂等落地，仍然只有一条气泡。
    listeners.sendCommitted({
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      messageId: '99999999-9999-7999-8999-999999999999',
      seq: 9,
      requestId: 'req-echo-first-1'
    })
    await flushPromises()
    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('发送中')
    expect(wrapper.text()).not.toContain('发送失败')
  })

  it('keeps a single bubble when the committed frame arrives before the server echo', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('回执先到的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.message-row')).toHaveLength(3)

    listeners.sendCommitted({
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      messageId: '99999999-9999-7999-8999-999999999999',
      seq: 9,
      requestId: 'req-committed-first-1'
    })
    await flushPromises()
    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('发送中')

    // 随后到达的服务端回声按 messageId / seq 别名归并，不产生重复气泡。
    await listeners.privateMessage({
      type: 'privateMessage',
      conversationId,
      seq: 9,
      messageId: '99999999-9999-7999-8999-999999999999',
      fromUserId: '11111111-1111-7111-8111-111111111111',
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '回执先到的消息',
      createdAtEpochMs: 1774060187920
    })
    await flushPromises()
    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(wrapper.text()).toContain('回执先到的消息')
  })

  it('does not send or clear the composer before realtime authentication completes', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = false
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)

    await flushPromises()

    await wrapper.get('textarea').setValue('还没认证')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()

    expect(sendPrivateText).not.toHaveBeenCalled()
    expect(wrapper.get('textarea').element.value).toBe('还没认证')
    expect(wrapper.text()).toContain('IM 正在认证，请稍后重试')
  })

  it('updates the realtime status when the client emits state changes', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = false
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)

    await flushPromises()

    expect(wrapper.text()).toContain('实时认证中')

    listeners.stateChanged({
      connected: true,
      authed: true,
      sessionId: 'sess-1',
      userId: ''
    })
    await flushPromises()

    expect(wrapper.text()).toContain('实时已就绪')
    expect(listImConversationMessages).toHaveBeenCalledWith(conversationId, { afterSeq: 8, limit: 100 })
  })

  it('marks a rejected send failed and retries it with the same clientMsgId without duplicating the message', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('待重试的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('待重试的消息')
    expect(wrapper.text()).toContain('发送中')

    listeners.sendRejected({
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      message: '发送频率过高',
      traceId: 'trace-reject-1'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('发送失败')
    expect(wrapper.text()).toContain('发送频率过高')
    expect(wrapper.text()).not.toContain('发送中')
    const retryButton = wrapper.get('.message-retry')
    expect(retryButton.attributes('disabled')).toBeUndefined()

    sendPrivateText.mockClear()
    await retryButton.trigger('click')
    await flushPromises()

    // 同一写尝试的重试复用原 clientMsgId，不生成新 key。
    expect(sendPrivateText).toHaveBeenCalledTimes(1)
    expect(sendPrivateText).toHaveBeenCalledWith({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '待重试的消息',
      clientMsgId: 'client-msg-1'
    })
    expect(wrapper.text()).toContain('发送中')
    expect(wrapper.text()).not.toContain('发送失败')
    expect(wrapper.text()).not.toContain('发送频率过高')

    listeners.sendCommitted({
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      messageId: '99999999-9999-7999-8999-999999999999',
      seq: 9,
      requestId: 'req-retry-1'
    })
    await flushPromises()

    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(wrapper.text()).toContain('待重试的消息')
    expect(wrapper.text()).not.toContain('发送中')
    expect(wrapper.text()).not.toContain('发送失败')
  })

  it('keeps a failed send retryable only when realtime is ready and never resends while offline', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('断网后重试')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()

    listeners.sendRejected({ cmd: 'sendPrivateText', clientMsgId: 'client-msg-1', message: '发送失败' })
    await flushPromises()
    expect(wrapper.text()).toContain('发送失败')

    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    await flushPromises()

    sendPrivateText.mockClear()
    const retryButton = wrapper.get('.message-retry')
    expect(retryButton.attributes('disabled')).toBeDefined()
    await retryButton.trigger('click')
    await flushPromises()
    expect(sendPrivateText).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('发送失败')
  })

  it('ignores a retry for a message that is no longer failed', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('只重试一次')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    listeners.sendRejected({ cmd: 'sendPrivateText', clientMsgId: 'client-msg-1', message: '发送失败' })
    await flushPromises()

    sendPrivateText.mockClear()
    await wrapper.get('.message-retry').trigger('click')
    await flushPromises()
    expect(sendPrivateText).toHaveBeenCalledTimes(1)

    // 消息回到 pending 后重试入口随即消失，同一写尝试不会被重复提交。
    expect(wrapper.find('.message-retry').exists()).toBe(false)
    expect(wrapper.text()).toContain('发送中')

    listeners.sendRejected({ cmd: 'sendPrivateText', clientMsgId: 'client-msg-unknown', message: '无关失败' })
    await flushPromises()
    expect(wrapper.find('.message-retry').exists()).toBe(false)
    expect(wrapper.text()).toContain('发送中')
  })

  it('marks a lost pending send failed after the delivery timeout and retries it with the same clientMsgId', async () => {
    useViewFakeTimers()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('失联的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('发送中')

    // 兜底时限未到：正常在途的发送不被误转失败。
    await vi.advanceTimersByTimeAsync(9_999)
    await flushPromises()
    expect(wrapper.text()).toContain('发送中')
    expect(wrapper.find('.message-retry').exists()).toBe(false)

    // 帧写入后连接死亡且服务端从未收到：超时后转失败态并开放既有重试入口。
    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()
    expect(wrapper.text()).toContain('发送失败')
    expect(wrapper.text()).toContain('发送超时')
    expect(wrapper.text()).not.toContain('发送中')

    sendPrivateText.mockClear()
    await wrapper.get('.message-retry').trigger('click')
    await flushPromises()

    // 重发复用原 clientMsgId（同一写尝试），并重新进入发送中。
    expect(sendPrivateText).toHaveBeenCalledTimes(1)
    expect(sendPrivateText).toHaveBeenCalledWith({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '失联的消息',
      clientMsgId: 'client-msg-1'
    })
    expect(wrapper.text()).toContain('发送中')
    expect(wrapper.text()).not.toContain('发送失败')

    // 重试收到的 committed 同样解除兜底：之后不再被超时器误伤。
    listeners.sendCommitted({
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      messageId: '99999999-9999-7999-8999-999999999999',
      seq: 9,
      requestId: 'req-timeout-retry-1'
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(20_000)
    await flushPromises()

    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(wrapper.text()).toContain('失联的消息')
    expect(wrapper.text()).not.toContain('发送中')
    expect(wrapper.text()).not.toContain('发送失败')
  })

  it('clears the delivery timeout when the committed frame arrives before the deadline', async () => {
    useViewFakeTimers()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('正常发送')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('发送中')

    listeners.sendCommitted({
      cmd: 'sendPrivateText',
      clientMsgId: 'client-msg-1',
      messageId: '99999999-9999-7999-8999-999999999999',
      seq: 9,
      requestId: 'req-commit-1'
    })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(20_000)
    await flushPromises()

    expect(wrapper.text()).toContain('正常发送')
    expect(wrapper.text()).not.toContain('发送中')
    expect(wrapper.text()).not.toContain('发送失败')
    expect(wrapper.find('.message-retry').exists()).toBe(false)
  })

  it('clears the delivery timeout when the send is rejected before the deadline', async () => {
    useViewFakeTimers()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('被拒绝的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()

    listeners.sendRejected({ cmd: 'sendPrivateText', clientMsgId: 'client-msg-1', message: '发送频率过高' })
    await flushPromises()
    expect(wrapper.text()).toContain('发送频率过高')

    // reject 已终结这次写尝试：迟到的超时器不能覆盖拒绝原因或重复标记。
    await vi.advanceTimersByTimeAsync(20_000)
    await flushPromises()
    expect(wrapper.text()).toContain('发送失败')
    expect(wrapper.text()).toContain('发送频率过高')
    expect(wrapper.text()).not.toContain('发送超时')
  })

  it('does not fail a pending send that reconnect backfill proves persisted', async () => {
    useViewFakeTimers()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    listImConversationMessages.mockResolvedValueOnce({
      items: [{
        messageId: '99999999-9999-7999-8999-999999999999',
        seq: 9,
        fromUserId: '11111111-1111-7111-8111-111111111111',
        toUserId: '22222222-2222-7222-8222-222222222222',
        content: '已持久化消息',
        clientMsgId: 'client-msg-1',
        createdAtEpochMs: 1774060187920
      }]
    })
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('写入后断线的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('发送中')

    // 服务端实际已收到但回执丢失：重连 backfill 以 HTTP 事实收敛 pending。
    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    await flushPromises()
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-2', userId: '' })
    await flushPromises()
    expect(listImConversationMessages).toHaveBeenCalledWith(conversationId, { afterSeq: 8, limit: 100 })
    expect(wrapper.text()).toContain('已持久化消息')
    expect(wrapper.text()).not.toContain('发送中')

    await vi.advanceTimersByTimeAsync(20_000)
    await flushPromises()
    expect(wrapper.text()).not.toContain('发送失败')
    expect(wrapper.text()).not.toContain('发送超时')
    expect(wrapper.find('.message-retry').exists()).toBe(false)
  })

  it('does not carry pending send timeouts across conversation switches', async () => {
    useViewFakeTimers()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationA = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const conversationB = '11111111-1111-7111-8111-111111111111_33333333-3333-7333-8333-333333333333'
    const wrapper = mountView(conversationA)
    await flushPromises()

    await wrapper.get('textarea').setValue('切换前的失联消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('发送中')

    await wrapper.setProps({ conversationId: conversationB })
    await flushPromises()
    await vi.advanceTimersByTimeAsync(20_000)
    await flushPromises()

    expect(wrapper.text()).toContain('第一条消息')
    expect(wrapper.text()).not.toContain('切换前的失联消息')
    expect(wrapper.text()).not.toContain('发送失败')
    expect(wrapper.text()).not.toContain('发送超时')
  })

  it('fails a pending send when no receipt arrives before the fallback timeout', async () => {
    useViewFakeTimers()
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('没有回执的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('发送中')

    // 帧写入后连接立刻死亡时不会有任何回执：pending 不能永远挂起，兜底超时转失败态。
    await vi.advanceTimersByTimeAsync(10_000)
    await flushPromises()
    expect(wrapper.text()).toContain('发送失败')
    expect(wrapper.text()).toContain('发送超时')
    expect(wrapper.text()).not.toContain('发送中')
  })

  it('shows a pending send and replaces it with HTTP backfill on reconnect', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    listImConversationMessages.mockResolvedValueOnce({
      items: [{
        messageId: '99999999-9999-7999-8999-999999999999',
        seq: 9,
        fromUserId: '11111111-1111-7111-8111-111111111111',
        toUserId: '22222222-2222-7222-8222-222222222222',
        content: '已持久化消息',
        clientMsgId: 'client-msg-1',
        createdAtEpochMs: 1774060187920
      }]
    })
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('待提交消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('待提交消息')
    expect(wrapper.text()).toContain('发送中')

    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    await flushPromises()
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-2', userId: '' })
    await flushPromises()

    expect(listImConversationMessages).toHaveBeenCalledWith(conversationId, { afterSeq: 8, limit: 100 })
    expect(wrapper.text()).toContain('已持久化消息')
    expect(wrapper.text()).not.toContain('待提交消息')
    expect(wrapper.text()).not.toContain('发送中')
  })

  it('backfills a pending send even when a later realtime frame already advanced the visible tail', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = true
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    listImConversationMessages.mockResolvedValueOnce({
      items: [
        {
          messageId: '99999999-9999-7999-8999-999999999999',
          seq: 9,
          fromUserId: '11111111-1111-7111-8111-111111111111',
          toUserId: '22222222-2222-7222-8222-222222222222',
          content: 'pending 的持久化结果',
          clientMsgId: 'client-msg-1',
          createdAtEpochMs: 1774060187920
        },
        {
          messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
          seq: 10,
          fromUserId: '22222222-2222-7222-8222-222222222222',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '实时尾消息的持久化结果',
          clientMsgId: 'client-live-10',
          createdAtEpochMs: 1774060188920
        }
      ]
    })
    const wrapper = mountView(conversationId)
    await flushPromises()

    await wrapper.get('textarea').setValue('等待 committed 的消息')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await listeners.privateMessage({
      conversationId,
      messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
      seq: 10,
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '实时尾消息',
      clientMsgId: 'client-live-10',
      createdAtEpochMs: 1774060188920
    })
    await flushPromises()

    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-2', userId: '' })
    await flushPromises()

    expect(listImConversationMessages).toHaveBeenCalledWith(conversationId, { afterSeq: 8, limit: 100 })
    expect(wrapper.text()).toContain('pending 的持久化结果')
    expect(wrapper.text()).not.toContain('等待 committed 的消息')
    expect(wrapper.text()).not.toContain('发送中')
    expect(wrapper.findAll('.message-row')).toHaveLength(4)
  })

  it('only marks the contiguous backfill waterline read and advances after the gap is filled', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = false
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    listImConversationMessages
      .mockResolvedValueOnce({
        items: [
          {
            messageId: '99999999-9999-7999-8999-999999999999',
            seq: 9,
            fromUserId: '22222222-2222-7222-8222-222222222222',
            toUserId: '11111111-1111-7111-8111-111111111111',
            content: '连续消息 9',
            clientMsgId: 'client-9',
            createdAtEpochMs: 1774060187920
          },
          {
            messageId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
            seq: 11,
            fromUserId: '22222222-2222-7222-8222-222222222222',
            toUserId: '11111111-1111-7111-8111-111111111111',
            content: '越过缺口的消息 11',
            clientMsgId: 'client-11',
            createdAtEpochMs: 1774060189920
          }
        ]
      })
      .mockResolvedValueOnce({
        items: [
          {
            messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
            seq: 10,
            fromUserId: '11111111-1111-7111-8111-111111111111',
            toUserId: '22222222-2222-7222-8222-222222222222',
            content: '补回缺口 10',
            clientMsgId: 'client-10',
            createdAtEpochMs: 1774060188920
          },
          {
            messageId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
            seq: 11,
            fromUserId: '22222222-2222-7222-8222-222222222222',
            toUserId: '11111111-1111-7111-8111-111111111111',
            content: '消息 11',
            clientMsgId: 'client-11',
            createdAtEpochMs: 1774060189920
          }
        ]
      })
    mountView(conversationId)
    await flushPromises()

    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-1', userId: '' })
    await flushPromises()
    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-2', userId: '' })
    await flushPromises()

    expect(listImConversationMessages).toHaveBeenNthCalledWith(1, conversationId, { afterSeq: 8, limit: 100 })
    expect(listImConversationMessages).toHaveBeenNthCalledWith(2, conversationId, { afterSeq: 9, limit: 100 })
    expect(markImConversationRead).toHaveBeenNthCalledWith(2, conversationId, 9)
    expect(markImConversationRead).toHaveBeenNthCalledWith(3, conversationId, 11)
  })

  it('queues one more backfill pass when reconnect happens during an in-flight pass', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = false
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    /** @type {((value: unknown) => void) | undefined} */
    let resolveFirstBackfill
    listImConversationMessages
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirstBackfill = resolve }))
      .mockResolvedValueOnce({
        items: [{
          messageId: '99999999-9999-7999-8999-999999999999',
          seq: 9,
          fromUserId: '22222222-2222-7222-8222-222222222222',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '第二次恢复补回的消息',
          clientMsgId: 'client-rerun-9',
          createdAtEpochMs: 1774060187920
        }]
      })
    const wrapper = mountView(conversationId)
    await flushPromises()

    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-1', userId: '' })
    await flushPromises()
    expect(listImConversationMessages).toHaveBeenCalledTimes(1)

    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-2', userId: '' })
    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-3', userId: '' })
    await flushPromises()
    expect(listImConversationMessages).toHaveBeenCalledTimes(1)

    if (!resolveFirstBackfill) throw new Error('resolver not captured')
    resolveFirstBackfill({ items: [] })
    await flushPromises()

    expect(listImConversationMessages).toHaveBeenCalledTimes(2)
    expect(listImConversationMessages).toHaveBeenNthCalledWith(2, conversationId, { afterSeq: 8, limit: 100 })
    expect(wrapper.text()).toContain('第二次恢复补回的消息')
  })

  it('completes empty reconnect rounds and retries from the same waterline on the next reconnect', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = false
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    mountView(conversationId)
    await flushPromises()

    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-1', userId: '' })
    await flushPromises()
    listeners.stateChanged({ connected: false, authed: false, sessionId: '', userId: '' })
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-2', userId: '' })
    await flushPromises()

    expect(listImConversationMessages).toHaveBeenCalledTimes(2)
    expect(listImConversationMessages).toHaveBeenNthCalledWith(1, conversationId, { afterSeq: 8, limit: 100 })
    expect(listImConversationMessages).toHaveBeenNthCalledWith(2, conversationId, { afterSeq: 8, limit: 100 })
  })

  it('waits for the initial latest-history baseline before reconnect backfill', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = false
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    /** @type {((value: unknown) => void) | undefined} */
    let resolveInitialHistory
    listImConversationHistory.mockImplementationOnce(() => new Promise((resolve) => { resolveInitialHistory = resolve }))
    mountView(conversationId)
    await flushPromises()

    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-1', userId: '' })
    await flushPromises()

    expect(listImConversationHistory).toHaveBeenCalledTimes(1)
    expect(listImConversationHistory).toHaveBeenCalledWith(conversationId, { limit: 50 })
    expect(listImConversationMessages).not.toHaveBeenCalled()

    if (!resolveInitialHistory) throw new Error('resolver not captured')
    resolveInitialHistory({
      items: [{
        messageId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
        seq: 80,
        fromUserId: '22222222-2222-7222-8222-222222222222',
        toUserId: '11111111-1111-7111-8111-111111111111',
        content: '最近 50 条中的尾消息',
        clientMsgId: 'client-history-80',
        createdAtEpochMs: 1774060187920
      }],
      nextBeforeSeq: 31,
      hasMore: true,
      lastReadSeq: 0
    })
    await flushPromises()

    expect(listImConversationMessages).toHaveBeenCalledTimes(1)
    expect(listImConversationMessages).toHaveBeenCalledWith(conversationId, { afterSeq: 80, limit: 100 })
  })

  it('renders realtime private messages from normalized client events', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 640 })
    await flushPromises()

    // realtime client 已把 WS 帧的 createdAtEpochMillis 归一为与 HTTP history 同名的 createdAtEpochMs。
    await listeners.privateMessage({
      type: 'privateMessage',
      conversationId,
      seq: 9,
      messageId: '99999999-9999-7999-8999-999999999999',
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '实时帧时间戳字段',
      createdAtEpochMs: 1774060187920
    })
    await flushPromises()

    expect(wrapper.text()).toContain('实时帧时间戳字段')
    expect(wrapper.findAll('.message-row')).toHaveLength(3)
    expect(chatArea.scrollTop).toBe(640)
    expect(markImConversationRead).toHaveBeenLastCalledWith(conversationId, 9)
  })

  it('rejects realtime private messages that miss persisted timestamps', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    mountView(conversationId)
    await flushPromises()

    // handler 对无法落账的消息必须 reject（realtime client 据此上报 listener 错误），不能静默吞掉。
    await expect(listeners.privateMessage({
      conversationId,
      messageId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      seq: 9,
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '缺少时间'
    })).rejects.toThrow('createdAtEpochMs 非法')
  })

  it('ignores an old history response after the route switches conversations', async () => {
    const conversationA = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const conversationB = '11111111-1111-7111-8111-111111111111_33333333-3333-7333-8333-333333333333'
    /** @type {((value: unknown) => void) | undefined} */
    let resolveConversationA
    /** @type {((value: unknown) => void) | undefined} */
    let resolveConversationB
    listImConversationHistory
      .mockImplementationOnce(() => new Promise((resolve) => { resolveConversationA = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveConversationB = resolve }))

    const wrapper = mountView(conversationA)
    await wrapper.setProps({ conversationId: conversationB })
    await flushPromises()

    if (!resolveConversationB) throw new Error('resolver not captured')
    resolveConversationB({
      items: [{
        messageId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
        seq: 4,
        fromUserId: '33333333-3333-7333-8333-333333333333',
        toUserId: '11111111-1111-7111-8111-111111111111',
        content: '会话 B 的消息',
        clientMsgId: 'client-b-route',
        createdAtEpochMs: 1774060184920
      }],
      nextBeforeSeq: null,
      hasMore: false
    })
    await flushPromises()
    if (!resolveConversationA) throw new Error('resolver not captured')
    resolveConversationA({
      items: [{
        messageId: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
        seq: 5,
        fromUserId: '22222222-2222-7222-8222-222222222222',
        toUserId: '11111111-1111-7111-8111-111111111111',
        content: '会话 A 的迟到消息',
        clientMsgId: 'client-a-route',
        createdAtEpochMs: 1774060185920
      }],
      nextBeforeSeq: null,
      hasMore: false
    })
    await flushPromises()

    expect(wrapper.text()).toContain('会话 B 的消息')
    expect(wrapper.text()).not.toContain('会话 A 的迟到消息')
    expect(markImConversationRead).toHaveBeenCalledTimes(1)
    expect(markImConversationRead).toHaveBeenCalledWith(conversationB, 4)
  })

  it('ignores a stale backfill run after the route switches conversations', async () => {
    imRealtimeClient.state.connected = true
    imRealtimeClient.state.authed = false
    const conversationA = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const conversationB = '11111111-1111-7111-8111-111111111111_33333333-3333-7333-8333-333333333333'
    listImConversationHistory
      .mockResolvedValueOnce({
        items: [{
          messageId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          seq: 8,
          fromUserId: '22222222-2222-7222-8222-222222222222',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '会话 A 基线',
          clientMsgId: 'client-a-baseline',
          createdAtEpochMs: 1774060183920
        }],
        nextBeforeSeq: null,
        hasMore: false
      })
      .mockResolvedValueOnce({
        items: [{
          messageId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          seq: 4,
          fromUserId: '33333333-3333-7333-8333-333333333333',
          toUserId: '11111111-1111-7111-8111-111111111111',
          content: '会话 B 基线',
          clientMsgId: 'client-b-baseline',
          createdAtEpochMs: 1774060184920
        }],
        nextBeforeSeq: null,
        hasMore: false
      })
    /** @type {((value: unknown) => void) | undefined} */
    let resolveConversationABackfill
    listImConversationMessages.mockImplementationOnce(
      () => new Promise((resolve) => { resolveConversationABackfill = resolve })
    )

    const wrapper = mountView(conversationA)
    await flushPromises()
    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-1', userId: '' })
    await flushPromises()
    expect(listImConversationMessages).toHaveBeenCalledWith(conversationA, { afterSeq: 8, limit: 100 })

    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 700 })
    await wrapper.setProps({ conversationId: conversationB })
    await flushPromises()
    expect(wrapper.text()).toContain('会话 B 基线')
    const readCallCount = markImConversationRead.mock.calls.length
    const scrollTop = chatArea.scrollTop

    if (!resolveConversationABackfill) throw new Error('resolver not captured')
    resolveConversationABackfill({
      items: [{
        messageId: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
        seq: 9,
        fromUserId: '22222222-2222-7222-8222-222222222222',
        toUserId: '11111111-1111-7111-8111-111111111111',
        content: '会话 A 的迟到补拉',
        clientMsgId: 'client-a-stale-backfill',
        createdAtEpochMs: 1774060185920
      }]
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('会话 A 的迟到补拉')
    expect(markImConversationRead).toHaveBeenCalledTimes(readCallCount)
    expect(chatArea.scrollTop).toBe(scrollTop)
  })

  it('clears messages and ignores stale HTTP and realtime data after account switching', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    /** @type {((value: unknown) => void) | undefined} */
    let resolveHistory
    listImConversationHistory.mockImplementationOnce(() => new Promise((resolve) => { resolveHistory = resolve }))

    const wrapper = mountView(conversationId)
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-user-b',
      me: {
        userId: '33333333-3333-7333-8333-333333333333',
        username: 'user-b',
        authorities: []
      }
    })
    await flushPromises()

    await listeners.privateMessage({
      conversationId,
      messageId: 'eeeeeeee-eeee-7eee-8eee-eeeeeeeeeeee',
      seq: 7,
      fromUserId: '22222222-2222-7222-8222-222222222222',
      toUserId: '11111111-1111-7111-8111-111111111111',
      content: '旧身份实时消息',
      clientMsgId: 'client-old-live',
      createdAtEpochMs: 1774060186920
    })
    if (!resolveHistory) throw new Error('resolver not captured')
    resolveHistory({
      items: [{
        messageId: 'ffffffff-ffff-7fff-8fff-ffffffffffff',
        seq: 6,
        fromUserId: '22222222-2222-7222-8222-222222222222',
        toUserId: '11111111-1111-7111-8111-111111111111',
        content: '旧身份历史消息',
        clientMsgId: 'client-old-history',
        createdAtEpochMs: 1774060185920
      }],
      nextBeforeSeq: null,
      hasMore: false
    })
    await flushPromises()

    expect(wrapper.findAll('.message-row')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('旧身份实时消息')
    expect(wrapper.text()).not.toContain('旧身份历史消息')
    expect(markImConversationRead).not.toHaveBeenCalled()
  })

  it('keeps draft, messages, and scroll position across access token rotation', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 640 })
    await flushPromises()

    expect(wrapper.findAll('.message-row')).toHaveLength(2)
    expect(chatArea.scrollTop).toBe(640)

    // 用户向上滚动并输入草稿，随后后台请求触发 401 静默刷新（token 轮换）。
    chatArea.scrollTop = 200
    await wrapper.get('textarea').setValue('输入到一半的草稿')

    const auth = useAuthStore()
    auth.installSession({ accessToken: 'rotated-token', me: null })
    await flushPromises()
    auth.setMe({ userId: '11111111-1111-7111-8111-111111111111', username: 'me', authorities: [] })
    await flushPromises()

    expect(wrapper.get('textarea').element.value).toBe('输入到一半的草稿')
    expect(wrapper.findAll('.message-row')).toHaveLength(2)
    expect(wrapper.text()).toContain('第一条消息')
    expect(wrapper.text()).toContain('第二条消息')
    expect(chatArea.scrollTop).toBe(200)
    // 轮换不触发消息历史的重载。
    expect(listImConversationHistory).toHaveBeenCalledTimes(1)
  })
})
