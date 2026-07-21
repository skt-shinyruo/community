<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载商品详情…</div>

    <div v-else class="market-detail-shell">
      <UiPageHeader>
        <template #title>{{ detail.title || '市场商品详情' }}</template>
        <template #subtitle>{{ detail.description || '查看价格、库存和履约方式。' }}</template>
      </UiPageHeader>

      <div class="market-price-box">
        <strong>{{ detail.unitPriceText }}</strong>
        <span>{{ detail.statusLabel }}</span>
        <span>{{ detail.stockText }}</span>
        <span>{{ detail.trustLabel }}</span>
      </div>

      <section class="market-split">
        <UiCard class="market-panel">
          <UiPageHeader>
            <template #title>安全下单</template>
            <template #subtitle>下单后资金进入钱包托管，按履约方式跟进交付、收货或争议处理。</template>
          </UiPageHeader>

          <div class="market-form-grid">
            <label class="market-field">
              <span>购买数量</span>
              <UiInput v-model.number="quantity" type="number" min="1" placeholder="输入购买数量" />
            </label>
            <label v-if="detail.goodsType === 'PHYSICAL' && auth.authed" class="market-field">
              <span>收货地址</span>
              <div v-if="addressLoading" class="muted" data-test="market-address-loading">正在加载收货地址…</div>
              <div v-else-if="addressError" class="error" data-test="market-address-error">{{ addressError }}</div>
              <select
                v-else-if="addressOptions.length"
                v-model="selectedAddressId"
                class="market-select"
                data-test="market-address-select"
              >
                <option value="">请选择收货地址</option>
                <option v-for="item in addressOptions" :key="item.addressId" :value="String(item.addressId)">
                  {{ item.receiverName }} · {{ item.city }} · {{ item.detailAddress }}
                </option>
              </select>
              <div v-else class="muted" data-test="market-address-empty">暂无收货地址</div>
            </label>
            <div class="market-risk-note">
              <strong>钱包托管</strong>
              <span>确认商品、库存和履约方式后再提交；未完成前请优先在订单详情里处理争议。</span>
            </div>
            <UiButton data-test="market-order-submit" :disabled="submitting" @click="submitOrder">
              {{ submitting ? '下单中…' : '安全下单' }}
            </UiButton>
            <div v-if="orderMessage" class="market-success-note" data-test="market-order-success" role="status">
              <strong>{{ orderMessage }}</strong>
              <UiButton
                v-if="createdOrderId"
                variant="secondary"
                :disabled="submitting"
                @click="goCreatedOrder"
              >
                查看订单详情
              </UiButton>
            </div>
          </div>
        </UiCard>

        <UiCard class="market-panel">
          <UiPageHeader>
            <template #title>交易说明</template>
            <template #subtitle>价格、卖家、履约和托管状态会决定下一步是否安全。</template>
          </UiPageHeader>

          <ul class="market-bullets">
            <li>卖家：{{ detail.sellerLabel }}</li>
            <li>商品类型：{{ detail.goodsTypeLabel }}</li>
            <li>履约方式：{{ detail.fulfillmentLabel }}</li>
            <li>托管状态：{{ detail.trustLabel }}</li>
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
import { useRoute, useRouter } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  createMarketOrder,
  getMarketListingDetail,
  listMarketAddresses
} from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState } from './marketState'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const addressLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const addressError = ref('')
const orderMessage = ref('')
const createdOrderId = ref('')
const listing = ref({})
const quantity = ref(1)
const addresses = ref([])
const selectedAddressId = ref('')
let listingSequence = 0
let addressSequence = 0

const detail = computed(() => buildMarketState({ listings: [listing.value] }).listings[0] || {})
const addressOptions = computed(() => (Array.isArray(addresses.value) ? addresses.value : []))

function resetAddressState() {
  addressSequence += 1
  addressLoading.value = false
  addressError.value = ''
  addresses.value = []
  selectedAddressId.value = ''
}

function isCurrentListingRequest(sequence, listingId) {
  return sequence === listingSequence && normalizeOpaqueId(route.params.listingId) === listingId
}

function isCurrentAddressRequest(sequence, listingId, authGeneration) {
  const currentListingId = normalizeOpaqueId(listing.value?.listingId) || normalizeOpaqueId(route.params.listingId)
  return sequence === addressSequence &&
    normalizeOpaqueId(route.params.listingId) === listingId &&
    currentListingId === listingId &&
    auth.tokenGeneration === authGeneration
}

