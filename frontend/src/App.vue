<!-- 应用入口：接入 AppShell（Notion 风格工作区骨架）与全局 Toast。 -->
<template>
  <UiToast ref="toastRef" />

  <div v-if="isAuthRoute" class="auth-app-frame">
    <AuthShell>
      <RouterView @trace="app.setTraceId($event)" />
    </AuthShell>
  </div>

  <AppShell v-else :mode="shellMode">
    <RouterView v-slot="{ Component }">
      <Transition name="fade" mode="out-in">
        <component :is="Component" @trace="app.setTraceId($event)" />
      </Transition>
    </RouterView>
    <template v-if="showRightPanel" #right>
      <RightPanel />
    </template>
  </AppShell>

  <UiScrollTop />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, RouterView } from 'vue-router'
import { ensureSessionReady, shouldBootstrapSession } from './auth/session'
import { useAuthStore } from './stores/auth'
import { useAppStore } from './stores/app'
import { imRealtimeClient } from './im/imRealtimeClient'
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
  if (name === 'login' || name === 'register') return true
  const path = String(route.path || '')
  return path.startsWith('/auth/')
})

const isAdminRoute = computed(() => String(route.meta?.navGroup || '') === 'admin')

const shellMode = computed(() => (isAdminRoute.value ? 'admin' : 'public'))

const RIGHT_PANEL_ROUTE_NAMES = new Set([
  'posts',
  'postDetail',
  'search',
  'userProfile',
  'followees',
  'followers',
  'bookmarks',
  'leaderboard'
])

const showRightPanel = computed(() => {
  if (isAuthRoute.value || isAdminRoute.value) return false
  return RIGHT_PANEL_ROUTE_NAMES.has(String(route.name || ''))
})

async function bootstrapSession() {
  if (!shouldBootstrapSession({ auth })) {
    return
  }
  const session = await ensureSessionReady({ auth })
  if (session.state === 'anonymous') {
    auth.clear()
  }
}

onMounted(bootstrapSession)

// IM realtime lifecycle: connect on login, disconnect on logout or token refresh.
watch(
  () => auth.accessToken,
  (token, prev) => {
    const next = String(token || '').trim()
    const prevToken = String(prev || '').trim()
    if (!next) {
      imRealtimeClient.disconnect()
      return
    }
    if (prevToken && prevToken !== next) {
      imRealtimeClient.disconnect()
    }
    imRealtimeClient.connect(next)
  },
  { immediate: true }
)

let offRoomUpdates = null
onMounted(() => {
  offRoomUpdates = imRealtimeClient.on('roomUpdatedBatch', (msg) => {
    const n = Array.isArray(msg?.items) ? msg.items.length : 0
    if (n <= 0) return
    showToast({
      type: 'info',
      title: '群聊有新消息',
      text: `${n} 个群聊有新消息（点击进入群聊查看内容）`
    })
  })
})

onBeforeUnmount(() => {
  try { offRoomUpdates?.() } catch {}
})
</script>
