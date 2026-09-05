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
        UiField: {
          props: ['label', 'error'],
          template: '<div class="ui-field-stub"><label>{{ label }}</label><slot /><p v-if="error" role="alert">{{ error }}</p></div>'
        },
        UiInput: {
          props: ['modelValue', 'disabled', 'type', 'placeholder'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" :disabled="disabled" :type="type" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)" />'
        },
        UiModalConfirm: {
          props: ['title', 'message', 'confirmText', 'confirmVariant'],
          emits: ['confirm', 'cancel'],
          template: '<div data-test="wallet-confirm"><p>{{ message }}</p><button data-test="wallet-confirm-cancel" @click="$emit(\'cancel\')">取消</button><button data-test="wallet-confirm-ok" @click="$emit(\'confirm\')">{{ confirmText }}</button></div>'
        },
        UiSkeleton: { template: '<div data-test="wallet-skeleton"><slot /></div>' },
        UiState: { template: '<div><slot /><slot name="description" /><slot name="actions" /></div>' },
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

function txnItems(count, prefix = 'txn') {
  return Array.from({ length: count }, (_, index) => ({
    txnRef: `${prefix}-${index}`,
    txnType: 'TRANSFER',
    amount: -1,
    counterpartLabel: `用户 ${prefix}-${index}`,
    status: 'SUCCEEDED'
  }))
}

function findButton(wrapper, text) {
  return wrapper.findAll('button').find((button) => button.text() === text)
}

async function confirmWalletAction(wrapper) {
  const dialog = wrapper.find('[data-test="wallet-confirm"]')
  expect(dialog.exists()).toBe(true)
  await dialog.find('[data-test="wallet-confirm-ok"]').trigger('click')
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
    await confirmWalletAction(wrapper)
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
    await confirmWalletAction(wrapper)
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
    expect(wrapper.find('[data-test="wallet-confirm"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('请输入有效的目标用户 ID')
  })

  it('requires capital-loss confirmation before submitting a transfer', async () => {
    const wrapper = mountWalletView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[2].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[3].setValue('25')
    await wrapper.findAll('button').find((button) => button.text() === '发起转账').trigger('click')
    await flushPromises()

    expect(createTransfer).not.toHaveBeenCalled()
    const dialog = wrapper.find('[data-test="wallet-confirm"]')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('11111111-1111-7111-8111-111111111111')
    expect(dialog.text()).toContain('25')

    await dialog.find('[data-test="wallet-confirm-cancel"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="wallet-confirm"]').exists()).toBe(false)
    expect(createTransfer).not.toHaveBeenCalled()

    await wrapper.findAll('button').find((button) => button.text() === '发起转账').trigger('click')
    await confirmWalletAction(wrapper)
    await flushPromises()
    expect(createTransfer).toHaveBeenCalledTimes(1)
  })

  it('requires capital-loss confirmation before discarding test credits', async () => {
    const wrapper = mountWalletView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[1].setValue('3')
    await wrapper.findAll('button').find((button) => button.text() === '销毁测试积分').trigger('click')
    await flushPromises()

    expect(createWithdrawal).not.toHaveBeenCalled()
    const dialog = wrapper.find('[data-test="wallet-confirm"]')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('3')

    await confirmWalletAction(wrapper)
    await flushPromises()
    expect(createWithdrawal).toHaveBeenCalledTimes(1)
    expect(createWithdrawal.mock.calls[0][0]).toMatchObject({ amount: 3 })
  })

  it('rejects an invalid discard amount inline without opening the confirmation', async () => {
    const wrapper = mountWalletView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    await inputs[1].setValue('-2')
    await wrapper.findAll('button').find((button) => button.text() === '销毁测试积分').trigger('click')
    await flushPromises()

    expect(createWithdrawal).not.toHaveBeenCalled()
    expect(wrapper.find('[data-test="wallet-confirm"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('请输入有效的测试积分数量')
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
    const submitConfirmed = async () => {
      await submit().trigger('click')
      await confirmWalletAction(wrapper)
      await flushPromises()
    }

    await inputs[2].setValue('11111111-1111-7111-8111-111111111111')
    await inputs[3].setValue('25')
    await submitConfirmed()
    expect(wrapper.text()).toContain('temporary transfer failure')

    await submitConfirmed()
    expect(observedKeys[1]).toBe(observedKeys[0])

    const nextInputs = wrapper.findAll('input')
    await nextInputs[2].setValue('22222222-2222-7222-8222-222222222222')
    await nextInputs[3].setValue('10')
    await submitConfirmed()
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
    await confirmWalletAction(wrapper)
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

  it('appends the ledger feed by growing the limit window until exhaustion', async () => {
    getWalletTransactions.mockImplementation((limit) =>
      Promise.resolve({ data: txnItems(Math.min(limit, 24), `page-${limit}`), traceId: `trace-${limit}` })
    )
    const wrapper = mountWalletView()
    await flushPromises()

    expect(getWalletTransactions).toHaveBeenCalledWith(12)
    expect(wrapper.findAll('.wallet-feed-item').length).toBe(12)
    expect(findButton(wrapper, '加载更多')).toBeTruthy()

    await findButton(wrapper, '加载更多').trigger('click')
    await flushPromises()
    expect(getWalletTransactions).toHaveBeenCalledWith(24)
    expect(wrapper.findAll('.wallet-feed-item').length).toBe(24)
    expect(wrapper.text()).toContain('用户 page-24-23')
    expect(findButton(wrapper, '加载更多')).toBeTruthy()

    await findButton(wrapper, '加载更多').trigger('click')
    await flushPromises()
    expect(getWalletTransactions).toHaveBeenCalledWith(36)
    expect(wrapper.findAll('.wallet-feed-item').length).toBe(24)
    expect(findButton(wrapper, '加载更多')).toBeFalsy()
    expect(wrapper.text()).toContain('已经到底了')
  })

  it('keeps the current feed window and reports a tail error when loading more fails', async () => {
    getWalletTransactions
      .mockResolvedValueOnce({ data: txnItems(12), traceId: 'trace-page-1' })
      .mockRejectedValueOnce(new Error('ledger page down'))
      .mockResolvedValueOnce({ data: txnItems(24), traceId: 'trace-page-2' })
    const wrapper = mountWalletView()
    await flushPromises()

    await findButton(wrapper, '加载更多').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('ledger page down')
    expect(wrapper.findAll('.wallet-feed-item').length).toBe(12)
    expect(findButton(wrapper, '加载更多')).toBeTruthy()

    await findButton(wrapper, '加载更多').trigger('click')
    await flushPromises()
    expect(wrapper.text()).not.toContain('ledger page down')
    expect(wrapper.findAll('.wallet-feed-item').length).toBe(24)
  })

  it('hides the load-more control and marks the end when the first page is not full', async () => {
    getWalletTransactions.mockResolvedValue({ data: txnItems(5), traceId: 'trace-wallet-transactions' })
    const wrapper = mountWalletView()
    await flushPromises()

    expect(wrapper.findAll('.wallet-feed-item').length).toBe(5)
    expect(findButton(wrapper, '加载更多')).toBeFalsy()
    expect(wrapper.text()).toContain('已经到底了')
  })

  it('retries a failed initial load from the error state action', async () => {
    getWalletSummary.mockRejectedValueOnce(new Error('summary down'))
    getWalletTransactions.mockRejectedValueOnce(new Error('ledger down'))
    getWalletCapabilities.mockRejectedValueOnce(new Error('capabilities down'))

    const wrapper = mountWalletView()
    await flushPromises()
    expect(wrapper.text()).toContain('summary down')

    await wrapper.find('[data-test="wallet-reload-retry"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('summary down')
    expect(wrapper.text()).toContain('可用余额')
    expect(wrapper.text()).toContain('1000')
  })

  it('does not present an empty ledger state while transactions have never loaded', async () => {
    getWalletTransactions.mockRejectedValueOnce(new Error('ledger unavailable'))

    const wrapper = mountWalletView()
    await flushPromises()

    expect(wrapper.text()).toContain('部分钱包数据加载失败：ledger unavailable')
    expect(wrapper.text()).not.toContain('暂无交易记录')
  })
})
