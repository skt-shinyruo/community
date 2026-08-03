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
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState } from './marketState'

const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const submittingId = ref('')
const disputes = ref([])
let loadGeneration = 0
let actionGeneration = 0

const state = computed(() => buildMarketState({ disputes: disputes.value }))
const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  [...auth.authorities].sort().join(',')
].join(':'))

function isCurrentLoad(generation, scope) {
  return generation === loadGeneration && scope === sessionScope.value && auth.authed && auth.isAdmin
}

function isCurrentAction(generation, scope, disputeId) {
  return generation === actionGeneration &&
    scope === sessionScope.value &&
    submittingId.value === disputeId &&
    auth.authed &&
    auth.isAdmin
}

async function reload() {
  if (!auth.authed || !auth.isAdmin) return
  const generation = ++loadGeneration
  const scope = sessionScope.value
  loading.value = true
  error.value = ''
  try {
    const { data } = await listAdminMarketDisputes()
    if (!isCurrentLoad(generation, scope)) return
    disputes.value = Array.isArray(data) ? data : []
  } catch (e) {
    if (!isCurrentLoad(generation, scope)) return
    error.value = e?.message || '加载争议失败'
  } finally {
    if (isCurrentLoad(generation, scope)) loading.value = false
  }
}

async function resolve(disputeId, action) {
  const normalizedDisputeId = normalizeOpaqueId(disputeId)
  if (!normalizedDisputeId || !auth.authed || !auth.isAdmin || submittingId.value !== '') return
  const generation = ++actionGeneration
  const scope = sessionScope.value
  submittingId.value = normalizedDisputeId
  try {
    await adminResolveMarketDispute(disputeId, action, { note: action === 'refund' ? 'refund' : 'release' })
    if (!isCurrentAction(generation, scope, normalizedDisputeId)) return
    await reload()
  } catch (e) {
    if (!isCurrentAction(generation, scope, normalizedDisputeId)) return
    error.value = e?.message || '处理争议失败'
  } finally {
    if (isCurrentAction(generation, scope, normalizedDisputeId)) submittingId.value = ''
  }
}

function resetForSession() {
  loadGeneration += 1
  actionGeneration += 1
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
  loadGeneration += 1
  actionGeneration += 1
})
</script>
