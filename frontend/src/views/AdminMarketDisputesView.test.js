// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { adminResolveMarketDispute, listAdminMarketDisputes } = vi.hoisted(() => ({
  adminResolveMarketDispute: vi.fn(),
  listAdminMarketDisputes: vi.fn()
}))

vi.mock('../api/services/marketService', () => ({
  adminResolveMarketDispute,
  listAdminMarketDisputes
}))

import AdminMarketDisputesView from './AdminMarketDisputesView.vue'
import { useAuthStore } from '../stores/auth'

let auth

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  auth = useAuthStore()
  auth.installSession({
    accessToken: 'market-admin-token',
    me: {
      userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      username: 'admin',
      authorities: ['ROLE_ADMIN']
    }
  })
  return mount(AdminMarketDisputesView, {
    global: {
      plugins: [pinia],
      stubs: {
        UiBreadcrumb: { template: '<nav><slot /></nav>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot /></header>' }
      }
    }
  })
}

describe('AdminMarketDisputesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listAdminMarketDisputes.mockResolvedValue({
      data: [
        {
          disputeId: '11111111-1111-7111-8111-111111111111',
          goodsType: 'PHYSICAL',
          reason: '货不对板',
          status: 'SELLER_REJECTED',
          buyerNote: '收到的商品与描述不符',
          sellerNote: '不同意退款'
        }
      ],
      traceId: 'trace-disputes'
    })
    adminResolveMarketDispute.mockResolvedValue({ data: {}, traceId: 'trace-resolve' })
  })

  it('renders dispute labels and forwards the chosen resolution action', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('待管理员裁定')
    expect(wrapper.text()).toContain('需要管理员裁定')
    expect(wrapper.text()).toContain('收到的商品与描述不符')
    expect(wrapper.text()).toContain('不同意退款')
    expect(wrapper.text()).not.toContain('旧奖励后台')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(adminResolveMarketDispute).toHaveBeenCalledWith('11111111-1111-7111-8111-111111111111', 'refund', { note: 'refund' })
  })

  it('renders an explicit empty state after a successful empty response', async () => {
    listAdminMarketDisputes.mockResolvedValueOnce({ data: [], traceId: '' })

    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('暂无待处理争议')
    expect(wrapper.findAll('.market-admin-row')).toHaveLength(0)
  })

  it('keeps the new administrator response when the previous session resolves later', async () => {
    const stale = deferred()
    listAdminMarketDisputes
      .mockReset()
      .mockReturnValueOnce(stale.promise)
      .mockResolvedValueOnce({
        data: [{
          disputeId: '22222222-2222-7222-8222-222222222222',
          goodsType: 'VIRTUAL',
          reason: 'new-session-dispute',
          status: 'SELLER_REJECTED'
        }]
      })
    const wrapper = mountView()
    await nextTick()

    auth.installSession({
      accessToken: 'next-market-admin-token',
      me: {
        userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb',
        username: 'next-admin',
        authorities: ['ROLE_ADMIN']
      }
    })
    await flushPromises()
    stale.resolve({
      data: [{
        disputeId: '33333333-3333-7333-8333-333333333333',
        goodsType: 'PHYSICAL',
        reason: 'stale-session-dispute',
        status: 'SELLER_REJECTED'
      }]
    })
    await flushPromises()

    expect(wrapper.text()).toContain('new-session-dispute')
    expect(wrapper.text()).not.toContain('stale-session-dispute')
    expect(listAdminMarketDisputes).toHaveBeenCalledTimes(2)
  })

  it('does not reload or commit a resolution response after admin permission is revoked', async () => {
    const pendingResolution = deferred()
    adminResolveMarketDispute.mockReset().mockReturnValueOnce(pendingResolution.promise)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('button').trigger('click')
    auth.setMe({
      userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      username: 'former-admin',
      authorities: ['ROLE_USER']
    })
    await nextTick()
    pendingResolution.resolve({ data: {}, traceId: 'stale-resolution' })
    await flushPromises()

    expect(listAdminMarketDisputes).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.market-admin-row')).toHaveLength(0)
  })
})
