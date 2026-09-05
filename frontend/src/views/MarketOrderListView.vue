<template>
  <div class="page market-orders-page">
    <UiPageHeader>
      <template #title>{{ policy.title }}</template>
      <template #subtitle>{{ policy.subtitle }}</template>
    </UiPageHeader>

    <UiTabs
      :model-value="side"
      :tabs="sideTabs"
      label="买卖订单"
      @update:model-value="onSideSelect"
    >
      <template #panel="{ active }">
        <section v-if="active" class="market-orders-shell" :aria-label="policy.listAriaLabel">
          <div v-if="!loading && !error" class="market-orders-meta">
            <span class="market-orders-count">{{ state.orders.length }} 笔订单</span>
          </div>

          <UiSkeleton v-if="loading" variant="list" :rows="4" :label="policy.loadingLabel" />
          <UiState v-else-if="error" variant="error" :title="error">
            <template #description>{{ policy.errorDescription }}</template>
            <template #actions>
              <UiButton variant="secondary" :disabled="loading" data-test="market-orders-retry" @click="reload">重试</UiButton>
            </template>
          </UiState>

          <template v-else>
            <UiState v-if="state.orders.length === 0">
              {{ policy.emptyTitle }}
              <template #description>{{ policy.emptyDescription }}</template>
              <template #actions>
                <UiButton :to="policy.emptyAction.to">{{ policy.emptyAction.label }}</UiButton>
              </template>
            </UiState>

            <div v-else class="market-orders-list">
              <RouterLink
                v-for="item in state.orders"
                :key="item.orderId"
                class="market-order-card"
                :to="{ name: 'marketOrderDetail', params: { orderId: item.orderId } }"
              >
                <div class="market-order-card-main">
                  <h2 class="market-order-card-title">{{ item.listingTitleSnapshot || `订单 #${item.orderId}` }}</h2>
                  <p class="market-order-card-line">请求号 {{ item.requestId || '-' }}</p>
                  <div class="market-order-card-meta">
                    <span class="market-order-chip">{{ item.goodsTypeLabel }}</span>
                    <UiBadge :variant="item.statusVariant">{{ item.statusLabel }}</UiBadge>
                    <span>{{ item.fundsLabel }}</span>
                    <span>{{ item.fulfillmentLabel }}</span>
                  </div>
                  <p class="market-order-card-next">下一步：{{ item.nextActionLabel }}</p>
                </div>
                <strong class="market-order-card-amount">{{ item.totalAmountText }}</strong>
              </RouterLink>
            </div>

            <p v-if="pageError" class="error market-orders-error" role="alert">{{ pageError }}</p>
            <div v-if="loadingMore || hasNext" class="market-orders-tail">
              <UiButton v-if="loadingMore" variant="ghost" disabled>
                <LoaderCircle :size="14" aria-hidden="true" class="market-orders-spinner" />
                正在加载…
              </UiButton>
              <UiButton v-else variant="secondary" data-test="market-orders-load-more" @click="loadMore">加载更多</UiButton>
            </div>
            <p v-else-if="state.orders.length > 0" class="market-orders-end">已经到底了</p>
          </template>
        </section>
      </template>
    </UiTabs>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { LoaderCircle } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiTabs from '../components/ui/UiTabs.vue'
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
    listAriaLabel: '购买订单列表',
    loadingLabel: '正在加载购买订单',
    emptyTitle: '暂无购买订单',
    emptyDescription: '完成下单后，这里会显示请求号、状态和自动确认信息。',
    emptyAction: Object.freeze({ label: '去市场逛逛', to: Object.freeze({ name: 'market' }) }),
    initialError: '加载购买订单失败',
    moreError: '加载更多购买订单失败',
    errorDescription: '购买订单加载失败，可以重试或稍后再来。'
  }),
  selling: Object.freeze({
    listOrders: listSellingMarketOrders,
    title: '我的出售订单',
    subtitle: '卖家关注的是交付和争议。卖家列表聚焦待交付和争议中的订单，不再只给静态说明。',
    listAriaLabel: '出售订单列表',
    loadingLabel: '正在加载出售订单',
    emptyTitle: '暂无出售订单',
    emptyDescription: '有买家下单后，这里会显示卖家要处理的订单状态和金额。',
    emptyAction: Object.freeze({ label: '查看我的出售', to: Object.freeze({ name: 'marketMyListings' }) }),
    initialError: '加载出售订单失败',
    moreError: '加载更多出售订单失败',
    errorDescription: '出售订单加载失败，可以重试或稍后再来。'
  })
})

// 域内 tabs 深链到既有 buying / selling 两条路由，不新增路由或 query 合同。
const SIDE_TABS = Object.freeze([
  Object.freeze({ value: 'buying', label: '买入', routeName: 'marketBuyingOrders' }),
  Object.freeze({ value: 'selling', label: '卖出', routeName: 'marketSellingOrders' })
])

const router = useRouter()
const auth = useAuthStore()
const policy = computed(() => ORDER_LIST_POLICIES[props.side] || ORDER_LIST_POLICIES.buying)
const sideTabs = SIDE_TABS.map(({ value, label }) => ({ value, label }))

const { state, loading, loadingMore, error, pageError, hasNext, reload, loadMore } = useMarketOrderList({
  auth,
  side: computed(() => props.side),
  listOrders: (params) => policy.value.listOrders(params),
  initialError: computed(() => policy.value.initialError),
  moreError: computed(() => policy.value.moreError)
})

function onSideSelect(side) {
  const target = SIDE_TABS.find((tab) => tab.value === side)
  if (!target || side === props.side) return
  router.push({ name: target.routeName })
}
</script>

<style scoped>
.market-orders-page {
  gap: var(--space-5);
}

.market-orders-shell {
  display: grid;
  gap: var(--space-3);
}

.market-orders-meta {
  display: flex;
  justify-content: flex-end;
}

.market-orders-count {
  font-size: 13px;
  color: var(--text-3);
}

.market-orders-list {
  display: grid;
  gap: var(--space-3);
}

.market-order-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  text-decoration: none;
  color: var(--text-1);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.market-order-card:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.market-order-card:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.market-order-card-main {
  min-width: 0;
  display: grid;
  gap: var(--space-1);
}

.market-order-card-title {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.market-order-card-line {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-xs);
  overflow-wrap: anywhere;
}

.market-order-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-3);
  align-items: center;
  color: var(--text-2);
  font-size: 13px;
}

.market-order-chip {
  display: inline-flex;
  align-items: center;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  border: 1px solid var(--border);
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--text-1);
}

.market-order-card-next {
  margin: 0;
  color: var(--text-2);
  font-size: 13px;
}

.market-order-card-amount {
  font-size: var(--text-md);
  font-weight: 700;
  color: var(--text-1);
  white-space: nowrap;
}

.market-orders-error {
  margin: 0;
  font-size: var(--text-sm);
}

.market-orders-tail {
  display: flex;
  justify-content: center;
}

.market-orders-spinner {
  animation: market-orders-spin 0.8s linear infinite;
}

@keyframes market-orders-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .market-orders-spinner {
    animation: none;
  }
}

.market-orders-end {
  margin: 0;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 900px) {
  .market-order-card {
    grid-template-columns: 1fr;
  }
}
</style>
