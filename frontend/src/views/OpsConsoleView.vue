<template>
  <div class="page" style="max-width: 860px; margin: 0 auto">
    <UiCard flat>
      <UiPageHeader>
        <template #title>Ops Console</template>
        <template #subtitle>高风险运维入口（仅管理员）。break-glass 默认关闭，执行前请确认配置。</template>
      </UiPageHeader>
    </UiCard>

    <UiCard style="margin-top: 12px">
      <div class="stack" style="gap: 12px">
        <div style="font-weight: 800">Ops Token</div>
        <div class="muted" style="font-size: 12px">
          说明：该 token 仅用于本次浏览器会话内发送，不会写入本地存储；页面刷新后会清空。
        </div>
        <UiInput v-model.trim="opsToken" type="password" placeholder="X-Ops-Token（可留空）" autocomplete="off" />
      </div>
    </UiCard>

    <UiCard style="margin-top: 12px">
      <div class="stack" style="gap: 12px">
        <div style="font-weight: 800">Search - 重建索引</div>
        <div class="muted" style="font-size: 12px">
          常见前置条件：<br />
          1) <code>OPS_SEARCH_REINDEX_ENABLED=true</code>（临时开启）<br />
          2) <code>OPS_SEARCH_REINDEX_ALLOWLIST</code> 命中（来源 IP/CIDR）<br />
          3) <code>OPS_SEARCH_TOKEN</code>（即 X-Ops-Token）正确<br />
          4) Redis 可用（用于 single-flight + rate limit；不可用时 fail-closed）
        </div>

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
import UiInput from '../components/ui/UiInput.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiModalConfirm from '../components/ui/UiModalConfirm.vue'

const emit = defineEmits(['trace'])
const showToast = inject('showToast', () => {})

const opsToken = ref('')
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
    const { data, traceId } = await reindex({ opsToken: opsToken.value })
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

