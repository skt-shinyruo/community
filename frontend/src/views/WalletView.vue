<template>
  <div class="page wallet-page">
    <UiPageHeader>
      <template #title>钱包</template>
      <template #subtitle>{{ state.hero.statusText }}</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading || loadingMore || submittingKey !== ''" @click="reload">
          {{ loading ? '刷新中…' : '刷新' }}
        </UiButton>
      </template>
    </UiPageHeader>

    <div class="wallet-summary-strip">
      <div class="wallet-summary-main">
        <span class="wallet-label">可用余额</span>
        <strong>{{ state.hero.balance }}</strong>
        <p>当前可用于站内消费和转账的积分，不代表法定货币或可兑付余额。</p>
      </div>
      <div class="wallet-summary-side">
        <div class="wallet-summary-metric">
          <span class="wallet-label">最近流水</span>
          <strong>{{ state.feed.length }}</strong>
          <p>积分发放、销毁、转账和交易相关流水会显示在这里。</p>
        </div>
      </div>
    </div>

    <UiState v-if="error" variant="error">
      {{ error }}
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" data-test="wallet-reload-retry" @click="reload">重试</UiButton>
      </template>
    </UiState>
    <UiSkeleton v-if="loading && !ready" variant="card" label="正在加载钱包" />

    <div v-if="ready" class="wallet-layout">
      <UiCard class="wallet-panel">
        <UiPageHeader>
          <template #title>钱包动作</template>
          <template #subtitle>钱包仅处理站内积分账务；真实支付与外部出款当前未接入。</template>
        </UiPageHeader>

        <UiState v-if="testCredits.enabled" class="wallet-test-notice">
          测试积分工具
          <template #description>仅用于开发和验收，不涉及真实充值、支付或银行出款。</template>
        </UiState>

        <div class="wallet-action-grid">
          <section v-if="testCredits.grant.enabled" class="wallet-action-card">
            <h2>领取测试积分</h2>
            <p>本账号剩余 {{ testCredits.grant.remainingAmount }}，单次最多 {{ testCredits.grant.maxAmountPerRequest }}。</p>
            <UiField label="领取数量" :error="formErrors.recharge">
              <UiInput v-model.number="rechargeForm.amount" type="number" :disabled="submittingKey !== ''" />
            </UiField>
            <p v-if="actionErrors.recharge" class="error wallet-action-error" role="alert">{{ actionErrors.recharge }}</p>
            <UiButton :disabled="submittingKey !== '' || testCredits.grant.remainingAmount <= 0" @click="submitRecharge">
              {{ submittingKey === 'recharge' ? '领取中…' : '领取测试积分' }}
            </UiButton>
          </section>

          <section v-if="testCredits.discard.enabled" class="wallet-action-card">
            <h2>销毁测试积分</h2>
            <p>本账号剩余配额 {{ testCredits.discard.remainingAmount }}；该操作不会产生外部出款。</p>
            <UiField label="销毁数量" :error="formErrors.withdraw">
              <UiInput v-model.number="withdrawForm.amount" type="number" :disabled="submittingKey !== ''" />
            </UiField>
            <p v-if="actionErrors.withdraw" class="error wallet-action-error" role="alert">{{ actionErrors.withdraw }}</p>
            <UiButton :disabled="submittingKey !== '' || testCredits.discard.remainingAmount <= 0" @click="requestWithdrawal">
              {{ submittingKey === 'withdraw' ? '销毁中…' : '销毁测试积分' }}
            </UiButton>
          </section>

          <section class="wallet-action-card">
            <h2>转账</h2>
            <p>直接把积分转给另一位成员。</p>
            <UiField label="目标用户 ID" :error="formErrors.transferToUserId">
              <UiInput v-model.trim="transferForm.toUserId" :disabled="submittingKey !== ''" />
            </UiField>
            <UiField label="转账金额" :error="formErrors.transferAmount">
              <UiInput v-model.number="transferForm.amount" type="number" :disabled="submittingKey !== ''" />
            </UiField>
            <p v-if="actionErrors.transfer" class="error wallet-action-error" role="alert">{{ actionErrors.transfer }}</p>
            <UiButton :disabled="submittingKey !== ''" @click="requestTransfer">
              {{ submittingKey === 'transfer' ? '转账中…' : '发起转账' }}
            </UiButton>
          </section>
        </div>
      </UiCard>

      <UiCard class="wallet-panel">
        <UiPageHeader>
          <template #title>最近流水</template>
          <template #subtitle>按时间查看钱包流水、状态和对方信息。</template>
        </UiPageHeader>

        <UiState v-if="txnsLoaded && state.feed.length === 0">
          暂无交易记录
          <template #description>产生积分发放、销毁、转账或交易托管后，这里会显示流水摘要。</template>
        </UiState>

        <template v-else-if="state.feed.length > 0">
          <div class="wallet-feed">
            <article v-for="item in state.feed" :key="item.key" class="wallet-feed-item">
              <div class="wallet-feed-main">
                <strong>{{ item.label }}</strong>
                <span>{{ item.meta }}</span>
              </div>
              <div class="wallet-feed-amount" :class="{ 'is-negative': item.amount < 0 }">
                {{ item.amountText }}
              </div>
            </article>
          </div>

          <p v-if="feedError" class="error wallet-feed-error" role="alert">{{ feedError }}</p>
          <div v-if="loadingMore || hasMoreFeed" class="wallet-feed-tail">
            <UiButton v-if="loadingMore" variant="ghost" disabled>
              <LoaderCircle :size="14" aria-hidden="true" class="wallet-feed-spinner" />
              正在加载…
            </UiButton>
            <UiButton v-else variant="secondary" class="wallet-feed-more-btn" @click="loadMore">加载更多</UiButton>
          </div>
          <p v-else-if="feedExhausted" class="wallet-feed-end">已经到底了</p>
        </template>
      </UiCard>
    </div>

    <UiModalConfirm
      v-if="confirmation.open"
      :title="confirmation.title"
      :message="confirmation.message"
      :confirm-text="confirmation.confirmText"
      :confirm-variant="confirmation.variant"
      @cancel="closeConfirmation"
      @confirm="runConfirmation"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { LoaderCircle } from 'lucide-vue-next'
