// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const { listNotices, markRead, topicSummary, getImUnreadSummary } = vi.hoisted(() => ({
  listNotices: vi.fn(),
  markRead: vi.fn(),
  topicSummary: vi.fn(),
  getImUnreadSummary: vi.fn()
}))

vi.mock('../api/services/noticeService', () => ({
  listNotices,
  markRead,
  topicSummary
}))

vi.mock('../api/services/imCoreChatService', () => ({
  getImUnreadSummary
}))

import NoticeDetailView from './NoticeDetailView.vue'
import { useAuthStore } from '../stores/auth'
import { useInboxUnreadStore } from '../stores/inboxUnread'

const mountedWrappers = []

function notice(index, { status = 0, type = 'COMMENT_CREATED', payload = {} } = {}) {
  return {
    id: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
    status,
    content: JSON.stringify({ type, payload }),
    createTime: 1774060182920 + index
  }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function mountNoticeDetailView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'token-user-a',
    me: { userId: '11111111-1111-7111-8111-111111111111', username: 'user-a', authorities: [] }
  })
  const wrapper = mount(NoticeDetailView, {
    props: { topic: 'comment' },
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        UiBadge: { template: '<span class="ui-badge-stub"><slot /></span>' },
        UiSkeleton: { template: '<div class="ui-skeleton-stub" role="status" />' },
        UiState: { props: ['title'], template: '<div>{{ title }}<slot /><slot name="description" /><slot name="actions" /></div>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /><slot /></header>' },
        UiButton: {
          props: ['disabled', 'variant', 'to'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
  mountedWrappers.push(wrapper)
  return wrapper
}

function findButton(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text().trim() === text)
}

describe('NoticeDetailView', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    listNotices.mockResolvedValue({
      data: [
        notice(0),
        notice(1, { type: 'LIKE_CREATED' })
      ],
      traceId: 'trace-notices'
    })
    markRead.mockResolvedValue({ traceId: 'trace-mark-read' })
    topicSummary.mockResolvedValue({ data: [] })
    getImUnreadSummary.mockResolvedValue({ rooms: [], conversations: [] })
  })

  afterEach(() => {
    mountedWrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  })

  it('renders the topic feed with unread rail, weak chip and a back link to the summary', async () => {
    const wrapper = mountNoticeDetailView()
    await flushPromises()

    expect(listNotices).toHaveBeenCalledWith('comment', { page: 0, size: 10 })
    expect(wrapper.text()).toContain('评论通知')
    expect(wrapper.text()).toContain('有人回复了你的内容')
    expect(wrapper.text()).toContain('返回通知汇总')

    const cards = wrapper.findAll('.notice-card')
    expect(cards).toHaveLength(2)
    expect(cards[0].classes()).toContain('unread')
    expect(wrapper.text()).toContain('未读')
  })

  it('shows the skeleton during the first load', async () => {
    const pending = deferred()
    listNotices.mockImplementationOnce(() => pending.promise)

    const wrapper = mountNoticeDetailView()
    await flushPromises()
    expect(wrapper.find('.ui-skeleton-stub').exists()).toBe(true)

    pending.resolve({ data: [notice(0)], traceId: 'trace-late' })
    await flushPromises()
    expect(wrapper.find('.ui-skeleton-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('有人回复了你的内容')
  })

  it('shows an error state with retry and recovers after the retry succeeds', async () => {
    listNotices.mockRejectedValueOnce(new Error('detail exploded'))

    const wrapper = mountNoticeDetailView()
    await flushPromises()
    expect(wrapper.text()).toContain('detail exploded')

    await findButton(wrapper, '重试').trigger('click')
    await flushPromises()

    expect(listNotices).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('有人回复了你的内容')
    expect(wrapper.text()).not.toContain('detail exploded')
  })

  it('shows the empty state with a link back to the summary', async () => {
    listNotices.mockResolvedValueOnce({ data: [], traceId: 'trace-empty' })

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无通知')
    const back = wrapper.findAll('button').find((button) => button.text() === '返回通知汇总')
    expect(back).toBeTruthy()
  })

  it('appends the next page via load-more and shows the end note when exhausted', async () => {
    const firstPage = Array.from({ length: 10 }, (_, index) => notice(index))
    listNotices
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockResolvedValueOnce({ data: [notice(10, { type: 'FOLLOW_CREATED' })], traceId: 'trace-page-1' })

    const wrapper = mountNoticeDetailView()
    await flushPromises()
    expect(wrapper.findAll('.notice-card')).toHaveLength(10)

    await findButton(wrapper, '加载更多').trigger('click')
    await flushPromises()

    expect(listNotices.mock.calls.map(([, request]) => request.page)).toEqual([0, 1])
    expect(wrapper.findAll('.notice-card')).toHaveLength(11)
    expect(wrapper.text()).toContain('你收到了新的关注')
    expect(wrapper.text()).toContain('已经到底了')
  })

  it('keeps the loaded list and reports a failed load-more inline, with the button as retry', async () => {
    const firstPage = Array.from({ length: 10 }, (_, index) => notice(index))
    listNotices
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockRejectedValueOnce(new Error('temporary notice failure'))
      .mockResolvedValueOnce({ data: [notice(10, { type: 'FOLLOW_CREATED' })], traceId: 'trace-page-1' })

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    await findButton(wrapper, '加载更多').trigger('click')
    await flushPromises()
    expect(wrapper.find('.notice-detail-inline-error').text()).toContain('temporary notice failure')
    expect(wrapper.findAll('.notice-card')).toHaveLength(10)

    await findButton(wrapper, '加载更多').trigger('click')
    await flushPromises()

    expect(listNotices.mock.calls.map(([, request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.findAll('.notice-card')).toHaveLength(11)
    expect(wrapper.text()).toContain('你收到了新的关注')
  })

  it('marks only the loaded unread notices read and flips them locally without a reload', async () => {
    listNotices.mockResolvedValueOnce({
      data: [
        notice(0),
        notice(1, { status: 1, type: 'LIKE_CREATED' })
      ],
      traceId: 'trace-mixed'
    })

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    await findButton(wrapper, '标记已读').trigger('click')
    await flushPromises()

    expect(markRead).toHaveBeenCalledWith(['00000000-0000-7000-8000-000000000001'])
    expect(listNotices).toHaveBeenCalledTimes(1)
    const cards = wrapper.findAll('.notice-card')
    expect(cards[0].classes()).not.toContain('unread')
    expect(wrapper.findAll('.notice-card.unread')).toHaveLength(0)
  })

  it('refreshes the shell unread badge after a successful mark-read', async () => {
    topicSummary.mockResolvedValue({ data: [{ topic: 'like', unreadCount: 3 }] })
    getImUnreadSummary.mockResolvedValue({ rooms: [], conversations: [{ conversationId: 'c1', unreadCount: 4 }] })

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    await findButton(wrapper, '标记已读').trigger('click')
    await flushPromises()

    const inboxUnread = useInboxUnreadStore()
    expect(inboxUnread.noticeUnread).toBe(3)
    expect(inboxUnread.messageUnread).toBe(4)
    expect(topicSummary).toHaveBeenCalledWith({ silent: true })
  })

  it('keeps the unread state and reports the failure when mark-read fails', async () => {
    markRead.mockRejectedValueOnce(new Error('mark read exploded'))

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    await findButton(wrapper, '标记已读').trigger('click')
    await flushPromises()

    expect(wrapper.find('.notice-detail-inline-error').text()).toContain('mark read exploded')
    expect(wrapper.findAll('.notice-card.unread')).toHaveLength(2)
    expect(topicSummary).not.toHaveBeenCalled()
  })

  it('disables mark-read when every loaded notice is already read', async () => {
    listNotices.mockResolvedValueOnce({
      data: [notice(0, { status: 1 })],
      traceId: 'trace-all-read'
    })

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    expect(findButton(wrapper, '标记已读').attributes('disabled')).toBeDefined()
  })

  it('ignores the previous topic response after the route reuses the component', async () => {
    let resolveComment
    let resolveLike
    listNotices
      .mockImplementationOnce(() => new Promise((resolve) => { resolveComment = resolve }))
      .mockImplementationOnce(() => new Promise((resolve) => { resolveLike = resolve }))

    const wrapper = mountNoticeDetailView()
    await wrapper.setProps({ topic: 'like' })
    await flushPromises()

    expect(listNotices).toHaveBeenNthCalledWith(1, 'comment', { page: 0, size: 10 })
    expect(listNotices).toHaveBeenNthCalledWith(2, 'like', { page: 0, size: 10 })

    resolveLike({
      data: [notice(2, { type: 'LIKE_CREATED' })],
      traceId: 'trace-like'
    })
    await flushPromises()
    resolveComment({
      data: [notice(3)],
      traceId: 'trace-comment'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('你的内容收到了新的点赞')
    expect(wrapper.text()).not.toContain('有人回复了你的内容')
  })

  it('does not apply the old identity mark-read result after account switching', async () => {
    let resolveOldMarkRead
    listNotices
      .mockResolvedValueOnce({ data: [notice(0)], traceId: 'trace-user-a' })
      .mockResolvedValueOnce({ data: [notice(1, { type: 'FOLLOW_CREATED' })], traceId: 'trace-user-b' })
    markRead.mockImplementationOnce(() => new Promise((resolve) => { resolveOldMarkRead = resolve }))

    const wrapper = mountNoticeDetailView()
    await flushPromises()
    await findButton(wrapper, '标记已读').trigger('click')
    await vi.waitFor(() => {
      expect(markRead).toHaveBeenCalledTimes(1)
      expect(resolveOldMarkRead).toBeTypeOf('function')
    })

    useAuthStore().installSession({
      accessToken: 'token-user-b',
      me: { userId: '22222222-2222-7222-8222-222222222222', username: 'user-b', authorities: [] }
    })
    await flushPromises()
    resolveOldMarkRead({ traceId: 'trace-stale-mark-read' })
    await flushPromises()

    expect(listNotices).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('你收到了新的关注')
    expect(wrapper.text()).not.toContain('有人回复了你的内容')
    expect(wrapper.findAll('.notice-card.unread')).toHaveLength(1)
    expect(topicSummary).not.toHaveBeenCalled()
  })

  it('submits UUID notice ids unchanged when marking the page read', async () => {
    listNotices.mockResolvedValueOnce({
      data: [
        {
          id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          status: 0,
          content: JSON.stringify({ type: 'COMMENT_CREATED', payload: {} }),
          createTime: 1774060182920
        },
        {
          id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          status: 0,
          content: JSON.stringify({ type: 'LIKE_CREATED', payload: {} }),
          createTime: 1774060182921
        }
      ],
      traceId: 'trace-notices'
    })

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    await findButton(wrapper, '标记已读').trigger('click')
    await flushPromises()

    expect(markRead).toHaveBeenCalledWith([
      'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    ])
  })
})
