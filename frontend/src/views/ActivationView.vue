<!-- 激活页面：展示激活成功/重复/失败三态（对齐 legacy 语义）。 -->
<template>
  <UiCard class="auth-view-card activation-card">
    <UiPageHeader>
      <template #title>账号激活</template>
      <template #subtitle>验证激活码并确认你的账号状态。</template>
      <template #actions>
        <RouterLink class="btn secondary" to="/auth/login">去登录</RouterLink>
      </template>
    </UiPageHeader>

    <div class="activation-body">
      <div v-if="loading" class="muted">请求中…</div>
      <div v-else-if="error" class="error">{{ error }}</div>

      <UiCard v-else flat class="activation-result-card">
        <div class="activation-result-stack">
          <div class="activation-result-title">{{ title }}</div>
          <div class="muted">{{ desc }}</div>
          <RouterLink class="btn ghost" to="/posts">返回社区</RouterLink>
        </div>
      </UiCard>
    </div>
  </UiCard>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { activation as apiActivation } from '../api/services/authService'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

const emit = defineEmits(['trace'])
const props = defineProps({ userId: String, code: String })

const loading = ref(false)
const error = ref('')
const result = ref(null) // 0=success, 1=repeat, 2=failure

const title = computed(() => {
  if (result.value === 0) return '激活成功'
  if (result.value === 1) return '重复激活'
  if (result.value === 2) return '激活失败'
  return '未知状态'
})

const desc = computed(() => {
  if (result.value === 0) return '你的账号已可正常使用。'
  if (result.value === 1) return '该账号已激活，无需重复操作。'
  if (result.value === 2) return '激活码不正确或已失效。'
  return '请稍后重试。'
})

async function run() {
  error.value = ''
  loading.value = true
  try {
    const { data, traceId } = await apiActivation(props.userId, props.code)
    emit('trace', traceId || '')
    result.value = data
  } catch (e) {
    error.value = e?.message || '激活失败'
  } finally {
    loading.value = false
  }
}

onMounted(run)
</script>

<style scoped>
.activation-card {
  display: grid;
  gap: 12px;
}

.activation-body {
  margin-top: 12px;
}

.activation-result-card {
  border-radius: 16px;
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 88%, var(--bg) 12%);
  padding: 16px;
}

.activation-result-stack {
  display: grid;
  gap: 8px;
}

.activation-result-title {
  font-weight: 800;
}
</style>
