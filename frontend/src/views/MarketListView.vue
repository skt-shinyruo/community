<template>
  <div class="page market-list-page">
    <UiPageHeader>
      <template #title>统一市场</template>
      <template #subtitle>通过钱包托管购买虚拟商品和实物商品，按履约方式跟进订单。</template>
      <template #actions>
        <UiButton variant="secondary" :to="{ name: 'marketMyListings' }">我的出售</UiButton>
        <UiButton variant="secondary" :to="{ name: 'marketSellingOrders' }">出售订单</UiButton>
        <UiButton variant="secondary" :to="{ name: 'marketBuyingOrders' }">我的购买</UiButton>
        <UiButton :to="{ name: 'marketPublish' }">发布商品</UiButton>
      </template>
    </UiPageHeader>

    <section class="market-trust-strip" aria-label="交易保障">
      <span>钱包托管</span>
      <span>履约方式清晰</span>
      <span>争议可裁定</span>
    </section>

    <section class="market-catalog" aria-label="在售商品目录">
      <div class="market-toolbar">
        <div class="market-search">
          <UiInput
            v-model.trim="searchKeyword"
            type="search"
            placeholder="搜索已加载商品的标题、描述或卖家"
            aria-label="页内搜索商品"
            :disabled="loading && listings.length === 0"
          />
        </div>
        <span class="market-count">{{ countText }}</span>
      </div>

      <UiSkeleton v-if="loading && listings.length === 0" variant="list" :rows="4" label="正在加载市场商品" />
      <UiState v-else-if="error" variant="error" :title="error">
        <template #description>市场商品加载失败，可以重试或稍后再来。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" data-test="market-list-retry" @click="reload">重试</UiButton>
        </template>
      </UiState>

      <template v-else>
        <UiState v-if="state.listings.length === 0">
          暂无在售商品
          <template #description>发布商品后，买家通过钱包托管下单；虚拟商品按交付方式处理，实物商品按配送状态跟进。</template>
          <template #actions>
            <UiButton :to="{ name: 'marketPublish' }">发布商品</UiButton>
          </template>
        </UiState>

        <UiState v-else-if="visibleListings.length === 0">
          没有匹配「{{ searchKeyword }}」的商品
          <template #description>页内搜索只覆盖已加载的商品，可以换个关键词、清除搜索或继续加载更多。</template>
          <template #actions>
            <UiButton variant="secondary" data-test="market-search-clear" @click="clearSearch">清除搜索</UiButton>
          </template>
        </UiState>

        <div v-else class="market-listings">
          <RouterLink
            v-for="item in visibleListings"
            :key="item.listingId"
            class="market-listing"
            :to="{ name: 'marketDetail', params: { listingId: item.listingId } }"
          >
            <div class="market-listing-main">
              <h2 class="market-listing-title">{{ item.title }}</h2>
              <div class="market-listing-seller">卖家：{{ item.sellerLabel }}</div>
              <p class="market-listing-desc">{{ item.description }}</p>
            </div>
            <div class="market-listing-meta">
              <span class="market-chip">{{ item.goodsTypeLabel }}</span>
              <UiBadge :variant="item.statusVariant">{{ item.statusLabel }}</UiBadge>
              <span>{{ item.fulfillmentLabel }}</span>
              <span>{{ item.trustLabel }}</span>
              <span>{{ item.stockText }}</span>
              <strong class="market-listing-price">{{ item.unitPriceText }}</strong>
            </div>
          </RouterLink>
        </div>

        <p v-if="pageError" class="error market-inline-error" role="alert">{{ pageError }}</p>
        <div v-if="loadingMore || hasNext" class="market-feed-tail">
          <UiButton v-if="loadingMore" variant="ghost" disabled>
            <LoaderCircle :size="14" aria-hidden="true" class="market-feed-spinner" />
            正在加载…
          </UiButton>
          <UiButton v-else variant="secondary" data-test="market-load-more" @click="loadMore">加载更多</UiButton>
        </div>
        <p v-else-if="state.listings.length > 0" class="market-feed-end">已经到底了</p>
      </template>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { LoaderCircle } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiState from '../components/ui/UiState.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listMarketListings } from '../api/services/marketService'
