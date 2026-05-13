<template>
  <div class="page wallet-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>钱包</template>
      <template #subtitle>{{ state.hero.statusText }}</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading || submittingKey !== ''" @click="reload">
          {{ loading ? '刷新中…' : '刷新' }}
        </UiButton>
      </template>
    </UiPageHeader>

    <div class="wallet-summary-strip">
      <div class="wallet-summary-main">
        <span class="wallet-label">可用余额</span>
        <strong>{{ state.hero.balance }}</strong>
        <p>当前可用于消费、转账和提现的站内积分。</p>
      </div>
      <div class="wallet-summary-side">
        <div class="wallet-summary-metric">
          <span class="wallet-label">最近流水</span>
          <strong>{{ state.feed.length }}</strong>
          <p>充值、提现、转账和交易相关流水会显示在这里。</p>
        </div>
      </div>
    </div>

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading && !ready" class="muted wallet-state">正在加载钱包…</div>

    <div v-else class="wallet-layout">
      <UiCard class="wallet-panel">
        <UiPageHeader>
          <template #title>钱包动作</template>
          <template #subtitle>充值、提现和转账会进入钱包账务流程，请确认金额和对象后提交。</template>
        </UiPageHeader>

        <div class="wallet-action-grid">
          <section class="wallet-action-card">
            <h2>充值</h2>
            <p>向钱包补充可立即使用的站内积分。</p>
            <UiInput v-model.number="rechargeForm.amount" type="number" placeholder="输入充值金额" />
            <UiButton :disabled="submittingKey !== ''" @click="submitRecharge">
              {{ submittingKey === 'recharge' ? '充值中…' : '确认充值' }}
            </UiButton>
          </section>

          <section class="wallet-action-card">
            <h2>提现</h2>
            <p>发起提现申请，保留钱包里的真实余额语义。</p>
            <UiInput v-model.number="withdrawForm.amount" type="number" placeholder="输入提现金额" />
            <UiButton :disabled="submittingKey !== ''" @click="submitWithdrawal">
              {{ submittingKey === 'withdraw' ? '提交中…' : '申请提现' }}
            </UiButton>
          </section>

          <section class="wallet-action-card">
            <h2>转账</h2>
            <p>直接把积分转给另一位成员。</p>
            <UiInput v-model.trim="transferForm.toUserId" placeholder="目标用户 ID" />
            <UiInput v-model.number="transferForm.amount" type="number" placeholder="输入转账金额" />
            <UiButton :disabled="submittingKey !== ''" @click="submitTransfer">
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

        <UiState v-if="state.feed.length === 0">
          暂无交易记录
          <template #description>产生充值、提现、转账或交易托管后，这里会显示流水摘要。</template>
        </UiState>

        <div v-else class="wallet-feed">
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
      </UiCard>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  createRecharge,
  createTransfer,
  createWithdrawal,
  getWalletSummary
} from '../api/services/walletService'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { isUuid, normalizeOpaqueId } from '../utils/opaqueId'
import { buildWalletState } from './walletState'

const loading = ref(false)
const ready = ref(false)
const error = ref('')
const submittingKey = ref('')
const summary = ref({ balance: 0 })
const txns = ref([])

const rechargeForm = ref({ amount: '' })
const withdrawForm = ref({ amount: '' })
const transferForm = ref({ toUserId: '', amount: '' })

const state = computed(() =>
  buildWalletState({
    summary: summary.value,
    txns: txns.value
  })
)

function normalizeSummary(data) {
  const safe = data && typeof data === 'object' ? data : {}
  return {
    ...safe
  }
}

function prependTxn(entry) {
  txns.value = [entry, ...txns.value].slice(0, 12)
}

function requirePositiveAmount(amount, fallbackMessage) {
  const value = Number(amount || 0)
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(fallbackMessage)
  }
  return value
}

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await getWalletSummary()
    summary.value = normalizeSummary(data)
    ready.value = true
  } catch (e) {
    error.value = e?.message || '加载钱包失败'
  } finally {
    loading.value = false
  }
}

async function submitRecharge() {
  let amount = 0
  try {
    amount = requirePositiveAmount(rechargeForm.value.amount, '请输入有效的充值金额')
  } catch (e) {
    error.value = e.message
    return
  }

  submittingKey.value = 'recharge'
  error.value = ''
  try {
    const { data } = await createRecharge({ amount })
    prependTxn({
      txnType: 'RECHARGE',
      amount,
      counterpartLabel: '平台入账',
      requestId: data?.requestId || '',
      status: data?.status || 'SUCCEEDED'
    })
    rechargeForm.value.amount = ''
    await reload()
  } catch (e) {
    error.value = e?.message || '充值失败'
  } finally {
    submittingKey.value = ''
  }
}

async function submitWithdrawal() {
  let amount = 0
  try {
    amount = requirePositiveAmount(withdrawForm.value.amount, '请输入有效的提现金额')
  } catch (e) {
    error.value = e.message
    return
  }

  submittingKey.value = 'withdraw'
  error.value = ''
  try {
    const { data } = await createWithdrawal({ amount })
    prependTxn({
      txnType: 'WITHDRAW',
      amount: -amount,
      counterpartLabel: '提现申请',
      requestId: data?.requestId || '',
      status: data?.status || 'PENDING'
    })
    withdrawForm.value.amount = ''
    await reload()
  } catch (e) {
    error.value = e?.message || '提现失败'
  } finally {
    submittingKey.value = ''
  }
}

async function submitTransfer() {
  const toUserId = normalizeOpaqueId(transferForm.value.toUserId)
  let amount = 0
  try {
    if (!isUuid(toUserId)) {
      throw new Error('请输入有效的目标用户 ID')
    }
    amount = requirePositiveAmount(transferForm.value.amount, '请输入有效的转账金额')
  } catch (e) {
    error.value = e.message
    return
  }

  submittingKey.value = 'transfer'
  error.value = ''
  try {
    const { data } = await createTransfer({ toUserId, amount })
    prependTxn({
      txnType: 'TRANSFER',
      amount: -amount,
      counterpartLabel: `用户 ${toUserId}`,
      requestId: data?.requestId || '',
      status: data?.status || 'SUCCEEDED'
    })
    transferForm.value.toUserId = ''
    transferForm.value.amount = ''
    await reload()
  } catch (e) {
    error.value = e?.message || '转账失败'
  } finally {
    submittingKey.value = ''
  }
}

onMounted(reload)
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
  gap: 16px;
}

.wallet-summary-main,
.wallet-summary-side,
.wallet-action-card,
.wallet-feed-item,
.wallet-panel {
  display: grid;
  gap: 12px;
}

.wallet-summary-main,
.wallet-summary-side {
  padding: 22px 24px;
  border-radius: 12px;
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
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.wallet-state {
  padding: 24px 0;
}

.wallet-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  gap: 18px;
  align-items: start;
}

.wallet-action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.wallet-action-card {
  padding: 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 90%, var(--bg) 10%);
}

.wallet-action-card h2 {
  margin: 0;
  font-size: 1.05rem;
}

.wallet-feed {
  display: grid;
  gap: 12px;
}

.wallet-feed-item {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  padding: 14px 0;
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

@media (max-width: 960px) {
  .wallet-summary-strip,
  .wallet-layout,
  .wallet-action-grid {
    grid-template-columns: 1fr;
  }
}
</style>
