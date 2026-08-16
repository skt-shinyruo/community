// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../stores/auth'

const {
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletCapabilities,
  getWalletSummary,
  getWalletTransactions
} = vi.hoisted(() => ({
  createRecharge: vi.fn(),
  createTransfer: vi.fn(),
  createWithdrawal: vi.fn(),
  getWalletCapabilities: vi.fn(),
  getWalletSummary: vi.fn(),
  getWalletTransactions: vi.fn()
}))

vi.mock('../api/services/walletService', () => ({
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletCapabilities,
  getWalletSummary,
  getWalletTransactions
}))

import WalletView from './WalletView.vue'

function mountWalletView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.installSession({
    accessToken: 'wallet-token-1',
    me: { userId: '11111111-1111-7111-8111-111111111111', username: 'wallet-user-1' }
  })
  return mount(WalletView, {
    global: {
      plugins: [pinia],
      stubs: {
        UiBreadcrumb: true,
        UiCard: { template: '<section><slot /></section>' },
        UiState: { template: '<div><slot /><slot name="description" /></div>' },
        UiPageHeader: { template: '<header><slot /><slot name="title" /><slot name="subtitle" /></header>' },
        UiButton: {
          props: ['disabled', 'variant'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('WalletView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    getWalletSummary.mockResolvedValue({ data: { balance: 1000, status: 'ACTIVE' }, traceId: 'trace-wallet-summary' })
    getWalletCapabilities.mockResolvedValue({
      data: {
        balanceUnit: 'INTERNAL_TEST_CREDIT',
        realPaymentsSupported: false,
        realPayoutsSupported: false,
        testCredits: {
          enabled: true,
          grant: { enabled: true, maxAmountPerRequest: 1000, totalQuota: 5000, usedAmount: 0, remainingAmount: 5000 },
          discard: { enabled: true, maxAmountPerRequest: 1000, totalQuota: 5000, usedAmount: 0, remainingAmount: 5000 }
        }
      },
      traceId: 'trace-wallet-capabilities'
    })
    getWalletTransactions.mockResolvedValue({ data: [], traceId: 'trace-wallet-transactions' })
    createRecharge.mockResolvedValue({ data: {}, traceId: 'trace-recharge' })
    createTransfer.mockResolvedValue({ data: { status: 'SUCCEEDED' }, traceId: 'trace-transfer' })
    createWithdrawal.mockResolvedValue({ data: {}, traceId: 'trace-withdrawal' })
  })

  it('loads persisted wallet transactions during initial reload', async () => {
    getWalletTransactions.mockResolvedValue({
      data: [
        {
          txnRef: 'wallet:transfer:history',
          txnType: 'TRANSFER',
          amount: -25,
          counterpartLabel: '用户 11111111-1111-7111-8111-111111111111',
          status: 'SUCCEEDED'
        }
      ],
      traceId: 'trace-wallet-transactions'
    })

    const wrapper = mountWalletView()
    await flushPromises()

    expect(getWalletSummary).toHaveBeenCalledTimes(1)
    expect(getWalletTransactions).toHaveBeenCalledWith(12)
    expect(wrapper.text()).toContain('转账转出')
    expect(wrapper.text()).toContain('-25 积分')
    expect(wrapper.text()).toContain('用户 11111111-1111-7111-8111-111111111111')
  })

  it('reloads persisted transactions after transfer instead of rendering local synthetic history', async () => {
    getWalletTransactions
      .mockResolvedValueOnce({ data: [], traceId: 'trace-wallet-transactions-1' })
      .mockResolvedValueOnce({
        data: [
          {
            txnRef: 'wallet:transfer:server',
            txnType: 'TRANSFER',
            amount: -25,
            counterpartLabel: '用户 server-confirmed',
            status: 'SUCCEEDED'
          }
        ],
        traceId: 'trace-wallet-transactions-2'
      })

    const wrapper = mountWalletView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[3].setValue('25')
    await wrapper.findAll('button').find((button) => button.text() === '发起转账').trigger('click')
    await flushPromises()

    expect(createTransfer).toHaveBeenCalledTimes(1)
    expect(getWalletTransactions).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('用户 server-confirmed')
    expect(wrapper.text()).not.toContain('用户 11111111-1111-7111-8111-111111111111')
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

  it('renders wallet as an asset and ledger surface without demo copy', async () => {
    const wrapper = mountWalletView()
    await flushPromises()

    expect(wrapper.text()).toContain('可用余额')
    expect(wrapper.text()).toContain('最近流水')
    expect(wrapper.text()).not.toContain('当前会话')
    expect(wrapper.text()).not.toContain('后续')
  })

  it('hides test-credit mutation controls when the backend disables them', async () => {
    getWalletCapabilities.mockResolvedValue({
      data: {
        testCredits: {
          enabled: false,
          grant: { enabled: false },
          discard: { enabled: false }
        }
      },
      traceId: 'trace-wallet-capabilities-disabled'
    })

    const wrapper = mountWalletView()
    await flushPromises()

    expect(wrapper.text()).not.toContain('领取测试积分')
    expect(wrapper.text()).not.toContain('销毁测试积分')
    expect(wrapper.text()).toContain('真实支付与外部出款当前未接入')
  })

  it('keeps successful wallet sections visible when transactions are temporarily unavailable', async () => {
    getWalletTransactions.mockRejectedValueOnce(new Error('ledger unavailable'))

    const wrapper = mountWalletView()
    await flushPromises()

    expect(wrapper.text()).toContain('部分钱包数据加载失败：ledger unavailable')
    expect(wrapper.text()).toContain('可用余额')
    expect(wrapper.text()).toContain('转账')
  })

  it('reuses the write-attempt key for a manual retry and renews it after success', async () => {
    const observedKeys = []
    createTransfer
      .mockImplementationOnce((_command, { writeAttempt }) => {
        observedKeys.push(writeAttempt.begin())
        return Promise.reject(new Error('temporary transfer failure'))
      })
      .mockImplementation((_command, { writeAttempt }) => {
        observedKeys.push(writeAttempt.begin())
        return Promise.resolve({ data: { status: 'SUCCEEDED' }, traceId: '' })
      })
    const wrapper = mountWalletView()
    await flushPromises()
    const inputs = wrapper.findAll('input')
    const submit = () => wrapper.findAll('button').find((button) => button.text() === '发起转账')

    await inputs[2].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[3].setValue('25')
    await submit().trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('temporary transfer failure')

    await submit().trigger('click')
    await flushPromises()
    expect(observedKeys[1]).toBe(observedKeys[0])

    const nextInputs = wrapper.findAll('input')
    await nextInputs[2].setValue('22222222-2222-7222-8222-222222222222')
    await nextInputs[3].setValue('10')
    await submit().trigger('click')
    await flushPromises()
    expect(observedKeys[2]).not.toBe(observedKeys[1])
  })

  it('does not let a completed transfer clear a newer form intent', async () => {
    const pendingTransfer = deferred()
    createTransfer.mockReturnValueOnce(pendingTransfer.promise)
    const wrapper = mountWalletView()
    await flushPromises()
    const inputs = wrapper.findAll('input')
    const submit = wrapper.findAll('button').find((button) => button.text() === '发起转账')

    await inputs[2].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[3].setValue('25')
    await submit.trigger('click')
    await vi.waitFor(() => expect(createTransfer).toHaveBeenCalledTimes(1))

    expect(inputs[2].attributes('disabled')).toBeDefined()
    expect(inputs[3].attributes('disabled')).toBeDefined()
    inputs[2].element.disabled = false
    inputs[3].element.disabled = false
    await inputs[2].setValue('22222222-2222-7222-8222-222222222222')
    await inputs[3].setValue('30')
    pendingTransfer.resolve({ data: { status: 'SUCCEEDED' }, traceId: 'trace-old-transfer' })
    await flushPromises()

    expect(inputs[2].element.value).toBe('22222222-2222-7222-8222-222222222222')
    expect(inputs[3].element.value).toBe('30')
    expect(getWalletSummary).toHaveBeenCalledTimes(1)
    expect(submit.attributes('disabled')).toBeUndefined()
  })

  it('discards a previous identity wallet response after the session changes', async () => {
    const oldSummary = deferred()
    const oldTransactions = deferred()
    const oldCapabilities = deferred()
    getWalletSummary
      .mockReturnValueOnce(oldSummary.promise)
      .mockResolvedValueOnce({ data: { balance: 222, status: 'ACTIVE' }, traceId: 'trace-new-summary' })
    getWalletTransactions
      .mockReturnValueOnce(oldTransactions.promise)
      .mockResolvedValueOnce({
        data: [{ txnRef: 'new-wallet-txn', txnType: 'TRANSFER', amount: 8, counterpartLabel: 'new-user-ledger' }],
        traceId: 'trace-new-transactions'
      })
    getWalletCapabilities
      .mockReturnValueOnce(oldCapabilities.promise)
      .mockResolvedValueOnce({ data: { testCredits: { enabled: false } }, traceId: 'trace-new-capabilities' })

    const wrapper = mountWalletView()
    const auth = useAuthStore()
    auth.installSession({
      accessToken: 'wallet-token-2',
      me: { userId: '22222222-2222-7222-8222-222222222222', username: 'wallet-user-2' }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('222')
    expect(wrapper.text()).toContain('new-user-ledger')

    oldSummary.resolve({ data: { balance: 111, status: 'FROZEN' }, traceId: 'trace-old-summary' })
    oldTransactions.resolve({
      data: [{ txnRef: 'old-wallet-txn', txnType: 'TRANSFER', amount: -9, counterpartLabel: 'old-user-ledger' }],
      traceId: 'trace-old-transactions'
    })
    oldCapabilities.resolve({
      data: { testCredits: { enabled: true, grant: { enabled: true, remainingAmount: 999 } } },
      traceId: 'trace-old-capabilities'
    })
    await flushPromises()

    expect(wrapper.text()).toContain('222')
    expect(wrapper.text()).toContain('new-user-ledger')
    expect(wrapper.text()).not.toContain('old-user-ledger')
    expect(wrapper.text()).not.toContain('999')
  })
})
