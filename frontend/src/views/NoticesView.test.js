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

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

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
        UiBadge: { template: '<span class="ui-badge-stub"><slot /></span>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiSkeleton: { template: '<div class="ui-skeleton-stub" role="status" />' },
        UiState: { props: ['title'], template: '<div>{{ title }}<slot /><slot name="description" /><slot name="actions" /></div>' },
        UiButton: {
          props: ['disabled', 'variant', 'to'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
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

  it('renders grouped notice topics with unread rail and weak chip', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(topicSummary).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('1 个主题需要处理')
    expect(wrapper.text()).toContain('评论')
    expect(wrapper.text()).toContain('未读 2')
    expect(wrapper.text()).toContain('共 4 条')
    expect(wrapper.text()).toContain('打开通知')

    const rows = wrapper.findAll('a')
    expect(rows[0].attributes('href')).toBe('/notices/comment')
    expect(rows[0].classes()).toContain('unread')
    expect(rows[1].attributes('href')).toBe('/notices/follow')
    expect(rows[1].classes()).not.toContain('unread')
    expect(rows[0].find('svg').exists()).toBe(true)
  })

  it('shows the skeleton during the first load instead of bare loading text', async () => {
    const pending = deferred()
    topicSummary.mockImplementationOnce(() => pending.promise)

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('.ui-skeleton-stub').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('暂无通知')

    pending.resolve({ data: [], traceId: 'trace-late' })
    await flushPromises()
    expect(wrapper.find('.ui-skeleton-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无通知')
  })

  it('shows an error state with retry and recovers after the retry succeeds', async () => {
    topicSummary.mockRejectedValueOnce(new Error('summary exploded'))

    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('summary exploded')

    const retry = wrapper.findAll('button').find((button) => button.text() === '重试')
    expect(retry).toBeTruthy()
    await retry.trigger('click')
    await flushPromises()

    expect(topicSummary).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('评论')
    expect(wrapper.text()).not.toContain('summary exploded')
  })

  it('keeps the loaded list and reports refresh failures inline', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('评论')

    topicSummary.mockRejectedValueOnce(new Error('refresh exploded'))
    const refresh = wrapper.findAll('button').find((button) => button.text() === '刷新')
    await refresh.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('评论')
    expect(wrapper.find('.notices-inline-error').text()).toContain('refresh exploded')
  })

  it('shows the empty state with a primary next step', async () => {
    topicSummary.mockResolvedValueOnce({ data: [], traceId: 'trace-empty' })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无通知')
    const next = wrapper.findAll('button').find((button) => button.text() === '浏览帖子')
    expect(next).toBeTruthy()
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
