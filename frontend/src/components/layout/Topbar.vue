<!-- Topbar：全局顶部栏（标题优先 + 桌面搜索 + 账户与偏好入口）。 -->
<template>
  <div class="app-topbar" :class="`app-topbar--${props.mode}`">
    <div class="topbar-leading">
      <button
        class="btn-icon topbar-menu-btn"
        type="button"
        aria-label="折叠或展开侧边栏"
        title="折叠/展开侧边栏"
        @click="ui.toggleSidebar"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
      </button>
      <div class="topbar-title">
        <div class="topbar-title-eyebrow">{{ modeEyebrow }}</div>
        <div class="topbar-title-main-row">
          <div class="topbar-title-main">{{ title }}</div>
          <span v-if="props.mode === 'admin'" class="topbar-mode-badge">Admin</span>
        </div>
        <div v-if="subtitle" class="topbar-title-sub">{{ subtitle }}</div>
      </div>
    </div>

    <div class="topbar-trailing">
      <div v-if="showShellSearch" class="topbar-search">
        <div class="topbar-search-field">
          <span class="topbar-search-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
          </span>
          <input
            id="topbar-global-search"
            name="global-search"
            type="search"
            ref="searchInputEl"
            class="input topbar-search-input"
            :placeholder="`搜索… (${isMac ? '⌘' : 'Ctrl'} K)`"
            aria-label="全局搜索"
            v-model.trim="searchKeyword"
            @keydown.enter="submitSearch"
          />
        </div>
      </div>

      <div class="topbar-actions">
        <div ref="overflowRef" class="topbar-overflow">
          <button
            class="btn-icon topbar-overflow-trigger"
            type="button"
            :aria-expanded="overflowOpen ? 'true' : 'false'"
            aria-label="打开页面偏好设置"
            title="页面偏好设置"
            @click="overflowOpen = !overflowOpen"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="4" y1="7" x2="20" y2="7"></line><line x1="4" y1="12" x2="20" y2="12"></line><line x1="4" y1="17" x2="20" y2="17"></line><circle cx="9" cy="7" r="2"></circle><circle cx="15" cy="12" r="2"></circle><circle cx="11" cy="17" r="2"></circle></svg>
          </button>
          <div v-if="overflowOpen" class="topbar-overflow-menu">
            <button
              class="topbar-overflow-item"
              type="button"
              :aria-label="ui.density === 'compact' ? '切换到舒适密度' : '切换到紧凑密度'"
              @click="toggleDensity"
            >
              {{ ui.density === 'compact' ? '切换为舒适密度' : '切换为紧凑密度' }}
            </button>
            <button
              class="topbar-overflow-item"
              type="button"
              :aria-label="ui.theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'"
              @click="toggleTheme"
            >
              {{ ui.theme === 'dark' ? '切换为浅色主题' : '切换为深色主题' }}
            </button>
          </div>
        </div>

        <template v-if="auth.authed">
          <RouterLink v-if="auth.userId" :to="`/users/${auth.userId}`" class="topbar-user-link">
            <UiAvatar :src="auth.me?.headerUrl || ''" :name="auth.username || ''" :size="30" />
            <span class="topbar-user-meta">
              <span class="topbar-user-name">{{ auth.username || `成员 ${auth.userId}` }}</span>
              <UiRoleBadge :user="auth.me" />
            </span>
          </RouterLink>
          <button class="btn-icon" type="button" aria-label="登出" @click="onLogout" title="登出">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
          </button>
        </template>

        <template v-else>
          <RouterLink class="btn topbar-login-btn" to="/auth/login">登录</RouterLink>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import http from '../../api/http'
import UiAvatar from '../ui/UiAvatar.vue'
import UiRoleBadge from '../ui/UiRoleBadge.vue'

const props = defineProps({
  mode: { type: String, default: 'public' }
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()

const searchKeyword = ref('')
const searchInputEl = ref(null)
const overflowOpen = ref(false)
const overflowRef = ref(null)
const desktopSearchVisible = ref(typeof window === 'undefined' ? true : window.innerWidth > 920)

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform || '')

function resolveMetaText(v) {
  if (typeof v === 'function') return v(route)
  if (typeof v === 'string') return v
  return ''
}

const title = computed(() => {
  const t = resolveMetaText(route.meta?.title)
  return t || 'Community'
})

const subtitle = computed(() => resolveMetaText(route.meta?.subtitle))

const modeEyebrow = computed(() => (props.mode === 'admin' ? 'Operations Desk' : 'Discussion Workspace'))
const showShellSearch = computed(() => props.mode === 'public' && desktopSearchVisible.value)

async function onLogout() {
  try {
    await http.post('/api/auth/logout')
  } finally {
    auth.clear()
    router.replace({ name: 'login' })
  }
}

function submitSearch() {
  const q = searchKeyword.value || ''
  const query = q ? { ...route.query, q } : { ...route.query }
  if (q) {
    query.q = q
  } else {
    delete query.q
  }
  // 在同一路由下触发搜索时，replace 可减少历史记录噪音。
  if (String(route.name || '') === 'search') {
    router.replace({ name: 'search', query })
    return
  }
  router.push({ name: 'search', query })
}

function toggleDensity() {
  ui.toggleDensity()
  overflowOpen.value = false
}

function toggleTheme() {
  ui.toggleTheme()
  overflowOpen.value = false
}

function syncDesktopSearchVisible() {
  if (typeof window === 'undefined') return
  desktopSearchVisible.value = window.innerWidth > 920
}

function onDocumentClick(event) {
  if (!overflowOpen.value) return
  if (overflowRef.value?.contains?.(event.target)) return
  overflowOpen.value = false
}

function onKeydown(e) {
  const key = String(e?.key || '').toLowerCase()
  const meta = !!e?.metaKey
  const ctrl = !!e?.ctrlKey
  if (key === 'escape') {
    overflowOpen.value = false
    return
  }
  if (key !== 'k') return
  if (!(meta || ctrl)) return
  if (!showShellSearch.value) return
  e.preventDefault()
  searchInputEl.value?.focus?.()
}

onMounted(() => {
  syncDesktopSearchVisible()
  window.addEventListener('resize', syncDesktopSearchVisible)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('click', onDocumentClick)
  const q = route.query?.q
  if (typeof q === 'string') searchKeyword.value = q
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncDesktopSearchVisible)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('click', onDocumentClick)
})

watch(
  () => route.query?.q,
  (q) => {
    if (typeof q === 'string') {
      searchKeyword.value = q
      return
    }
    // 不强制清空：保留用户最近一次输入，便于复用。
  }
)

watch(
  () => route.fullPath,
  () => {
    overflowOpen.value = false
  }
)
</script>
