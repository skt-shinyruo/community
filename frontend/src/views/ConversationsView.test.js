// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listImConversations } = vi.hoisted(() => ({
  listImConversations: vi.fn()
}))

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversations
}))

import ConversationsView from './ConversationsView.vue'

function mountView() {
  return mount(ConversationsView, {
    global: {
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :href="to"><slot /></a>'
        },
        UiAvatar: {
          props: ['name', 'src', 'size'],
          template: '<div :data-name="name"></div>'
        },
        UiCard: { template: '<section><slot /></section>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiEmpty: { template: '<div><slot /><slot name="description" /></div>' }
      }
    }
  })
}

describe('ConversationsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listImConversations.mockResolvedValue([
      {
        conversationId: 'conv-a',
        otherUserId: '11111111-1111-7111-8111-111111111111',
        unreadCount: 2,
        lastMessage: {
          content: '最后一条消息',
          createdAtEpochMs: 1774060182920
        }
      },
      {
        conversationId: 'conv-b',
        otherUserId: '22222222-2222-7222-8222-222222222222',
        unreadCount: 0,
        lastMessage: null
      }
    ])
  })

  it('renders inbox rows with unread state and stable links', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(listImConversations).toHaveBeenCalledWith({ page: 0, size: 20 })
    expect(wrapper.text()).toContain('1 个对话待处理')
    expect(wrapper.text()).toContain('有新消息待查看')
    expect(wrapper.text()).toContain('最后一条消息')
    expect(wrapper.text()).toContain('线程已同步')
    expect(wrapper.findAll('a')[0].attributes('href')).toBe('/messages/conv-a')
    expect(wrapper.findAll('a')[1].attributes('href')).toBe('/messages/conv-b')
  })
})
