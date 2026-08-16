// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { listActions, listReports, takeAction } = vi.hoisted(() => ({
  listActions: vi.fn(),
  listReports: vi.fn(),
  takeAction: vi.fn()
}))

vi.mock('../api/services/moderationService', () => ({
  listActions,
  listReports,
  takeAction
}))

import ModerationView from './ModerationView.vue'
import { useAuthStore } from '../stores/auth'

let auth

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function mountModerationView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore()
  auth.installSession({
    accessToken: 'moderation-token',
    me: {
      userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      username: 'moderator',
      authorities: ['ROLE_MODERATOR']
    }
  })
  return mount(ModerationView, {
    global: {
      plugins: [pinia],
      stubs: {
        UiBadge: { template: '<span><slot /></span>' },
        UiBreadcrumb: true,
        UiCard: { template: '<section><slot /></section>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiIconButton: {
          emits: ['click'],
          template: '<button @click="$emit(\'click\')"><slot /></button>'
        },
        UiPageHeader: { template: '<header><slot /><slot name="title" /><slot name="subtitle" /><slot name="actions" /></header>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('ModerationView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listActions.mockResolvedValue({ data: [], traceId: 'trace-actions' })
    listReports.mockResolvedValue({
      data: [
        {
          id: '22222222-2222-7222-8222-222222222222',
          reporterId: '11111111-1111-7111-8111-111111111111',
          targetType: 1,
          targetId: '33333333-3333-7333-8333-333333333333',
          reason: 'spam',
          detail: 'spam detail',
          status: 0,
          createTime: '2026-04-29T00:00:00Z'
        }
      ],
      traceId: 'trace-reports'
    })
    takeAction.mockResolvedValue({ data: '44444444-4444-7444-8444-444444444444', traceId: 'trace-action' })
  })

  it('submits selected report ids as opaque UUID strings', async () => {
    const wrapper = mountModerationView()
    await flushPromises()

    await wrapper.findAll('button').find((button) => button.text() === '处置').trigger('click')
    expect(wrapper.text()).toContain('风险动作')
    expect(wrapper.get('[role="dialog"]').attributes('aria-modal')).toBe('true')
    await wrapper.find('textarea').setValue('confirmed spam')
    await wrapper.findAll('button').find((button) => button.text() === '确认处置').trigger('click')
    await flushPromises()

    expect(takeAction).toHaveBeenCalledWith({
      reportId: '22222222-2222-7222-8222-222222222222',
      action: 'reject',
      reason: 'confirmed spam',
      durationSeconds: undefined
    })
  })

  it('keeps reports visible and retries the same page after load-more fails', async () => {
    const firstPage = Array.from({ length: 20 }, (_, index) => ({
      id: `00000000-0000-7000-8000-${String(index + 1).padStart(12, '0')}`,
      reporterId: '11111111-1111-7111-8111-111111111111',
      targetType: 1,
      targetId: '33333333-3333-7333-8333-333333333333',
      reason: `report-${index + 1}`,
      status: 0,
      createTime: '2026-04-29T00:00:00Z'
    }))
    listReports
      .mockResolvedValueOnce({ data: firstPage, traceId: 'trace-page-0' })
      .mockRejectedValueOnce(new Error('temporary moderation failure'))
      .mockResolvedValueOnce({
        data: [{ ...firstPage[0], id: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', reason: 'page-two-report' }],
        traceId: 'trace-page-1'
      })

    const wrapper = mountModerationView()
    await flushPromises()
    const loadMore = () => wrapper.findAll('button').find((button) => button.text() === '加载更多')

    await loadMore().trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('temporary moderation failure')
    expect(wrapper.text()).toContain('report-1')

    await loadMore().trigger('click')
    await flushPromises()

    expect(listReports.mock.calls.map(([request]) => request.page)).toEqual([0, 1, 1])
    expect(wrapper.text()).toContain('page-two-report')
  })

  it('ignores a stale report response after the status filter starts a newer request', async () => {
    const stale = deferred()
    listReports
      .mockReset()
      .mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce({
        data: [{
          id: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
          reporterId: '11111111-1111-7111-8111-111111111111',
          targetType: 1,
          targetId: '33333333-3333-7333-8333-333333333333',
          reason: 'new-filter-result',
          status: 1,
          createTime: '2026-04-29T00:00:00Z'
        }]
      })

    const wrapper = mountModerationView()
    await nextTick()
    wrapper.vm.statusFilter = '1'
    await nextTick()
    await flushPromises()

    stale.resolve({
      data: [{
        id: 'cccccccc-cccc-7ccc-8ccc-cccccccccccc',
        reporterId: '11111111-1111-7111-8111-111111111111',
        targetType: 1,
        targetId: '33333333-3333-7333-8333-333333333333',
        reason: 'stale-filter-result',
        status: 0,
        createTime: '2026-04-29T00:00:00Z'
      }]
    })
    await flushPromises()

    expect(listReports).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('new-filter-result')
    expect(wrapper.text()).not.toContain('stale-filter-result')
  })

  it('clears reports and ignores the pending response when moderation permission is revoked', async () => {
    const pending = deferred()
    listReports.mockReset().mockReturnValueOnce(pending.promise)

    const wrapper = mountModerationView()
    await nextTick()
    auth.setMe({
      userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      username: 'former-moderator',
      authorities: ['ROLE_USER']
    })
    await nextTick()

    pending.resolve({
      data: [{
        id: 'dddddddd-dddd-7ddd-8ddd-dddddddddddd',
        reporterId: '11111111-1111-7111-8111-111111111111',
        targetType: 1,
        targetId: '33333333-3333-7333-8333-333333333333',
        reason: 'private-report',
        status: 0,
        createTime: '2026-04-29T00:00:00Z'
      }]
    })
    await flushPromises()

    expect(wrapper.text()).not.toContain('private-report')
    expect(listReports).toHaveBeenCalledTimes(1)
  })
})