import {
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletCapabilities,
  getWalletSummary,
  getWalletTransactions
} from '../api/services/walletService'
import { createWriteAttempt } from '../api/writeAttempt'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { useAuthStore } from '../stores/auth'
import { isUuid, normalizeOpaqueId } from '../utils/opaqueId'
import {
  WALLET_FEED_PAGE_SIZE,
  buildWalletState,
  nextWalletFeedLimit,
  walletDiscardConfirmation,
  walletFeedExhausted,
  walletFeedHasMore,
  walletTransferConfirmation
} from './walletState'
import { settleNamedRequests } from '../utils/settledRequests'

const auth = useAuthStore()
const loading = ref(false)
const ready = ref(false)
const error = ref('')
const submittingKey = ref('')
const summary = ref({ balance: 0 })
const txns = ref([])
const capabilities = ref({})

const rechargeForm = ref({ amount: '' })
const withdrawForm = ref({ amount: '' })
const transferForm = ref({ toUserId: '', amount: '' })

// 字段校验错误内联在对应 UiField，写失败内联在对应操作卡，页面级 error 只承担加载失败。
const formErrors = ref({ recharge: '', withdraw: '', transferToUserId: '', transferAmount: '' })
const actionErrors = ref({ recharge: '', withdraw: '', transfer: '' })

// 流水按 limit 窗口追加：feedLimit 是当前已请求的窗口大小，加载更多把窗口扩一页。
const feedLimit = ref(WALLET_FEED_PAGE_SIZE)
const loadingMore = ref(false)
const feedError = ref('')
const txnsLoaded = ref(false)

// 转账 / 销毁测试积分为资损动作，先经 UiModalConfirm 复述金额与对方再进入提交流程。
const confirmation = reactive({
  open: false,
  title: '',
  message: '',
  confirmText: '确认',
  variant: 'primary',
  action: /** @type {null | (() => Promise<void> | void)} */ (null)
})
const writeAttempts = {
  recharge: createWriteAttempt(),
  withdraw: createWriteAttempt(),
  transfer: createWriteAttempt()
}
let reloadGeneration = 0
let actionGeneration = 0

const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))

const state = computed(() =>
  buildWalletState({
    summary: summary.value,
    txns: txns.value
  })
)

const testCredits = computed(() => normalizeCapabilities(capabilities.value).testCredits)

const hasMoreFeed = computed(() =>
  walletFeedHasMore({ count: txns.value.length, limit: feedLimit.value })
)
const feedExhausted = computed(() =>
  walletFeedExhausted({ count: txns.value.length, limit: feedLimit.value })
)

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

function requirePositiveAmount(amount, fallbackMessage) {
  const value = Number(amount || 0)
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(fallbackMessage)
  }
  return value
}

