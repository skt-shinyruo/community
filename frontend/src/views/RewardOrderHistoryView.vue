<template>
  <div class="page reward-orders-page">
    <UiBreadcrumb />

    <div v-if="surface.showHeader" class="reward-orders-head">
      <UiPageHeader>
        <template #title>{{ surface.title }}</template>
        <template #subtitle>{{ surface.subtitle }}</template>
        <template #actions>
          <div class="reward-orders-actions">
            <UiButton variant="secondary" :disabled="loading" @click="reload">
              {{ loading ? '刷新中…' : '刷新' }}
            </UiButton>
            <UiButton v-if="surface.canReturnToShop" variant="secondary" @click="goShop">返回商城</UiButton>
          </div>
        </template>
      </UiPageHeader>
    </div>

    <UiEmpty v-if="surface.bodyState === 'error'" type="error">{{ error }}</UiEmpty>
    <UiEmpty v-else-if="surface.bodyState === 'empty'">
      暂无兑换记录
      <template #description>兑换成功后，自动发放和人工履约都会在这里保留一条状态记录。</template>
    </UiEmpty>
    <div v-else-if="surface.bodyState === 'loading'" class="muted reward-orders-state">正在加载兑换记录…</div>

    <UiCard v-else class="reward-orders-list">
      <article v-for="order in state.orders" :key="order.id" class="reward-order-row">
        <div class="reward-order-main">
          <div class="reward-order-title-row">
            <strong>{{ order.itemNameSnapshot }}</strong>
            <UiTag>{{ order.statusLabel }}</UiTag>
          </div>
          <p>{{ order.itemDescSnapshot || '暂无说明' }}</p>
        </div>
        <div class="reward-order-meta">
          <span>{{ order.fulfillmentLabel }}</span>
          <span>{{ order.costBalanceSnapshot }} 积分</span>
        </div>
      </article>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listRewardOrders } from '../api/services/rewardShopService'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiTag from '../components/ui/UiTag.vue'
import { buildRewardShopState } from './rewardShopState'
import { buildRewardOrderHistorySurface } from './rewardOrderHistorySurface'

const router = useRouter()

const loading = ref(false)
const error = ref('')
const orders = ref([])

const state = computed(() =>
  buildRewardShopState({
    rewardBalance: 0,
    items: [],
    orders: orders.value
  })
)

const surface = computed(() =>
  buildRewardOrderHistorySurface({
    loading: loading.value,
    error: error.value,
    orders: state.value.orders
  })
)

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listRewardOrders()
    orders.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载兑换记录失败'
  } finally {
    loading.value = false
  }
}

function goShop() {
  router.push({ name: 'rewardShop' })
}

onMounted(reload)
</script>

<style scoped>
.reward-orders-page {
  max-width: 920px;
  margin: 0 auto;
  gap: var(--space-5);
}

.reward-orders-head {
  padding: 0 0 8px;
  margin-bottom: 4px;
}

.reward-orders-list {
  display: grid;
  gap: var(--space-4);
}

.reward-orders-list,
.reward-orders-head {
  width: 100%;
}

.reward-orders-head {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.reward-orders-head :deep(.page-header) {
  gap: 0;
}

.reward-orders-head :deep(.page-header-subtitle) {
  margin: 4px 0 0;
}

.reward-orders-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.reward-orders-state {
  padding: 24px 0;
}

.reward-order-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 18px 0;
  border-bottom: 1px solid var(--border);
}

.reward-order-row:first-child {
  padding-top: 0;
}

.reward-order-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.reward-order-main {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.reward-order-title-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.reward-order-main p {
  margin: 0;
  color: var(--text-2);
}

.reward-order-meta {
  display: grid;
  gap: 4px;
  text-align: right;
  color: var(--text-3);
  font-size: 13px;
  white-space: nowrap;
}

@media (max-width: 720px) {
  .reward-order-row {
    flex-direction: column;
    align-items: stretch;
  }

  .reward-order-meta {
    text-align: left;
  }
}
</style>
