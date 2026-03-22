<template>
  <div class="page reward-ops-page">
    <UiBreadcrumb />

    <UiCard class="reward-ops-shell">
      <UiPageHeader>
        <template #title>奖励运营后台</template>
        <template #subtitle>集中管理商品目录、人工履约订单和轻量指标，不让运营动作散落到数据库脚本里。</template>
        <template #actions>
          <UiButton variant="secondary" :disabled="loading" @click="reload">{{ loading ? '刷新中…' : '刷新' }}</UiButton>
        </template>
      </UiPageHeader>

      <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>

      <div class="reward-ops-metrics">
        <UiCard flat class="reward-ops-metric-card">
          <span class="reward-ops-label">启用商品</span>
          <strong>{{ metrics.activeItemCount || 0 }}</strong>
        </UiCard>
        <UiCard flat class="reward-ops-metric-card">
          <span class="reward-ops-label">待处理订单</span>
          <strong>{{ metrics.pendingOrderCount || 0 }}</strong>
        </UiCard>
        <UiCard flat class="reward-ops-metric-card">
          <span class="reward-ops-label">已退款</span>
          <strong>{{ metrics.refundedOrderCount || 0 }}</strong>
        </UiCard>
      </div>

      <UiCard flat class="reward-ops-form-card">
        <div class="reward-ops-form-head">
          <h2>商品编辑</h2>
          <p>用一张表单完成新增、编辑与停用。</p>
        </div>
        <div class="reward-ops-form-grid">
          <UiInput v-model.trim="itemForm.itemId" placeholder="商品 ID（编辑时填写）" />
          <UiInput v-model.trim="itemForm.itemName" placeholder="商品名称" />
          <UiInput v-model.trim="itemForm.itemDesc" placeholder="商品说明" />
          <UiInput v-model.trim="itemForm.costBalance" placeholder="积分成本" />
          <UiInput v-model.trim="itemForm.stock" placeholder="库存" />
          <UiInput v-model.trim="itemForm.perUserLimit" placeholder="每人限兑" />
          <select v-model="itemForm.fulfillmentMode" class="input">
            <option value="AUTO">自动发放</option>
            <option value="MANUAL">人工发放</option>
          </select>
          <select v-model="itemForm.status" class="input">
            <option value="ACTIVE">启用</option>
            <option value="INACTIVE">停用</option>
          </select>
          <UiButton :disabled="savingItem" @click="saveItem">{{ savingItem ? '保存中…' : '保存商品' }}</UiButton>
        </div>
      </UiCard>

      <div class="reward-ops-section">
        <h3>商品列表</h3>
        <div class="reward-ops-item-grid">
          <UiCard v-for="item in state.items" :key="item.id" flat class="reward-ops-item-card">
            <div class="reward-ops-item-head">
              <strong>{{ item.itemName }}</strong>
              <UiTag v-if="item.warningLabel">{{ item.warningLabel }}</UiTag>
            </div>
            <p>{{ item.itemDesc || '暂无说明' }}</p>
            <div class="reward-ops-item-meta">
              <span>{{ item.status }}</span>
              <span>{{ item.costBalance }} 积分</span>
              <span>库存 {{ item.stock }}</span>
            </div>
          </UiCard>
        </div>
      </div>

      <div class="reward-ops-section">
        <h3>订单队列</h3>
        <div class="reward-ops-order-list">
          <div v-for="order in state.orders" :key="order.id" class="reward-ops-order-row">
            <div class="reward-ops-order-main">
              <strong>{{ order.itemNameSnapshot }}</strong>
              <span class="muted">{{ order.statusLabel }}</span>
            </div>
            <div class="reward-ops-order-actions">
              <UiButton v-if="order.canFulfill" :disabled="processingOrderId === order.id" @click="processOrder(order.id, 'FULFILL')">发放</UiButton>
              <UiButton v-if="order.canCancel" variant="secondary" :disabled="processingOrderId === order.id" @click="processOrder(order.id, 'CANCEL')">取消</UiButton>
              <UiButton v-if="order.canRefund" variant="secondary" :disabled="processingOrderId === order.id" @click="processOrder(order.id, 'REFUND')">退款</UiButton>
            </div>
          </div>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  getAdminRewardMetrics,
  listAdminRewardItems,
  listAdminRewardOrders,
  processAdminRewardOrder,
  upsertAdminRewardItem
} from '../api/services/adminRewardOpsService'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiTag from '../components/ui/UiTag.vue'
import { buildGrowthAdminState } from './growthAdminState'