async function reload() {
  const generation = ++reloadGeneration
  const scope = sessionScope.value
  loading.value = true
  error.value = ''
  feedError.value = ''
  try {
    const outcome = await settleNamedRequests({
      summary: () => getWalletSummary(),
      transactions: () => getWalletTransactions(feedLimit.value),
      capabilities: () => getWalletCapabilities()
    })
    if (generation !== reloadGeneration || scope !== sessionScope.value) return
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
    if (generation === reloadGeneration && scope === sessionScope.value) {
      loading.value = false
    }
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || submittingKey.value !== '' || !hasMoreFeed.value) return
  const generation = ++reloadGeneration
  const scope = sessionScope.value
  const targetLimit = nextWalletFeedLimit(feedLimit.value)
  loadingMore.value = true
  feedError.value = ''
  try {
    const outcome = await getWalletTransactions(targetLimit)
    if (generation !== reloadGeneration || scope !== sessionScope.value) return
    txns.value = normalizeTxns(outcome?.data)
    feedLimit.value = targetLimit
    txnsLoaded.value = true
  } catch (e) {
    if (generation !== reloadGeneration || scope !== sessionScope.value) return
    feedError.value = e?.message || '加载更多流水失败'
  } finally {
    // 结果可能因更新的 reload 而作废，但本次请求已结束，尾部指示必须复位。
    loadingMore.value = false
  }
}

function openConfirmation({ title, message, confirmText, variant = 'primary' }, action) {
  if (submittingKey.value !== '' || typeof action !== 'function') return
  confirmation.title = title
  confirmation.message = message
  confirmation.confirmText = confirmText
  confirmation.variant = variant
  confirmation.action = action
  confirmation.open = true
}

function closeConfirmation() {
  confirmation.open = false
  confirmation.action = null
}

async function runConfirmation() {
  const action = confirmation.action
  if (!action) return
  closeConfirmation()
  await action()
}

