<template>
  <div class="page ops-page">
    <UiCard flat class="admin-page-header">
      <UiPageHeader>
        <template #title>Ops Console</template>
        <template #subtitle>用于承载高风险但低频的运维动作，必须明确风险、范围和结果。</template>
      </UiPageHeader>
    </UiCard>

    <UiCard class="ops-card">
      <div class="ops-head">
        <div>
          <div class="ops-eyebrow">高风险操作</div>
          <div class="ops-title">Search · 重建索引</div>
        </div>
      </div>

      <div class="ops-copy muted">
        该操作会触发全文索引的重建，可能带来较高的瞬时负载。它应该是“明确知道为什么要做”时才使用的动作，而不是常规刷新手段。
      </div>

      <div class="ops-risk-box">
        <div class="ops-risk-title">执行前确认</div>
        <ul class="ops-risk-list">
          <li>确定当前搜索结果异常不是索引延迟导致</li>
          <li>确认当前时间窗口允许额外负载</li>
          <li>准备记录返回的 jobId 与处理数量</li>
        </ul>
      </div>

      <div class="ops-actions">
        <UiButton variant="secondary" :disabled="loading" @click="openConfirm">重建索引</UiButton>
      </div>

      <div v-if="error" class="error">{{ error }}</div>
      <div v-if="successMsg" class="success">{{ successMsg }}</div>
    </UiCard>

    <UiModalConfirm
      v-if="confirmOpen"
      title="确认重建索引"
      message="该操作可能耗时较长并影响线上性能；确认继续？"
      confirm-text="继续"
      confirm-variant="danger"
      @cancel="confirmOpen = false"
      @confirm="onConfirm"
    />
  </div>
</template>

<script setup>
import { inject, ref } from 'vue'
import { reindex } from '../api/services/searchService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'

const emit = defineEmits(['trace'])
const showToast = inject('showToast', () => {})

const confirmOpen = ref(false)
const loading = ref(false)
const error = ref('')
const successMsg = ref('')

function openConfirm() {
  error.value = ''
  successMsg.value = ''
  confirmOpen.value = true
}

async function onConfirm() {
  confirmOpen.value = false
  error.value = ''
  successMsg.value = ''
  loading.value = true
  try {
    const { data, traceId } = await reindex()
    emit('trace', traceId || '')
    const count = Number(data?.indexedCount || 0)
    const jobId = String(data?.jobId || '').trim()
    successMsg.value = `已处理 ${count} 条${jobId ? `（jobId=${jobId}）` : ''}`
    showToast({ type: 'success', title: '重建完成', text: successMsg.value })
  } catch (e) {
    const code = e?.code
    const msg = e?.message || '请求失败'
    error.value = code ? `${msg}（code=${code}）` : msg
    showToast({ type: 'error', title: '重建失败', text: error.value })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.ops-page {
  max-width: 920px;
}

.ops-card {
  display: grid;
  gap: 16px;
}

.ops-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ops-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-3);
  margin-bottom: 4px;
}

.ops-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-1);
}

.ops-copy {
  font-size: 14px;
  line-height: 1.7;
}

.ops-risk-box {
  border: 1px solid color-mix(in srgb, var(--danger) 28%, var(--border) 72%);
  background: color-mix(in srgb, var(--danger) 5%, var(--surface) 95%);
  border-radius: 18px;
  padding: 16px;
}

.ops-risk-title {
  font-size: 14px;
  font-weight: 800;
  color: var(--text-1);
  margin-bottom: 8px;
}

.ops-risk-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-2);
  display: grid;
  gap: 6px;
}

.ops-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
