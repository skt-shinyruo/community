import { computed, getCurrentInstance, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletCapabilities,
  getWalletSummary,
  getWalletTransactions
} from '../api/services/walletService'
import { createWriteAttempt } from '../api/writeAttempt'
import { useAuthStore } from '../stores/auth'
import { identityScope } from '../stores/identityScope'
import { createLatestRequestTracker } from '../utils/latestRequest'
import { isUuid, normalizeOpaqueId } from '../utils/opaqueId'
import { parsePointsAmount } from '../utils/pointsAmount'
import { settleNamedRequests } from '../utils/settledRequests'
import { WALLET_FEED_PAGE_SIZE, nextWalletFeedLimit, walletDiscardConfirmation, walletFeedCapped, walletFeedExhausted, walletFeedHasMore, walletTransferConfirmation } from './walletState'
import { useConfirmationState } from './useConfirmationState'

// 组件 setup 之外（单测直接调用）不注册 onMounted / onBeforeUnmount；scope watch 在两种上下文都生效。
const inComponentSetup = () => !!getCurrentInstance()

function normalizeSummary(data) {
  const safe = data && typeof data === 'object' ? data : {}
  return {
    ...safe
  }
}

function normalizeTxns(data) {
  return Array.isArray(data) ? data.map((item) => ({ ...item })) : []
}

function normalizeAction(action) {
  const safe = action && typeof action === 'object' ? action : {}
  return {
    enabled: safe.enabled === true,
    maxAmountPerRequest: Math.max(0, Number(safe.maxAmountPerRequest || 0)),
    totalQuota: Math.max(0, Number(safe.totalQuota || 0)),
    usedAmount: Math.max(0, Number(safe.usedAmount || 0)),
    remainingAmount: Math.max(0, Number(safe.remainingAmount || 0))
  }
}

function normalizeCapabilities(data) {
  const safe = data && typeof data === 'object' ? data : {}
  const credits = safe.testCredits && typeof safe.testCredits === 'object' ? safe.testCredits : {}
  return {
    balanceUnit: String(safe.balanceUnit || 'INTERNAL_TEST_CREDIT'),
    realPaymentsSupported: safe.realPaymentsSupported === true,
    realPayoutsSupported: safe.realPayoutsSupported === true,
    testCredits: {
      enabled: credits.enabled === true,
      grant: normalizeAction(credits.grant),
      discard: normalizeAction(credits.discard)
    }
  }
}

