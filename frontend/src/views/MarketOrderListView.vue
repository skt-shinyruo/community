<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>{{ policy.title }}</template>
      <template #subtitle>{{ policy.subtitle }}</template>
    </UiPageHeader>

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">{{ policy.loadingText }}</div>

    <section v-else class="market-list-shell">
      <header class="market-section-head">
        <div>
          <span class="market-kicker">{{ policy.kicker }}</span>
          <h2>{{ policy.sectionTitle }}</h2>
        </div>
        <span class="market-summary">{{ state.orders.length }} 笔订单</span>
      </header>

      <UiState v-if="state.orders.length === 0">
        {{ policy.emptyTitle }}
        <template #description>{{ policy.emptyDescription }}</template>
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
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listBuyingMarketOrders, listSellingMarketOrders } from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { useMarketOrderList } from './market/useMarketOrderList'

const props = defineProps({
  side: {
    type: String,
    required: true,
    validator: (value) => value === 'buying' || value === 'selling'
  }
})

const ORDER_LIST_POLICIES = Object.freeze({
  buying: Object.freeze({
    listOrders: listBuyingMarketOrders,
    title: '我的购买',
    subtitle: '托管、交付、确认、申诉一屏看清。这里只展示当前账号的买单，优先把请求号、状态和金额看清。',
    loadingText: '正在加载购买订单…',
    kicker: '买单列表',
    sectionTitle: '按订单查看托管和交付进度',
    emptyTitle: '暂无购买订单',
    emptyDescription: '完成下单后，这里会显示请求号、状态和自动确认信息。',
    initialError: '加载购买订单失败',
    moreError: '加载更多购买订单失败'
  }),
  selling: Object.freeze({
    listOrders: listSellingMarketOrders,
    title: '我的出售订单',
    subtitle: '卖家关注的是交付和争议。卖家列表聚焦待交付和争议中的订单，不再只给静态说明。',
    loadingText: '正在加载出售订单…',
    kicker: '卖单列表',
    sectionTitle: '从订单详情继续交付和处理申诉',
    emptyTitle: '暂无出售订单',
    emptyDescription: '有买家下单后，这里会显示卖家要处理的订单状态和金额。',
    initialError: '加载出售订单失败',
    moreError: '加载更多出售订单失败'
  })
})

const policy = ORDER_LIST_POLICIES[props.side]
const auth = useAuthStore()
const { state, loading, loadingMore, error, pageError, hasNext, loadMore } = useMarketOrderList({
  auth,
  listOrders: policy.listOrders,
  initialError: policy.initialError,
  moreError: policy.moreError
})
</script>
