<template>
  <div class="page market-page">
    <UiBreadcrumb />

    <UiPageHeader>
      <template #title>发布商品</template>
      <template #subtitle>先决定商品类型，再填履约字段。虚拟商品继续区分自动交付和手工交付；实物商品只保留最小必填的库存与描述。</template>
    </UiPageHeader>

    <UiCard class="market-panel">
      <UiPageHeader>
        <template #title>发布商品</template>
        <template #subtitle>统一市场入口按 goodsType 区分商品，不再拆成独立虚拟市场页面。</template>
      </UiPageHeader>

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
          <UiInput v-model="form.title" placeholder="例如：Steam 兑换码" />
        </label>
        <label class="market-field">
          <span>描述</span>
          <textarea v-model="form.description" class="market-textarea" placeholder="说明交付内容与适用范围" />
        </label>
        <label class="market-field">
          <span>价格</span>
          <UiInput v-model.number="form.unitPrice" type="number" min="1" placeholder="输入积分价格" />
        </label>
        <label v-if="isVirtual" class="market-field">
          <span>交付方式</span>
          <select v-model="form.deliveryMode" class="market-select">
            <option value="PRELOADED">自动交付</option>
            <option value="MANUAL">卖家手工交付</option>
          </select>
        </label>
        <label class="market-field">
          <span>库存数量</span>
          <UiInput v-model.number="form.stockTotal" type="number" min="1" placeholder="输入库存数量" />
        </label>
        <label v-if="isVirtual && form.deliveryMode === 'PRELOADED'" class="market-field">
          <span>预存内容</span>
          <textarea
            v-model="inventoryText"
            class="market-textarea"
            placeholder="每行一条卡密或兑换码；手工交付商品可留空"
          />
        </label>
      </div>

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
import { computed, ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { createMarketListing } from '../api/services/marketService'

const form = ref({
  goodsType: 'VIRTUAL',
  title: '',
  description: '',
  unitPrice: 1999,
  deliveryMode: 'PRELOADED',
  stockTotal: 1,
  minPurchaseQuantity: 1,
  maxPurchaseQuantity: 1
})
const inventoryText = ref('')
const submitting = ref(false)
const message = ref('发布后可从“我的出售”继续管理库存和订单。')
const isVirtual = computed(() => form.value.goodsType === 'VIRTUAL')

async function submit() {
  const payloads = inventoryText.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)

  if (isVirtual.value && form.value.deliveryMode === 'PRELOADED' && payloads.length === 0) {
    message.value = '自动交付商品至少需要一条预存内容。'
    return
  }

  submitting.value = true
  message.value = ''
  try {
    const payload = {
      goodsType: form.value.goodsType,
      title: form.value.title,
      description: form.value.description,
      unitPrice: Number(form.value.unitPrice || 0),
      stockTotal: Number(form.value.stockTotal || 0),
      minPurchaseQuantity: Number(form.value.minPurchaseQuantity || 1),
      maxPurchaseQuantity: Number(form.value.maxPurchaseQuantity || 1)
    }

    if (isVirtual.value) {
      payload.deliveryMode = form.value.deliveryMode
      payload.stockMode = 'FINITE'
      if (form.value.deliveryMode === 'PRELOADED') {
        payload.inventory = { payloadType: 'CODE', payloads }
      }
    }

    await createMarketListing(payload)
    message.value = '发布成功，继续前往我的出售查看商品状态。'
    inventoryText.value = ''
  } catch (e) {
    message.value = e?.message || '发布失败'
  } finally {
    submitting.value = false
  }
}
</script>
