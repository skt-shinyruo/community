<template>
  <div class="page market-detail-page">
    <nav aria-label="页面层级">
      <UiButton variant="ghost" :to="{ name: 'market' }">
        <ArrowLeft :size="16" aria-hidden="true" />
        返回市场
      </UiButton>
    </nav>

    <UiSkeleton v-if="loading && !hasListing" variant="detail" label="正在加载商品详情" />
    <UiState v-else-if="error" variant="error" :title="error">
      <template #description>商品详情加载失败，可以重试或返回市场。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" data-test="market-detail-retry" @click="loadDetail()">重试</UiButton>
      </template>
    </UiState>

    <template v-else-if="hasListing">
      <UiPageHeader>
        <template #title>{{ detail.title || '市场商品详情' }}</template>
        <template #subtitle>{{ detail.description || '查看价格、库存和履约方式。' }}</template>
      </UiPageHeader>

      <div class="market-price-box">
        <strong class="market-price-value">{{ detail.unitPriceText }}</strong>
        <UiBadge :variant="detail.statusVariant">{{ detail.statusLabel }}</UiBadge>
        <span>{{ detail.stockText }}</span>
        <span>{{ detail.trustLabel }}</span>
      </div>

      <section class="market-split">
        <section class="market-detail-panel" aria-labelledby="market-order-heading">
          <h2 id="market-order-heading" class="market-detail-heading">安全下单</h2>
          <p class="market-detail-intro">下单后资金进入钱包托管，按履约方式跟进交付、收货或争议处理。</p>

          <div class="market-order-form">
            <UiField label="购买数量">
              <UiInput v-model.number="quantity" type="number" min="1" :disabled="submitting" />
            </UiField>
            <UiField v-if="detail.goodsType === 'PHYSICAL' && auth.authed" label="收货地址" :error="addressError" data-test="market-address-field">
              <UiSelect
                v-if="addressLoading || addressOptions.length > 0"
                v-model="selectedAddressId"
                data-test="market-address-select"
                :options="addressSelectOptions"
                :placeholder="addressLoading ? '正在加载收货地址…' : '请选择收货地址'"
                :disabled="submitting || addressLoading"
                :loading="addressLoading"
              />
              <p v-else-if="!addressError" class="market-address-empty" data-test="market-address-empty">
                暂无收货地址，
                <RouterLink class="market-address-link" :to="{ name: 'settings', query: { section: 'addresses' } }">到设置添加</RouterLink>
              </p>
            </UiField>
            <div class="market-risk-note">
              <strong>钱包托管</strong>
              <span>确认商品、库存和履约方式后再提交；未完成前请优先在订单详情里处理争议。</span>
            </div>
            <p v-if="orderError" class="error market-order-error" role="alert" data-test="market-order-error">{{ orderError }}</p>
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
        </section>

        <section class="market-detail-panel" aria-labelledby="market-facts-heading">
          <h2 id="market-facts-heading" class="market-detail-heading">交易说明</h2>
          <p class="market-detail-intro">价格、卖家、履约和托管状态会决定下一步是否安全。</p>

          <ul class="market-facts">
            <li>卖家：{{ detail.sellerLabel }}</li>
            <li>商品类型：{{ detail.goodsTypeLabel }}</li>
            <li>履约方式：{{ detail.fulfillmentLabel }}</li>
            <li>托管状态：{{ detail.trustLabel }}</li>
            <li>商品状态：{{ detail.statusLabel }}</li>
            <li>库存状态：{{ detail.stockText }}</li>
          </ul>
        </section>
      </section>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiState from '../components/ui/UiState.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  createMarketOrder,
  getMarketListingDetail,
  listMarketAddresses
} from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState } from './marketState'
import { createWriteAttempt } from '../api/writeAttempt'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const addressLoading = ref(false)
const submitting = ref(false)
const error = ref('')
const addressError = ref('')
const orderError = ref('')
const orderMessage = ref('')
const createdOrderId = ref('')
const listing = ref({})
const quantity = ref(1)
const addresses = ref([])
const selectedAddressId = ref('')
let listingSequence = 0
let addressSequence = 0
let orderSequence = 0
const orderAttempt = createWriteAttempt()

const detail = computed(() => buildMarketState({ listings: [listing.value] }).listings[0] || {})
const hasListing = computed(() => Object.keys(listing.value || {}).length > 0)
const addressOptions = computed(() => (Array.isArray(addresses.value) ? addresses.value : []))
const addressSelectOptions = computed(() => addressOptions.value.map((item) => ({
  value: String(item.addressId),
  label: `${item.receiverName} · ${item.city} · ${item.detailAddress}`
})))
const authScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))

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

function isCurrentAddressRequest(sequence, listingId, requestedAuthScope) {
  const currentListingId = normalizeOpaqueId(listing.value?.listingId) || normalizeOpaqueId(route.params.listingId)
  return sequence === addressSequence &&
    normalizeOpaqueId(route.params.listingId) === listingId &&
    currentListingId === listingId &&
    authScope.value === requestedAuthScope
}

function isCurrentOrderRequest(sequence, listingId, requestedAuthScope) {
  return sequence === orderSequence &&
    normalizeOpaqueId(route.params.listingId) === listingId &&
    authScope.value === requestedAuthScope &&
    auth.authed
}

