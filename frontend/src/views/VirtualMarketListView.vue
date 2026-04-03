<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero">
      <div>
        <span class="market-kicker">虚拟市场</span>
        <h1>用户卖家 · 固定价一口价</h1>
        <p>官方商城继续保留，这里只承接用户之间的虚拟商品托管交易。</p>
      </div>
      <div class="market-hero-actions">
        <RouterLink class="btn" :to="{ name: 'virtualMarketPublish' }">发布商品</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'virtualMarketBuyingOrders' }">我的购买</RouterLink>
      </div>
    </section>

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted">正在加载市场…</div>

    <section v-else class="market-list-shell">
      <header class="market-section-head">
        <div>
          <span class="market-kicker">在售列表</span>
          <h2>自动交付和手工交付分区展示</h2>
        </div>
        <span class="market-summary">{{ state.listings.length }} 个商品</span>
      </header>

      <UiEmpty v-if="state.listings.length === 0">
        暂无在售商品
        <template #description>第一个卖家可以直接从“发布商品”开始创建预存库存或手工交付商品。</template>
      </UiEmpty>

      <div v-else class="market-list">
        <RouterLink
          v-for="item in state.listings"
          :key="item.listingId"
          class="market-row"
          :to="{ name: 'virtualMarketDetail', params: { listingId: item.listingId } }"
        >
          <div class="market-row-main">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
          <div class="market-row-meta">
            <span class="market-pill">{{ item.deliveryLabel }}</span>
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
import UiEmpty from '../components/ui/UiEmpty.vue'
import { listVirtualListings } from '../api/services/virtualMarketService'
import { buildVirtualMarketState } from './virtualMarketState'

const loading = ref(false)
const error = ref('')
const listings = ref([])

const state = computed(() => buildVirtualMarketState({ listings: listings.value }))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listVirtualListings()
    listings.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载虚拟市场失败'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>
