<template>
  <div class="page virtual-market-page">
    <UiBreadcrumb />

    <section class="market-hero market-hero--compact">
      <div>
        <span class="market-kicker">卖家发布</span>
        <h1>先定价格，再决定交付方式</h1>
        <p>预存库存适合卡密和兑换码，手工交付适合邀请码或人工确认型商品。</p>
      </div>
    </section>

    <UiCard class="market-panel">
      <UiPageHeader>
        <template #title>发布商品</template>
        <template #subtitle>第一版只支持固定价一口价，库存和交付内容按最小字段填写即可。</template>
      </UiPageHeader>

      <div class="market-form-grid market-form-grid--wide">
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
        <label class="market-field">
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
        <label class="market-field">
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
import { ref } from 'vue'
import UiBreadcrumb from '../components/ui/UiBreadcrumb.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiCard from '../components/ui/UiCard.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { createVirtualListing } from '../api/services/virtualMarketService'

const form = ref({
  title: '',
  description: '',
  unitPrice: 1999,
  deliveryMode: 'PRELOADED',
  stockMode: 'FINITE',
  stockTotal: 1,
  minPurchaseQuantity: 1,
  maxPurchaseQuantity: 1
})
const inventoryText = ref('')
const submitting = ref(false)
const message = ref('发布后可从“我的出售”继续管理库存和订单。')

async function submit() {
  submitting.value = true
  message.value = ''
  try {
    const payloads = inventoryText.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    const payload = {
      ...form.value,
      inventory: form.value.deliveryMode === 'PRELOADED'
        ? { payloadType: 'CODE', payloads }
        : undefined
    }
    await createVirtualListing(payload)
    message.value = '发布成功，继续前往我的出售查看商品状态。'
    inventoryText.value = ''
  } catch (e) {
    message.value = e?.message || '发布失败'
  } finally {
    submitting.value = false
  }
}
</script>
