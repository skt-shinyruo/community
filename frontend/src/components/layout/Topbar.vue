<!-- Topbar：全局顶部栏（页面标题 + 搜索 + 主题/密度 + 用户操作）。 -->
<template>
  <div class="app-topbar">
    <div class="row" style="min-width: 240px">
      <button class="btn ghost" @click="ui.toggleSidebar" title="折叠/展开侧边栏">☰</button>
      <div class="topbar-title">
        <div class="topbar-title-main">{{ title }}</div>
        <div v-if="subtitle" class="topbar-title-sub muted">{{ subtitle }}</div>
      </div>
    </div>

    <div class="topbar-search">
      <input
        ref="searchInputEl"
        class="input"
        :placeholder="`搜索…（${isMac ? '⌘' : 'Ctrl'} K）`"
        v-model.trim="searchKeyword"
        @keydown.enter="submitSearch"
      />
    </div>

    <div class="row" style="justify-content: flex-end; min-width: 320px">
      <span v-if="app.traceId" class="muted" style="font-size: 12px">traceId: {{ app.traceId }}</span>

      <button
        class="btn secondary"
        @click="ui.toggleRightPanel"
        :title="ui.rightPanelOpen ? '隐藏右侧上下文面板' : '显示右侧上下文面板'"
      >
        {{ ui.rightPanelOpen ? '面板开' : '面板关' }}
      </button>

      <button class="btn secondary" @click="ui.toggleDensity" :title="ui.density === 'compact' ? '切换到舒适' : '切换到紧凑'">
        {{ ui.density === 'compact' ? '紧凑' : '舒适' }}
      </button>
      <button class="btn secondary" @click="ui.toggleTheme" :title="ui.theme === 'dark' ? '切换到浅色' : '切换到深色'">
        {{ ui.theme === 'dark' ? '深色' : '浅色' }}
      </button>

      <template v-if="auth.authed">
        <RouterLink class="btn secondary" v-if="auth.userId" :to="`/users/${auth.userId}`">{{ auth.username || '我的主页' }}</RouterLink>
        <button class="btn danger secondary" @click="onLogout">登出</button>
      </template>

      <template v-else>
        <RouterLink class="btn secondary" to="/auth/login">登录</RouterLink>
        <RouterLink class="btn" to="/auth/register">注册</RouterLink>
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