import { buildMarketState, filterMarketListings, mergeMarketPage } from './marketState'

const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const pageError = ref('')
const listings = ref([])
const page = ref(0)
const hasNext = ref(false)
const searchKeyword = ref('')
const pageSize = 20

const state = computed(() => buildMarketState({ listings: listings.value }))
const visibleListings = computed(() => filterMarketListings(state.value.listings, searchKeyword.value))
const countText = computed(() => {
  const total = state.value.listings.length
  if (searchKeyword.value) return `匹配 ${visibleListings.value.length} / 已加载 ${total} 个商品`
  return `${total} 个商品`
})

function clearSearch() {
  searchKeyword.value = ''
}

async function reload() {
  loading.value = true
  error.value = ''
  pageError.value = ''
  try {
    const { data, hasNext: nextAvailable, page: loadedPage } = await listMarketListings({ page: 0, size: pageSize })
    listings.value = Array.isArray(data) ? data : []
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    error.value = e?.message || '加载市场失败'
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasNext.value) return
  loadingMore.value = true
  pageError.value = ''
  const targetPage = page.value + 1
  try {
    const { data, hasNext: nextAvailable, page: loadedPage } = await listMarketListings({
      page: targetPage,
      size: pageSize
    })
    listings.value = mergeMarketPage(listings.value, data, 'listingId')
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    pageError.value = e?.message || '加载更多商品失败'
  } finally {
    loadingMore.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.market-list-page {
  gap: var(--space-5);
}

.market-trust-strip {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.market-trust-strip span {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-full);
  background: var(--success-weak);
  border: 1px solid color-mix(in srgb, var(--success) 20%, var(--border) 80%);
  color: var(--text-1);
  font-size: var(--text-xs);
  font-weight: 700;
}

.market-catalog {
  display: grid;
  gap: var(--space-3);
}

.market-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.market-search {
  flex: 1;
  min-width: 0;
  max-width: 420px;
}

.market-count {
  margin-left: auto;
  font-size: 13px;
  color: var(--text-3);
  white-space: nowrap;
}

.market-listings {
  display: grid;
  gap: var(--space-3);
}

.market-listing {
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

.market-listing:hover {
  border-color: var(--border-strong);
  background: color-mix(in srgb, var(--surface) 55%, var(--surface-2));
}

.market-listing:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.market-listing-main {
  min-width: 0;
}

.market-listing-title {
  margin: 0 0 var(--space-1);
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.market-listing-seller {
  margin-bottom: var(--space-1);
  color: var(--text-3);
  font-size: var(--text-xs);
  font-weight: 700;
}

.market-listing-desc {
  margin: 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.market-listing-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2) var(--space-3);
  align-items: center;
  color: var(--text-2);
  font-size: 13px;
}

.market-chip {
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

.market-listing-price {
  font-size: var(--text-md);
  font-weight: 700;
  color: var(--text-1);
  white-space: nowrap;
}

.market-inline-error {
  margin: 0;
  font-size: var(--text-sm);
}

.market-feed-tail {
  display: flex;
  justify-content: center;
}

.market-feed-spinner {
  animation: market-feed-spin 0.8s linear infinite;
}

@keyframes market-feed-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .market-feed-spinner {
    animation: none;
  }
}

.market-feed-end {
  margin: 0;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 900px) {
  .market-listing {
    grid-template-columns: 1fr;
  }

  .market-listing-meta {
    justify-content: flex-start;
  }

  .market-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .market-search {
    max-width: none;
  }

  .market-count {
    margin-left: 0;
  }
}
</style>
