<template>
  <div class="page market-order-detail-page">
    <nav aria-label="页面层级">
      <UiButton variant="ghost" :to="{ name: backTarget.name }">
        <ArrowLeft :size="16" aria-hidden="true" />
        {{ backTarget.label }}
      </UiButton>
    </nav>

    <UiSkeleton v-if="loading" variant="detail" label="正在加载订单详情" />
    <UiState v-else-if="error" variant="error" :title="error">
      <template #description>订单详情加载失败，可以重试或返回订单列表。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" data-test="market-order-detail-retry" @click="loadDetail">重试</UiButton>
      </template>
    </UiState>

    <template v-else>
      <UiPageHeader>
        <template #title>订单详情</template>
        <template #subtitle>订单 ID：{{ route.params.orderId }}。这里继续承接交付、确认与申诉动作。</template>
      </UiPageHeader>

      <UiState v-if="!detail.orderId">
        暂无订单详情
        <template #description>刷新后仍为空时，请检查订单是否存在或当前账号是否有权限查看。</template>
        <template #actions>
          <UiButton :to="{ name: 'marketBuyingOrders' }">查看我的购买</UiButton>
        </template>
      </UiState>

      <template v-else>
        <section class="market-order-summary" aria-label="订单摘要">
          <div class="market-order-summary-main">
            <div class="market-order-badges">
              <span class="market-order-chip">{{ detail.goodsTypeLabel }}</span>
              <UiBadge :variant="detail.statusVariant">{{ detail.statusLabel }}</UiBadge>
              <span v-if="viewerRoleLabel" class="market-order-chip market-order-chip--role">{{ viewerRoleLabel }}</span>
            </div>
            <h2 class="market-order-summary-title">{{ detail.listingTitleSnapshot || `订单 #${detail.orderId}` }}</h2>
            <p class="market-order-summary-line">请求号 {{ detail.requestId || '-' }}</p>
            <p class="market-order-summary-line">履约：{{ detail.fulfillmentLabel }} · 资金：{{ detail.fundsLabel }}</p>
            <p class="market-order-summary-next">下一步：{{ detail.nextActionLabel }}</p>
          </div>
          <strong class="market-order-amount">{{ detail.totalAmountText }}</strong>
        </section>

        <section class="market-order-lifecycle" aria-label="订单生命周期">
          <div
            v-for="step in detail.lifecycleSteps"
            :key="step.key"
            class="market-order-lifecycle-step"
            :data-state="step.state"
          >
            <span>{{ step.label }}</span>
          </div>
        </section>

        <section v-if="hasAvailableActions" class="market-order-panel" aria-labelledby="market-order-actions-heading">
          <h2 id="market-order-actions-heading" class="market-order-heading">订单操作</h2>
          <p class="market-order-intro">根据当前账号角色和订单状态继续推进履约、确认、取消或申诉。</p>

          <p v-if="actionError" class="error market-order-action-error" role="alert">{{ actionError }}</p>

          <div class="market-order-form-grid">
            <template v-if="canDeliver">
              <UiField label="交付内容">
                <UiTextarea
                  v-model.trim="deliveryForm.deliveryContent"
                  rows="4"
                  placeholder="输入卡密、邀请码或其他交付内容"
                  :disabled="actionSubmitting"
                />
              </UiField>
              <div class="market-order-actions">
                <UiButton :disabled="actionSubmitting" @click="submitDelivery">
                  {{ actionSubmitting ? '提交中…' : '提交交付' }}
                </UiButton>
              </div>
            </template>

            <template v-if="canShip">
              <div class="market-order-form-grid market-order-form-grid--wide">
                <UiField label="承运商">
                  <UiInput v-model.trim="shipForm.carrierName" placeholder="例如：顺丰" autocomplete="off" :disabled="actionSubmitting" />
                </UiField>
                <UiField label="运单号">
                  <UiInput v-model.trim="shipForm.trackingNo" placeholder="输入运单号" autocomplete="off" :disabled="actionSubmitting" />
                </UiField>
              </div>
              <UiField label="发货备注">
                <UiTextarea
                  v-model.trim="shipForm.shippingRemark"
                  rows="3"
                  placeholder="可选，补充配送说明"
                  :disabled="actionSubmitting"
                />
              </UiField>
              <div class="market-order-actions">
                <UiButton :disabled="actionSubmitting" @click="submitShipment">
                  {{ actionSubmitting ? '提交中…' : '确认发货' }}
                </UiButton>
              </div>
            </template>

            <div v-if="canConfirm || canCancel" class="market-order-actions">
              <UiButton v-if="canConfirm" :disabled="actionSubmitting" @click="requestConfirm">
                {{ actionSubmitting ? '提交中…' : confirmButtonText }}
              </UiButton>
              <UiButton v-if="canCancel" variant="dangerSecondary" :disabled="actionSubmitting" @click="requestCancel">
                {{ actionSubmitting ? '提交中…' : '取消订单' }}
              </UiButton>
            </div>

            <template v-if="canDispute">
              <UiField label="申诉原因">
                <UiInput v-model.trim="disputeForm.reason" placeholder="简要说明问题" autocomplete="off" :disabled="actionSubmitting" />
              </UiField>
              <UiField label="申诉说明">
                <UiTextarea
                  v-model.trim="disputeForm.buyerNote"
                  rows="3"
                  placeholder="描述未收到、内容无效或其他异常"
                  :disabled="actionSubmitting"
                />
              </UiField>
              <div class="market-order-actions">
                <UiButton variant="secondary" :disabled="actionSubmitting" @click="submitDispute">
                  {{ actionSubmitting ? '提交中…' : '发起申诉' }}
                </UiButton>
              </div>
            </template>
          </div>
        </section>

        <section class="market-order-panel" aria-labelledby="market-order-audit-heading">
          <h2 id="market-order-audit-heading" class="market-order-heading">审计上下文</h2>
          <p class="market-order-intro">请求号、资金状态、履约状态和下一步动作都用于判断订单是否需要继续处理。</p>
          <ul class="market-order-bullets">
            <li>资金状态：{{ detail.fundsLabel }}</li>
            <li>履约状态：{{ detail.fulfillmentLabel }}</li>
            <li>确认状态：{{ detail.statusLabel }}</li>
            <li>争议状态：{{ detail.lifecycleSteps?.[4]?.label || '无争议' }}</li>
            <li>下一步：{{ detail.nextActionLabel }}</li>
          </ul>
        </section>

        <section v-if="detail.goodsType === 'VIRTUAL'" class="market-order-panel" aria-labelledby="market-order-delivery-heading">
          <h2 id="market-order-delivery-heading" class="market-order-heading">交付内容</h2>
          <p class="market-order-intro">自动交付和卖家手工交付的内容都在这里回看。</p>

          <UiState v-if="deliveryContents.length === 0">
            暂无交付内容
            <template #description>订单进入交付阶段后，这里会展示卡密、邀请码或其他发货内容。</template>
          </UiState>

          <ul v-else class="market-order-bullets">
            <li v-for="(item, index) in deliveryContents" :key="`${detail.orderId}-${index}`">{{ item }}</li>
          </ul>
        </section>

        <section v-else class="market-order-panel" aria-labelledby="market-order-shipment-heading">
          <h2 id="market-order-shipment-heading" class="market-order-heading">发货信息</h2>
          <p class="market-order-intro">实物商品订单在这里查看发货和地址快照。</p>

          <UiState v-if="!shipment">
            暂无发货信息
            <template #description>卖家录入发货信息后，这里会显示承运商、运单号和备注。</template>
          </UiState>

          <ul v-else class="market-order-bullets">
            <li>承运商：{{ shipment.carrierName }}</li>
            <li>运单号：{{ shipment.trackingNo }}</li>
            <li v-if="shipment.shippingRemark">发货备注：{{ shipment.shippingRemark }}</li>
            <li v-if="addressSnapshot">收货地址：{{ addressSnapshot }}</li>
          </ul>
        </section>
      </template>
    </template>

    <UiModalConfirm
      v-if="confirmation.open"
      :title="confirmation.title"
      :message="confirmation.message"
      :confirm-text="confirmation.confirmText"
      :confirm-variant="confirmation.variant"
      :busy="actionSubmitting"
      @cancel="closeConfirmation"
      @confirm="runConfirmation"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import UiState from '../components/ui/UiState.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
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
import {
  buildMarketState,
  marketOrderCancelConfirmation,
  marketOrderConfirmConfirmation
} from './marketState'

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
// 确认完成 / 收货（放款给卖家）与取消订单（中止履约并触发退款）是资损动作，
// 先经 UiModalConfirm 复述金额与不可撤销后果再进入原提交流程。
const confirmation = reactive({
  open: false,
  title: '',
  message: '',
  confirmText: '确认',
  variant: 'danger',
  action: /** @type {null | (() => Promise<void> | void)} */ (null)
})
let activeRequestToken = 0
let actionGeneration = 0

