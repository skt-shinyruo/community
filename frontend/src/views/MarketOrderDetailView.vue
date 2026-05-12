<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载订单详情…</div>

    <UiCard v-else class="market-panel">
      <UiPageHeader>
        <template #title>订单详情</template>
        <template #subtitle>订单 ID：{{ route.params.orderId }}。这里继续承接交付、确认与申诉动作。</template>
      </UiPageHeader>

      <UiState v-if="!detail.orderId">
        暂无订单详情
        <template #description>刷新后仍为空时，请检查订单是否存在或当前账号是否有权限查看。</template>
      </UiState>

      <template v-else>
        <div class="market-order-list">
          <article class="market-order-row">
            <div>
              <strong>{{ detail.listingTitleSnapshot || `订单 #${detail.orderId}` }}</strong>
              <p>请求号 {{ detail.requestId || '-' }}</p>
              <p>{{ detail.goodsTypeLabel }} · {{ detail.statusLabel }} · {{ detail.autoConfirmText }}</p>
            </div>
            <strong>{{ detail.totalAmountText }}</strong>
          </article>
        </div>

        <section v-if="detail.goodsType === 'VIRTUAL'" class="market-panel">
          <UiPageHeader>
            <template #title>交付内容</template>
            <template #subtitle>自动交付和卖家手工交付的内容都在这里回看。</template>
          </UiPageHeader>

          <UiState v-if="deliveryContents.length === 0">
            暂无交付内容
            <template #description>订单进入交付阶段后，这里会展示卡密、邀请码或其他发货内容。</template>
          </UiState>

          <ul v-else class="market-bullets">
            <li v-for="(item, index) in deliveryContents" :key="`${detail.orderId}-${index}`">{{ item }}</li>
          </ul>
        </section>

        <section v-else class="market-panel">
          <UiPageHeader>
            <template #title>发货信息</template>
            <template #subtitle>实物商品订单在这里查看发货和地址快照。</template>
          </UiPageHeader>

          <UiState v-if="!shipment">
            暂无发货信息
            <template #description>卖家录入发货信息后，这里会显示承运商、运单号和备注。</template>
          </UiState>

          <ul v-else class="market-bullets">
            <li>承运商：{{ shipment.carrierName }}</li>
            <li>运单号：{{ shipment.trackingNo }}</li>
            <li v-if="shipment.shippingRemark">发货备注：{{ shipment.shippingRemark }}</li>
            <li v-if="addressSnapshot">收货地址：{{ addressSnapshot }}</li>
          </ul>
        </section>
      </template>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { getMarketOrderDetail } from '../api/services/marketService'
import { buildMarketState } from './marketState'

const route = useRoute()
const loading = ref(false)
const error = ref('')
const order = ref(null)
let activeRequestToken = 0

const detail = computed(() => {
  const orders = order.value?.orderId ? [order.value] : []
  return buildMarketState({ orders }).orders[0] || {}
})
const deliveryContents = computed(() => (Array.isArray(order.value?.deliveryContents) ? order.value.deliveryContents : []))
const shipment = computed(() => order.value?.shipment || null)
const addressSnapshot = computed(() => {
  const parts = [
    order.value?.provinceSnapshot,
    order.value?.citySnapshot,
    order.value?.districtSnapshot,
    order.value?.detailAddressSnapshot
  ].map((part) => String(part || '').trim()).filter(Boolean)
  return parts.join(' ')
})

async function loadDetail() {
  const requestToken = ++activeRequestToken
  loading.value = true
  error.value = ''
  order.value = null
  try {
    const { data } = await getMarketOrderDetail(route.params.orderId)
    if (requestToken !== activeRequestToken) return
    order.value = data?.orderId ? data : null
  } catch (e) {
    if (requestToken !== activeRequestToken) return
    error.value = e?.message || '加载订单详情失败'
  } finally {
    if (requestToken !== activeRequestToken) return
    loading.value = false
  }
}

watch(
  () => route.params.orderId,
  () => {
    loadDetail()
  },
  { immediate: true }
)
</script>