// 钱包页的 transport 流程：reloadGeneration / actionGeneration、流水追加分页、
// 三个资损写（充值 / 销毁 / 转账）的 WriteAttempt 与请求生命周期、二次确认弹窗
// 和身份 scope 隔离都收在这里；WalletView.vue 只绑定公开的 model / actions。
export function useWalletWorkflow() {
  const auth = useAuthStore()
  const loading = ref(false)
  const ready = ref(false)
  const error = ref('')
  const submittingKey = ref('')
  const summary = ref({ balance: 0 })
  const txns = ref(/** @type {Array<Record<string, unknown>>} */ ([]))
  const capabilities = ref({})

  // 金额字段接受输入框原文与测试直写数字（parsePointsAmount 统一处理两种口径）。
  const rechargeForm = ref(/** @type {{ amount: string | number }} */ ({ amount: '' }))
  const withdrawForm = ref(/** @type {{ amount: string | number }} */ ({ amount: '' }))
  const transferForm = ref(/** @type {{ toUserId: string, amount: string | number }} */ ({ toUserId: '', amount: '' }))

  // 字段校验错误内联在对应 UiField，写失败内联在对应操作卡，页面级 error 只承担加载失败。
  const formErrors = ref({ recharge: '', withdraw: '', transferToUserId: '', transferAmount: '' })
  const actionErrors = ref({ recharge: '', withdraw: '', transfer: '' })

  // 流水按 limit 窗口追加：feedLimit 是当前已请求的窗口大小，加载更多把窗口扩一页。
  const feedLimit = ref(WALLET_FEED_PAGE_SIZE)
  const loadingMore = ref(false)
  const feedError = ref('')
  const txnsLoaded = ref(false)

  // 转账 / 销毁测试积分为资损动作，先经 UiModalConfirm 复述金额与对方再进入提交流程。
  const { confirmation, confirm: openConfirmation, closeConfirmation, runConfirmation } = useConfirmationState({
    isBusy: computed(() => submittingKey.value !== '')
  })
  const testCredits = computed(() => normalizeCapabilities(capabilities.value).testCredits)
  const writeAttempts = {
    recharge: createWriteAttempt(),
    withdraw: createWriteAttempt(),
    transfer: createWriteAttempt()
  }

  const sessionScope = computed(() => identityScope(auth))
  const reloadTracker = createLatestRequestTracker({ getScope: () => sessionScope.value })
  const actionTracker = createLatestRequestTracker({ getScope: () => sessionScope.value })

  const hasMoreFeed = computed(() =>
    walletFeedHasMore({ count: txns.value.length, limit: feedLimit.value })
  )
  const feedExhausted = computed(() =>
    walletFeedExhausted({ count: txns.value.length, limit: feedLimit.value })
  )
  const feedCapped = computed(() =>
    walletFeedCapped({ count: txns.value.length, limit: feedLimit.value })
  )

  async function reload() {
    const requestHandle = reloadTracker.begin()
    loading.value = true
    error.value = ''
    feedError.value = ''
    try {
      const outcome = await settleNamedRequests({
        summary: () => getWalletSummary(),
        transactions: () => getWalletTransactions(feedLimit.value),
        capabilities: () => getWalletCapabilities()
      })
      if (!reloadTracker.isCurrent(requestHandle)) return
      if (outcome.results.summary.ok) summary.value = normalizeSummary(outcome.results.summary.value?.data)
      if (outcome.results.transactions.ok) {
        txns.value = normalizeTxns(outcome.results.transactions.value?.data)
        txnsLoaded.value = true
      }
      if (outcome.results.capabilities.ok) capabilities.value = normalizeCapabilities(outcome.results.capabilities.value?.data)
      ready.value = ready.value || outcome.anySucceeded
      if (!outcome.allSucceeded) {
        const firstError = outcome.results[outcome.failedKeys[0]]?.error
        error.value = outcome.anySucceeded
          ? `部分钱包数据加载失败：${firstError?.message || '请稍后重试'}`
          : (firstError?.message || '加载钱包失败')
      }
    } finally {
      if (reloadTracker.isCurrent(requestHandle)) {
        loading.value = false
      }
    }
  }

  async function loadMore() {
    if (loading.value || loadingMore.value || submittingKey.value !== '' || !hasMoreFeed.value) return
    const requestHandle = reloadTracker.begin()
    const targetLimit = nextWalletFeedLimit(feedLimit.value)
    loadingMore.value = true
    feedError.value = ''
    try {
      const outcome = await getWalletTransactions(targetLimit)
      if (!reloadTracker.isCurrent(requestHandle)) return
      txns.value = normalizeTxns(outcome?.data)
      feedLimit.value = targetLimit
      txnsLoaded.value = true
    } catch (e) {
      if (!reloadTracker.isCurrent(requestHandle)) return
      feedError.value = e?.message || '加载更多流水失败'
    } finally {
      // 结果可能因更新的 reload 而作废，但本次请求已结束，尾部指示必须复位。
      loadingMore.value = false
    }
  }

  function rechargeIntent() {
    return JSON.stringify([Number(rechargeForm.value.amount || 0)])
  }

  function withdrawalIntent() {
    return JSON.stringify([Number(withdrawForm.value.amount || 0)])
  }

  function transferIntent() {
    return JSON.stringify([
      normalizeOpaqueId(transferForm.value.toUserId),
      Number(transferForm.value.amount || 0)
    ])
  }

  function isCurrentActionIntent(requestHandle, requestedIntent, currentIntent) {
    return actionTracker.isCurrent(requestHandle) && requestedIntent === currentIntent()
  }

  async function submitRecharge() {
    const parsedAmount = parsePointsAmount(rechargeForm.value.amount)
    if (!parsedAmount.valid) {
      formErrors.value.recharge = parsedAmount.message
      actionErrors.value.recharge = ''
      return
    }
    const amount = parsedAmount.amount

    const requestHandle = actionTracker.begin()
    const requestedIntent = rechargeIntent()
    submittingKey.value = 'recharge'
    formErrors.value.recharge = ''
    actionErrors.value.recharge = ''
    try {
      await createRecharge({ amount }, { writeAttempt: writeAttempts.recharge })
      if (!isCurrentActionIntent(requestHandle, requestedIntent, rechargeIntent)) return
      rechargeForm.value.amount = ''
      writeAttempts.recharge.succeed()
      await reload()
    } catch (e) {
      if (!isCurrentActionIntent(requestHandle, requestedIntent, rechargeIntent)) return
      actionErrors.value.recharge = e?.message || '领取测试积分失败'
    } finally {
      if (actionTracker.isCurrent(requestHandle)) submittingKey.value = ''
    }
  }

  function requestWithdrawal() {
    formErrors.value.withdraw = ''
    actionErrors.value.withdraw = ''
    const parsedAmount = parsePointsAmount(withdrawForm.value.amount)
    if (!parsedAmount.valid) {
      formErrors.value.withdraw = parsedAmount.message
      return
    }
    openConfirmation(
      { ...walletDiscardConfirmation({ amount: parsedAmount.amount }), variant: 'danger' },
      () => submitWithdrawal(parsedAmount.amount)
    )
  }

  async function submitWithdrawal(amount) {
    const requestHandle = actionTracker.begin()
    const requestedIntent = withdrawalIntent()
    submittingKey.value = 'withdraw'
    formErrors.value.withdraw = ''
    actionErrors.value.withdraw = ''
    try {
      await createWithdrawal({ amount }, { writeAttempt: writeAttempts.withdraw })
      if (!isCurrentActionIntent(requestHandle, requestedIntent, withdrawalIntent)) return
      withdrawForm.value.amount = ''
      writeAttempts.withdraw.succeed()
      await reload()
    } catch (e) {
      if (!isCurrentActionIntent(requestHandle, requestedIntent, withdrawalIntent)) return
      actionErrors.value.withdraw = e?.message || '销毁测试积分失败'
    } finally {
      if (actionTracker.isCurrent(requestHandle)) submittingKey.value = ''
    }
  }

  function requestTransfer() {
    formErrors.value.transferToUserId = ''
    formErrors.value.transferAmount = ''
    actionErrors.value.transfer = ''
    const toUserId = normalizeOpaqueId(transferForm.value.toUserId)
    let valid = true
    if (!isUuid(toUserId)) {
      formErrors.value.transferToUserId = '请输入有效的目标用户 ID'
      valid = false
    }
    const parsedAmount = parsePointsAmount(transferForm.value.amount)
    if (!parsedAmount.valid) {
      formErrors.value.transferAmount = parsedAmount.message
      valid = false
    }
    if (!valid) return
    openConfirmation(
      { ...walletTransferConfirmation({ toUserId, amount: parsedAmount.amount }), variant: 'danger' },
      () => submitTransfer(toUserId, parsedAmount.amount)
    )
  }

  async function submitTransfer(toUserId, amount) {
    const requestHandle = actionTracker.begin()
    const requestedIntent = transferIntent()
    submittingKey.value = 'transfer'
    formErrors.value.transferToUserId = ''
    formErrors.value.transferAmount = ''
    actionErrors.value.transfer = ''
    try {
      await createTransfer({ toUserId, amount }, { writeAttempt: writeAttempts.transfer })
      if (!isCurrentActionIntent(requestHandle, requestedIntent, transferIntent)) return
      transferForm.value.toUserId = ''
      transferForm.value.amount = ''
      writeAttempts.transfer.succeed()
      await reload()
    } catch (e) {
      if (!isCurrentActionIntent(requestHandle, requestedIntent, transferIntent)) return
      actionErrors.value.transfer = e?.message || '转账失败'
    } finally {
      if (actionTracker.isCurrent(requestHandle)) submittingKey.value = ''
    }
  }

  function resetPrivateState() {
    summary.value = { balance: 0 }
    txns.value = []
    capabilities.value = {}
    rechargeForm.value = { amount: '' }
    withdrawForm.value = { amount: '' }
    transferForm.value = { toUserId: '', amount: '' }
    loading.value = false
    ready.value = false
    error.value = ''
    submittingKey.value = ''
    feedLimit.value = WALLET_FEED_PAGE_SIZE
    loadingMore.value = false
    feedError.value = ''
    txnsLoaded.value = false
    formErrors.value = { recharge: '', withdraw: '', transferToUserId: '', transferAmount: '' }
    actionErrors.value = { recharge: '', withdraw: '', transfer: '' }
    closeConfirmation()
    Object.values(writeAttempts).forEach((attempt) => attempt.cancel())
  }

  if (inComponentSetup()) {
    onMounted(() => {
      if (auth.authed) reload()
    })
    onBeforeUnmount(() => {
      reloadTracker.invalidate()
      actionTracker.invalidate()
      Object.values(writeAttempts).forEach((attempt) => attempt.cancel())
    })
  }
  watch(sessionScope, () => {
    reloadTracker.invalidate()
    actionTracker.invalidate()
    resetPrivateState()
    if (auth.authed) reload()
  })
  watch(rechargeForm, () => writeAttempts.recharge.changeIntent(), { deep: true })
  watch(withdrawForm, () => writeAttempts.withdraw.changeIntent(), { deep: true })
  watch(transferForm, () => writeAttempts.transfer.changeIntent(), { deep: true })

  const model = {
    loading,
    ready,
    error,
    submittingKey,
    summary,
    txns,
    testCredits,
    rechargeForm,
    withdrawForm,
    transferForm,
    formErrors,
    actionErrors,
    feedLimit,
    hasMoreFeed,
    feedExhausted,
    feedCapped,
    feedError,
    txnsLoaded
  }
  const actions = {
    reload,
    loadMore,
    closeConfirmation,
    runConfirmation,
    submitRecharge,
    requestWithdrawal,
    requestTransfer
  }

  return { model, actions, confirmation }
}
