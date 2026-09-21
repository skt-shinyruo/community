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
  /** @type {((value: unknown) => void) | undefined} */
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  if (!resolve) throw new Error('deferred resolve not captured')
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
        UiPageHeader: { template: '<header><slot name="title" /><slot name="subtitle" /><slot /></header>' },
        UiModalConfirm: {
          props: ['title', 'message', 'confirmText', 'confirmVariant', 'busy'],
          emits: ['confirm', 'cancel'],
          template: '<div data-test="resolution-modal"><h2>{{ title }}</h2><p>{{ message }}</p><slot /><button data-test="resolution-cancel" @click="$emit(\'cancel\')">取消</button><button data-test="resolution-confirm" @click="$emit(\'confirm\')">{{ confirmText }}</button></div>'
        },
        UiField: {
          props: ['label', 'help', 'error'],
          template: '<div><label>{{ label }}</label><slot /></div>'
        },
        UiTextarea: {
          props: ['modelValue', 'disabled'],
          emits: ['update:modelValue'],
          template: '<textarea :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        }
      }
    }
  })
}

function findModal(wrapper) {
  return wrapper.find('[data-test="resolution-modal"]')
}

async function openResolution(wrapper, buttonText) {
  const button = wrapper.findAll('button').find((item) => item.text() === buttonText)
  await button.trigger('click')
  return findModal(wrapper)
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
          totalAmount: 12900,
          buyerNote: '收到的商品与描述不符',
          sellerNote: '不同意退款'
        }
      ],
      traceId: 'trace-disputes'
    })
    adminResolveMarketDispute.mockResolvedValue({ data: {}, traceId: 'trace-resolve' })
  })

  it('renders dispute labels and the order amount', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('实物商品')
    expect(wrapper.text()).toContain('待管理员裁定')
    expect(wrapper.text()).toContain('需要管理员裁定')
    expect(wrapper.text()).toContain('订单金额：12900 积分')
    expect(wrapper.text()).toContain('收到的商品与描述不符')
    expect(wrapper.text()).toContain('不同意退款')
    expect(wrapper.text()).not.toContain('旧奖励后台')
  })

  it('requires confirmation that restates the amount before resolving, and forwards the admin note', async () => {
    const wrapper = mountView()
    await flushPromises()

    const modal = await openResolution(wrapper, '退回买家')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('12900 积分')
    expect(modal.text()).toContain('退回买家')
    expect(modal.text()).toContain('不可撤销')
    expect(adminResolveMarketDispute).not.toHaveBeenCalled()

    await modal.find('textarea').setValue('证据支持买家')
    const confirm = modal.findAll('button').find((item) => item.text() === '退回买家')
    await confirm.trigger('click')
    await flushPromises()

    expect(adminResolveMarketDispute).toHaveBeenCalledWith(
      '11111111-1111-7111-8111-111111111111',
      'refund',
      { note: '证据支持买家' }
    )
    expect(findModal(wrapper).exists()).toBe(false)
  })

  it('does not call the resolution api when the admin cancels the confirmation', async () => {
    const wrapper = mountView()
    await flushPromises()

    const modal = await openResolution(wrapper, '放款卖家')
    expect(modal.text()).toContain('放款给卖家')
    const cancel = modal.findAll('button').find((item) => item.text() === '取消')
    await cancel.trigger('click')

    expect(adminResolveMarketDispute).not.toHaveBeenCalled()
    expect(findModal(wrapper).exists()).toBe(false)
  })

  it('submits an empty note without a placeholder when the admin leaves the reason blank', async () => {
    const wrapper = mountView()
    await flushPromises()

    const modal = await openResolution(wrapper, '放款卖家')
    const confirm = modal.findAll('button').find((item) => item.text() === '放款卖家')
    await confirm.trigger('click')
    await flushPromises()

    expect(adminResolveMarketDispute).toHaveBeenCalledWith(
      '11111111-1111-7111-8111-111111111111',
      'release',
      { note: '' }
    )
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

    const modal = await openResolution(wrapper, '退回买家')
    const confirm = modal.findAll('button').find((item) => item.text() === '退回买家')
    if (!confirm) throw new Error('resolution confirm button not found')
    await confirm.trigger('click')
    auth.setMe({
      userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa',
      username: 'former-admin',
      authorities: ['ROLE_USER']
    })
    await nextTick()
    pendingResolution.resolve({ data: {}, traceId: 'stale-resolution' })
    await flushPromises()

    expect(listAdminMarketDisputes).toHaveBeenCalledTimes(1)
    // 同一账号权限回收不再重置视图：已加载的行保留（身份未切换），
    // 迟到的裁定响应经实时 isAdmin 校验丢弃，不会触发提交后的重载。
    expect(wrapper.findAll('.market-admin-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('货不对板')
  })
})
