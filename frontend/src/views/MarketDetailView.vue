<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
    <div v-else-if="loading" class="muted">正在加载商品详情…</div>

    <div v-else class="market-detail-shell">
      <section class="market-hero market-hero--detail">
        <div>
          <span class="market-kicker">{{ detail.goodsTypeLabel || '商品详情' }}</span>
          <h1>{{ detail.title || '市场商品详情' }}</h1>
          <p>{{ detail.description || '查看价格、库存和履约方式。' }}</p>
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
            <template #subtitle>仍然使用固定价托管下单；实物商品会在这里选择收货地址。</template>
          </UiPageHeader>

          <div class="market-form-grid">
            <label class="market-field">
              <span>购买数量</span>
              <UiInput v-model.number="quantity" type="number" min="1" placeholder="输入购买数量" />
            </label>
            <label v-if="detail.goodsType === 'PHYSICAL'" class="market-field">
              <span>收货地址</span>
              <select v-model="selectedAddressId" class="market-select">
                <option value="">请选择收货地址</option>
                <option v-for="item in addressOptions" :key="item.addressId" :value="String(item.addressId)">
                  {{ item.receiverName }} · {{ item.city }} · {{ item.detailAddress }}
                </option>
              </select>
            </label>
            <UiButton :disabled="submitting" @click="submitOrder">
              {{ submitting ? '下单中…' : '立即托管下单' }}
            </UiButton>
          </div>
        </UiCard>

        <UiCard class="market-panel">
          <UiPageHeader>
            <template #title>交易说明</template>
            <template #subtitle>同一个市场入口里，虚拟商品看交付方式，实物商品看发货与收货。</template>
          </UiPageHeader>

          <ul class="market-bullets">
            <li>商品类型：{{ detail.goodsTypeLabel }}</li>
            <li>履约方式：{{ detail.goodsType === 'VIRTUAL' ? detail.deliveryLabel : detail.shipmentLabel }}</li>
            <li>商品状态：{{ detail.statusLabel }}</li>
            <li>库存状态：{{ detail.stockText }}</li>
          </ul>
        </UiCard>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  createMarketOrder,
  getMarketListingDetail,
  listMarketAddresses
} from '../api/services/marketService'
import { buildMarketState } from './marketState'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const listing = ref({})
const quantity = ref(1)
const addresses = ref([])
const selectedAddressId = ref('')

const detail = computed(() => buildMarketState({ listings: [listing.value] }).listings[0] || {})
const addressOptions = computed(() => (Array.isArray(addresses.value) ? addresses.value : []))

function buildRequestId() {
  if (globalThis.crypto?.randomUUID) return `market:order:${globalThis.crypto.randomUUID()}`
  return `market:order:${Date.now()}`
}

async function loadDetail() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await getMarketListingDetail(route.params.listingId)
    listing.value = data || {}
    if (String(data?.goodsType || '').trim().toUpperCase() === 'PHYSICAL') {
      const addressResp = await listMarketAddresses()
      addresses.value = Array.isArray(addressResp.data) ? addressResp.data : []
      const defaultAddress = addresses.value.find((item) => item?.isDefault) || addresses.value[0] || null
      selectedAddressId.value = defaultAddress ? String(defaultAddress.addressId) : ''
    } else {
      addresses.value = []
      selectedAddressId.value = ''
    }
  } catch (e) {
    error.value = e?.message || '加载商品失败'
  } finally {
    loading.value = false
  }
}

async function submitOrder() {
  if (detail.value.goodsType === 'PHYSICAL' && !selectedAddressId.value) {
    error.value = '请选择收货地址'
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await createMarketOrder({
      requestId: buildRequestId(),
      listingId: Number(route.params.listingId),
      quantity: Math.max(1, Number(quantity.value || 1)),
      addressId: detail.value.goodsType === 'PHYSICAL' ? Number(selectedAddressId.value) : undefined
    })
    await loadDetail()
  } catch (e) {
    error.value = e?.message || '下单失败'
  } finally {
    submitting.value = false
  }
}

watch(
  () => route.params.listingId,
  () => {
    loadDetail()
  },
  { immediate: true }
)
</script>
