<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>发布商品</template>
      <template #subtitle>按发布流程填写交易信息、履约信息和库存预存，提交后从“我的出售”继续管理。</template>
    </UiPageHeader>

    <UiCard class="market-panel">
      <UiPageHeader>
        <template #title>发布流程</template>
        <template #subtitle>先确认商品类型，再填写价格、履约和库存；自动交付商品必须预存内容。</template>
      </UiPageHeader>

      <section class="market-workflow-section" aria-label="交易信息">
        <h2>交易信息</h2>
        <div class="market-form-grid market-form-grid--wide">
          <label class="market-field">
            <span>商品类型</span>
            <select v-model="form.goodsType" class="market-select">
              <option value="VIRTUAL">虚拟商品</option>
              <option value="PHYSICAL">实物商品</option>
            </select>
          </label>
          <label class="market-field">
            <span>标题</span>
            <input v-model="form.title" class="input" placeholder="例如：Steam 兑换码" />
          </label>
          <label class="market-field">
            <span>描述</span>
            <textarea v-model="form.description" class="market-textarea" placeholder="说明交付内容与适用范围" />
          </label>
          <label class="market-field">
            <span>价格</span>
            <input v-model.number="form.unitPrice" class="input" type="number" min="1" placeholder="输入积分价格" />
          </label>
        </div>
      </section>

      <section class="market-workflow-section" aria-label="履约信息">
        <h2>履约信息</h2>
        <div class="market-form-grid market-form-grid--wide">
          <label v-if="isVirtual" class="market-field">
            <span>交付方式</span>
            <select v-model="form.deliveryMode" class="market-select">
              <option value="PRELOADED">自动交付</option>
              <option value="MANUAL">卖家手工交付</option>
            </select>
          </label>
          <label class="market-field">
            <span>库存数量</span>
            <input v-model.number="form.stockTotal" class="input" type="number" min="1" placeholder="输入库存数量" />
          </label>
        </div>
      </section>

      <section v-if="isVirtual && form.deliveryMode === 'PRELOADED'" class="market-workflow-section" aria-label="库存预存">
        <h2>库存预存</h2>
        <div class="market-form-grid">
          <label class="market-field">
            <span>预存内容</span>
            <textarea
              v-model="inventoryText"
              class="market-textarea"
              placeholder="每行一条卡密或兑换码；手工交付商品可留空"
            />
          </label>
        </div>
      </section>

      <div class="market-inline-actions">
        <UiButton :disabled="submitting" @click="submit">
          {{ submitting ? '发布中…' : '确认发布' }}
        </UiButton>
        <span class="muted">{{ message }}</span>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { createMarketListing } from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { normalizeOpaqueId } from '../utils/opaqueId'

const DEFAULT_MESSAGE = '发布后可从“我的出售”继续管理库存和订单。'
const auth = useAuthStore()
const form = ref(emptyListingForm())
const inventoryText = ref('')
const submitting = ref(false)
const message = ref(DEFAULT_MESSAGE)
let submitGeneration = 0

const isVirtual = computed(() => form.value.goodsType === 'VIRTUAL')
const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))

function emptyListingForm() {
  return {
    goodsType: 'VIRTUAL',
    title: '',
    description: '',
    unitPrice: 1999,
    deliveryMode: 'PRELOADED',
    stockTotal: 1,
    minPurchaseQuantity: 1,
    maxPurchaseQuantity: 1
  }
}

function isCurrentSubmit(generation, scope) {
  return generation === submitGeneration && scope === sessionScope.value && auth.authed
}

async function submit() {
  if (submitting.value || !auth.authed) return

  const payloads = inventoryText.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)

  if (isVirtual.value && form.value.deliveryMode === 'PRELOADED' && payloads.length === 0) {
    message.value = '自动交付商品至少需要一条预存内容。'
    return
  }

  const generation = ++submitGeneration
  const scope = sessionScope.value
  const currentForm = { ...form.value }
  submitting.value = true
  message.value = ''
  try {
    const payload = {
      goodsType: currentForm.goodsType,
      title: currentForm.title,
      description: currentForm.description,
      unitPrice: Number(currentForm.unitPrice || 0),
      stockTotal: Number(currentForm.stockTotal || 0),
      minPurchaseQuantity: Number(currentForm.minPurchaseQuantity || 1),
      maxPurchaseQuantity: Number(currentForm.maxPurchaseQuantity || 1)
    }

    if (currentForm.goodsType === 'VIRTUAL') {
      payload.deliveryMode = currentForm.deliveryMode
      payload.stockMode = 'FINITE'
      if (currentForm.deliveryMode === 'PRELOADED') {
        payload.inventory = { payloadType: 'CODE', payloads }
      }
    }

    await createMarketListing(payload)
    if (!isCurrentSubmit(generation, scope)) return
    message.value = '发布成功，继续前往我的出售查看商品状态。'
    inventoryText.value = ''
  } catch (e) {
    if (!isCurrentSubmit(generation, scope)) return
    message.value = e?.message || '发布失败'
  } finally {
    if (isCurrentSubmit(generation, scope)) submitting.value = false
  }
}

watch(sessionScope, () => {
  submitGeneration += 1
  form.value = emptyListingForm()
  inventoryText.value = ''
  submitting.value = false
  message.value = DEFAULT_MESSAGE
})

onBeforeUnmount(() => {
  submitGeneration += 1
})
</script>