const loading = ref(false)
const savingItem = ref(false)
const processingOrderId = ref(0)
const error = ref('')
const items = ref([])
const orders = ref([])
const metrics = ref({})

const itemForm = ref({
  itemId: '',
  itemName: '',
  itemDesc: '',
  costBalance: '',
  stock: '',
  perUserLimit: '',
  fulfillmentMode: 'AUTO',
  status: 'ACTIVE'
})

const state = computed(() =>
  buildGrowthAdminState({
    accounts: [],
    items: items.value,
    orders: orders.value
  })
)

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const [itemResp, orderResp, metricsResp] = await Promise.all([
      listAdminRewardItems(),
      listAdminRewardOrders(),
      getAdminRewardMetrics()
    ])
    items.value = itemResp.data
    orders.value = orderResp.data
    metrics.value = metricsResp.data
  } catch (e) {
    error.value = e?.message || '加载奖励运营后台失败'
  } finally {
    loading.value = false
  }
}

async function saveItem() {
  savingItem.value = true
  error.value = ''
  try {
    await upsertAdminRewardItem({
      itemId: itemForm.value.itemId ? Number(itemForm.value.itemId) : undefined,
      itemName: itemForm.value.itemName,
      itemDesc: itemForm.value.itemDesc,
      costBalance: Number(itemForm.value.costBalance || 0),
      stock: Number(itemForm.value.stock || 0),
      perUserLimit: Number(itemForm.value.perUserLimit || 0),
      fulfillmentMode: itemForm.value.fulfillmentMode,
      status: itemForm.value.status
    })
    await reload()
  } catch (e) {
    error.value = e?.message || '保存商品失败'
  } finally {
    savingItem.value = false
  }
}

async function processOrder(orderId, action) {
  processingOrderId.value = Number(orderId || 0)
  error.value = ''
  try {
    await processAdminRewardOrder({
      orderId,
      action,
      note: 'ops action',
      confirm: true
    })
    await reload()
  } catch (e) {
    error.value = e?.message || '处理订单失败'
  } finally {
    processingOrderId.value = 0
  }
}

onMounted(reload)
</script>

<style scoped>
.reward-ops-page {
  max-width: 1120px;
  margin: 0 auto;
  gap: var(--space-5);
}

.reward-ops-shell,
.reward-ops-section,
.reward-ops-item-grid,
.reward-ops-order-list {
  display: grid;
  gap: 16px;
}

.reward-ops-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.reward-ops-metric-card,
.reward-ops-form-card,
.reward-ops-item-card {
  display: grid;
  gap: 10px;
}

.reward-ops-form-head h2,
.reward-ops-section h3 {
  margin: 0;
}

.reward-ops-form-head p,
.reward-ops-item-card p {
  margin: 0;
  color: var(--text-2);
}

.reward-ops-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: center;
}

.reward-ops-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.reward-ops-metric-card strong {
  font-size: 2rem;
  line-height: 1;
}

.reward-ops-item-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.reward-ops-item-head,
.reward-ops-item-meta,
.reward-ops-order-row,
.reward-ops-order-actions {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.reward-ops-item-meta {
  color: var(--text-2);
  flex-wrap: wrap;
}

.reward-ops-order-row {
  padding: 12px 14px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border);
}

.reward-ops-order-main {
  display: grid;
  gap: 4px;
}

@media (max-width: 960px) {
  .reward-ops-metrics,
  .reward-ops-form-grid,
  .reward-ops-item-grid {
    grid-template-columns: 1fr;
  }

  .reward-ops-order-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
