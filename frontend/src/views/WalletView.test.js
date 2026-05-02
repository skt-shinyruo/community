// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const {
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletSummary
} = vi.hoisted(() => ({
  createRecharge: vi.fn(),
  createTransfer: vi.fn(),
  createWithdrawal: vi.fn(),
  getWalletSummary: vi.fn()
}))

vi.mock('../api/services/walletService', () => ({
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletSummary
}))

import WalletView from './WalletView.vue'

function mountWalletView() {
  return mount(WalletView, {
    global: {
      stubs: {
        UiBreadcrumb: true,
        UiCard: { template: '<section><slot /></section>' },
        UiEmpty: { template: '<div><slot /><slot name="description" /></div>' },
        UiPageHeader: { template: '<header><slot /><slot name="title" /><slot name="subtitle" /></header>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        },
        UiInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        }
      }
    }
  })
}

describe('WalletView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getWalletSummary.mockResolvedValue({ data: { balance: 1000 }, traceId: 'trace-wallet-summary' })
    createRecharge.mockResolvedValue({ data: {}, traceId: 'trace-recharge' })
    createTransfer.mockResolvedValue({ data: { status: 'SUCCEEDED' }, traceId: 'trace-transfer' })
    createWithdrawal.mockResolvedValue({ data: {}, traceId: 'trace-withdrawal' })
  })

  it('submits transfer target user ids as opaque UUID strings', async () => {
    const wrapper = mountWalletView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[3].setValue('25')
    await wrapper.findAll('button').find((button) => button.text() === '发起转账').trigger('click')
    await flushPromises()

    expect(createTransfer).toHaveBeenCalledTimes(1)
    expect(createTransfer.mock.calls[0][0]).toMatchObject({
      toUserId: '11111111-1111-7111-8111-111111111111',
      amount: 25
    })
  })

  it('validates transfer target as UUID before calling the API', async () => {
    const wrapper = mountWalletView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('not-a-uuid')
    await inputs[3].setValue('25')
    await wrapper.findAll('button').find((button) => button.text() === '发起转账').trigger('click')
    await flushPromises()

    expect(createTransfer).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入有效的目标用户 ID')
  })
})
