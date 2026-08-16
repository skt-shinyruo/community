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
        <section class="market-lifecycle" aria-label="订单生命周期">
          <div
            v-for="step in detail.lifecycleSteps"
            :key="step.key"
            class="market-lifecycle-step"
            :data-state="step.state"
          >
            <span>{{ step.label }}</span>
          </div>
        </section>

        <div class="market-order-list">
          <article class="market-order-row">
            <div>
              <strong>{{ detail.listingTitleSnapshot || `订单 #${detail.orderId}` }}</strong>
              <p>请求号 {{ detail.requestId || '-' }}</p>
              <p>{{ detail.goodsTypeLabel }} · {{ detail.statusLabel }} · {{ detail.fundsLabel }}</p>
              <p>履约：{{ detail.fulfillmentLabel }} · 下一步：{{ detail.nextActionLabel }}</p>
            </div>
            <strong>{{ detail.totalAmountText }}</strong>
          </article>
        </div>

        <section v-if="hasAvailableActions" class="market-panel">
          <UiPageHeader>
            <template #title>订单操作</template>
            <template #subtitle>根据当前账号角色和订单状态继续推进履约、确认、取消或申诉。</template>
          </UiPageHeader>

          <UiState v-if="actionError" variant="error">{{ actionError }}</UiState>

          <div class="market-form-grid">
            <template v-if="canDeliver">
              <label class="market-field">
                <span>交付内容</span>
                <textarea v-model.trim="deliveryForm.deliveryContent" class="input multiline" rows="4" placeholder="输入卡密、邀请码或其他交付内容" />
              </label>
              <div class="market-inline-actions">
                <UiButton :disabled="actionSubmitting" @click="submitDelivery">
                  {{ actionSubmitting ? '提交中…' : '提交交付' }}
                </UiButton>
              </div>
            </template>

            <template v-if="canShip">
              <div class="market-form-grid market-form-grid--wide">
                <label class="market-field">
                  <span>承运商</span>
                  <input v-model.trim="shipForm.carrierName" class="input" placeholder="例如：顺丰" autocomplete="off" />
                </label>
                <label class="market-field">
                  <span>运单号</span>
                  <input v-model.trim="shipForm.trackingNo" class="input" placeholder="输入运单号" autocomplete="off" />
                </label>
              </div>
              <label class="market-field">
                <span>发货备注</span>
                <textarea v-model.trim="shipForm.shippingRemark" class="input multiline" rows="3" placeholder="可选，补充配送说明" />
              </label>
              <div class="market-inline-actions">
                <UiButton :disabled="actionSubmitting" @click="submitShipment">
                  {{ actionSubmitting ? '提交中…' : '确认发货' }}
                </UiButton>
              </div>
            </template>

            <div v-if="canConfirm || canCancel" class="market-inline-actions">
              <UiButton v-if="canConfirm" :disabled="actionSubmitting" @click="submitConfirm">
                {{ actionSubmitting ? '提交中…' : confirmButtonText }}
              </UiButton>
              <UiButton v-if="canCancel" variant="dangerSecondary" :disabled="actionSubmitting" @click="submitCancel">
                {{ actionSubmitting ? '提交中…' : '取消订单' }}
              </UiButton>
            </div>

            <template v-if="canDispute">
              <div class="market-form-grid market-form-grid--wide">
                <label class="market-field">
                  <span>申诉原因</span>
                  <input v-model.trim="disputeForm.reason" class="input" placeholder="简要说明问题" autocomplete="off" />
                </label>
              </div>
              <label class="market-field">
                <span>申诉说明</span>
                <textarea v-model.trim="disputeForm.buyerNote" class="input multiline" rows="3" placeholder="描述未收到、内容无效或其他异常" />
              </label>
              <div class="market-inline-actions">
                <UiButton variant="secondary" :disabled="actionSubmitting" @click="submitDispute">
                  {{ actionSubmitting ? '提交中…' : '发起申诉' }}
                </UiButton>
              </div>
            </template>
          </div>
        </section>

        <section class="market-panel">
          <UiPageHeader>
            <template #title>审计上下文</template>
            <template #subtitle>请求号、资金状态、履约状态和下一步动作都用于判断订单是否需要继续处理。</template>
          </UiPageHeader>
          <ul class="market-bullets">
            <li>资金状态：{{ detail.fundsLabel }}</li>
            <li>履约状态：{{ detail.fulfillmentLabel }}</li>
            <li>确认状态：{{ detail.statusLabel }}</li>
            <li>争议状态：{{ detail.lifecycleSteps?.[4]?.label || '无争议' }}</li>
            <li>下一步：{{ detail.nextActionLabel }}</li>
          </ul>
        </section>

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
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  cancelMarketOrder,
  confirmMarketOrder,
  deliverMarketOrder,
  getMarketOrderDetail,
  openMarketOrderDispute,
  shipMarketOrder
} from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId, sameOpaqueId } from '../utils/opaqueId'
import { buildMarketState } from './marketState'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const actionSubmitting = ref(false)
const order = ref(null)
const deliveryForm = reactive({
  deliveryContent: ''
})
const shipForm = reactive({
  carrierName: '',
  trackingNo: '',
  shippingRemark: ''
})
const disputeForm = reactive({
  reason: '',
  buyerNote: ''
})
let activeRequestToken = 0
let actionGeneration = 0

