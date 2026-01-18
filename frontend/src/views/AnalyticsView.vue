<!-- 统计页面：UV/DAU 查询（权限：ADMIN/MODERATOR）。 -->
<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>统计（UV / DAU）</template>
        <template #subtitle>仅管理员/版主可访问</template>
        <template #actions>
          <UiButton @click="query" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</UiButton>
        </template>
      </UiPageHeader>

      <div v-if="!auth.isAdminOrModerator" class="error" style="margin-top: 12px">无权限：仅管理员/版主可访问统计接口。</div>

      <div class="row" style="flex-wrap: wrap; margin-top: 12px">
        <div style="width: 200px">
          <div class="muted" style="font-size: 12px">开始日期</div>
          <UiInput type="date" v-model="start" />
        </div>
        <div style="width: 200px">
          <div class="muted" style="font-size: 12px">结束日期</div>
          <UiInput type="date" v-model="end" />
        </div>
      </div>

      <div v-if="error" class="error" style="margin-top: 12px">{{ error }}</div>

      <UiCard v-else flat style="margin-top: 12px">
        <div class="row" style="justify-content: space-between; flex-wrap: wrap">
          <div>
            UV：<span style="font-weight: 900">{{ uvResult }}</span>
          </div>
          <div>
            DAU：<span style="font-weight: 900">{{ dauResult }}</span>
          </div>
        </div>
      </UiCard>
    </UiCard>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { uv, dau } from '../api/services/analyticsService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'
import UiInput from '../components/ui/UiInput.vue'

const emit = defineEmits(['trace'])
const auth = useAuthStore()

const today = new Date().toISOString().slice(0, 10)
const start = ref(today)
const end = ref(today)

const loading = ref(false)
const error = ref('')
const uvResult = ref('-')
const dauResult = ref('-')

async function query() {
  error.value = ''
  if (!auth.isAdminOrModerator) {
    error.value = '无权限'
    return
  }
  loading.value = true
  try {
    const [uvResp, dauResp] = await Promise.all([uv({ start: start.value, end: end.value }), dau({ start: start.value, end: end.value })])
    uvResult.value = uvResp?.data ?? '-'
    dauResult.value = dauResp?.data ?? '-'
    emit('trace', uvResp?.traceId || dauResp?.traceId || '')
  } catch (e) {
    error.value = e?.message || '查询失败'
  } finally {
    loading.value = false
  }
}
</script>
