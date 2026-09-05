<template>
  <div class="page market-inventory-page">
    <UiBreadcrumb />

    <UiSkeleton v-if="loading" variant="list" :rows="3" label="正在加载库存" />
    <UiState v-else-if="error" variant="error" :title="error">
      <template #description>库存加载失败，可以重试或返回我的出售。</template>
      <template #actions>
        <UiButton variant="secondary" :disabled="loading" data-test="inventory-retry" @click="reload">重试</UiButton>
      </template>
    </UiState>

    <template v-else>
      <UiPageHeader>
        <template #title>库存管理</template>
        <template #subtitle>预存库存商品在这里维护卡密或兑换码，并及时失效不应继续出售的内容。商品 ID：{{ route.params.listingId }}</template>
      </UiPageHeader>

      <section class="inventory-panel" aria-labelledby="inventory-add-heading">
        <h2 id="inventory-add-heading" class="inventory-heading">追加库存</h2>
        <div class="inventory-form-grid">
          <UiField label="内容类型">
            <UiSelect v-model="payloadType" :options="payloadTypeOptions" :disabled="submitting" />
          </UiField>
          <UiField
            label="追加库存"
            :error="inventoryError"
            help="每行一条库存内容，例如一行一个兑换码"
          >
            <UiTextarea
              v-model="inventoryText"
              placeholder="每行一条库存内容，例如一行一个兑换码"
              :disabled="submitting"
            />
          </UiField>
        </div>

        <div class="inventory-actions">
          <UiButton data-test="inventory-add-submit" :disabled="submitting" @click="submitInventory">
            {{ submitting ? '提交中…' : '追加库存' }}
          </UiButton>
          <p v-if="actionError" class="error inventory-alert" role="alert">{{ actionError }}</p>
          <p v-else-if="message" class="inventory-message" role="status">{{ message }}</p>
        </div>
      </section>

      <section class="inventory-panel" aria-labelledby="inventory-list-heading">
        <header class="inventory-list-head">
          <h2 id="inventory-list-heading" class="inventory-heading">库存内容</h2>
          <span v-if="state.inventory.length > 0" class="inventory-count">{{ state.inventory.length }} 条</span>
        </header>

        <UiState v-if="inventoryItems.length === 0">
          暂无库存
          <template #description>提交新的卡密或兑换码后，这里会显示库存状态和失效动作。</template>
        </UiState>

        <UiTable
          v-else
          :columns="inventoryColumns"
          :rows="sortedInventory"
          row-key="inventoryUnitId"
          caption="预存库存内容列表"
          :sort-key="inventorySort.key"
          :sort-direction="inventorySort.direction"
          data-test="inventory-table"
          @sort="onSort"
        >
          <template #cell-payloadType="{ row }">
            {{ row.payloadTypeLabel }}
          </template>
          <template #cell-status="{ row }">
            <UiBadge :variant="row.statusVariant">{{ row.statusLabel }}</UiBadge>
          </template>
          <template #cell-action="{ row }">
            <UiButton
              v-if="row.status === 'AVAILABLE'"
              variant="secondary"
              class="inventory-invalidate-btn"
              :disabled="submitting"
              @click="requestInvalidate(row)"
            >
              失效
            </UiButton>
            <span v-else class="inventory-no-action" aria-hidden="true">—</span>
          </template>
        </UiTable>

        <p v-if="pageError" class="error inventory-inline-error" role="alert">{{ pageError }}</p>
        <div v-if="loadingMore || hasNext" class="inventory-feed-tail">
          <UiButton v-if="loadingMore" variant="ghost" disabled>
            <LoaderCircle :size="14" aria-hidden="true" class="inventory-feed-spinner" />
            正在加载…
          </UiButton>
          <UiButton v-else variant="secondary" data-test="inventory-load-more" @click="loadMore">加载更多</UiButton>
        </div>
        <p v-else-if="state.inventory.length > 0" class="inventory-feed-end">已经到底了</p>
      </section>
    </template>

    <UiModalConfirm
      v-if="invalidateTarget"
      title="失效库存内容"
      :message="invalidateMessage"
      confirm-text="确认失效"
      confirm-variant="danger"
      @cancel="closeInvalidate"
      @confirm="confirmInvalidate"
    />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { LoaderCircle } from 'lucide-vue-next'
