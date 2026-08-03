// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listNotices, markRead } = vi.hoisted(() => ({
  listNotices: vi.fn(),
  markRead: vi.fn()
}))

vi.mock('../api/services/noticeService', () => ({
  listNotices,
  markRead
}))

import NoticeDetailView from './NoticeDetailView.vue'
import { useAuthStore } from '../stores/auth'

function mountNoticeDetailView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'token-user-a',
    me: { userId: '11111111-1111-7111-8111-111111111111', username: 'user-a', authorities: [] }
  })
  return mount(NoticeDetailView, {
    props: { topic: 'comment' },
    global: {
      plugins: [pinia],
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        UiCard: { template: '<section><slot /></section>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /><slot /></header>' },
        UiPagination: true,
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('NoticeDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listNotices.mockResolvedValue({
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
    markRead.mockResolvedValue({ traceId: 'trace-mark-read' })
  })

  it('submits UUID notice ids unchanged when marking the page read', async () => {
    const wrapper = mountNoticeDetailView()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '标记本页已读').trigger('click')
    await flushPromises()

    expect(markRead).toHaveBeenCalledWith([
      'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb'
    ])
  })

  it('does not advance the page when loading the next page fails', async () => {
    const firstPage = Array.from({ length: 10 }, (_, index) => ({
      id: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
      status: 0,
      content: JSON.stringify({ type: 'COMMENT_CREATED', payload: {} }),
      createTime: 1774060182920 + index
    }))
    const secondPage = [{
      id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
      status: 0,
      content: JSON.stringify({ type: 'FOLLOW_CREATED', payload: {} }),
      createTime: 1774060182999
    }]
    listNotices
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockRejectedValueOnce(new Error('temporary notice failure'))
      .mockResolvedValueOnce({ data: secondPage, traceId: 'trace-page-1' })

    const wrapper = mountNoticeDetailView()
    await flushPromises()

    await wrapper.vm.nextPage()
    await flushPromises()
    expect(wrapper.text()).toContain('temporary notice failure')
    expect(wrapper.text()).toContain('有人回复了你的内容')

    await wrapper.vm.nextPage()
    await flushPromises()

    expect(listNotices.mock.calls.map(([, request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.text()).toContain('你收到了新的关注')
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
      data: [{
        id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
        status: 0,
        content: JSON.stringify({ type: 'LIKE_CREATED', payload: {} }),
        createTime: 1774060182999
      }],
      traceId: 'trace-like'
    })
    await flushPromises()
    resolveComment({
      data: [{
        id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
        status: 0,
        content: JSON.stringify({ type: 'COMMENT_CREATED', payload: {} }),
        createTime: 1774060183000
      }],
      traceId: 'trace-comment'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('你的内容收到了新的点赞')
    expect(wrapper.text()).not.toContain('有人回复了你的内容')
    expect(wrapper.emitted('trace')).toEqual([['trace-like']])
  })

  it('does not refresh the new identity after an old mark-read request completes', async () => {
    let resolveOldMarkRead
    listNotices
      .mockResolvedValueOnce({
        data: [{
          id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
          status: 0,
          content: JSON.stringify({ type: 'COMMENT_CREATED', payload: {} }),
          createTime: 1774060182920
        }],
        traceId: 'trace-user-a'
      })
      .mockResolvedValueOnce({
        data: [{
          id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          status: 0,
          content: JSON.stringify({ type: 'FOLLOW_CREATED', payload: {} }),
          createTime: 1774060183920
        }],
        traceId: 'trace-user-b'
      })
    markRead.mockImplementationOnce(() => new Promise((resolve) => { resolveOldMarkRead = resolve }))

    const wrapper = mountNoticeDetailView()
    await flushPromises()
    await wrapper.findAll('button').find((button) => button.text() === '标记本页已读').trigger('click')

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
    expect(wrapper.emitted('trace')).toEqual([['trace-user-a'], ['trace-user-b']])
  })
})
