<template>
  <div class="page market-publish-page">
    <nav aria-label="页面层级">
      <UiButton variant="ghost" :to="{ name: 'market' }">
        <ArrowLeft :size="16" aria-hidden="true" />
        返回市场
      </UiButton>
    </nav>

    <UiPageHeader>
      <template #title>发布商品</template>
      <template #subtitle>按发布流程填写交易信息、履约信息和库存预存，提交后从“我的出售”继续管理。</template>
    </UiPageHeader>

    <section class="publish-panel" aria-labelledby="publish-workflow-heading">
      <h2 id="publish-workflow-heading" class="publish-heading">发布流程</h2>
      <p class="publish-intro">先确认商品类型，再填写价格、履约和库存；自动交付商品必须预存内容。</p>

      <section class="publish-section" aria-label="交易信息">
        <h3 class="publish-section-heading">交易信息</h3>
        <div class="publish-form-grid">
          <UiField label="商品类型">
            <UiSelect v-model="form.goodsType" :options="goodsTypeOptions" :disabled="submitting" />
          </UiField>
          <UiField label="标题" required>
            <UiInput v-model="form.title" placeholder="例如：Steam 兑换码" :disabled="submitting" />
          </UiField>
          <UiField label="描述" class="publish-field--span">
            <UiTextarea v-model="form.description" placeholder="说明交付内容与适用范围" :disabled="submitting" />
          </UiField>
          <UiField label="价格" required help="单位：积分">
            <UiInput
              v-model.number="form.unitPrice"
              type="number"
              min="1"
              placeholder="输入积分价格"
              :disabled="submitting"
            />
          </UiField>
        </div>
      </section>

      <section class="publish-section" aria-label="履约信息">
        <h3 class="publish-section-heading">履约信息</h3>
        <div class="publish-form-grid">
          <UiField v-if="isVirtual" label="交付方式">
            <UiSelect v-model="form.deliveryMode" :options="deliveryModeOptions" :disabled="submitting" />
          </UiField>
          <UiField label="库存数量" required>
            <UiInput
              v-model.number="form.stockTotal"
              type="number"
              min="1"
              placeholder="输入库存数量"
              :disabled="submitting"
            />
          </UiField>
        </div>
      </section>

      <section v-if="isVirtual && form.deliveryMode === 'PRELOADED'" class="publish-section" aria-label="库存预存">
        <h3 class="publish-section-heading">库存预存</h3>
        <UiField label="预存内容" required :error="preloadError" help="每行一条卡密或兑换码；手工交付商品可留空">
          <UiTextarea v-model="inventoryText" placeholder="每行一条卡密或兑换码；手工交付商品可留空" :disabled="submitting" />
        </UiField>
      </section>

      <div class="publish-actions">
        <UiButton data-test="publish-submit" :disabled="submitting" @click="submit">
          {{ submitting ? '发布中…' : '确认发布' }}
        </UiButton>
        <p v-if="submitError" class="error publish-alert" role="alert">{{ submitError }}</p>
        <p v-else-if="message" class="publish-message" role="status">{{ message }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ArrowLeft } from 'lucide-vue-next'
import UiButton from '../components/ui/UiButton.vue'
import UiField from '../components/ui/UiField.vue'
import UiInput from '../components/ui/UiInput.vue'
import UiSelect from '../components/ui/UiSelect.vue'
import UiTextarea from '../components/ui/UiTextarea.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import { createMarketListing } from '../api/services/marketService'
import { useAuthStore } from '../stores/auth'
import { createLatestRequestTracker } from '../utils/latestRequest'
import { normalizeOpaqueId } from '../utils/opaqueId'

const DEFAULT_MESSAGE = '发布后可从“我的出售”继续管理库存和订单。'
const GOODS_TYPE_OPTIONS = Object.freeze([
  { value: 'VIRTUAL', label: '虚拟商品' },
  { value: 'PHYSICAL', label: '实物商品' }
])
const DELIVERY_MODE_OPTIONS = Object.freeze([
  { value: 'PRELOADED', label: '自动交付' },
  { value: 'MANUAL', label: '卖家手工交付' }
])

const auth = useAuthStore()
const form = ref(emptyListingForm())
const inventoryText = ref('')
const submitting = ref(false)
// 反馈按规范 6.3 分渠道：可定位到字段的校验错误内联在 UiField，提交失败内联在操作区，
// 说明与成功文案以 role=status 文本播报。
const message = ref(DEFAULT_MESSAGE)
const submitError = ref('')
const preloadError = ref('')

const goodsTypeOptions = GOODS_TYPE_OPTIONS
const deliveryModeOptions = DELIVERY_MODE_OPTIONS

const isVirtual = computed(() => form.value.goodsType === 'VIRTUAL')
const sessionScope = computed(() => [
  auth.tokenGeneration,
  normalizeOpaqueId(auth.userId),
  auth.authed ? 'authenticated' : 'anonymous'
].join(':'))
const submitTracker = createLatestRequestTracker({ getScope: () => sessionScope.value })

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

function isCurrentSubmit(requestHandle) {
  return submitTracker.isCurrent(requestHandle) && auth.authed
}

async function submit() {
  if (submitting.value || !auth.authed) return

  const payloads = inventoryText.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)

  preloadError.value = ''
  submitError.value = ''
  if (isVirtual.value && form.value.deliveryMode === 'PRELOADED' && payloads.length === 0) {
    preloadError.value = '自动交付商品至少需要一条预存内容。'
    return
  }

  const requestHandle = submitTracker.begin()
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
    if (!isCurrentSubmit(requestHandle)) return
    message.value = '发布成功，继续前往我的出售查看商品状态。'
    inventoryText.value = ''
  } catch (e) {
    if (!isCurrentSubmit(requestHandle)) return
    submitError.value = e?.message || '发布失败'
  } finally {
    if (isCurrentSubmit(requestHandle)) submitting.value = false
  }
}

watch(sessionScope, () => {
  submitTracker.invalidate()
  form.value = emptyListingForm()
  inventoryText.value = ''
  submitting.value = false
  message.value = DEFAULT_MESSAGE
  submitError.value = ''
  preloadError.value = ''
})

watch(inventoryText, () => {
  if (preloadError.value) preloadError.value = ''
})

// 商品类型 / 交付方式切换会卸载预存字段，挂起的字段错误随之清除，避免回切后误报。
watch([isVirtual, () => form.value.deliveryMode], () => {
  if (preloadError.value) preloadError.value = ''
})

onBeforeUnmount(() => {
  submitTracker.invalidate()
})
</script>

<style scoped>
.market-publish-page {
  gap: var(--space-5);
}

.publish-panel {
  display: grid;
  gap: var(--space-4);
  padding: var(--card-padding);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.publish-heading {
  margin: 0;
  font-size: var(--text-md);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: 0;
  color: var(--text-1);
}

.publish-intro {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-sm);
}

.publish-section {
  display: grid;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: 1px solid var(--border);
}

.publish-section-heading {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 650;
  letter-spacing: 0;
  color: var(--text-1);
}

.publish-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.publish-field--span {
  grid-column: 1 / -1;
}

.publish-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
  padding-top: var(--space-4);
  border-top: 1px solid var(--border);
}

.publish-alert {
  margin: 0;
  font-size: var(--text-sm);
}

.publish-message {
  margin: 0;
  color: var(--text-3);
  font-size: var(--text-sm);
}

@media (max-width: 900px) {
  .publish-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
