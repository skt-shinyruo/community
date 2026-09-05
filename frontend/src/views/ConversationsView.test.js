// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listImConversationPage, getImUnreadSummary, topicSummary } = vi.hoisted(() => ({
  listImConversationPage: vi.fn(),
  getImUnreadSummary: vi.fn(),
  topicSummary: vi.fn()
}))

vi.mock('../api/services/imCoreChatService', () => ({
  listImConversationPage,
  getImUnreadSummary
}))

vi.mock('../api/services/noticeService', () => ({
  topicSummary
}))

import ConversationsView from './ConversationsView.vue'
import { useAuthStore } from '../stores/auth'
import { useInboxUnreadStore } from '../stores/inboxUnread'

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'token-user-a',
    me: { userId: '11111111-1111-7111-8111-111111111111', username: 'user-a', authorities: [] }
  })
  return mount(ConversationsView, {
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :href="to"><slot /></a>'
        },
        UiAvatar: {
          props: ['name', 'src', 'size'],
          template: '<div :data-name="name"></div>'
        },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiButton: {
          props: ['disabled', 'variant', 'to'],
          emits: ['click'],
          template: '<button :disabled="disabled" :data-to="to ? JSON.stringify(to) : undefined" @click="$emit(\'click\')"><slot /></button>'
        },
        UiSkeleton: {
          props: ['variant', 'rows', 'label'],
          template: '<div role="status">{{ label }}</div>'
        },
        UiState: {
          props: ['title'],
          template: '<div><h2 v-if="title">{{ title }}</h2><slot /><slot name="description" /><slot name="actions" /></div>'
        }
      }
    }
  })
}

