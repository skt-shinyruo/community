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
        UiButton: {
          props: ['disabled'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('WalletAdminView', () => {
  beforeEach(() => {
    freezeWallet.mockReset()
    reverseWalletTxn.mockReset()
    window.localStorage.clear()
  })

  it('submits wallet freeze requests with UUID user ids', async () => {
    freezeWallet.mockResolvedValue({ data: null, traceId: 'trace-freeze' })

    const wrapper = mountAdminView()

    expect(wrapper.text()).toContain('高风险资金操作')
    expect(wrapper.text()).toContain('审计追踪')

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[1].setValue('risk-control')
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(freezeWallet).toHaveBeenCalledWith({
      userId: '11111111-1111-7111-8111-111111111111',
      reason: 'risk-control'
    })
  })

  it('does not append an old administrator action after the identity changes', async () => {
    const pendingFreeze = deferred()
    freezeWallet.mockReturnValue(pendingFreeze.promise)
    const wrapper = mountAdminView()

    const inputs = wrapper.findAll('input')
    await inputs[0].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[1].setValue('old-admin-private-reason')
    await wrapper.find('button').trigger('click')

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
})