const detail = computed(() => {
  const orders = order.value?.orderId ? [order.value] : []
  return buildMarketState({ orders }).orders[0] || {}
})
const deliveryContents = computed(() => (Array.isArray(order.value?.deliveryContents) ? order.value.deliveryContents : []))
const shipment = computed(() => order.value?.shipment || null)
const normalizedGoodsType = computed(() => String(order.value?.goodsType || '').trim().toUpperCase())
const normalizedDeliveryMode = computed(() => String(order.value?.deliveryModeSnapshot || '').trim().toUpperCase())
const isBuyer = computed(() => sameOpaqueId(auth.userId, order.value?.buyerUserId))
const isSeller = computed(() => sameOpaqueId(auth.userId, order.value?.sellerUserId))
function allowsOrderAction(action) {
  return Array.isArray(detail.value.allowedActions) && detail.value.allowedActions.includes(action)
}
const canDeliver = computed(() => {
  return isSeller.value
    && allowsOrderAction('fulfill')
    && normalizedGoodsType.value === 'VIRTUAL'
    && normalizedDeliveryMode.value === 'MANUAL'
})
const canShip = computed(() => {
  return isSeller.value
    && allowsOrderAction('fulfill')
    && normalizedGoodsType.value === 'PHYSICAL'
})
const canConfirm = computed(() => isBuyer.value && allowsOrderAction('confirm'))
const canCancel = computed(() => isBuyer.value && allowsOrderAction('cancel'))
const canDispute = computed(() => isBuyer.value && allowsOrderAction('dispute'))
const hasAvailableActions = computed(() => canDeliver.value || canShip.value || canConfirm.value || canCancel.value || canDispute.value)
const confirmButtonText = computed(() => detail.value.confirmButtonText || '确认完成')
// 买家 / 卖家视角用文字 chip 明示，返回入口跟随卖家视角落到出售订单列表。
const viewerRoleLabel = computed(() => {
  if (isBuyer.value) return '我是买家'
  if (isSeller.value) return '我是卖家'
  return ''
})
const backTarget = computed(() => (isSeller.value && !isBuyer.value)
  ? { name: 'marketSellingOrders', label: '返回出售订单' }
  : { name: 'marketBuyingOrders', label: '返回我的购买' })
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

