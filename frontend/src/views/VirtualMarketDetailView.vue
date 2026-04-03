<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted">正在加载商品详情…</div>

    <div v-else class="market-detail-shell">
      <section class="market-hero market-hero--detail">
        <div>
          <span class="market-kicker">{{ detail.deliveryLabel }}</span>
          <h1>{{ detail.title || '虚拟商品详情' }}</h1>
          <p>{{ detail.description || '查看价格、库存和交付方式。' }}</p>
        </div>
        <div class="market-price-box">
          <strong>{{ detail.unitPriceText }}</strong>
          <span>{{ detail.statusLabel }}</span>
          <span>{{ detail.stockText }}</span>
        </div>
      </section>

      <section class="market-split">
        <UiCard class="market-panel">
          <UiPageHeader>
            <template #title>交易动作</template>
            <template #subtitle>第一版先保留固定价下单，数量和托管逻辑都按后端真实接口执行。</template>
          </UiPageHeader>

          <div class="market-form-grid">
            <label class="market-field">
              <span>购买数量</span>
              <UiInput v-model.number="quantity" type="number" min="1" placeholder="输入购买数量" />
            </label>
            <UiButton :disabled="submitting" @click="submitOrder">
              {{ submitting ? '下单中…' : '立即托管下单' }}
            </UiButton>
          </div>
        </UiCard>

        <UiCard class="market-panel">
          <UiPageHeader>
            <template #title>交易说明</template>
            <template #subtitle>这里展示的是用户卖家商品，不会和官方商城的兑换流混在一起。</template>
          </UiPageHeader>

          <ul class="market-bullets">
            <li>交付方式：{{ detail.deliveryLabel }}</li>
            <li>商品状态：{{ detail.statusLabel }}</li>
            <li>库存状态：{{ detail.stockText }}</li>
          </ul>
        </UiCard>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { createVirtualOrder, getVirtualListingDetail } from '../api/services/virtualMarketService'
import { buildVirtualMarketState } from './virtualMarketState'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const listing = ref({})
const quantity = ref(1)

const detail = computed(() => buildVirtualMarketState({ listings: [listing.value] }).listings[0] || {})

function buildRequestId() {
  if (globalThis.crypto?.randomUUID) return `market:order:${globalThis.crypto.randomUUID()}`
  return `market:order:${Date.now()}`
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await getVirtualListingDetail(route.params.listingId)
    listing.value = data || {}
  } catch (e) {
    error.value = e?.message || '加载商品失败'
  } finally {
    loading.value = false
  }
}

async function submitOrder() {
  submitting.value = true
  error.value = ''
  try {
    await createVirtualOrder({
      requestId: buildRequestId(),
      listingId: Number(route.params.listingId),
      quantity: Math.max(1, Number(quantity.value || 1))
    })
    await loadDetail()
  } catch (e) {
    error.value = e?.message || '下单失败'
  } finally {
    submitting.value = false
  }
}

onMounted(loadDetail)
</script>
