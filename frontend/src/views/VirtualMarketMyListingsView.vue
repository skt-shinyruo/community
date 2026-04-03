<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero market-hero--compact">
      <div>
        <span class="market-kicker">我的出售</span>
        <h1>把在售商品、库存入口和卖单动作放到一个工作面</h1>
        <p>这里直接展示当前账号的商品状态，预存库存商品可以继续进入库存页维护卡密。</p>
      </div>
      <div class="market-hero-actions">
        <RouterLink class="btn" :to="{ name: 'virtualMarketPublish' }">继续发布</RouterLink>
        <RouterLink class="btn secondary" :to="{ name: 'virtualMarketSellingOrders' }">查看卖单</RouterLink>
      </div>
    </section>

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
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
            <span class="market-pill">{{ item.deliveryLabel }}</span>
            <span>{{ item.statusLabel }}</span>
            <span>{{ item.stockText }}</span>
            <strong>{{ item.unitPriceText }}</strong>
          </div>
          <div class="market-inline-actions">
            <RouterLink
              v-if="item.deliveryMode === 'PRELOADED'"
              class="btn secondary"
              :to="{ name: 'virtualMarketInventory', params: { listingId: item.listingId } }"
            >
              库存管理
            </RouterLink>
            <RouterLink class="btn secondary" :to="{ name: 'virtualMarketSellingOrders' }">查看卖单</RouterLink>
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
import { listMyVirtualListings } from '../api/services/virtualMarketService'
import { buildVirtualMarketState } from './virtualMarketState'

const loading = ref(false)
const error = ref('')
const listings = ref([])

const state = computed(() => buildVirtualMarketState({ listings: listings.value }))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listMyVirtualListings()
    listings.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载我的出售商品失败'
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>
