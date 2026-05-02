// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
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

function mountNoticeDetailView() {
  return mount(NoticeDetailView, {
    props: { topic: 'comment' },
    global: {
      stubs: {
        RouterLink: { template: '<a><slot /></a>' },
        UiCard: { template: '<section><slot /></section>' },
        UiEmpty: { template: '<div><slot /><slot name="description" /></div>' },
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
})
