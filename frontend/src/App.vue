<template>
  <div class="container">
    <div class="row" style="justify-content: space-between; margin-bottom: 12px">
      <h2 style="margin: 0">Community</h2>
      <div class="row">
        <span class="muted" v-if="traceId">traceId: {{ traceId }}</span>
        <button class="btn secondary" v-if="authed" @click="onLogout">登出</button>
      </div>
    </div>

    <RouterView @trace="traceId = $event" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAuthStore } from './stores/auth'
import http from './api/http'

const auth = useAuthStore()
const traceId = ref('')
const authed = computed(() => !!auth.accessToken)

async function onLogout() {
  try {
    await http.post('/api/auth/logout')
  } finally {
    auth.clear()
    location.href = '/#/login'
  }
}
</script>

