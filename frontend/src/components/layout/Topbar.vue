<!-- Topbar：全局顶部栏（页面标题 + 搜索 + 主题/密度 + 用户操作）。 -->
<template>
  <div class="app-topbar">
    <div class="row" style="min-width: 240px">
      <button class="btn-icon" @click="ui.toggleSidebar" title="折叠/展开侧边栏">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
      </button>
      <div class="topbar-title">
        <div class="topbar-title-main">{{ title }}</div>
      </div>
    </div>

    <div class="topbar-search">
      <div style="position: relative; width: 100%">
         <span style="position: absolute; left: 10px; top: 10px; color: var(--muted)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
         </span>
         <input
          ref="searchInputEl"
          class="input"
          style="padding-left: 32px; border-radius: 20px"
          :placeholder="`Search... (${isMac ? '⌘' : 'Ctrl'} K)`"
          v-model.trim="searchKeyword"
          @keydown.enter="submitSearch"
        />
      </div>
    </div>

    <div class="row" style="justify-content: flex-end; min-width: 320px; gap: 8px">
      <button class="btn-icon" @click="ui.toggleDensity" :title="ui.density === 'compact' ? '切换到舒适' : '切换到紧凑'">
        <svg v-if="ui.density === 'compact'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
        <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="3" y1="9" x2="21" y2="9"></line><line x1="3" y1="15" x2="21" y2="15"></line></svg>
      </button>

      <button class="btn-icon" @click="ui.toggleTheme" :title="ui.theme === 'dark' ? '切换到浅色' : '切换到深色'">
         <svg v-if="ui.theme === 'dark'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="5"></circle><line x1="12" y1="1" x2="12" y2="3"></line><line x1="12" y1="21" x2="12" y2="23"></line><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line><line x1="1" y1="12" x2="3" y2="12"></line><line x1="21" y1="12" x2="23" y2="12"></line><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line></svg>
         <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>
      </button>

      <button
        class="btn-icon"
        @click="ui.toggleRightPanel"
        :title="ui.rightPanelOpen ? '隐藏侧栏' : '显示侧栏'"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="15" y1="3" x2="15" y2="21"></line></svg>
      </button>

      <div style="width: 1px; height: 24px; background: var(--border); margin: 0 4px"></div>

      <template v-if="auth.authed">
        <RouterLink v-if="auth.userId" :to="`/users/${auth.userId}`">
           <UiAvatar :src="auth.user?.headerUrl || ''" :name="auth.username || ''" :size="32" />
        </RouterLink>
        <button class="btn-icon" @click="onLogout" title="登出">
           <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
        </button>
      </template>

      <template v-else>
        <RouterLink class="btn primary" to="/auth/login" style="height: 32px; padding: 0 16px; font-size: 13px">Log In</RouterLink>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useAppStore } from '../../stores/app'
import { useUiStore } from '../../stores/ui'
import http from '../../api/http'
import UiAvatar from '../ui/UiAvatar.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const app = useAppStore()
const ui = useUiStore()

const searchKeyword = ref('')
const searchInputEl = ref(null)

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform || '')

const title = computed(() => {
  const name = String(route.name || '')
  const postId = route.params?.postId
  const userId = route.params?.userId
  const topic = route.params?.topic

  if (name === 'posts') return '帖子'
  if (name === 'postDetail') return `帖子 #${postId || ''}`
  if (name === 'search') return '搜索'
  if (name === 'messages') return '私信'
  if (name === 'messageDetail') return '私信详情'
  if (name === 'notices') return '通知'
  if (name === 'noticeDetail') return `通知：${topic || ''}`
  if (name === 'analytics') return '统计'
  if (name === 'settings') return '设置'
  if (name === 'userProfile') return `用户 #${userId || ''}`
  if (name === 'followees') return '关注列表'
  if (name === 'followers') return '粉丝列表'
  if (name === 'dev') return '联调'
  if (name === 'forbidden') return '无权限'
  if (name === 'notFound') return '未找到'
  return 'Community'
})

const subtitle = computed(() => {
  const name = String(route.name || '')
  if (name === 'posts') return '高信息密度浏览 · 支持最新/最热'
  if (name === 'postDetail') return '文档化阅读 · 评论与回复树'
  if (name === 'search') return '全局搜索 · 关键词高亮'
  return ''
})

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
