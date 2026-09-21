// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'

const { freezeWallet, reverseWalletTxn } = vi.hoisted(() => ({
  freezeWallet: vi.fn(),
  reverseWalletTxn: vi.fn()
}))

vi.mock('../api/services/walletService', () => ({
  freezeWallet,
  reverseWalletTxn
}))

import WalletAdminView from './WalletAdminView.vue'

function mountAdminView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.installSession({
    accessToken: 'admin-token-1',
    me: { userId: 'aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', username: 'admin-1', authorities: ['ROLE_ADMIN'] }
  })
  return mount(WalletAdminView, {
    global: {
      plugins: [pinia],
      stubs: {
        UiBreadcrumb: true,
        UiCard: { template: '<div><slot /></div>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiPageHeader: { template: '<div><slot /><slot name="title" /><slot name="subtitle" /></div>' },
        UiModalConfirm: {
          props: ['title', 'message', 'confirmText', 'confirmVariant'],
          emits: ['confirm', 'cancel'],
          template: '<div data-test="wallet-admin-confirm"><p>{{ message }}</p><button data-test="wallet-admin-confirm-cancel" @click="$emit(\'cancel\')">取消</button><button data-test="wallet-admin-confirm-ok" @click="$emit(\'confirm\')">{{ confirmText }}</button></div>'
        },
        UiButton: {
          props: ['disabled'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

function deferred() {
  /** @type {((value: unknown) => void) | undefined} */
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  if (!resolve) throw new Error('deferred resolve not captured')
  return { promise, resolve }
}

describe('WalletAdminView', () => {
  beforeEach(() => {
    freezeWallet.mockReset()
    reverseWalletTxn.mockReset()
    window.localStorage.clear()
  })

  async function confirmAdminAction(wrapper) {
    const dialog = wrapper.find('[data-test="wallet-admin-confirm"]')
    expect(dialog.exists()).toBe(true)
    await dialog.find('[data-test="wallet-admin-confirm-ok"]').trigger('click')
  }

  it('submits wallet freeze requests with UUID user ids after a danger confirmation', async () => {
    freezeWallet.mockResolvedValue({ data: null, traceId: 'trace-freeze' })

    const wrapper = mountAdminView()

    expect(wrapper.text()).toContain('高风险资金操作')
    expect(wrapper.text()).toContain('审计追踪')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[1].setValue('risk-control')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    // 资损动作先经确认弹窗复述对象，确认前不调服务。
    expect(freezeWallet).not.toHaveBeenCalled()
    const dialog = wrapper.get('[data-test="wallet-admin-confirm"]')
    expect(dialog.text()).toContain('11111111-1111-7111-8111-111111111111')
    await dialog.find('[data-test="wallet-admin-confirm-ok"]').trigger('click')
    await flushPromises()

    expect(freezeWallet).toHaveBeenCalledWith({
      userId: '11111111-1111-7111-8111-111111111111',
      reason: 'risk-control'
    })
    expect(wrapper.text()).toContain('已冻结钱包')
  })

  it('rejects a non-UUID freeze user id inline without opening a confirmation or calling the service', async () => {
    const wrapper = mountAdminView()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('not-a-uuid')
    await inputs[1].setValue('risk-control')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入有效的目标用户 ID')
    expect(wrapper.find('[data-test="wallet-admin-confirm"]').exists()).toBe(false)
    expect(freezeWallet).not.toHaveBeenCalled()
  })

  it('does not freeze the wallet when the confirmation is cancelled', async () => {
    const wrapper = mountAdminView()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[1].setValue('risk-control')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[data-test="wallet-admin-confirm"]')
    await dialog.find('[data-test="wallet-admin-confirm-cancel"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="wallet-admin-confirm"]').exists()).toBe(false)
    expect(freezeWallet).not.toHaveBeenCalled()
  })

  it('restates the transaction reference in a confirmation before reversing it', async () => {
    reverseWalletTxn.mockResolvedValue({ data: null, traceId: 'trace-reverse' })

    const wrapper = mountAdminView()

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('transfer:req-1')
    await inputs[3].setValue('fat-finger')
    const reverseButton = wrapper.findAll('button').find((button) => button.text().includes('执行回滚'))
    if (!reverseButton) throw new Error('reverse button not found')
    await reverseButton.trigger('click')
    await flushPromises()

    expect(reverseWalletTxn).not.toHaveBeenCalled()
    const dialog = wrapper.get('[data-test="wallet-admin-confirm"]')
    expect(dialog.text()).toContain('transfer:req-1')
    expect(dialog.text()).toContain('不可撤销')
    await dialog.find('[data-test="wallet-admin-confirm-ok"]').trigger('click')
    await flushPromises()

    expect(reverseWalletTxn).toHaveBeenCalledWith({ txnRef: 'transfer:req-1', reason: 'fat-finger' })
    expect(wrapper.text()).toContain('已提交回滚')
  })

  it('does not append an old administrator action after the identity changes', async () => {
    const pendingFreeze = deferred()
    freezeWallet.mockReturnValue(pendingFreeze.promise)
    const wrapper = mountAdminView()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[1].setValue('old-admin-private-reason')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    await confirmAdminAction(wrapper)

    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'admin-token-2',
      me: { userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', username: 'admin-2', authorities: ['ROLE_ADMIN'] }
    })
    await flushPromises()

    expect(wrapper.findAll('input').every((input) => input.element.value === '')).toBe(true)
    pendingFreeze.resolve({ data: null, traceId: 'trace-old-admin-freeze' })
    await flushPromises()

    expect(wrapper.text()).toContain('暂无操作记录')
    expect(wrapper.text()).not.toContain('old-admin-private-reason')
    expect(wrapper.text()).not.toContain('已冻结钱包')
  })

  it('closes a pending confirmation when the identity changes', async () => {
    const wrapper = mountAdminView()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[1].setValue('risk-control')
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="wallet-admin-confirm"]').exists()).toBe(true)

    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'admin-token-2',
      me: { userId: 'bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', username: 'admin-2', authorities: ['ROLE_ADMIN'] }
    })
    await flushPromises()

    expect(wrapper.find('[data-test="wallet-admin-confirm"]').exists()).toBe(false)
    expect(freezeWallet).not.toHaveBeenCalled()
  })
})
