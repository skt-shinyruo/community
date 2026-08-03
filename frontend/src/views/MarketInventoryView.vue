<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiState v-if="error" variant="error">{{ error }}</UiState>
    <div v-else-if="loading" class="muted">正在加载库存…</div>

    <template v-else>
      <UiPageHeader>
        <template #title>库存管理</template>
        <template #subtitle>预存库存商品在这里维护卡密或兑换码，并及时失效不应继续出售的内容。商品 ID：{{ route.params.listingId }}</template>
      </UiPageHeader>

      <UiCard class="market-panel">
        <div class="market-form-grid market-form-grid--wide">
          <label class="market-field">
            <span>内容类型</span>
            <select v-model="payloadType" class="market-select">
              <option value="CODE">兑换码</option>
              <option value="TEXT">文本</option>
              <option value="LINK">链接</option>
            </select>
          </label>
          <label class="market-field">
            <span>追加库存</span>
            <textarea
              v-model="inventoryText"
              class="market-textarea"
              placeholder="每行一条库存内容，例如一行一个兑换码"
            />
          </label>
        </div>

        <div class="market-inline-actions">
          <UiButton :disabled="submitting" @click="submitInventory">
            {{ submitting ? '提交中…' : '追加库存' }}
          </UiButton>
          <span class="muted">{{ message }}</span>
        </div>

        <UiState v-if="inventoryItems.length === 0">
          暂无库存
          <template #description>提交新的卡密或兑换码后，这里会显示库存状态和失效动作。</template>
        </UiState>

        <div v-else class="market-order-list">
          <article v-for="item in state.inventory" :key="item.inventoryUnitId" class="market-order-row">
            <div>
              <strong>{{ item.payloadContent }}</strong>
              <p>{{ item.payloadType }} · {{ item.statusLabel }}</p>
            </div>
            <UiButton
              v-if="item.status === 'AVAILABLE'"
              variant="secondary"
              :disabled="submitting"
              @click="invalidateItem(item.inventoryUnitId)"
            >
              失效
            </UiButton>
          </article>
        </div>

        <div v-if="hasNext" class="market-inline-actions">
          <UiButton variant="secondary" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </UiButton>
        </div>
        <UiState v-if="pageError" variant="error">{{ pageError }}</UiState>
      </UiCard>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiState from '../components/ui/UiState.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  addMarketInventory,
  invalidateMarketInventory,
  listMarketInventory
} from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'
import { buildMarketState, mergeMarketPage } from './marketState'

const route = useRoute()
const auth = useAuthStore()
const loading = ref(false)
const loadingMore = ref(false)
const submitting = ref(false)
const error = ref('')
const pageError = ref('')
const message = ref('库存页会直接反映当前可售内容。')
const payloadType = ref('CODE')
const inventoryText = ref('')
const inventory = ref([])
const page = ref(0)
const hasNext = ref(false)
const pageSize = 20
let requestGeneration = 0
let actionGeneration = 0

const inventoryItems = computed(() => (Array.isArray(inventory.value) ? inventory.value : []))
const state = computed(() => buildMarketState({ inventory: inventoryItems.value }))
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

async function submitInventory() {
  if (!auth.authed || submitting.value) return
  const payloads = inventoryText.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  if (payloads.length === 0) {
    message.value = '请至少输入一条库存内容。'
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
    message.value = e?.message || '追加库存失败'
  } finally {
    if (isCurrentAction(generation, scope)) submitting.value = false
  }
}

async function invalidateItem(inventoryUnitId) {
  if (!auth.authed || submitting.value) return
  const generation = ++actionGeneration
  const scope = viewScope.value
  submitting.value = true
  message.value = ''
  try {
    await invalidateMarketInventory(inventoryUnitId)
    if (!isCurrentAction(generation, scope)) return
    message.value = '库存已失效。'
    await reload()
  } catch (e) {
    if (!isCurrentAction(generation, scope)) return
    message.value = e?.message || '失效库存失败'
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
    page.value = 0
    hasNext.value = false
    loading.value = false
    loadingMore.value = false
    submitting.value = false
    error.value = ''
    pageError.value = ''
    message.value = '库存页会直接反映当前可售内容。'
    payloadType.value = 'CODE'
    inventoryText.value = ''
    if (auth.authed && normalizeOpaqueId(route.params.listingId)) reload()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  requestGeneration += 1
  actionGeneration += 1
})
</script>
