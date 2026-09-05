<template>
  <div class="page market-my-listings-page">
    <nav aria-label="页面层级">
      <UiButton variant="ghost" :to="{ name: 'market' }">
        <ArrowLeft :size="16" aria-hidden="true" />
        返回市场
      </UiButton>
    </nav>

    <UiPageHeader>
      <template #title>我的出售</template>
      <template #subtitle>把在售商品、库存入口和卖单动作放到一个工作面。这里直接展示当前账号的商品状态；只有虚拟预存库存商品才需要继续进入库存页维护。</template>
      <template #actions>
        <UiButton :to="{ name: 'marketPublish' }">继续发布</UiButton>
        <UiButton variant="secondary" :to="{ name: 'marketSellingOrders' }">查看卖单</UiButton>
      </template>
    </UiPageHeader>

    <UiSkeleton v-if="loading" variant="list" :rows="3" label="正在加载我的出售商品" />
    <UiState v-else-if="error" variant="error" :title="error">
      <template #description>我的出售商品加载失败，可以重试或稍后再来。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" data-test="my-listings-retry" @click="reload">重试</UiButton>
      </template>
    </UiState>

    <section v-else class="my-listings-shell" aria-label="出售商品列表">
      <header class="my-listings-head">
        <div>
          <span class="my-listings-kicker">商品列表</span>
          <h2 class="my-listings-heading">先看商品状态，再决定进库存还是进卖单</h2>
        </div>
        <span class="my-listings-summary">{{ state.listings.length }} 个商品</span>
      </header>

      <UiState v-if="state.listings.length === 0">
        暂无出售商品
        <template #description>创建商品后，这里会显示交付方式、库存状态和管理入口。</template>
        <template #actions>
          <UiButton :to="{ name: 'marketPublish' }">发布商品</UiButton>
        </template>
      </UiState>

      <div v-else class="listing-rows">
        <article v-for="item in state.listings" :key="item.listingId" class="listing-row" data-test="my-listing-row">
          <div class="listing-row-main">
            <strong class="listing-row-title">{{ item.title }}</strong>
            <p class="listing-row-desc">{{ item.description }}</p>
          </div>
          <div class="listing-row-meta">
            <span class="listing-chip">{{ item.goodsTypeLabel }}</span>
            <UiBadge :variant="item.statusVariant">{{ item.statusLabel }}</UiBadge>
            <span>{{ item.fulfillmentLabel }}</span>
            <span>{{ item.trustLabel }}</span>
            <span>{{ item.stockText }}</span>
            <strong class="listing-row-price">{{ item.unitPriceText }}</strong>
          </div>
          <div class="listing-row-actions">
            <UiButton
              v-if="item.goodsType === 'VIRTUAL' && item.deliveryMode === 'PRELOADED'"
              variant="secondary"
              :to="{ name: 'marketInventory', params: { listingId: item.listingId } }"
            >
              库存管理
            </UiButton>
            <UiButton variant="secondary" :to="{ name: 'marketSellingOrders' }">查看卖单</UiButton>
          </div>
        </article>
      </div>

      <p v-if="pageError" class="error my-listings-inline-error" role="alert">{{ pageError }}</p>
      <div v-if="loadingMore || hasNext" class="my-listings-feed-tail">
        <UiButton v-if="loadingMore" variant="ghost" disabled>
          <LoaderCircle :size="14" aria-hidden="true" class="my-listings-feed-spinner" />
          正在加载…
        </UiButton>
        <UiButton v-else variant="secondary" data-test="my-listings-load-more" @click="loadMore">加载更多</UiButton>
      </div>
      <p v-else-if="state.listings.length > 0" class="my-listings-feed-end">已经到底了</p>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowLeft, LoaderCircle } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listMyMarketListings } from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState, mergeMarketPage } from './marketState'

const auth = useAuthStore()
const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const pageError = ref('')
const listings = ref([])
const page = ref(0)
const hasNext = ref(false)
const pageSize = 20
let requestGeneration = 0

const state = computed(() => buildMarketState({ listings: listings.value }))
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
    const { data, hasNext: nextAvailable, page: loadedPage } = await listMyMarketListings({ page: 0, size: pageSize })
    if (!isCurrentRequest(generation, scope)) return
    listings.value = Array.isArray(data) ? data : []
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    if (!isCurrentRequest(generation, scope)) return
    error.value = e?.message || '加载我的出售商品失败'
  } finally {
    if (isCurrentRequest(generation, scope)) loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasNext.value) return
  loadingMore.value = true
  pageError.value = ''
  const targetPage = page.value + 1
  const generation = ++requestGeneration
  const scope = sessionScope.value
  try {
    const { data, hasNext: nextAvailable, page: loadedPage } = await listMyMarketListings({
      page: targetPage,
      size: pageSize
    })
    if (!isCurrentRequest(generation, scope)) return
    listings.value = mergeMarketPage(listings.value, data, 'listingId')
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    if (!isCurrentRequest(generation, scope)) return
    pageError.value = e?.message || '加载更多出售商品失败'
  } finally {
    if (isCurrentRequest(generation, scope)) loadingMore.value = false
  }
}

watch(
  sessionScope,
  () => {
    requestGeneration += 1
    listings.value = []
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

<style scoped>
.market-my-listings-page {
  gap: var(--space-5);
}

.my-listings-shell {
  display: grid;
  gap: var(--space-3);
}

.my-listings-head {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  align-items: flex-end;
}

.my-listings-kicker {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--accent-weak);
  color: var(--accent-text);
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0;
}

.my-listings-heading {
  margin: var(--space-1) 0 0;
  font-size: var(--text-md);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.my-listings-summary {
  color: var(--text-1);
  font-size: var(--text-lg);
  font-weight: 700;
  white-space: nowrap;
}

.listing-rows {
  display: grid;
  gap: var(--space-3);
}

.listing-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-3) var(--space-4);
  align-items: center;
  padding: var(--space-4) var(--space-5);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.listing-row:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.listing-row-main {
  min-width: 0;
}

.listing-row-title {
  display: block;
  margin-bottom: var(--space-1);
  font-size: var(--text-md);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.listing-row-desc {
  margin: 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.listing-row-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2) var(--space-3);
  align-items: center;
  color: var(--text-2);
  font-size: 13px;
}

.listing-chip {
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

.listing-row-price {
  font-size: var(--text-md);
  font-weight: 700;
  color: var(--text-1);
  white-space: nowrap;
}

.listing-row-actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.my-listings-inline-error {
  margin: 0;
  font-size: var(--text-sm);
}

.my-listings-feed-tail {
  display: flex;
  justify-content: center;
}

.my-listings-feed-spinner {
  animation: my-listings-feed-spin 0.8s linear infinite;
}

@keyframes my-listings-feed-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .my-listings-feed-spinner {
    animation: none;
  }
}

.my-listings-feed-end {
  margin: 0;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 900px) {
  .listing-row {
    grid-template-columns: 1fr;
  }

  .listing-row-meta {
    justify-content: flex-start;
  }

  .my-listings-head {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }
}
</style>
