<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>我的出售</template>
      <template #subtitle>把在售商品、库存入口和卖单动作放到一个工作面。这里直接展示当前账号的商品状态；只有虚拟预存库存商品才需要继续进入库存页维护。</template>
      <template #actions>
        <RouterLink class="btn" :to="{ name: 'marketPublish' }">继续发布</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'marketSellingOrders' }">查看卖单</RouterLink>
      </template>
    </UiPageHeader>

    <UiEmpty v-if="error" variant="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted">正在加载我的出售商品…</div>

    <section v-else class="market-list-shell">
      <header class="market-section-head">
        <div>
          <span class="market-kicker">商品列表</span>
          <h2>先看商品状态，再决定进库存还是进卖单</h2>
        </div>
        <span class="market-summary">{{ state.listings.length }} 个商品</span>
      </header>

      <UiEmpty v-if="state.listings.length === 0">
        暂无出售商品
        <template #description>创建商品后，这里会显示交付方式、库存状态和管理入口。</template>
      </UiEmpty>

      <div v-else class="market-list">
        <article v-for="item in state.listings" :key="item.listingId" class="market-row">
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
          <div class="market-inline-actions">
            <RouterLink
              v-if="item.goodsType === 'VIRTUAL' && item.deliveryMode === 'PRELOADED'"
              class="btn secondary"
              :to="{ name: 'marketInventory', params: { listingId: item.listingId } }"
            >
              库存管理
            </RouterLink>
            <RouterLink class="btn secondary" :to="{ name: 'marketSellingOrders' }">查看卖单</RouterLink>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { listMyMarketListings } from '../api/services/marketService'
import { buildMarketState } from './marketState'

const loading = ref(false)
const error = ref('')
const listings = ref([])

const state = computed(() => buildMarketState({ listings: listings.value }))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listMyMarketListings()
    listings.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载我的出售商品失败'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>