import UiBadge from '../components/ui/UiBadge.vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiField from '../components/ui/UiField.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiState from '../components/ui/UiState.vue'
import UiSkeleton from '../components/ui/UiSkeleton.vue'
import UiTable from '../components/ui/UiTable.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  addMarketInventory,
  invalidateMarketInventory,
  listMarketInventory
} from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState, mergeMarketPage, nextTableSort, sortMarketInventory } from './marketState'

const DEFAULT_MESSAGE = '库存页会直接反映当前可售内容。'
const PAYLOAD_TYPE_OPTIONS = Object.freeze([
  { value: 'CODE', label: '兑换码' },
  { value: 'TEXT', label: '文本' },
  { value: 'LINK', label: '链接' }
])
// UiTable 列合同：内容 / 类型 / 状态可横向比较，类型与状态列挂排序钩子，操作列承载失效动作。
const INVENTORY_COLUMNS = Object.freeze([
  { key: 'payloadContent', label: '内容' },
  { key: 'payloadType', label: '类型', sortable: true },
  { key: 'status', label: '状态', sortable: true },
  { key: 'action', label: '操作' }
])

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const loadingMore = ref(false)
const submitting = ref(false)
const error = ref('')
const pageError = ref('')
const message = ref(DEFAULT_MESSAGE)
const inventoryError = ref('')
const actionError = ref('')
const payloadType = ref('CODE')
const inventoryText = ref('')
const inventory = ref([])
const inventorySort = ref({ key: '', direction: 'asc' })
const invalidateTarget = ref(null)
const page = ref(0)
const hasNext = ref(false)
const pageSize = 20
let requestGeneration = 0
let actionGeneration = 0

const inventoryItems = computed(() => (Array.isArray(inventory.value) ? inventory.value : []))
const state = computed(() => buildMarketState({ inventory: inventoryItems.value }))
const sortedInventory = computed(() => sortMarketInventory(state.value.inventory, inventorySort.value))
const inventoryColumns = INVENTORY_COLUMNS
const payloadTypeOptions = PAYLOAD_TYPE_OPTIONS
const invalidateMessage = computed(() => {
  const target = invalidateTarget.value
  if (!target) return ''
  return `失效后「${target.payloadContent}」不再可售，且无法恢复为可售状态。确认继续？`
})
const viewScope = computed(() => [
  normalizeOpaqueId(route.params.listingId),
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))

function isCurrentRequest(generation, scope) {
  return generation === requestGeneration && scope === viewScope.value
}

function isCurrentAction(generation, scope) {
  return generation === actionGeneration && scope === viewScope.value
}

async function reload() {
  const generation = ++requestGeneration
  const scope = viewScope.value
  const listingId = normalizeOpaqueId(route.params.listingId)
  loading.value = true
  loadingMore.value = false
  error.value = ''
  pageError.value = ''
  try {
    const { data, hasNext: nextAvailable, page: loadedPage } = await listMarketInventory(
      listingId,
      { page: 0, size: pageSize }
    )
    if (!isCurrentRequest(generation, scope)) return
    inventory.value = Array.isArray(data) ? data : []
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    if (!isCurrentRequest(generation, scope)) return
    error.value = e?.message || '加载库存失败'
  } finally {
    if (isCurrentRequest(generation, scope)) loading.value = false
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasNext.value) return
  loadingMore.value = true
  pageError.value = ''
  const targetPage = page.value + 1
  const generation = ++requestGeneration
  const scope = viewScope.value
  const listingId = normalizeOpaqueId(route.params.listingId)
  try {
    const { data, hasNext: nextAvailable, page: loadedPage } = await listMarketInventory(
      listingId,
      { page: targetPage, size: pageSize }
    )
    if (!isCurrentRequest(generation, scope)) return
    inventory.value = mergeMarketPage(inventory.value, data, 'inventoryUnitId')
    page.value = loadedPage
    hasNext.value = nextAvailable
  } catch (e) {
    if (!isCurrentRequest(generation, scope)) return
    pageError.value = e?.message || '加载更多库存失败'
  } finally {
    if (isCurrentRequest(generation, scope)) loadingMore.value = false
  }
}

