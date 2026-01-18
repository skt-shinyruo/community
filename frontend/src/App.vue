<!-- 应用入口：接入 AppShell（Notion 风格工作区骨架）与全局 Toast。 -->
<template>
  <UiToast :type="app.toast.type" :message="app.toast.message" @close="app.clearToast()" />

  <AuthShell v-if="isAuthRoute">
    <RouterView @trace="app.setTraceId($event)" />
  </AuthShell>

  <AppShell v-else>
    <RouterView v-slot="{ Component }">
      <Transition name="fade" mode="out-in">
        <component :is="Component" @trace="app.setTraceId($event)" />
      </Transition>
    </RouterView>
    <template #right>
      <RightPanel />
    </template>
  </AppShell>
  
  <UiToast ref="toastRef" />
</template>

<script setup>
import { onMounted, provide, ref } from 'vue'
import { useRoute, RouterView } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useAppStore } from './stores/app'
import { me } from './api/services/authService'
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
    const { data, traceId } = await me()
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
