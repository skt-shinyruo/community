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
          <p v-else-if="feedCapped" class="wallet-feed-end" data-test="wallet-feed-capped">已显示最近 {{ WALLET_FEED_MAX_LIMIT }} 条流水</p>
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
import { computed } from 'vue'
import { LoaderCircle } from 'lucide-vue-next'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  WALLET_FEED_MAX_LIMIT,
  buildWalletState
} from './walletState'
import { useWalletWorkflow } from './useWalletWorkflow'

const { model, actions, confirmation } = useWalletWorkflow()

const {
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
  loadingMore,
  feedError,
  txnsLoaded,
  hasMoreFeed,
  feedExhausted,
  feedCapped
} = model

const {
  reload,
  loadMore,
  closeConfirmation,
  runConfirmation,
  submitRecharge,
  requestWithdrawal,
  requestTransfer
} = actions

const state = computed(() =>
  buildWalletState({
    summary: summary.value,
    txns: txns.value
  })
)
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
