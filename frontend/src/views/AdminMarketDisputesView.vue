<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>争议裁定</template>
      <template #subtitle>管理员只处理最终裁定，不处理普通卖家动作。这里专门承接卖家拒绝后的争议收口。</template>
    </UiPageHeader>

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载争议…</div>

    <UiState v-else-if="state.disputes.length === 0">
      暂无待处理争议
      <template #description>当前没有等待管理员裁定的市场争议。</template>
    </UiState>

    <div v-else class="market-admin-list">
      <article v-for="item in state.disputes" :key="item.disputeId" class="market-admin-row">
        <div>
          <strong>争议 #{{ item.disputeId }}</strong>
          <p>{{ item.goodsTypeLabel }} · {{ item.reason }} · {{ item.statusLabel }}</p>
          <p>{{ item.nextActionLabel }}</p>
          <p v-if="item.buyerNote || item.sellerNote">买家说明：{{ item.buyerNote || '未填写' }} · 卖家说明：{{ item.sellerNote || '未填写' }}</p>
        </div>
        <div class="market-inline-actions">
          <UiButton variant="secondary" :disabled="submittingId !== ''" @click="resolve(item.disputeId, 'refund')">
            退回买家
          </UiButton>
          <UiButton :disabled="submittingId !== ''" @click="resolve(item.disputeId, 'release')">
            放款卖家
          </UiButton>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { adminResolveMarketDispute, listAdminMarketDisputes } from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { createLatestRequestTracker } from '../utils/latestRequest'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState } from './marketState'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const submittingId = ref('')
const disputes = ref([])

const state = computed(() => buildMarketState({ disputes: disputes.value }))
const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  [...auth.authorities].sort().join(',')
].join(':'))
const loadTracker = createLatestRequestTracker({ getScope: () => sessionScope.value })
const actionTracker = createLatestRequestTracker({ getScope: () => sessionScope.value })

function isCurrentLoad(requestHandle) {
  return loadTracker.isCurrent(requestHandle) && auth.authed && auth.isAdmin
}

function isCurrentAction(requestHandle, disputeId) {
  return actionTracker.isCurrent(requestHandle) &&
    submittingId.value === disputeId &&
    auth.authed &&
    auth.isAdmin
}

async function reload() {
  if (!auth.authed || !auth.isAdmin) return
  const requestHandle = loadTracker.begin()
  loading.value = true
  error.value = ''
  try {
    const { data } = await listAdminMarketDisputes()
    if (!isCurrentLoad(requestHandle)) return
    disputes.value = Array.isArray(data) ? data : []
  } catch (e) {
    if (!isCurrentLoad(requestHandle)) return
    error.value = e?.message || '加载争议失败'
  } finally {
    if (isCurrentLoad(requestHandle)) loading.value = false
  }
}

async function resolve(disputeId, action) {
  const normalizedDisputeId = normalizeOpaqueId(disputeId)
  if (!normalizedDisputeId || !auth.authed || !auth.isAdmin || submittingId.value !== '') return
  const requestHandle = actionTracker.begin()
  submittingId.value = normalizedDisputeId
  try {
    await adminResolveMarketDispute(disputeId, action, { note: action === 'refund' ? 'refund' : 'release' })
    if (!isCurrentAction(requestHandle, normalizedDisputeId)) return
    await reload()
  } catch (e) {
    if (!isCurrentAction(requestHandle, normalizedDisputeId)) return
    error.value = e?.message || '处理争议失败'
  } finally {
    if (isCurrentAction(requestHandle, normalizedDisputeId)) submittingId.value = ''
  }
}

function resetForSession() {
  loadTracker.invalidate()
  actionTracker.invalidate()
  loading.value = false
  error.value = ''
  submittingId.value = ''
  disputes.value = []
  if (auth.authed && auth.isAdmin) reload()
}

watch(sessionScope, resetForSession)
onMounted(() => {
  if (auth.authed && auth.isAdmin) reload()
})
onBeforeUnmount(() => {
  loadTracker.invalidate()
  actionTracker.invalidate()
})
</script>

<style scoped>
.market-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.market-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.market-admin-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.market-admin-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  text-decoration: none;
  color: inherit;
  transition: background-color 160ms ease, border-color 160ms ease;
}

.market-admin-row:hover {
  background: color-mix(in srgb, var(--surface) 88%, var(--surface-2) 12%);
  border-color: var(--border-strong);
}

.market-admin-row strong {
  display: block;
  margin-bottom: 4px;
}

.market-admin-row p {
  margin: 0;
  color: var(--text-2);
}

@media (max-width: 900px) {
  .market-admin-row {
    grid-template-columns: 1fr;
  }
}
</style>