function openConfirmation({ title, message, confirmText, variant = 'danger' }, action) {
  if (actionSubmitting.value || typeof action !== 'function') return
  confirmation.title = title
  confirmation.message = message
  confirmation.confirmText = confirmText
  confirmation.variant = variant
  confirmation.action = action
  confirmation.open = true
}

function closeConfirmation() {
  confirmation.open = false
  confirmation.action = null
}

async function runConfirmation() {
  const action = confirmation.action
  if (!action) return
  closeConfirmation()
  await action()
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

function requestConfirm() {
  const orderId = normalizeOpaqueId(route.params.orderId)
  openConfirmation(
    marketOrderConfirmConfirmation(detail.value),
    () => runOrderAction(
      orderId,
      (targetOrderId) => confirmMarketOrder(targetOrderId),
      '确认订单失败'
    )
  )
}

function requestCancel() {
  const orderId = normalizeOpaqueId(route.params.orderId)
  openConfirmation(
    marketOrderCancelConfirmation(detail.value),
    () => runOrderAction(
      orderId,
      (targetOrderId) => cancelMarketOrder(targetOrderId),
      '取消订单失败'
    )
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
    closeConfirmation()
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

<style scoped>
.market-order-detail-page {
  gap: var(--space-5);
}

.market-order-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-4);
  align-items: start;
}

.market-order-summary-main {
  min-width: 0;
  display: grid;
  gap: var(--space-2);
}

.market-order-badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.market-order-chip {
  display: inline-flex;
  align-items: center;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  border: 1px solid var(--border);
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--text-1);
}

.market-order-chip--role {
  background: var(--accent-weak);
  border-color: color-mix(in srgb, var(--accent) 18%, var(--border) 82%);
  color: var(--accent-text);
}

.market-order-summary-title {
  margin: 0;
  font-size: 19px;
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.market-order-summary-line {
  margin: 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.market-order-summary-next {
  margin: 0;
  color: var(--text-1);
  font-size: var(--text-sm);
  font-weight: 600;
}

.market-order-amount {
  font-size: var(--text-xl);
  font-weight: 800;
  color: var(--text-1);
  white-space: nowrap;
}

.market-order-lifecycle {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--space-2);
}

.market-order-lifecycle-step {
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text-2);
  font-size: 13px;
  font-weight: 700;
  text-align: center;
}

.market-order-lifecycle-step[data-state='complete'] {
  border-color: color-mix(in srgb, var(--success) 28%, var(--border) 72%);
  background: var(--success-weak);
  color: var(--text-1);
}

.market-order-lifecycle-step[data-state='active'] {
  border-color: color-mix(in srgb, var(--warning) 36%, var(--border) 64%);
  background: var(--warning-weak);
  color: var(--text-1);
}

.market-order-panel {
  display: grid;
  gap: var(--space-3);
  padding: var(--card-padding);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.market-order-heading {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.market-order-intro {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-sm);
}

.market-order-form-grid {
  display: grid;
  gap: var(--space-4);
}

.market-order-form-grid--wide {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.market-order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  align-items: center;
}

.market-order-action-error {
  margin: 0;
  font-size: var(--text-sm);
}

.market-order-bullets {
  margin: 0;
  padding-left: var(--space-5);
  color: var(--text-2);
  font-size: var(--text-sm);
}

.market-order-bullets li + li {
  margin-top: var(--space-2);
}

@media (max-width: 900px) {
  .market-order-summary {
    grid-template-columns: 1fr;
  }

  .market-order-amount {
    white-space: normal;
  }

  .market-order-lifecycle {
    grid-template-columns: 1fr;
  }

  .market-order-form-grid--wide {
    grid-template-columns: 1fr;
  }
}
</style>