function orderIntent(listingId = normalizeOpaqueId(route.params.listingId)) {
  const addressId = detail.value.goodsType === 'PHYSICAL'
    ? normalizeOpaqueId(selectedAddressId.value)
    : ''
  return JSON.stringify([
    listingId,
    Math.max(1, Number(quantity.value || 1)),
    addressId
  ])
}

function isCurrentOrderIntent(sequence, listingId, requestedAuthScope, requestedIntent) {
  return isCurrentOrderRequest(sequence, listingId, requestedAuthScope)
    && requestedIntent === orderIntent(listingId)
}

function signalStaleAddressResponse() {
  console.debug('stale_address_response')
}

async function loadAddressesFor({ listingId, goodsType, requestedAuthScope }) {
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
    if (!isCurrentAddressRequest(sequence, listingId, requestedAuthScope)) {
      signalStaleAddressResponse()
      return
    }
    addresses.value = Array.isArray(addressResp.data) ? addressResp.data : []
    const defaultAddress = addresses.value.find((item) => item?.defaultAddress) || addresses.value[0] || null
    selectedAddressId.value = defaultAddress ? String(defaultAddress.addressId) : ''
  } catch (e) {
    if (!isCurrentAddressRequest(sequence, listingId, requestedAuthScope)) {
      signalStaleAddressResponse()
      return
    }
    addressError.value = e?.message || '加载收货地址失败'
  } finally {
    if (isCurrentAddressRequest(sequence, listingId, requestedAuthScope)) {
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
  orderError.value = ''
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
      requestedAuthScope: authScope.value
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
  const sequence = ++orderSequence
  const requestedAuthScope = authScope.value
  const requestedIntent = orderIntent(listingId)
  submitting.value = true
  orderError.value = ''
  addressError.value = ''
  orderMessage.value = ''
  createdOrderId.value = ''
  try {
    const { data } = await createMarketOrder({
      listingId,
      quantity: Math.max(1, Number(quantity.value || 1)),
      addressId
    }, { writeAttempt: orderAttempt })
    if (!isCurrentOrderIntent(sequence, listingId, requestedAuthScope, requestedIntent)) return
    const orderId = normalizeOpaqueId(data?.orderId)
    orderAttempt.succeed()
    createdOrderId.value = orderId
    orderMessage.value = orderId ? `订单已创建：${orderId}` : '订单已创建，请到我的购买中查看。'
    if (orderId) {
      await goCreatedOrder()
      return
    }
    await loadDetail()
  } catch (e) {
    if (!isCurrentOrderIntent(sequence, listingId, requestedAuthScope, requestedIntent)) return
    orderError.value = e?.message || '下单失败'
  } finally {
    if (isCurrentOrderRequest(sequence, listingId, requestedAuthScope)) submitting.value = false
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
  () => [normalizeOpaqueId(route.params.listingId), authScope.value],
  ([listingId, requestedAuthScope], previous = []) => {
    const [previousListingId] = previous
    orderSequence += 1
    orderAttempt.cancel()
    submitting.value = false
    orderError.value = ''
    orderMessage.value = ''
    createdOrderId.value = ''
    if (listingId !== previousListingId) {
      quantity.value = 1
      loadDetail(listingId)
      return
    }
    loadAddressesFor({
      listingId,
      goodsType: String(listing.value?.goodsType || '').trim().toUpperCase(),
      requestedAuthScope
    })
  },
  { immediate: true }
)

watch([quantity, selectedAddressId], () => orderAttempt.changeIntent())

onBeforeUnmount(() => {
  listingSequence += 1
  addressSequence += 1
  orderSequence += 1
  orderAttempt.cancel()
})
</script>

<style scoped>
.market-detail-page {
  gap: var(--space-5);
}

.market-price-box {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  align-items: center;
  color: var(--text-2);
  font-size: 13px;
}

.market-price-value {
  font-size: var(--text-xl);
  font-weight: 800;
  color: var(--text-1);
}

.market-split {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.8fr);
  gap: var(--space-5);
  align-items: start;
}

.market-detail-panel {
  display: grid;
  gap: var(--space-3);
  padding: var(--card-padding);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.market-detail-heading {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.market-detail-intro {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-sm);
}

.market-order-form {
  display: grid;
  gap: var(--space-4);
}

.market-address-empty {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-sm);
}

.market-address-link {
  color: var(--link-color);
  text-decoration: none;
}

.market-address-link:hover {
  text-decoration: underline;
}

.market-risk-note {
  display: grid;
  gap: var(--space-1);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  border: 1px solid color-mix(in srgb, var(--warning) 24%, var(--border) 76%);
  background: var(--warning-weak);
  color: var(--text-2);
  font-size: 13px;
}

.market-risk-note strong {
  color: var(--text-1);
}

.market-order-error {
  margin: 0;
  font-size: var(--text-sm);
}

.market-success-note {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border-radius: var(--radius-md);
  border: 1px solid color-mix(in srgb, var(--success) 28%, var(--border) 72%);
  background: var(--success-weak);
}

.market-success-note strong {
  color: var(--text-1);
}

.market-facts {
  margin: 0;
  padding-left: var(--space-5);
  color: var(--text-2);
  font-size: var(--text-sm);
}

.market-facts li + li {
  margin-top: var(--space-2);
}

@media (max-width: 900px) {
  .market-split {
    grid-template-columns: 1fr;
  }
}
</style>
