<!-- 应用入口：接入 AppShell（Notion 风格工作区骨架）与全局 Toast。 -->
<template>
  <UiToast ref="toastRef" />

  <div v-if="isAuthRoute" class="auth-app-frame">
    <AuthShell>
      <RouterView />
    </AuthShell>
  </div>

  <AppShell v-else :mode="shellMode">
    <RouterView v-slot="{ Component }">
      <Transition name="fade" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </AppShell>

  <UiScrollTop />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, RouterView } from 'vue-router'
import { ensureSessionReady, shouldBootstrapSession } from './auth/session'
import { useAuthStore } from './stores/auth'
import { useInboxUnreadStore } from './stores/inboxUnread'
import { identityScope } from './stores/identityScope'
import { imRealtimeClient } from './im/imRealtimeClient'
import { setToastHandler } from './ui/toastService'
import AppShell from './components/layout/AppShell.vue'
import AuthShell from './components/layout/AuthShell.vue'
import UiToast from './components/ui/UiToast.vue'
import UiScrollTop from './components/ui/UiScrollTop.vue'

const auth = useAuthStore()
const inboxUnread = useInboxUnreadStore()
const route = useRoute()
const toastRef = ref(null)

const showToast = (payload) => {
  toastRef.value?.show?.(payload || {})
}

setToastHandler(showToast)

const isAuthRoute = computed(() => {
  const name = String(route.name || '')
  if (name === 'login' || name === 'register') return true
  const path = String(route.path || '')
  return path.startsWith('/auth/')
})

const isAdminRoute = computed(() => String(route.meta?.navGroup || '') === 'admin')

const shellMode = computed(() => (isAdminRoute.value ? 'admin' : 'public'))

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

// 未读角标生命周期：登录恢复/登出（身份变化）、窗口重新聚焦、IM 实时私信事件；不轮询。
watch(
  () => identityScope(auth),
  () => {
    if (auth.authed) {
      void inboxUnread.refresh()
      return
    }
    inboxUnread.reset()
  },
  { immediate: true }
)

function onWindowFocus() {
  if (auth.authed) void inboxUnread.refresh()
}

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
let offPrivateMessage = null
onMounted(() => {
  window.addEventListener('focus', onWindowFocus)
  offRoomUpdates = imRealtimeClient.on('roomUpdatedBatch', (msg) => {
    const n = Array.isArray(msg?.items) ? msg.items.length : 0
    if (n <= 0) return
    showToast({
      type: 'info',
      title: '群聊有新消息',
      text: `${n} 个群聊有新消息（点击进入群聊查看内容）`
    })
  })
  offPrivateMessage = imRealtimeClient.on('privateMessage', () => {
    void inboxUnread.refresh()
  })
})

onBeforeUnmount(() => {
  setToastHandler(null)
  window.removeEventListener('focus', onWindowFocus)
  try { offRoomUpdates?.() } catch {}
  try { offPrivateMessage?.() } catch {}
})
</script>
