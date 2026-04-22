<template>
  <div class="page wallet-admin-page">
    <UiBreadcrumb />

    <UiCard class="wallet-admin-shell">
      <UiPageHeader>
        <template #title>钱包后台</template>
        <template #subtitle>冻结钱包、回滚交易，并为后续审计收口保留统一操作面。</template>
      </UiPageHeader>

      <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>

      <div class="wallet-admin-grid">
        <section class="wallet-admin-card">
          <h2>冻结钱包</h2>
          <p>对风险用户做人工止损，避免继续转账、提现或消费。</p>
          <UiInput v-model.trim="freezeForm.userId" placeholder="目标用户 ID" />
          <UiInput v-model.trim="freezeForm.reason" placeholder="冻结原因" />
          <UiButton :disabled="submittingKey !== ''" @click="submitFreeze">
            {{ submittingKey === 'freeze' ? '提交中…' : '执行冻结' }}
          </UiButton>
        </section>

        <section class="wallet-admin-card">
          <h2>回滚交易</h2>
          <p>只追加反向交易，不直接篡改原始余额。</p>
          <UiInput v-model.trim="reverseForm.txnRef" placeholder="交易请求号，如 transfer:req-1" />
          <UiInput v-model.trim="reverseForm.reason" placeholder="回滚原因" />
          <UiButton :disabled="submittingKey !== ''" @click="submitReverse">
            {{ submittingKey === 'reverse' ? '提交中…' : '执行回滚' }}
          </UiButton>
        </section>
      </div>

      <section class="wallet-admin-log">
        <div class="wallet-admin-log-head">
          <h3>本次会话操作</h3>
          <span>第一版先展示本地操作摘要，完整审计查询后续接入。</span>
        </div>

        <UiEmpty v-if="actions.length === 0">
          暂无操作记录
          <template #description>成功提交冻结或回滚后，这里会追加一条当前会话内的摘要。</template>
        </UiEmpty>

        <div v-else class="wallet-admin-log-list">
          <article v-for="item in actions" :key="item.key" class="wallet-admin-log-item">
            <strong>{{ item.label }}</strong>
            <span>{{ item.text }}</span>
          </article>
        </div>
      </section>
    </UiCard>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { freezeWallet, reverseWalletTxn } from '../api/services/walletService'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { normalizeOpaqueId } from '../utils/opaqueId'

const error = ref('')
const submittingKey = ref('')
const actions = ref([])

const freezeForm = ref({
  userId: '',
  reason: ''
})

const reverseForm = ref({
  txnRef: '',
  reason: ''
})

function pushAction(label, text) {
  actions.value = [
    {
      key: `${Date.now()}-${actions.value.length}`,
      label,
      text
    },
    ...actions.value
  ].slice(0, 10)
}

async function submitFreeze() {
  const userId = normalizeOpaqueId(freezeForm.value.userId)
  const reason = String(freezeForm.value.reason || '').trim()
  if (!userId) {
    error.value = '请输入有效的目标用户 ID'
    return
  }
  if (!reason) {
    error.value = '请输入冻结原因'
    return
  }

  submittingKey.value = 'freeze'
  error.value = ''
  try {
    await freezeWallet({ userId, reason })
    pushAction('已冻结钱包', `用户 ${userId} · ${reason}`)
    freezeForm.value.userId = ''
    freezeForm.value.reason = ''
  } catch (e) {
    error.value = e?.message || '冻结失败'
  } finally {
    submittingKey.value = ''
  }
}

async function submitReverse() {
  const txnRef = String(reverseForm.value.txnRef || '').trim()
  const reason = String(reverseForm.value.reason || '').trim()
  if (!txnRef) {
    error.value = '请输入交易请求号'
    return
  }
  if (!reason) {
    error.value = '请输入回滚原因'
    return
  }

  submittingKey.value = 'reverse'
  error.value = ''
  try {
    await reverseWalletTxn({ txnRef, reason })
    pushAction('已提交回滚', `${txnRef} · ${reason}`)
    reverseForm.value.txnRef = ''
    reverseForm.value.reason = ''
  } catch (e) {
    error.value = e?.message || '回滚失败'
  } finally {
    submittingKey.value = ''
  }
}
</script>

<style scoped>
.wallet-admin-page {
  max-width: 1080px;
  margin: 0 auto;
  gap: var(--space-5);
}

.wallet-admin-shell,
.wallet-admin-card,
.wallet-admin-log,
.wallet-admin-log-item {
  display: grid;
  gap: 14px;
}

.wallet-admin-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.wallet-admin-card {
  padding: 18px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 92%, var(--bg) 8%);
}

.wallet-admin-card h2,
.wallet-admin-log-head h3 {
  margin: 0;
}

.wallet-admin-card p,
.wallet-admin-log-head span,
.wallet-admin-log-item span {
  color: var(--text-2);
  line-height: 1.6;
}

.wallet-admin-log-list {
  display: grid;
  gap: 12px;
}

.wallet-admin-log-item {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 94%, var(--bg) 6%);
}

@media (max-width: 860px) {
  .wallet-admin-grid {
    grid-template-columns: 1fr;
  }
}
</style>