describe('ConversationsView', () => {
  beforeEach(() => {
    // resetAllMocks 连同 once 实现队列一起清空，避免用例中途失败把残留实现泄漏给后续用例。
    vi.resetAllMocks()
    getImUnreadSummary.mockResolvedValue({ rooms: [], conversations: [] })
    topicSummary.mockResolvedValue({ data: [] })
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
    expect(wrapper.text()).toContain('等待你的回复')
    expect(wrapper.text()).toContain('2 条未读')
    expect(wrapper.text()).toContain('最后一条消息')
    expect(wrapper.text()).toContain('线程已同步')
    expect(wrapper.text()).not.toContain('成员 #11111111-1111-7111-8111-111111111111')
    expect(wrapper.findAll('a')[0].attributes('href')).toBe('/messages/conv-a')
    expect(wrapper.findAll('a')[1].attributes('href')).toBe('/messages/conv-b')
    const loadMore = wrapper.find('[data-testid="load-more-conversations"]')
    expect(loadMore.exists()).toBe(true)
    await loadMore.trigger('click')
    expect(listImConversationPage).toHaveBeenLastCalledWith({ cursor: 'cursor-2', size: 20 })
    // 追加分页进行时不展示可重复点击的加载更多按钮，尾部指示接管。
    expect(wrapper.find('[data-testid="load-more-conversations"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('正在加载…')

    resolvePageTwo(pageTwo)
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(3)
    expect(wrapper.text()).toContain('更新后的消息')
    expect(wrapper.text()).toContain('2 个对话待处理')
    expect(wrapper.text()).toContain('3 条未读')
    expect(wrapper.find('[data-testid="load-more-conversations"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('已经到底了')
  })

  it('marks unread conversations with the rail modifier and weak chip only', async () => {
    listImConversationPage.mockResolvedValueOnce({
      items: [
        {
          conversationId: 'conv-a',
          otherUserId: '11111111-1111-7111-8111-111111111111',
          unreadCount: 2,
          lastMessage: { content: '未读消息', createdAtEpochMs: 1774060182920 }
        },
        {
          conversationId: 'conv-b',
          otherUserId: '22222222-2222-7222-8222-222222222222',
          unreadCount: 0,
          lastMessage: { content: '已读消息', createdAtEpochMs: 1774060182920 }
        }
      ],
      nextCursor: null,
      hasMore: false
    })

    const wrapper = mountView()
    await flushPromises()

    const cards = wrapper.findAll('.conv-card')
    expect(cards).toHaveLength(2)
    expect(cards[0].classes()).toContain('conv-card--unread')
    expect(cards[0].find('.conv-card-chip').text()).toBe('2 条未读')
    expect(cards[1].classes()).not.toContain('conv-card--unread')
    expect(cards[1].find('.conv-card-chip').exists()).toBe(false)
  })

  it('shows a skeleton during the first load instead of the empty state', async () => {
    let resolveFirst
    listImConversationPage.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[role="status"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('暂无会话')
    expect(wrapper.text()).not.toContain('正在整理')

    resolveFirst({ items: [], nextCursor: null, hasMore: false })
    await flushPromises()

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无会话')
  })

  it('offers a next step in the empty state', async () => {
    listImConversationPage.mockResolvedValueOnce({ items: [], nextCursor: null, hasMore: false })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无会话')
    const nextStep = wrapper.find('[data-to]')
    expect(nextStep.exists()).toBe(true)
    expect(nextStep.attributes('data-to')).toContain('posts')
  })

  it('shows a retryable error state when the first load fails', async () => {
    listImConversationPage
      .mockRejectedValueOnce(new Error('加载会话失败'))
      .mockResolvedValueOnce({
        items: [{ conversationId: 'conv-a', otherUserId: '11111111-1111-7111-8111-111111111111', unreadCount: 0, lastMessage: null }],
        nextCursor: null,
        hasMore: false
      })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('加载会话失败')
    expect(wrapper.text()).not.toContain('暂无会话')
    const retry = wrapper.findAll('button').find((b) => b.text() === '重试')
    expect(retry).toBeTruthy()
    await retry.trigger('click')
    await flushPromises()

    expect(listImConversationPage).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('加载会话失败')
  })

  it('refreshes the shell unread badge after a successful list load', async () => {
    listImConversationPage.mockResolvedValueOnce({ items: [], nextCursor: null, hasMore: false })
    getImUnreadSummary.mockResolvedValueOnce({
      rooms: [],
      conversations: [{ conversationId: 'conv-x', unreadCount: 5 }]
    })

    const wrapper = mountView()
    await flushPromises()

    const inboxUnread = useInboxUnreadStore()
    expect(getImUnreadSummary).toHaveBeenCalled()
    expect(topicSummary).toHaveBeenCalled()
    expect(inboxUnread.messageUnread).toBe(5)
    wrapper.unmount()
  })

  it('keeps the badge refresh out of load-more pages', async () => {
    listImConversationPage
      .mockResolvedValueOnce({
        items: [{ conversationId: 'conv-a', otherUserId: '11111111-1111-7111-8111-111111111111', unreadCount: 0, lastMessage: null }],
        nextCursor: 'cursor-2',
        hasMore: true
      })
      .mockResolvedValueOnce({
        items: [{ conversationId: 'conv-b', otherUserId: '22222222-2222-7222-8222-222222222222', unreadCount: 0, lastMessage: null }],
        nextCursor: null,
        hasMore: false
      })

    const wrapper = mountView()
    await flushPromises()
    expect(getImUnreadSummary).toHaveBeenCalledTimes(1)

    await wrapper.find('[data-testid="load-more-conversations"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(2)
    expect(getImUnreadSummary).toHaveBeenCalledTimes(1)
  })

  it('keeps the loaded rows and reports append failures next to the tail control', async () => {
    listImConversationPage
      .mockResolvedValueOnce({
        items: [{ conversationId: 'conv-a', otherUserId: '11111111-1111-7111-8111-111111111111', unreadCount: 0, lastMessage: null }],
        nextCursor: 'cursor-2',
        hasMore: true
      })
      .mockRejectedValueOnce(new Error('追加失败'))

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="load-more-conversations"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.text()).toContain('追加失败')
    // 追加失败保留重试入口：加载更多按钮继续可用。
    const retry = wrapper.find('[data-testid="load-more-conversations"]')
    expect(retry.exists()).toBe(true)
    expect(retry.attributes('disabled')).toBeUndefined()
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

  it('keeps the last successful rows and cursor when refresh fails', async () => {
    listImConversationPage
      .mockResolvedValueOnce({
        items: [{ conversationId: 'conv-a', otherUserId: '11111111-1111-7111-8111-111111111111', unreadCount: 0, lastMessage: null }],
        nextCursor: 'cursor-2',
        hasMore: true
      })
      .mockRejectedValueOnce(new Error('刷新失败'))

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.find('a').attributes('href')).toBe('/messages/conv-a')
    expect(wrapper.text()).toContain('刷新失败')
    expect(wrapper.find('[data-testid="load-more-conversations"]').exists()).toBe(true)
  })

  it('ignores a stale load-more rejection after a successful refresh', async () => {
    let rejectStale
    listImConversationPage
      .mockResolvedValueOnce({
        items: [{ conversationId: 'conv-a', otherUserId: '11111111-1111-7111-8111-111111111111', unreadCount: 0, lastMessage: null }],
        nextCursor: 'cursor-2',
        hasMore: true
      })
      .mockImplementationOnce(() => new Promise((_, reject) => { rejectStale = reject }))
      .mockResolvedValueOnce({
        items: [{ conversationId: 'conv-new', otherUserId: '44444444-4444-7444-8444-444444444444', unreadCount: 0, lastMessage: null }],
        nextCursor: null,
        hasMore: false
      })

    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('[data-testid="load-more-conversations"]').trigger('click')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    rejectStale(new Error('旧请求失败'))
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.find('a').attributes('href')).toBe('/messages/conv-new')
    expect(wrapper.text()).not.toContain('旧请求失败')
    expect(wrapper.find('[data-testid="load-more-conversations"]').exists()).toBe(false)
  })

  it('clears rows and ignores the previous identity response after account switching', async () => {
    let resolveUserA
    let resolveUserB
    listImConversationPage
      .mockImplementationOnce(() => new Promise((resolve) => { resolveUserA = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveUserB = resolve }))

    const wrapper = mountView()
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'token-user-b',
      me: { userId: '22222222-2222-7222-8222-222222222222', username: 'user-b', authorities: [] }
    })
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(0)
    expect(listImConversationPage).toHaveBeenCalledTimes(2)

    resolveUserB({
      items: [{ conversationId: 'conv-user-b', otherUserId: '33333333-3333-7333-8333-333333333333', unreadCount: 0, lastMessage: null }],
      nextCursor: null,
      hasMore: false
    })
    await flushPromises()
    resolveUserA({
      items: [{ conversationId: 'conv-user-a', otherUserId: '44444444-4444-7444-8444-444444444444', unreadCount: 0, lastMessage: null }],
      nextCursor: null,
      hasMore: false
    })
    await flushPromises()

    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.find('a').attributes('href')).toBe('/messages/conv-user-b')
  })
})
