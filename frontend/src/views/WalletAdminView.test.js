// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const { freezeWallet, reverseWalletTxn } = vi.hoisted(() => ({
  freezeWallet: vi.fn(),
  reverseWalletTxn: vi.fn()
}))

vi.mock('../api/services/walletService', () => ({
  freezeWallet,
  reverseWalletTxn
}))

import WalletAdminView from './WalletAdminView.vue'

describe('WalletAdminView', () => {
  beforeEach(() => {
    freezeWallet.mockReset()
    reverseWalletTxn.mockReset()
  })

  it('submits wallet freeze requests with UUID user ids', async () => {
    freezeWallet.mockResolvedValue({ data: null, traceId: 'trace-freeze' })

    const wrapper = mount(WalletAdminView, {
      global: {
        stubs: {
          UiBreadcrumb: true,
          UiCard: { template: '<div><slot /></div>' },
          UiEmpty: { template: '<div><slot /><slot name="description" /></div>' },
          UiPageHeader: { template: '<div><slot /><slot name="title" /><slot name="subtitle" /></div>' },
          UiButton: {
            props: ['disabled'],
            template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
          },
          UiInput: {
            props: ['modelValue'],
            template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
          }
        }
      }
    })

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
})
