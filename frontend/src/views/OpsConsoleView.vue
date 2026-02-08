<template>
  <div class="page" style="max-width: 860px; margin: 0 auto">
    <UiCard flat>
      <UiPageHeader>
        <template #title>Ops Console</template>
        <template #subtitle>高风险运维入口（仅管理员）。请谨慎操作。</template>
      </UiPageHeader>
    </UiCard>

    <UiCard style="margin-top: 12px">
      <div class="stack" style="gap: 12px">
        <div style="font-weight: 800">Search - 重建索引</div>

        <div class="row" style="justify-content: flex-end; gap: 8px; flex-wrap: wrap">
          <UiButton variant="secondary" :disabled="loading" @click="openConfirm">重建索引</UiButton>
        </div>

        <div v-if="error" class="error">{{ error }}</div>
        <div v-if="successMsg" class="success">{{ successMsg }}</div>
      </div>
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