function onSort(columnKey) {
  inventorySort.value = nextTableSort(inventorySort.value, columnKey)
}

async function submitInventory() {
  if (!auth.authed || submitting.value) return
  const payloads = inventoryText.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  inventoryError.value = ''
  actionError.value = ''
  if (payloads.length === 0) {
    inventoryError.value = '请至少输入一条库存内容。'
    return
  }

  const generation = ++actionGeneration
  const scope = viewScope.value
  const listingId = normalizeOpaqueId(route.params.listingId)
  submitting.value = true
  message.value = ''
  try {
    await addMarketInventory(listingId, {
      payloadType: payloadType.value,
      payloads
    })
    if (!isCurrentAction(generation, scope)) return
    inventoryText.value = ''
    message.value = '库存已追加。'
    await reload()
  } catch (e) {
    if (!isCurrentAction(generation, scope)) return
    actionError.value = e?.message || '追加库存失败'
  } finally {
    if (isCurrentAction(generation, scope)) submitting.value = false
  }
}

// 失效库存影响可售内容且不可恢复，属于资损/影响他人操作，先经 UiModalConfirm 复述内容再执行。
function requestInvalidate(item) {
  if (!auth.authed || submitting.value) return
  invalidateTarget.value = item
}

function closeInvalidate() {
  invalidateTarget.value = null
}

async function confirmInvalidate() {
  const target = invalidateTarget.value
  closeInvalidate()
  if (!target) return
  await invalidateItem(target.inventoryUnitId)
}

async function invalidateItem(inventoryUnitId) {
  if (!auth.authed || submitting.value) return
  const generation = ++actionGeneration
  const scope = viewScope.value
  submitting.value = true
  message.value = ''
  actionError.value = ''
  try {
    await invalidateMarketInventory(inventoryUnitId)
    if (!isCurrentAction(generation, scope)) return
    message.value = '库存已失效。'
    await reload()
  } catch (e) {
    if (!isCurrentAction(generation, scope)) return
    actionError.value = e?.message || '失效库存失败'
  } finally {
    if (isCurrentAction(generation, scope)) submitting.value = false
  }
}

watch(
  viewScope,
  () => {
    requestGeneration += 1
    actionGeneration += 1
    inventory.value = []
    inventorySort.value = { key: '', direction: 'asc' }
    invalidateTarget.value = null
    page.value = 0
    hasNext.value = false
    loading.value = false
    loadingMore.value = false
    submitting.value = false
    error.value = ''
    pageError.value = ''
    message.value = DEFAULT_MESSAGE
    inventoryError.value = ''
    actionError.value = ''
    payloadType.value = 'CODE'
    inventoryText.value = ''
    if (auth.authed && normalizeOpaqueId(route.params.listingId)) reload()
  },
  { immediate: true }
)

watch(inventoryText, () => {
  if (inventoryError.value) inventoryError.value = ''
})

onBeforeUnmount(() => {
  requestGeneration += 1
  actionGeneration += 1
})
</script>

<style scoped>
.market-inventory-page {
  gap: var(--space-5);
}

.inventory-panel {
  display: grid;
  gap: var(--space-3);
  padding: var(--card-padding);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.inventory-heading {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.inventory-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 240px) minmax(0, 1fr);
  gap: var(--space-4);
}

.inventory-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
}

.inventory-alert,
.inventory-inline-error {
  margin: 0;
  font-size: var(--text-sm);
}

.inventory-message {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-sm);
}

.inventory-list-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-3);
}

.inventory-count {
  color: var(--text-3);
  font-size: 13px;
  white-space: nowrap;
}

.inventory-no-action {
  color: var(--muted);
}

.inventory-invalidate-btn {
  white-space: nowrap;
}

.inventory-feed-tail {
  display: flex;
  justify-content: center;
}

.inventory-feed-spinner {
  animation: inventory-feed-spin 0.8s linear infinite;
}

@keyframes inventory-feed-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .inventory-feed-spinner {
    animation: none;
  }
}

.inventory-feed-end {
  margin: 0;
  text-align: center;
  color: var(--text-3);
  font-size: 13px;
}

@media (max-width: 900px) {
  .inventory-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
