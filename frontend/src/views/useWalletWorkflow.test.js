// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
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

import { useWalletWorkflow } from './useWalletWorkflow'

const USER_ID = '11111111-1111-7111-8111-111111111111'
const OTHER_USER_ID = '22222222-2222-7222-8222-222222222222'

function createSubject() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.installSession({ accessToken: 'wallet-token-1', me: { userId: USER_ID, username: 'wallet-user-1' } })
  const workflow = useWalletWorkflow()
  return { auth, workflow }
}

// WriteAttempt 的 key 在 succeed/cancel/changeIntent 后被清空，必须在服务调用期间观察；
// 服务被 mock 时 key 尚未由真实 http 层物化，这里显式 begin() 读取。
function observeKeys(mock) {
  const keys = []
  mock.mockImplementation((payload, { writeAttempt } = {}) => {
    keys.push(writeAttempt.begin())
    return Promise.resolve({ data: {}, traceId: '' })
  })
  return keys
}

function txnList(count) {
  return Array.from({ length: count }, (_, index) => ({
    txnRef: `txn-${index}`,
    txnType: 'TRANSFER',
    amount: -1,
    counterpartLabel: `用户 ${index}`,
    status: 'SUCCEEDED'
  }))
}

function deferred() {
  let resolve
  const promise = new Promise((resolvePromise) => { resolve = resolvePromise })
  return { promise, resolve }
}

