<template>
  <div class="page growth-admin-page">
    <UiBreadcrumb />

    <UiCard class="growth-admin-shell">
      <UiPageHeader>
        <template #title>成长账户后台</template>
        <template #subtitle>把用户成长值、奖励余额和调账审计放进同一张操作桌，避免运营靠 SQL 兜底。</template>
      </UiPageHeader>

      <div class="growth-admin-search">
        <UiInput v-model.trim="searchForm.userId" placeholder="用户 ID" />
        <UiInput v-model.trim="searchForm.username" placeholder="用户名" />
        <UiInput v-model.trim="searchForm.email" placeholder="邮箱" />
        <UiButton :disabled="loading" @click="search">查询</UiButton>
      </div>

      <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>

      <div v-if="state.accounts.length > 0" class="growth-admin-account-grid">
        <UiCard v-for="account in state.accounts" :key="account.userId" flat class="growth-admin-account-card">
          <div class="growth-admin-account-head">
            <div>
              <div class="growth-admin-label">用户</div>
              <strong>{{ account.username }} #{{ account.userId }}</strong>
            </div>
            <UiTag>{{ account.riskLabel }}</UiTag>
          </div>
          <div class="growth-admin-account-meta">
            <span>成长值 {{ account.score }}</span>
            <span>等级 {{ account.level }}</span>
            <span>奖励 {{ account.rewardBalance }}</span>
            <span>冻结 {{ account.frozenBalance }}</span>
          </div>
        </UiCard>
      </div>

      <UiCard v-if="state.accounts.length > 0" flat class="growth-admin-adjust-card">
        <div class="growth-admin-adjust-head">
          <h2>手工调账</h2>
          <p>必须带原因和确认，不允许无痕修改。</p>
        </div>
        <div class="growth-admin-adjust-grid">
          <UiSelect
            v-model="adjustForm.assetType"
            class="growth-admin-adjust-select"
            aria-label="调账资产类型"
            :options="assetTypeOptions"
          />
          <UiInput v-model.trim="adjustForm.delta" placeholder="变更值，如 5 或 -3" />
          <UiInput v-model.trim="adjustForm.reason" placeholder="原因" />
          <UiCheckbox v-model="adjustForm.confirm" class="growth-admin-confirm" label="已确认" />
          <UiButton :disabled="adjusting" @click="submitAdjustment">{{ adjusting ? '提交中…' : '执行调账' }}</UiButton>
        </div>
      </UiCard>

      <div v-if="ledgers.length > 0" class="growth-admin-section">
        <h3>最近奖励流水</h3>
        <div class="growth-admin-list">
          <div v-for="ledger in ledgers" :key="ledger.id" class="growth-admin-row">
            <span>{{ ledger.eventType }}</span>
            <span>{{ ledger.delta }}</span>
            <span>{{ ledger.balanceAfter }}</span>
          </div>
        </div>
      </div>

      <div v-if="adjustments.length > 0" class="growth-admin-section">
        <h3>最近调账记录</h3>
        <div class="growth-admin-list">
          <div v-for="record in adjustments" :key="record.id" class="growth-admin-row">
            <span>{{ record.assetType }}</span>
            <span>{{ record.delta }}</span>
            <span>{{ record.beforeValue }} -> {{ record.afterValue }}</span>
          </div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  adjustAdminGrowth,
  listAdminGrowthAdjustments,
  listAdminGrowthLedgers,
  searchAdminGrowthUser
} from '../api/services/adminGrowthService'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiCheckbox from '../components/ui/UiCheckbox.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiTag from '../components/ui/UiTag.vue'
import { buildGrowthAdminState } from './growthAdminState'

const loading = ref(false)
const adjusting = ref(false)
const error = ref('')
const account = ref(null)
const ledgers = ref([])
const adjustments = ref([])
const assetTypeOptions = [
  { label: '奖励余额', value: 'REWARD_BALANCE' },
  { label: '成长值', value: 'SCORE' }
]

const searchForm = ref({
  userId: '',
  username: '',
  email: ''
})

const adjustForm = ref({
  assetType: 'REWARD_BALANCE',
  delta: '',
  reason: '',
  confirm: false
})

const state = computed(() =>
  buildGrowthAdminState({
    accounts: account.value ? [account.value] : [],
    items: [],
    orders: []
  })
)

async function search() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await searchAdminGrowthUser(searchForm.value)
    account.value = data
    if (data?.userId) {
      const [ledgerResp, adjustmentResp] = await Promise.all([
        listAdminGrowthLedgers(data.userId),
        listAdminGrowthAdjustments(data.userId)
      ])
      ledgers.value = ledgerResp.data
      adjustments.value = adjustmentResp.data
    } else {
      ledgers.value = []
      adjustments.value = []
    }
  } catch (e) {
    error.value = e?.message || '查询失败'
  } finally {
    loading.value = false
  }
}

async function submitAdjustment() {
  if (!account.value?.userId) return
  adjusting.value = true
  error.value = ''
  try {
    const { data } = await adjustAdminGrowth({
      targetUserId: account.value.userId,
      assetType: adjustForm.value.assetType,
      delta: Number(adjustForm.value.delta || 0),
      reason: adjustForm.value.reason,
      confirm: adjustForm.value.confirm
    })
    account.value = data
    const [ledgerResp, adjustmentResp] = await Promise.all([
      listAdminGrowthLedgers(account.value.userId),
      listAdminGrowthAdjustments(account.value.userId)
    ])
    ledgers.value = ledgerResp.data
    adjustments.value = adjustmentResp.data
  } catch (e) {
    error.value = e?.message || '调账失败'
  } finally {
    adjusting.value = false
  }
}
</script>

<style scoped>
.growth-admin-page {
  max-width: 1080px;
  margin: 0 auto;
  gap: var(--space-5);
}

.growth-admin-shell,
.growth-admin-account-card,
.growth-admin-adjust-card {
  display: grid;
  gap: 16px;
}

.growth-admin-search,
.growth-admin-adjust-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  align-items: center;
}

.growth-admin-account-grid,
.growth-admin-list,
.growth-admin-section {
  display: grid;
  gap: 12px;
}

.growth-admin-adjust-select {
  width: 100%;
}

.growth-admin-adjust-select :deep(.ui-select-trigger) {
  height: var(--control-height);
}

.growth-admin-confirm :deep(.ui-checkbox-copy) {
  color: var(--text-2);
  font-size: 13px;
  font-weight: 700;
}

.growth-admin-account-head,
.growth-admin-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.growth-admin-account-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--text-2);
}

.growth-admin-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.growth-admin-adjust-head h2,
.growth-admin-section h3 {
  margin: 0;
}

.growth-admin-adjust-head p {
  margin: 6px 0 0;
  color: var(--text-2);
}

.growth-admin-confirm {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  color: var(--text-2);
}

.growth-admin-row {
  padding: 12px 14px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
}

@media (max-width: 900px) {
  .growth-admin-search,
  .growth-admin-adjust-grid {
    grid-template-columns: 1fr;
  }
}
</style>
