<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>统一市场</template>
      <template #subtitle>同一个入口，同时浏览虚拟商品和实物商品。钱包托管仍然是统一结算底座，前台只按商品类型展示不同的履约语义。</template>
      <template #actions>
        <RouterLink class="btn" :to="{ name: 'marketPublish' }">发布商品</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'marketBuyingOrders' }">我的购买</RouterLink>
      </template>
    </UiPageHeader>

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
        <template #description>第一个卖家可以直接从“发布商品”开始创建虚拟商品或实物商品。</template>
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
            <p>{{ item.description }}</p>
          </div>
          <div class="market-row-meta">
            <span class="market-pill">{{ item.goodsTypeLabel }}</span>
            <span>{{ item.goodsType === 'VIRTUAL' ? item.deliveryLabel : item.shipmentLabel }}</span>
            <span>{{ item.statusLabel }}</span>
            <span>{{ item.stockText }}</span>
            <strong>{{ item.unitPriceText }}</strong>
          </div>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listMarketListings } from '../api/services/marketService'
import { buildMarketState } from './marketState'

const loading = ref(false)
const error = ref('')
const listings = ref([])

const state = computed(() => buildMarketState({ listings: listings.value }))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listMarketListings()
    listings.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载市场失败'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>
