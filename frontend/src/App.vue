<!-- 应用入口：接入 AppShell（Notion 风格工作区骨架）与全局 Toast。 -->
<template>
  <UiToast ref="toastRef" />

  <div v-if="isAuthRoute" style="min-height: 100vh; background: var(--bg)">
    <AuthShell v-if="useAuthShell">
      <RouterView @trace="app.setTraceId($event)" />
    </AuthShell>
    <RouterView v-else @trace="app.setTraceId($event)" />
  </div>

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

  <UiScrollTop />
</template>

<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute, RouterView } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useAppStore } from './stores/app'
import { me } from './api/services/authService'
import AppShell from './components/layout/AppShell.vue'
import AuthShell from './components/layout/AuthShell.vue'
import RightPanel from './components/layout/RightPanel.vue'
import UiToast from './components/ui/UiToast.vue'
import UiScrollTop from './components/ui/UiScrollTop.vue'

const auth = useAuthStore()
const app = useAppStore()
const route = useRoute()
const toastRef = ref(null)

// 统一全局 Toast 入口：页面通过 inject('showToast') 使用。
const showToast = (payload) => {
  toastRef.value?.show?.(payload || {})
}

provide('showToast', showToast)

// 兼容历史调用：Axios 拦截器会尝试使用 window.$toast。
if (typeof window !== 'undefined') {
  window.$toast = showToast
}

const isAuthRoute = computed(() => {
  const name = String(route.name || '')
  if (name === 'login' || name === 'register' || name === 'activation') return true
  const path = String(route.path || '')
  return path.startsWith('/auth/')
})

const useAuthShell = computed(() => {
  const name = String(route.name || '')
  // 登录/注册页面自带 full-bleed 视觉区块，其余认证页使用统一 AuthShell。
  if (name === 'login' || name === 'register') return false
  return true
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