function signalStaleAddressResponse() {
  console.debug('stale_address_response')
}

async function loadAddressesFor({ listingId, goodsType, authGeneration }) {
  const sequence = ++addressSequence
  addressLoading.value = false
  addressError.value = ''
  addresses.value = []
  selectedAddressId.value = ''

  if (!auth.authed || goodsType !== 'PHYSICAL') {
    return
  }

  addressLoading.value = true
  try {
    const addressResp = await listMarketAddresses()
    if (!isCurrentAddressRequest(sequence, listingId, authGeneration)) {
      signalStaleAddressResponse()
      return
    }
    addresses.value = Array.isArray(addressResp.data) ? addressResp.data : []
    const defaultAddress = addresses.value.find((item) => item?.defaultAddress) || addresses.value[0] || null
    selectedAddressId.value = defaultAddress ? String(defaultAddress.addressId) : ''
  } catch (e) {
    if (!isCurrentAddressRequest(sequence, listingId, authGeneration)) {
      signalStaleAddressResponse()
      return
    }
    addressError.value = e?.message || '加载收货地址失败'
  } finally {
    if (isCurrentAddressRequest(sequence, listingId, authGeneration)) {
      addressLoading.value = false
    }
  }
}

async function loadDetail(requestedListingId = normalizeOpaqueId(route.params.listingId)) {
  const sequence = ++listingSequence
  const listingId = normalizeOpaqueId(requestedListingId)
  resetAddressState()
  loading.value = true
  error.value = ''
  listing.value = {}
  if (!listingId) {
    error.value = '商品 ID 无效'
    loading.value = false
    return
  }
  try {
    const { data } = await getMarketListingDetail(listingId)
    if (!isCurrentListingRequest(sequence, listingId)) {
      return
    }
    listing.value = data || {}
    loading.value = false
    await loadAddressesFor({
      listingId,
      goodsType: String(data?.goodsType || '').trim().toUpperCase(),
      authGeneration: auth.tokenGeneration
    })
  } catch (e) {
    if (!isCurrentListingRequest(sequence, listingId)) {
      return
    }
    error.value = e?.message || '加载商品失败'
  } finally {
    if (isCurrentListingRequest(sequence, listingId)) {
      loading.value = false
    }
  }
}

async function submitOrder() {
  if (!auth.authed) {
    await router.push({
      name: 'login',
      query: { redirect: route.fullPath }
    })
    return
  }

  const listingId = normalizeOpaqueId(route.params.listingId)
  const addressId = detail.value.goodsType === 'PHYSICAL' ? normalizeOpaqueId(selectedAddressId.value) : undefined
  if (!listingId) {
    error.value = '商品 ID 无效'
    return
  }
  if (detail.value.goodsType === 'PHYSICAL' && !addressId) {
    addressError.value = '请选择收货地址'
    return
  }
  submitting.value = true
  error.value = ''
  addressError.value = ''
  orderMessage.value = ''
  createdOrderId.value = ''
  try {
    const { data } = await createMarketOrder({
      listingId,
      quantity: Math.max(1, Number(quantity.value || 1)),
      addressId
    })
    const orderId = normalizeOpaqueId(data?.orderId)
    createdOrderId.value = orderId
    orderMessage.value = orderId ? `订单已创建：${orderId}` : '订单已创建，请到我的购买中查看。'
    if (orderId) {
      await goCreatedOrder()
      return
    }
    await loadDetail()
  } catch (e) {
    error.value = e?.message || '下单失败'
  } finally {
    submitting.value = false
  }
}

async function goCreatedOrder() {
  const orderId = normalizeOpaqueId(createdOrderId.value)
  if (!orderId) return
  await router.push({
    name: 'marketOrderDetail',
    params: { orderId }
  })
}

watch(
  () => [normalizeOpaqueId(route.params.listingId), auth.tokenGeneration],
  ([listingId, authGeneration], previous = []) => {
    const [previousListingId] = previous
    if (listingId !== previousListingId) {
      orderMessage.value = ''
      createdOrderId.value = ''
      quantity.value = 1
      loadDetail(listingId)
      return
    }
    loadAddressesFor({
      listingId,
      goodsType: String(listing.value?.goodsType || '').trim().toUpperCase(),
      authGeneration
    })
  },
  { immediate: true }
)
</script>
