<template>
  <div class="page">
    <UiCard>
      <UiPageHeader>
        <template #title>联调</template>
        <template #subtitle>开发与调试入口 · {{ greeting }}</template>
        <template #actions>
          <UiButton variant="secondary" @click="refreshAll" :disabled="loading">{{ loading ? '刷新中…' : '刷新数据' }}</UiButton>
        </template>
      </UiPageHeader>

      <div class="dashboard-grid">
        <!-- Left Column -->
        <div class="main-column">
          <div class="row stat-row">
            <div class="stat-card">
              <div class="stat-value">{{ unreadCount }}</div>
              <div class="stat-label">未读通知</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ followingCount }}</div>
              <div class="stat-label">关注</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">{{ followerCount }}</div>
              <div class="stat-label">粉丝</div>
            </div>
          </div>

          <UiCard>
            <UiPageHeader>
              <template #title>快捷入口</template>
            </UiPageHeader>
            <div class="row action-row">
              <RouterLink to="/posts" class="btn secondary">浏览帖子</RouterLink>
              <RouterLink to="/search" class="btn secondary">搜索</RouterLink>
              <RouterLink to="/notices" class="btn secondary">查看通知</RouterLink>
            </div>
          </UiCard>
        </div>

        <!-- Right Column -->
        <div class="side-column">
          <UiCard>
            <div class="side-title">系统状态</div>
            <div class="status-item">
              <div class="status-dot green"></div>
              <div>运行正常</div>
            </div>
            <div class="muted trace-id">Trace ID: {{ app.traceId || 'N/A' }}</div>
          </UiCard>

          <UiCard>
            <div class="side-title">开发工具</div>
            <div class="stack tools-stack">
              <RouterLink to="/dev" class="btn secondary sm">组件库</RouterLink>
              <button class="btn secondary sm" @click="refreshAll">刷新数据</button>
            </div>
          </UiCard>
        </div>
      </div>
    </UiCard>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useAppStore } from '../stores/app'
import http from '../api/http'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiButton from '../components/ui/UiButton.vue'

const auth = useAuthStore()
const app = useAppStore()

const unreadCount = ref(0)
const followingCount = ref(0)
const followerCount = ref(0)
const loading = ref(false)

const greeting = computed(() => {
  const name = auth.username || 'Explorer'
  const hr = new Date().getHours()
  if (hr < 12) return `上午好，${name}`
  if (hr < 18) return `下午好，${name}`
  return `晚上好，${name}`
})

async function loadCounts() {
  if (!auth.authed) return
  loading.value = true
  try {
    const unreadResp = await http.get('/api/notices/unread-count')
    unreadCount.value = unreadResp?.data?.data ?? 0
    
    // Attempt to load my follow stats if possible, or just skip if expensive
    // Using the endpoints from previous HomeView
    if (auth.userId) {
       const [followingResp, followerResp] = await Promise.all([
         http.get(`/api/follows/${auth.userId}/followees/count`),
         http.get(`/api/follows/${auth.userId}/followers/count`)
       ])
       followingCount.value = followingResp?.data?.data ?? 0
       followerCount.value = followerResp?.data?.data ?? 0
    }
  } catch (e) {
     console.error('Failed to load dashboard stats', e)
     if (typeof window !== 'undefined' && window.$toast) {
       window.$toast({ type: 'error', text: '加载联调数据失败' })
     }
  }
  finally {
    loading.value = false
  }
}

async function refreshAll() {
   await loadCounts()
   // 轻提示：避免用户误以为按钮无响应
   if (typeof window !== 'undefined' && window.$toast) {
     window.$toast({ type: 'success', text: '已刷新' })
   }
}

onMounted(loadCounts)
</script>

<style scoped>
.dashboard-grid {
  margin-top: var(--space-4);
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: var(--space-6);
}
@media (max-width: 800px) {
  .dashboard-grid { grid-template-columns: 1fr; }
}

.side-column {
  display: grid;
  gap: var(--space-4);
  align-content: start;
}

.tools-stack {
  gap: var(--space-2);
}

.trace-id {
  font-size: var(--text-xs);
  margin-top: var(--space-2);
}

.stat-card {
   flex: 1;
   background: var(--surface);
   border-radius: var(--radius-md);
   padding: var(--card-padding);
   box-shadow: var(--shadow-sm);
   border: 1px solid var(--border);
   display: flex;
   flex-direction: column;
   align-items: center;
}
.stat-value { font-size: 24px; font-weight: 800; color: var(--accent); }
.stat-label { font-size: 13px; color: var(--text-2); font-weight: 600; }

.stat-row {
  gap: var(--space-4);
  margin-bottom: var(--space-6);
  flex-wrap: wrap;
}

.action-row {
  gap: var(--space-3);
  margin-top: var(--space-3);
  flex-wrap: wrap;
}

.side-title { font-weight: 800; margin-bottom: var(--space-3); font-size: 13px; color: var(--text-2); letter-spacing: 0.2px; }

.status-item {
   display: flex;
   align-items: center;
   gap: var(--space-2);
   font-size: 14px;
   font-weight: 500;
}
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-dot.green { background: var(--success); box-shadow: 0 0 0 4px color-mix(in srgb, var(--success) 18%, transparent); }
</style>