const detail = computed(() => {
  const orders = order.value?.orderId ? [order.value] : []
  return buildMarketState({ orders }).orders[0] || {}
})
const deliveryContents = computed(() => (Array.isArray(order.value?.deliveryContents) ? order.value.deliveryContents : []))
const shipment = computed(() => order.value?.shipment || null)
const normalizedStatus = computed(() => String(order.value?.status || '').trim().toUpperCase())
const normalizedGoodsType = computed(() => String(order.value?.goodsType || '').trim().toUpperCase())
const normalizedDeliveryMode = computed(() => String(order.value?.deliveryModeSnapshot || '').trim().toUpperCase())
const isBuyer = computed(() => sameOpaqueId(auth.userId, order.value?.buyerUserId))
const isSeller = computed(() => sameOpaqueId(auth.userId, order.value?.sellerUserId))
const canDeliver = computed(() => {
  return isSeller.value
    && normalizedStatus.value === 'ESCROWED'
    && normalizedGoodsType.value === 'VIRTUAL'
    && normalizedDeliveryMode.value === 'MANUAL'
})
const canShip = computed(() => {
  return isSeller.value
    && normalizedStatus.value === 'ESCROWED'
    && normalizedGoodsType.value === 'PHYSICAL'
})
const canConfirm = computed(() => isBuyer.value && ['DELIVERED', 'SHIPPED'].includes(normalizedStatus.value))
const canCancel = computed(() => isBuyer.value && ['ESCROW_PENDING', 'ESCROWED'].includes(normalizedStatus.value))
const canDispute = computed(() => isBuyer.value && ['DELIVERED', 'SHIPPED'].includes(normalizedStatus.value))
const hasAvailableActions = computed(() => canDeliver.value || canShip.value || canConfirm.value || canCancel.value || canDispute.value)
const confirmButtonText = computed(() => (normalizedStatus.value === 'SHIPPED' ? '确认收货' : '确认完成'))
const viewScope = computed(() => [
  normalizeOpaqueId(route.params.orderId),
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))
const addressSnapshot = computed(() => {
  const parts = [
    order.value?.provinceSnapshot,
    order.value?.citySnapshot,
    order.value?.districtSnapshot,
    order.value?.detailAddressSnapshot
  ].map((part) => String(part || '').trim()).filter(Boolean)
  return parts.join(' ')
})

function resetActionForms() {
  actionError.value = ''
  deliveryForm.deliveryContent = ''
  shipForm.carrierName = ''
  shipForm.trackingNo = ''
  shipForm.shippingRemark = ''
  disputeForm.reason = ''
  disputeForm.buyerNote = ''
}

