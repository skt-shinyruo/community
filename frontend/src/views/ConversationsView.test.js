// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listImConversationPage } = vi.hoisted(() => ({
  listImConversationPage: vi.fn()
}))

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversationPage
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
        UiState: { template: '<div><slot /><slot name="description" /></div>' }
      }
    }
  })
}

describe('ConversationsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads cursor pages, deduplicates rows, disables loading, and removes the control at the end', async () => {
    const pageOne = {
      items: [
        {
          conversationId: 'conv-a',
          otherUserId: '11111111-1111-7111-8111-111111111111',
          unreadCount: 2,
          lastMessage: { content: '最后一条消息', createdAtEpochMs: 1774060182920 }
        },
        {
          conversationId: 'conv-b',
          otherUserId: '22222222-2222-7222-8222-222222222222',
          unreadCount: 0,
          lastMessage: null
        }
      ],
      nextCursor: 'cursor-2',
      hasMore: true
    }
    const pageTwo = {
      items: [
        {
          conversationId: 'conv-b',
          otherUserId: '22222222-2222-7222-8222-222222222222',
          unreadCount: 3,
          lastMessage: { content: '更新后的消息', createdAtEpochMs: 1774060183920 }
        },
        {
          conversationId: 'conv-c',
          otherUserId: '33333333-3333-7333-8333-333333333333',
          unreadCount: 0,
          lastMessage: null
        }
      ],
      nextCursor: null,
      hasMore: false
    }
    let resolvePageTwo
    listImConversationPage
      .mockResolvedValueOnce(pageOne)
      .mockImplementationOnce(() => new Promise((resolve) => { resolvePageTwo = resolve }))

    const wrapper = mountView()
    await flushPromises()

    expect(listImConversationPage).toHaveBeenCalledWith({ cursor: '', size: 20 })
    expect(wrapper.text()).toContain('1 个对话待处理')
    expect(wrapper.text()).toContain('待回复')
    expect(wrapper.text()).toContain('最后一条消息')
    expect(wrapper.text()).toContain('线程已同步')
    expect(wrapper.text()).not.toContain('成员 #11111111-1111-7111-8111-111111111111')
    expect(wrapper.findAll('a')[0].attributes('href')).toBe('/messages/conv-a')
    expect(wrapper.findAll('a')[1].attributes('href')).toBe('/messages/conv-b')

    const loadMore = wrapper.find('[data-testid="load-more-conversations"]')
    expect(loadMore.exists()).toBe(true)
    await loadMore.trigger('click')
    expect(listImConversationPage).toHaveBeenLastCalledWith({ cursor: 'cursor-2', size: 20 })
    expect(wrapper.find('[data-testid="load-more-conversations"]').attributes('disabled')).toBeDefined()

    resolvePageTwo(pageTwo)
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(3)
    expect(wrapper.text()).toContain('更新后的消息')
    expect(wrapper.text()).toContain('2 个对话待处理')
    expect(wrapper.find('[data-testid="load-more-conversations"]').exists()).toBe(false)
  })

  it('refreshes from an empty cursor and ignores a stale load-more response', async () => {
    const firstPage = {
      items: [{ conversationId: 'conv-a', otherUserId: '11111111-1111-7111-8111-111111111111', unreadCount: 0, lastMessage: null }],
      nextCursor: 'cursor-2',
      hasMore: true
    }
    let resolveStale
    let resolveRefresh
    listImConversationPage
      .mockResolvedValueOnce(firstPage)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveStale = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveRefresh = resolve }))

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="load-more-conversations"]').trigger('click')
    await wrapper.find('button').trigger('click')

    expect(listImConversationPage).toHaveBeenNthCalledWith(3, { cursor: '', size: 20 })
    resolveRefresh({
      items: [{ conversationId: 'conv-new', otherUserId: '44444444-4444-7444-8444-444444444444', unreadCount: 0, lastMessage: null }],
      nextCursor: null,
      hasMore: false
    })
    await flushPromises()
    resolveStale({
      items: [{ conversationId: 'conv-stale', otherUserId: '55555555-5555-7555-8555-555555555555', unreadCount: 0, lastMessage: null }],
      nextCursor: null,
      hasMore: false
    })
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.find('a').attributes('href')).toBe('/messages/conv-new')
  })
})
