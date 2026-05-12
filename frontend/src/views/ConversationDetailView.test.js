// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
const {
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
    listImConversationMessages: vi.fn(),
    markImConversationRead: vi.fn(),
    sendPrivateText: client.sendPrivateText,
    imRealtimeClient: client
  }
})

vi.mock('../api/services/imCoreChatService', () => ({
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
    listImConversationMessages.mockResolvedValue({
      items: [
        {
          id: 'msg-1',
          seq: 3,
          fromId: '22222222-2222-7222-8222-222222222222',
          toId: '11111111-1111-7111-8111-111111111111',
          content: '第一条消息',
          createTime: 1774060182920
        },
        {
          id: 'msg-2',
          seq: 8,
          fromId: '11111111-1111-7111-8111-111111111111',
          toId: '22222222-2222-7222-8222-222222222222',
          content: '第二条消息',
          createTime: 1774060183920
        }
      ]
    })
    markImConversationRead.mockResolvedValue({})
    sendPrivateText.mockClear()
  })

  it('loads the thread, marks it read and sends through the resolved participant id', async () => {
    const conversationId = '11111111-1111-7111-8111-111111111111_22222222-2222-7222-8222-222222222222'
    const wrapper = mountView(conversationId)

    await flushPromises()

    expect(listImConversationMessages).toHaveBeenCalledWith(conversationId, { afterSeq: 0, limit: 50 })
    expect(markImConversationRead).toHaveBeenCalledWith(conversationId, 8)
    expect(wrapper.text()).toContain('消息时间线')
    expect(wrapper.text()).toContain('第一条消息')
    expect(wrapper.text()).toContain('第二条消息')
    expect(wrapper.text()).toContain('实时已连接')

    await wrapper.get('textarea').setValue('继续聊')
    await wrapper.get('button[aria-label="发送消息"]').trigger('click')
    await flushPromises()

    expect(sendPrivateText).toHaveBeenCalledWith({
      toUserId: '22222222-2222-7222-8222-222222222222',
      content: '继续聊'
    })
  })
})
