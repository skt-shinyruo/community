// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { topicSummary } = vi.hoisted(() => ({
  topicSummary: vi.fn()
}))

vi.mock('../api/services/noticeService', () => ({
  topicSummary
}))

import NoticesView from './NoticesView.vue'

function mountView() {
  return mount(NoticesView, {
    global: {
      stubs: {
        RouterLink: {
          props: ['to'],
          template: '<a :href="to"><slot /></a>'
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

describe('NoticesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    topicSummary.mockResolvedValue({
      data: [
        { topic: 'comment', noticeCount: 4, unreadCount: 2 },
        { topic: 'follow', noticeCount: 1, unreadCount: 0 }
      ],
      traceId: 'trace-notice-summary'
    })
  })

  it('renders grouped notice topics with unread counts', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(topicSummary).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('1 个主题有新动态')
    expect(wrapper.text()).toContain('评论')
    expect(wrapper.text()).toContain('有新内容')
    expect(wrapper.text()).toContain('未读 2')
    expect(wrapper.findAll('a')[0].attributes('href')).toBe('/notices/comment')
  })
})