function isCurrentDetailRequest(requestToken, scope) {
  return requestToken === activeRequestToken && scope === viewScope.value
}

function isCurrentAction(generation, scope, orderId) {
  return generation === actionGeneration &&
    scope === viewScope.value &&
    normalizeOpaqueId(route.params.orderId) === orderId
}

async function runOrderAction(orderId, action, fallbackMessage) {
  if (actionSubmitting.value || !auth.authed || !orderId || !sameOpaqueId(order.value?.orderId, orderId)) return
  const generation = ++actionGeneration
  const scope = viewScope.value
  actionSubmitting.value = true
  actionError.value = ''
  try {
    await action(orderId)
    if (!isCurrentAction(generation, scope, orderId)) return
    await loadDetail()
    if (!isCurrentAction(generation, scope, orderId)) return
    resetActionForms()
  } catch (e) {
    if (!isCurrentAction(generation, scope, orderId)) return
    actionError.value = e?.message || fallbackMessage
  } finally {
    if (isCurrentAction(generation, scope, orderId)) actionSubmitting.value = false
  }
}

async function submitDelivery() {
  const deliveryContent = deliveryForm.deliveryContent.trim()
  if (!deliveryContent) {
    actionError.value = '请输入交付内容'
    return
  }
  const orderId = normalizeOpaqueId(route.params.orderId)
  await runOrderAction(
    orderId,
    (targetOrderId) => deliverMarketOrder(targetOrderId, { deliveryContent }),
    '提交交付失败'
  )
}

async function submitShipment() {
  const carrierName = shipForm.carrierName.trim()
  const trackingNo = shipForm.trackingNo.trim()
  const shippingRemark = shipForm.shippingRemark.trim()
  if (!carrierName || !trackingNo) {
    actionError.value = '请输入承运商和运单号'
    return
  }
  const orderId = normalizeOpaqueId(route.params.orderId)
  await runOrderAction(
    orderId,
    (targetOrderId) => shipMarketOrder(targetOrderId, {
      carrierName,
      trackingNo,
      shippingRemark
    }),
    '提交发货失败'
  )
}

async function submitConfirm() {
  const orderId = normalizeOpaqueId(route.params.orderId)
  await runOrderAction(
    orderId,
    (targetOrderId) => confirmMarketOrder(targetOrderId),
    '确认订单失败'
  )
}

async function submitCancel() {
  const orderId = normalizeOpaqueId(route.params.orderId)
  await runOrderAction(
    orderId,
    (targetOrderId) => cancelMarketOrder(targetOrderId),
    '取消订单失败'
  )
}

async function submitDispute() {
  const reason = disputeForm.reason.trim()
  const buyerNote = disputeForm.buyerNote.trim()
  if (!reason || !buyerNote) {
    actionError.value = '请输入申诉原因和说明'
    return
  }
  const orderId = normalizeOpaqueId(route.params.orderId)
  await runOrderAction(
    orderId,
    (targetOrderId) => openMarketOrderDispute(targetOrderId, { reason, buyerNote }),
    '发起申诉失败'
  )
}

async function loadDetail() {
  const requestToken = ++activeRequestToken
  const scope = viewScope.value
  const orderId = normalizeOpaqueId(route.params.orderId)
  loading.value = true
  error.value = ''
  order.value = null
  try {
    const { data } = await getMarketOrderDetail(orderId)
    if (!isCurrentDetailRequest(requestToken, scope)) return
    order.value = data?.orderId ? data : null
  } catch (e) {
    if (!isCurrentDetailRequest(requestToken, scope)) return
    error.value = e?.message || '加载订单详情失败'
  } finally {
    if (isCurrentDetailRequest(requestToken, scope)) loading.value = false
  }
}

watch(
  viewScope,
  () => {
    activeRequestToken += 1
    actionGeneration += 1
    loading.value = false
    actionSubmitting.value = false
    error.value = ''
    order.value = null
    resetActionForms()
    if (auth.authed && normalizeOpaqueId(route.params.orderId)) loadDetail()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  activeRequestToken += 1
  actionGeneration += 1
})
</script>
