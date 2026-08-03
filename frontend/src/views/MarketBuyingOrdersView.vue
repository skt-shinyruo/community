<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>我的购买</template>
      <template #subtitle>托管、交付、确认、申诉一屏看清。这里只展示当前账号的买单，优先把请求号、状态和金额看清。</template>
    </UiPageHeader>

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载购买订单…</div>

    <section v-else class="market-list-shell">
      <header class="market-section-head">
        <div>
          <span class="market-kicker">买单列表</span>
          <h2>按订单查看托管和交付进度</h2>
        </div>
        <span class="market-summary">{{ state.orders.length }} 笔订单</span>
      </header>

      <UiState v-if="state.orders.length === 0">
        暂无购买订单
        <template #description>完成下单后，这里会显示请求号、状态和自动确认信息。</template>
      </UiState>

      <div v-else class="market-order-list">
        <RouterLink
          v-for="item in state.orders"
          :key="item.orderId"
          class="market-order-row"
          :to="{ name: 'marketOrderDetail', params: { orderId: item.orderId } }"
        >
            <div>
              <strong>{{ item.listingTitleSnapshot || `订单 #${item.orderId}` }}</strong>
              <p>请求号 {{ item.requestId || '-' }}</p>
              <p>{{ item.goodsTypeLabel }} · {{ item.statusLabel }} · {{ item.fundsLabel }}</p>
              <p>{{ item.fulfillmentLabel }} · {{ item.nextActionLabel }}</p>
            </div>
          <strong>{{ item.totalAmountText }}</strong>
        </RouterLink>
      </div>

      <div v-if="hasNext" class="market-inline-actions">
        <UiButton variant="secondary" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? '加载中…' : '加载更多' }}
        </UiButton>
      </div>
      <UiState v-if="pageError" variant="error">{{ pageError }}</UiState>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listBuyingMarketOrders } from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState, mergeMarketPage } from './marketState'

const auth = useAuthStore()
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const pageError = ref('')
const orders = ref([])
const page = ref(0)
const hasNext = ref(false)
const pageSize = 20
let requestGeneration = 0

const state = computed(() => buildMarketState({ orders: orders.value }))
const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))

function isCurrentRequest(generation, scope) {
  return generation === requestGeneration && scope === sessionScope.value
}

async function reload() {
  const generation = ++requestGeneration
  const scope = sessionScope.value
  loading.value = true
  loadingMore.value = false
  error.value = ''
  pageError.value = ''
  try {
    const { data, hasNext: nextAvailable, page: loadedPage } = await listBuyingMarketOrders({ page: 0, size: pageSize })
    if (!isCurrentRequest(generation, scope)) return
    orders.value = Array.isArray(data) ? data : []
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    if (!isCurrentRequest(generation, scope)) return
    error.value = e?.message || '加载购买订单失败'
  } finally {
    if (isCurrentRequest(generation, scope)) loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasNext.value) return
  loadingMore.value = true
  pageError.value = ''
  const targetPage = page.value + 1
  const generation = ++requestGeneration
  const scope = sessionScope.value
  try {
    const { data, hasNext: nextAvailable, page: loadedPage } = await listBuyingMarketOrders({
      page: targetPage,
      size: pageSize
    })
    if (!isCurrentRequest(generation, scope)) return
    orders.value = mergeMarketPage(orders.value, data, 'orderId')
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    if (!isCurrentRequest(generation, scope)) return
    pageError.value = e?.message || '加载更多购买订单失败'
  } finally {
    if (isCurrentRequest(generation, scope)) loadingMore.value = false
  }
}

watch(
  sessionScope,
  () => {
    requestGeneration += 1
    orders.value = []
    page.value = 0
    hasNext.value = false
    loading.value = false
    loadingMore.value = false
    error.value = ''
    pageError.value = ''
    if (auth.authed) reload()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  requestGeneration += 1
})
</script>
