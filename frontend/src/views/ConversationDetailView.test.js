// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const {
  listeners,
  listImConversationHistory,
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
    markImConversationRead: vi.fn(),
    sendPrivateText: client.sendPrivateText,
    imRealtimeClient: client
  }
})

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversationHistory,
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
  auth.setMe({
    userId: '11111111-1111-7111-8111-111111111111',
    username: 'me',
    authorities: []
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
        UiDivider: { template: '<hr />' },
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
    markImConversationRead.mockResolvedValue({})
    sendPrivateText.mockClear()
  })

  it('loads the latest history, marks its maximum seq read, scrolls bottom, and sends to the participant', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)
    const chatArea = wrapper.get('.chat-area').element
    Object.defineProperty(chatArea, 'scrollHeight', { configurable: true, value: 640 })

    await flushPromises()

    expect(listImConversationHistory).toHaveBeenCalledWith(conversationId, { limit: 50 })
    expect(markImConversationRead).toHaveBeenCalledWith(conversationId, 8)
    expect(chatArea.scrollTop).toBe(640)
    expect(wrapper.text()).toContain('消息时间线')
    expect(wrapper.text()).toContain('第一条消息')
    expect(wrapper.text()).toContain('第二条消息')
    expect(wrapper.text()).toContain('实时已就绪')

    await wrapper.get('textarea').setValue('继续聊')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
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
})