describe('useWalletWorkflow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    getWalletSummary.mockResolvedValue({ data: { balance: 1000, status: 'ACTIVE' }, traceId: 'trace-summary' })
    getWalletCapabilities.mockResolvedValue({ data: {}, traceId: 'trace-capabilities' })
    getWalletTransactions.mockResolvedValue({ data: [], traceId: 'trace-transactions' })
    createRecharge.mockResolvedValue({ data: {}, traceId: 'trace-recharge' })
    createTransfer.mockResolvedValue({ data: { status: 'SUCCEEDED' }, traceId: 'trace-transfer' })
    createWithdrawal.mockResolvedValue({ data: {}, traceId: 'trace-withdrawal' })
  })

  it('reloads the three wallet sections and keeps partial successes when some fail', async () => {
    const { workflow } = createSubject()
    await workflow.actions.reload()
    expect(getWalletSummary).toHaveBeenCalledTimes(1)
    expect(getWalletTransactions).toHaveBeenCalledWith(12)
    expect(getWalletCapabilities).toHaveBeenCalledTimes(1)
    expect(workflow.model.ready.value).toBe(true)
    expect(workflow.model.error.value).toBe('')

    getWalletTransactions.mockRejectedValueOnce(new Error('ledger unavailable'))
    await workflow.actions.reload()
    expect(workflow.model.error.value).toBe('部分钱包数据加载失败：ledger unavailable')
    expect(workflow.model.ready.value).toBe(true)
    expect(workflow.model.summary.value.balance).toBe(1000)
  })

  it('reuses the transfer write-attempt key on manual retry and renews it after success or edit', async () => {
    const { workflow } = createSubject()
    const { transferForm } = workflow.model
    transferForm.value.toUserId = USER_ID
    transferForm.value.amount = 25

    const keys = observeKeys(createTransfer)
    const submitConfirmed = async () => {
      await workflow.actions.requestTransfer()
      await workflow.actions.runConfirmation()
    }
    createTransfer.mockImplementationOnce((_payload, { writeAttempt } = {}) => {
      keys.push(writeAttempt.begin())
      return Promise.reject(new Error('temporary transfer failure'))
    })
    await submitConfirmed()
    // 失败后表单内容保留，人工重试必须复用同一 Idempotency-Key。
    expect(transferForm.value.amount).toBe(25)
    expect(workflow.model.actionErrors.value.transfer).toBe('temporary transfer failure')

    await submitConfirmed()
    expect(keys[1]).toBe(keys[0])

    // 成功后清空表单；重新填写并提交是新的业务意图，必须换 key。
    await submitConfirmed()
    expect(keys[2]).not.toBe(keys[0])
    expect(transferForm.value.amount).toBe('')

    transferForm.value.toUserId = USER_ID
    transferForm.value.amount = 10
    await submitConfirmed()
    expect(keys[3]).not.toBe(keys[2])
  })

  it('renews the recharge key after success, keeps the withdrawal key across retries, and changes key on intent change', async () => {
    const { workflow } = createSubject()
    const { rechargeForm, withdrawForm } = workflow.model

    const rechargeKeys = observeKeys(createRecharge)
    rechargeForm.value.amount = 5
    await workflow.actions.submitRecharge()
    expect(rechargeForm.value.amount).toBe('')
    // 成功即结束 attempt；重新填写并再次提交是新的业务意图。
    rechargeForm.value.amount = 5
    await workflow.actions.submitRecharge()
    expect(rechargeKeys[1]).not.toBe(rechargeKeys[0])

    const withdrawalKeys = observeKeys(createWithdrawal)
    const discardConfirmed = async () => {
      await workflow.actions.requestWithdrawal()
      await workflow.actions.runConfirmation()
    }
    // 首次失败：失败不结束 attempt，人工重试复用同一 key。
    createWithdrawal.mockImplementationOnce((_payload, { writeAttempt } = {}) => {
      withdrawalKeys.push(writeAttempt.begin())
      return Promise.reject(new Error('temporary discard failure'))
    })
    withdrawForm.value.amount = 3
    await discardConfirmed()
    withdrawForm.value.amount = 3
    await discardConfirmed()
    expect(withdrawalKeys[1]).toBe(withdrawalKeys[0])
    // 修改业务意图必须换 key。
    withdrawForm.value.amount = 4
    await discardConfirmed()
    expect(withdrawalKeys[2]).not.toBe(withdrawalKeys[0])
  })

  it('keeps confirmation restated values over the live form and closes before submitting', async () => {
    const { workflow } = createSubject()
    const { transferForm } = workflow.model
    transferForm.value.toUserId = USER_ID
    transferForm.value.amount = 25

    await workflow.actions.requestTransfer()
    expect(createTransfer).not.toHaveBeenCalled()
    expect(workflow.confirmation.open).toBe(true)
    expect(workflow.confirmation.variant).toBe('danger')

    // 确认弹窗打开期间表单被改动：提交值必须仍是复述的 25。
    transferForm.value.amount = 30
    await workflow.actions.runConfirmation()
    await Promise.resolve()

    expect(workflow.confirmation.open).toBe(false)
    expect(createTransfer).toHaveBeenCalledTimes(1)
    expect(createTransfer.mock.calls[0][0]).toMatchObject({ toUserId: USER_ID, amount: 25 })
  })

  it('validates amounts and UUID targets inline before any request or confirmation', async () => {
    const { workflow } = createSubject()
    const { transferForm, rechargeForm, withdrawForm } = workflow.model

    transferForm.value.toUserId = 'not-a-uuid'
    transferForm.value.amount = 25
    await workflow.actions.requestTransfer()
    expect(workflow.confirmation.open).toBe(false)
    expect(createTransfer).not.toHaveBeenCalled()
    expect(workflow.model.formErrors.value.transferToUserId).toBe('请输入有效的目标用户 ID')

    transferForm.value.toUserId = USER_ID
    transferForm.value.amount = 1.5
    await workflow.actions.requestTransfer()
    expect(workflow.confirmation.open).toBe(false)
    expect(createTransfer).not.toHaveBeenCalled()
    expect(workflow.model.formErrors.value.transferAmount).toBe('积分金额必须是整数，不支持小数')

    await workflow.actions.submitRecharge()
    expect(createRecharge).not.toHaveBeenCalled()
    expect(workflow.model.formErrors.value.recharge).toBe('请输入正整数积分金额')

    await workflow.actions.requestWithdrawal()
    expect(workflow.confirmation.open).toBe(false)
    expect(createWithdrawal).not.toHaveBeenCalled()
    expect(workflow.model.formErrors.value.withdraw).toBe('请输入正整数积分金额')
  })

  it('discards stale write effects when the form intent changes mid-flight', async () => {
    let resolveTransfer
    const pending = new Promise((resolve) => { resolveTransfer = resolve })
    createTransfer.mockReturnValueOnce(pending)
    const { workflow } = createSubject()
    const { transferForm } = workflow.model
    transferForm.value.toUserId = USER_ID
    transferForm.value.amount = 25

    await workflow.actions.requestTransfer()
    const inFlight = workflow.actions.runConfirmation()
    await vi.waitFor(() => expect(workflow.model.submittingKey.value).toBe('transfer'))

    transferForm.value.amount = 30
    resolveTransfer({ data: { status: 'SUCCEEDED' }, traceId: 'trace-old' })
    await inFlight

    // 旧的完成响应不得清空新表单意图，也不触发重载。
    expect(transferForm.value.amount).toBe(30)
    expect(workflow.model.submittingKey.value).toBe('')
    expect(getWalletSummary).not.toHaveBeenCalled()
  })

  it('discards responses from a previous identity scope after the session changes', async () => {
    const { auth, workflow } = createSubject()
    const oldSummary = deferred()
    const oldTransactions = deferred()
    const oldCapabilities = deferred()
    getWalletSummary.mockReturnValueOnce(oldSummary.promise)
    getWalletTransactions.mockReturnValueOnce(oldTransactions.promise)
    getWalletCapabilities.mockReturnValueOnce(oldCapabilities.promise)
    const pendingReload = workflow.actions.reload()
    expect(workflow.model.loading.value).toBe(true)

    auth.installSession({
      accessToken: 'wallet-token-2',
      me: { userId: OTHER_USER_ID, username: 'wallet-user-2' }
    })
    // scope watch 触发 reset + 新一轮 reload。
    await vi.waitFor(() => expect(getWalletSummary).toHaveBeenCalledTimes(2))
    await workflow.actions.reload()
    expect(workflow.model.summary.value.balance).toBe(1000)
    expect(workflow.model.ready.value).toBe(true)

    // 旧身份的迟到响应到达：latest-request tracker 按 scope 丢弃，不得覆盖新会话数据。
    oldSummary.resolve({ data: { balance: 111, status: 'FROZEN' }, traceId: 'trace-old-summary' })
    oldTransactions.resolve({ data: txnList(3), traceId: 'trace-old-txns' })
    oldCapabilities.resolve({ data: { testCredits: { enabled: true } }, traceId: 'trace-old-caps' })
    await Promise.all([pendingReload])

    expect(workflow.model.summary.value.balance).toBe(1000)
    expect(workflow.model.txns.value.length).toBe(0)
    expect(workflow.model.error.value).toBe('')
  })

  it('appends the feed by growing the limit window and reporting tail failures', async () => {
    const { workflow } = createSubject()
    getWalletTransactions.mockResolvedValueOnce({ data: txnList(12), traceId: 'trace-12' })
    await workflow.actions.reload()
    expect(workflow.model.txns.value.length).toBe(12)

    getWalletTransactions.mockResolvedValueOnce({ data: txnList(24), traceId: 'trace-24' })
    await workflow.actions.loadMore()
    expect(getWalletTransactions).toHaveBeenLastCalledWith(24)
    expect(workflow.model.feedLimit.value).toBe(24)

    getWalletTransactions.mockRejectedValueOnce(new Error('ledger page down'))
    await workflow.actions.loadMore()
    expect(workflow.model.feedLimit.value).toBe(24)
    expect(workflow.model.feedError.value).toBe('ledger page down')
    expect(workflow.model.txns.value.length).toBe(24)
  })

  it('resets private state and renews pending write attempts when the identity changes', async () => {
    const { auth, workflow } = createSubject()
    await workflow.actions.reload()
    const { transferForm } = workflow.model

    // 转账失败留下未完成的 attempt；身份切换必须 cancel 它，下次提交换新 key。
    const keys = []
    createTransfer.mockImplementation((_payload, { writeAttempt } = {}) => {
      keys.push(writeAttempt.begin())
      return Promise.reject(new Error('temporary failure'))
    })
    const submitConfirmed = async () => {
      await workflow.actions.requestTransfer()
      await workflow.actions.runConfirmation()
    }
    transferForm.value.toUserId = USER_ID
    transferForm.value.amount = 25
    await submitConfirmed()
    expect(keys.length).toBe(1)

    auth.clear()
    await vi.waitFor(() => expect(workflow.model.ready.value).toBe(false))

    expect(transferForm.value.amount).toBe('')
    expect(workflow.model.feedLimit.value).toBe(12)
    expect(workflow.model.txns.value.length).toBe(0)
    expect(workflow.confirmation.open).toBe(false)

    // reset 后的同一表单重填是全新业务尝试。
    auth.installSession({ accessToken: 'wallet-token-3', me: { userId: USER_ID, username: 'wallet-user-3' } })
    await vi.waitFor(() => expect(workflow.model.ready.value).toBe(true))
    transferForm.value.toUserId = USER_ID
    transferForm.value.amount = 25
    await submitConfirmed()
    expect(keys.length).toBe(2)
    expect(keys[1]).not.toBe(keys[0])
  })
})