function isCurrentAction(generation, scope) {
  return generation === actionGeneration && scope === sessionScope.value
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

function isCurrentActionIntent(generation, scope, requestedIntent, currentIntent) {
  return isCurrentAction(generation, scope) && requestedIntent === currentIntent()
}

async function submitRecharge() {
  let amount
  try {
    amount = requirePositiveAmount(rechargeForm.value.amount, '请输入有效的测试积分数量')
  } catch (e) {
    formErrors.value.recharge = e.message
    actionErrors.value.recharge = ''
    return
  }

  const generation = ++actionGeneration
  const scope = sessionScope.value
  const requestedIntent = rechargeIntent()
  submittingKey.value = 'recharge'
  formErrors.value.recharge = ''
  actionErrors.value.recharge = ''
  try {
    await createRecharge({ amount }, { writeAttempt: writeAttempts.recharge })
    if (!isCurrentActionIntent(generation, scope, requestedIntent, rechargeIntent)) return
    rechargeForm.value.amount = ''
    writeAttempts.recharge.succeed()
    await reload()
  } catch (e) {
    if (!isCurrentActionIntent(generation, scope, requestedIntent, rechargeIntent)) return
    actionErrors.value.recharge = e?.message || '领取测试积分失败'
  } finally {
    if (isCurrentAction(generation, scope)) submittingKey.value = ''
  }
}

function requestWithdrawal() {
  formErrors.value.withdraw = ''
  actionErrors.value.withdraw = ''
  let amount
  try {
    amount = requirePositiveAmount(withdrawForm.value.amount, '请输入有效的测试积分数量')
  } catch (e) {
    formErrors.value.withdraw = e.message
    return
  }
  openConfirmation(
    { ...walletDiscardConfirmation({ amount }), variant: 'danger' },
    () => submitWithdrawal()
  )
}

async function submitWithdrawal() {
  let amount
  try {
    amount = requirePositiveAmount(withdrawForm.value.amount, '请输入有效的测试积分数量')
  } catch (e) {
    formErrors.value.withdraw = e.message
    actionErrors.value.withdraw = ''
    return
  }

  const generation = ++actionGeneration
  const scope = sessionScope.value
  const requestedIntent = withdrawalIntent()
  submittingKey.value = 'withdraw'
  formErrors.value.withdraw = ''
  actionErrors.value.withdraw = ''
  try {
    await createWithdrawal({ amount }, { writeAttempt: writeAttempts.withdraw })
    if (!isCurrentActionIntent(generation, scope, requestedIntent, withdrawalIntent)) return
    withdrawForm.value.amount = ''
    writeAttempts.withdraw.succeed()
    await reload()
  } catch (e) {
    if (!isCurrentActionIntent(generation, scope, requestedIntent, withdrawalIntent)) return
    actionErrors.value.withdraw = e?.message || '销毁测试积分失败'
  } finally {
    if (isCurrentAction(generation, scope)) submittingKey.value = ''
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
  let amount = 0
  try {
    amount = requirePositiveAmount(transferForm.value.amount, '请输入有效的转账金额')
  } catch (e) {
    formErrors.value.transferAmount = e.message
    valid = false
  }
  if (!valid) return
  openConfirmation(
    { ...walletTransferConfirmation({ toUserId, amount }), variant: 'danger' },
    () => submitTransfer()
  )
}

async function submitTransfer() {
  const toUserId = normalizeOpaqueId(transferForm.value.toUserId)
  let amount
  try {
    if (!isUuid(toUserId)) {
      throw new Error('请输入有效的目标用户 ID')
    }
    amount = requirePositiveAmount(transferForm.value.amount, '请输入有效的转账金额')
  } catch (e) {
    if (isUuid(toUserId)) {
      formErrors.value.transferAmount = e.message
    } else {
      formErrors.value.transferToUserId = e.message
    }
    actionErrors.value.transfer = ''
    return
  }

  const generation = ++actionGeneration
  const scope = sessionScope.value
  const requestedIntent = transferIntent()
  submittingKey.value = 'transfer'
  formErrors.value.transferToUserId = ''
  formErrors.value.transferAmount = ''
  actionErrors.value.transfer = ''
  try {
    await createTransfer({ toUserId, amount }, { writeAttempt: writeAttempts.transfer })
    if (!isCurrentActionIntent(generation, scope, requestedIntent, transferIntent)) return
    transferForm.value.toUserId = ''
    transferForm.value.amount = ''
    writeAttempts.transfer.succeed()
    await reload()
  } catch (e) {
    if (!isCurrentActionIntent(generation, scope, requestedIntent, transferIntent)) return
    actionErrors.value.transfer = e?.message || '转账失败'
  } finally {
    if (isCurrentAction(generation, scope)) submittingKey.value = ''
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

onMounted(() => {
  if (auth.authed) reload()
})
watch(sessionScope, () => {
  reloadGeneration += 1
  actionGeneration += 1
  resetPrivateState()
  if (auth.authed) reload()
})
watch(rechargeForm, () => writeAttempts.recharge.changeIntent(), { deep: true })
watch(withdrawForm, () => writeAttempts.withdraw.changeIntent(), { deep: true })
watch(transferForm, () => writeAttempts.transfer.changeIntent(), { deep: true })
onBeforeUnmount(() => {
  reloadGeneration += 1
  actionGeneration += 1
  Object.values(writeAttempts).forEach((attempt) => attempt.cancel())
})
</script>

<style scoped>
.wallet-page {
  max-width: 1120px;
  margin: 0 auto;
  gap: var(--space-5);
}

.wallet-summary-strip {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: var(--space-4);
}

.wallet-summary-main,
.wallet-summary-side,
.wallet-action-card,
.wallet-feed-item,
.wallet-panel {
  display: grid;
  gap: var(--space-3);
}

.wallet-summary-main,
.wallet-summary-side {
  padding: var(--space-5) var(--space-6);
  border-radius: var(--radius-lg);
  border: 1px solid color-mix(in srgb, var(--border) 82%, var(--accent) 18%);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 94%, white 6%), var(--surface));
  box-shadow: var(--shadow-sm);
}

.wallet-summary-main strong,
.wallet-summary-side strong {
  font-size: clamp(2rem, 4vw, 3rem);
  line-height: 1;
}

.wallet-summary-main p,
.wallet-summary-side p,
.wallet-action-card p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.wallet-label {
  font-size: var(--text-xs);
  letter-spacing: 0;
  color: var(--text-3);
  font-weight: 700;
}

.wallet-test-notice {
  margin-bottom: var(--space-3);
}

.wallet-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  gap: var(--space-5);
  align-items: start;
}

.wallet-action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-3);
}

.wallet-action-card {
  padding: var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 90%, var(--bg) 10%);
}

.wallet-action-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.wallet-action-error,
.wallet-feed-error {
  margin: 0;
  font-size: var(--text-sm);
}

.wallet-feed {
  display: grid;
  gap: var(--space-3);
}

.wallet-feed-item {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--border);
}

.wallet-feed-item:first-child {
  padding-top: 0;
}

.wallet-feed-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.wallet-feed-main span {
  color: var(--text-3);
  font-size: 13px;
}

.wallet-feed-amount {
  font-weight: 800;
  color: var(--success);
}

.wallet-feed-amount.is-negative {
  color: var(--danger);
}

.wallet-feed-tail {
  display: flex;
  justify-content: center;
  padding-top: var(--space-2);
}

.wallet-feed-more-btn {
  min-width: 260px;
}

.wallet-feed-spinner {
  animation: wallet-feed-spin 0.8s linear infinite;
}

@keyframes wallet-feed-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .wallet-feed-spinner {
    animation: none;
  }
}

.wallet-feed-end {
  margin: 0;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 960px) {
  .wallet-summary-strip,
  .wallet-layout,
  .wallet-action-grid {
    grid-template-columns: 1fr;
  }
}
</style>
