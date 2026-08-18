// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { topicSummary } = vi.hoisted(() => ({
  topicSummary: vi.fn()
}))

vi.mock('../api/services/noticeService', () => ({
  topicSummary
}))

import NoticesView from './NoticesView.vue'
import { useAuthStore } from '../stores/auth'

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().installSession({
    accessToken: 'token-user-a',
    me: { userId: '11111111-1111-7111-8111-111111111111', username: 'user-a', authorities: [] }
  })
  return mount(NoticesView, {
    global: {
      plugins: [pinia],
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
    expect(wrapper.text()).toContain('1 个主题需要处理')
    expect(wrapper.text()).toContain('评论')
    expect(wrapper.text()).toContain('需要处理')
    expect(wrapper.text()).toContain('打开通知')
    expect(wrapper.text()).toContain('未读 2')
    expect(wrapper.text()).not.toContain('可快速处理的收件箱')
    expect(wrapper.findAll('a')[0].attributes('href')).toBe('/notices/comment')
  })

  it('clears private rows and ignores the previous identity response after account switching', async () => {
    let resolveUserA
    let resolveUserB
    topicSummary
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
    expect(topicSummary).toHaveBeenCalledTimes(2)

    resolveUserB({
      data: [{ topic: 'follow', noticeCount: 1, unreadCount: 1 }],
      traceId: 'trace-user-b'
    })
    await flushPromises()
    resolveUserA({
      data: [{ topic: 'comment', noticeCount: 8, unreadCount: 8 }],
      traceId: 'trace-user-a'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('关注')
    expect(wrapper.findAll('a')).toHaveLength(1)
    expect(wrapper.find('a').attributes('href')).toBe('/notices/follow')
  })
})
