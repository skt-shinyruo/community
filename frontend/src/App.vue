<!-- 应用入口：接入 AppShell（Notion 风格工作区骨架）与全局 Toast。 -->
<template>
  <UiToast :type="app.toast.type" :message="app.toast.message" @close="app.clearToast()" />

  <AuthShell v-if="isAuthRoute">
    <RouterView @trace="app.setTraceId($event)" />
  </AuthShell>

  <AppShell v-else>
    <RouterView @trace="app.setTraceId($event)" />
    <template #right>
      <RightPanel />
    </template>
  </AppShell>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useAppStore } from './stores/app'
import { me as fetchMe } from './api/services/authService'
import AppShell from './components/layout/AppShell.vue'
import AuthShell from './components/layout/AuthShell.vue'
import RightPanel from './components/layout/RightPanel.vue'
import UiToast from './components/ui/UiToast.vue'

const auth = useAuthStore()
const app = useAppStore()
const route = useRoute()
const isAuthRoute = computed(() => {
  const name = String(route.name || '')
  if (name === 'login' || name === 'register' || name === 'activation') return true
  const path = String(route.path || '')
  return path.startsWith('/auth/')
})

async function refreshMe() {
  if (!auth.accessToken) return
  try {
    const { data, traceId } = await fetchMe()
    auth.setMe(data)
    if (traceId) {
      app.setTraceId(traceId)
    }
  } catch (e) {
    // 这里不强制登出；交由请求拦截器处理 refresh 失败场景
  }
}

onMounted(refreshMe)
</script>
