<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>统一市场</template>
      <template #subtitle>通过钱包托管购买虚拟商品和实物商品，按履约方式跟进订单。</template>
      <template #actions>
        <RouterLink class="btn secondary" :to="{ name: 'marketBuyingOrders' }">我的购买</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'marketSellingOrders' }">我的出售</RouterLink>
        <RouterLink class="btn" :to="{ name: 'marketPublish' }">发布商品</RouterLink>
      </template>
    </UiPageHeader>

    <section class="market-trust-strip" aria-label="交易保障">
      <span>钱包托管</span>
      <span>履约方式清晰</span>
      <span>争议可裁定</span>
    </section>

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载市场…</div>

    <section v-else class="market-list-shell">
      <header class="market-section-head">
        <div>
          <span class="market-kicker">在售列表</span>
          <h2>按商品类型显示交付或发货信息</h2>
        </div>
        <span class="market-summary">{{ state.listings.length }} 个商品</span>
      </header>

      <UiState v-if="state.listings.length === 0">
        暂无在售商品
        <template #description>发布商品后，买家通过钱包托管下单；虚拟商品按交付方式处理，实物商品按配送状态跟进。</template>
      </UiState>

      <div v-else class="market-list">
        <RouterLink
          v-for="item in state.listings"
          :key="item.listingId"
          class="market-row"
          :to="{ name: 'marketDetail', params: { listingId: item.listingId } }"
        >
          <div class="market-row-main">
            <strong>{{ item.title }}</strong>
            <div class="market-row-seller">卖家：{{ item.sellerLabel }}</div>
            <p>{{ item.description }}</p>
          </div>
          <div class="market-row-meta">
            <span class="market-pill">{{ item.goodsTypeLabel }}</span>
            <span>{{ item.fulfillmentLabel }}</span>
            <span>{{ item.trustLabel }}</span>
            <span>{{ item.statusLabel }}</span>
            <span>{{ item.stockText }}</span>
            <strong>{{ item.unitPriceText }}</strong>
          </div>
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
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listMarketListings } from '../api/services/marketService'
import { buildMarketState, mergeMarketPage } from './marketState'

const loading = ref(false)
const loadingMore = ref(false)
const error = ref('')
const pageError = ref('')
const listings = ref([])
const page = ref(0)
const hasNext = ref(false)
const pageSize = 20

const state = computed(() => buildMarketState({ listings: listings.value }))

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
