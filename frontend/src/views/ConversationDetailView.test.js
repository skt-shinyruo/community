// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const {
  listeners,
  listImConversationHistory,
  listImConversationMessages,
  markImConversationRead,
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
    sendPrivateText: client.sendPrivateText,
    imRealtimeClient: client
  }
})

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversationHistory,
  listImConversationMessages,
  markImConversationRead
}))

vi.mock('../im/imRealtimeClient', () => ({
  imRealtimeClient
}))

import { useAuthStore } from '../stores/auth'
import ConversationDetailView from './ConversationDetailView.vue'

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
    sendPrivateText.mockClear()
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

  it('retains realtime messages that arrive while the latest history is loading', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
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
    expect(markImConversationRead).toHaveBeenLastCalledWith(conversationId, 9)
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
    let resolveInitialHistory
    listImConversationHistory.mockImplementationOnce(() => new Promise((resolve) => { resolveInitialHistory = resolve }))
    mountView(conversationId)
    await flushPromises()

    listeners.stateChanged({ connected: true, authed: true, sessionId: 'sess-1', userId: '' })
    await flushPromises()

    expect(listImConversationHistory).toHaveBeenCalledTimes(1)
    expect(listImConversationHistory).toHaveBeenCalledWith(conversationId, { limit: 50 })
    expect(listImConversationMessages).not.toHaveBeenCalled()

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

  it('rejects realtime private messages that miss persisted timestamps', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    mountView(conversationId)
    await flushPromises()

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
    let resolveConversationA
    let resolveConversationB
    listImConversationHistory
      .mockImplementationOnce(() => new Promise((resolve) => { resolveConversationA = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveConversationB = resolve }))

    const wrapper = mountView(conversationA)
    await wrapper.setProps({ conversationId: conversationB })
    await flushPromises()

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
})
