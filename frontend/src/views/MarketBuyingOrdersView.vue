<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>我的购买</template>
      <template #subtitle>托管、交付、确认、申诉一屏看清。这里只展示当前账号的买单，优先把请求号、状态和金额看清。</template>
    </UiPageHeader>

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted">正在加载购买订单…</div>

    <section v-else class="market-list-shell">
      <header class="market-section-head">
        <div>
          <span class="market-kicker">买单列表</span>
          <h2>按订单查看托管和交付进度</h2>
        </div>
        <span class="market-summary">{{ state.orders.length }} 笔订单</span>
      </header>

      <UiEmpty v-if="state.orders.length === 0">
        暂无购买订单
        <template #description>完成下单后，这里会显示请求号、状态和自动确认信息。</template>
      </UiEmpty>

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
            <p>{{ item.goodsTypeLabel }} · {{ item.statusLabel }} · {{ item.autoConfirmText }}</p>
          </div>
          <strong>{{ item.totalAmountText }}</strong>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listBuyingMarketOrders } from '../api/services/marketService'
import { buildMarketState } from './marketState'

const loading = ref(false)
const error = ref('')
const orders = ref([])

const state = computed(() => buildMarketState({ orders: orders.value }))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listBuyingMarketOrders()
    orders.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载购买订单失败'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>
