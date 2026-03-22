<!-- Topbar：全局顶部栏（页面标题 + 搜索 + 主题/密度 + 用户操作）。 -->
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

    <div v-if="props.mode === 'public'" class="topbar-search">
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
      <button
        class="btn-icon"
        type="button"
        :aria-label="ui.density === 'compact' ? '切换到舒适密度' : '切换到紧凑密度'"
        :title="ui.density === 'compact' ? '切换到舒适' : '切换到紧凑'"
        @click="ui.toggleDensity"
      >
        <svg v-if="ui.density === 'compact'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
        <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="3" y1="15" x2="21" y2="15"></line></svg>
      </button>

      <button
        class="btn-icon"
        type="button"
        :aria-label="ui.theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'"
        :title="ui.theme === 'dark' ? '切换到浅色' : '切换到深色'"
        @click="ui.toggleTheme"
      >
         <svg v-if="ui.theme === 'dark'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>
         <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>
      </button>

      <button
        v-if="props.mode === 'public'"
        class="btn-icon"
        @click="ui.toggleRightPanel"
        :title="ui.rightPanelOpen ? '隐藏侧栏' : '显示侧栏'"
        :aria-label="ui.rightPanelOpen ? '隐藏右侧面板' : '显示右侧面板'"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="15" y1="3" x2="15" y2="21"></line></svg>
      </button>

      <div class="topbar-divider"></div>

      <template v-if="auth.authed">
        <RouterLink v-if="auth.userId" :to="`/users/${auth.userId}`" class="topbar-user-link">
           <UiAvatar :src="auth.me?.headerUrl || ''" :name="auth.username || ''" :size="32" />
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

function onKeydown(e) {
  const key = String(e?.key || '').toLowerCase()
  const meta = !!e?.metaKey
  const ctrl = !!e?.ctrlKey
  if (key !== 'k') return
  if (!(meta || ctrl)) return
  e.preventDefault()
  searchInputEl.value?.focus?.()
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  const q = route.query?.q
  if (typeof q === 'string') searchKeyword.value = q
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
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
</script>
