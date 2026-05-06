<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiEmpty v-if="error" type="error">{{ error }}</UiEmpty>
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

        <UiEmpty v-if="inventoryItems.length === 0">
          暂无库存
          <template #description>提交新的卡密或兑换码后，这里会显示库存状态和失效动作。</template>
        </UiEmpty>

        <div v-else class="market-order-list">
          <article v-for="item in inventoryItems" :key="item.inventoryUnitId" class="market-order-row">
            <div>
              <strong>{{ item.payloadContent }}</strong>
              <p>{{ item.payloadType }} · {{ item.status }}</p>
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
      </UiCard>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiEmpty from '../components/ui/UiEmpty.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import {
  addMarketInventory,
  invalidateMarketInventory,
  listMarketInventory
} from '../api/services/marketService'

const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const message = ref('库存页会直接反映当前可售内容。')
const payloadType = ref('CODE')
const inventoryText = ref('')
const inventory = ref([])

const inventoryItems = computed(() => (Array.isArray(inventory.value) ? inventory.value : []))

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const { data } = await listMarketInventory(route.params.listingId)
    inventory.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e?.message || '加载库存失败'
  } finally {
    loading.value = false
  }
}

async function submitInventory() {
  const payloads = inventoryText.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  if (payloads.length === 0) {
    message.value = '请至少输入一条库存内容。'
    return
  }

  submitting.value = true
  message.value = ''
  try {
    await addMarketInventory(route.params.listingId, {
      payloadType: payloadType.value,
      payloads
    })
    inventoryText.value = ''
    message.value = '库存已追加。'
    await reload()
  } catch (e) {
    message.value = e?.message || '追加库存失败'
  } finally {
    submitting.value = false
  }
}

async function invalidateItem(inventoryUnitId) {
  submitting.value = true
  message.value = ''
  try {
    await invalidateMarketInventory(inventoryUnitId)
    message.value = '库存已失效。'
    await reload()
  } catch (e) {
    message.value = e?.message || '失效库存失败'
  } finally {
    submitting.value = false
  }
}

watch(
  () => route.params.listingId,
  () => {
    reload()
  },
  { immediate: true }
)
</script>
