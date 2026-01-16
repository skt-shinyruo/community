<template>
  <div class="card">
    <div class="stack">
      <div class="row" style="justify-content: space-between">
        <div>
          <div style="font-weight: 700">登录态</div>
          <div class="muted">用于联调：调用 /api/auth/me</div>
        </div>
        <button class="btn" @click="load">刷新</button>
      </div>

      <div v-if="loading" class="muted">加载中...</div>
      <div v-else-if="error" class="error">{{ error }}</div>
      <pre v-else style="margin: 0">{{ JSON.stringify(me, null, 2) }}</pre>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import http from '../api/http'

const emit = defineEmits(['trace'])

const me = ref(null)
const loading = ref(false)
const error = ref('')

async function load() {
  error.value = ''
  loading.value = true
  try {
    const resp = await http.get('/api/auth/me')
    me.value = resp?.data?.data || null
    emit('trace', resp?.data?.traceId || '')
  } catch (e) {
    error.value = e?.response?.data?.message || '请求失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

