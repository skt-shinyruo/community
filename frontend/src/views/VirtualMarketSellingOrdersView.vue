<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero market-hero--compact">
      <div>
        <span class="market-kicker">我的出售订单</span>
        <h1>卖家关注的是交付和争议</h1>
        <p>卖家列表聚焦待交付和争议中的订单，不再只给静态说明。</p>
      </div>
    </section>

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted">正在加载出售订单…</div>

    <section v-else class="market-list-shell">
      <header class="market-section-head">
        <div>
          <span class="market-kicker">卖单列表</span>
          <h2>从订单详情继续交付和处理申诉</h2>
        </div>
        <span class="market-summary">{{ state.orders.length }} 笔订单</span>
      </header>

      <UiEmpty v-if="state.orders.length === 0">
        暂无出售订单
        <template #description>有买家下单后，这里会显示卖家要处理的订单状态和金额。</template>
      </UiEmpty>

      <div v-else class="market-order-list">
        <RouterLink
          v-for="item in state.orders"
          :key="item.orderId"
          class="market-order-row"
          :to="{ name: 'virtualMarketOrderDetail', params: { orderId: item.orderId } }"
        >
          <div>
            <strong>{{ item.listingTitleSnapshot || `订单 #${item.orderId}` }}</strong>
            <p>请求号 {{ item.requestId || '-' }}</p>
            <p>{{ item.statusLabel }} · {{ item.autoConfirmText }}</p>
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
import { listSellingVirtualOrders } from '../api/services/virtualMarketService'
import { buildVirtualMarketState } from './virtualMarketState'

const loading = ref(false)
const error = ref('')
const orders = ref([])

const state = computed(() => buildVirtualMarketState({ orders: orders.value }))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listSellingVirtualOrders()
    orders.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载出售订单失败'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>
