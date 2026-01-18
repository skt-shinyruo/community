<template>
  <div class="page" style="max-width: 1000px; margin: 0 auto">
    <div class="dashboard-header">
       <h1 class="welcome-title">Good {{ timeOfDay }}, {{ auth.username || 'Explorer' }}.</h1>
       <div class="welcome-sub">Here is what's happening in your community today.</div>
    </div>

    <div class="dashboard-grid">
       <!-- Left Column: Main Stats/Activity -->
       <div class="main-column">
          <div class="row" style="gap: 16px; margin-bottom: 24px">
             <!-- Quick Stats Cards -->
             <div class="stat-card">
                <div class="stat-value">{{ unreadCount }}</div>
                <div class="stat-label">Unread Notices</div>
             </div>
             <div class="stat-card">
                <div class="stat-value">{{ followingCount }}</div>
                <div class="stat-label">Following</div>
             </div>
             <div class="stat-card">
                <div class="stat-value">{{ followerCount }}</div>
                <div class="stat-label">Followers</div>
             </div>
          </div>

          <UiCard>
             <UiPageHeader>
                <template #title>Quick Actions</template>
             </UiPageHeader>
             <div class="row" style="gap: 12px; margin-top: 16px; flex-wrap: wrap">
                <RouterLink to="/posts" class="action-btn">
                   <div class="action-icon">📝</div>
                   <div class="action-text">Browse Feed</div>
                </RouterLink>
                 <RouterLink to="/search" class="action-btn">
                   <div class="action-icon">🔍</div>
                   <div class="action-text">Search</div>
                </RouterLink>
                 <RouterLink to="/notices" class="action-btn">
                   <div class="action-icon">🔔</div>
                   <div class="action-text">Check Inbox</div>
                </RouterLink>
             </div>
          </UiCard>
       </div>

       <!-- Right Column: Sidebar info -->
       <div class="side-column">
          <UiCard>
             <div class="side-title">System Status</div>
             <div class="status-item">
                <div class="status-dot green"></div>
                <div>All Systems Operational</div>
             </div>
             <div class="muted" style="font-size: 12px; margin-top: 8px">
                Trace ID: {{ app.traceId || 'N/A' }}
             </div>
          </UiCard>

           <UiCard style="margin-top: 16px">
             <div class="side-title">Dev Tools</div>
             <div class="stack" style="gap: 8px">
                <RouterLink to="/dev" class="btn secondary sm">Component Library</RouterLink>
                <button class="btn secondary sm" @click="refreshAll">Refresh Data</button>
             </div>
          </UiCard>
       </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useAppStore } from '../stores/app'
import http from '../api/http'
import UiCard from '../components/ui/UiCard.vue'
import UiPageHeader from '../components/ui/UiPageHeader.vue'

const emit = defineEmits(['trace'])
const auth = useAuthStore()
const app = useAppStore()

const unreadCount = ref(0)
const followingCount = ref(0)
const followerCount = ref(0)

const timeOfDay = computed(() => {
   const hr = new Date().getHours()
   if (hr < 12) return 'Morning'
   if (hr < 18) return 'Afternoon'
   return 'Evening'
})

async function loadCounts() {
  if (!auth.authed) return
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
  }
}

async function refreshAll() {
   await loadCounts()
}

onMounted(loadCounts)
</script>

<style scoped>
.dashboard-header {
  margin-bottom: 32px;
}
.welcome-title {
  font-size: 32px;
  font-weight: 800;
  margin-bottom: 8px;
  background: linear-gradient(90deg, var(--text-1), var(--muted));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.welcome-sub {
  font-size: 18px;
  color: var(--muted);
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 24px;
}
@media (max-width: 800px) {
  .dashboard-grid { grid-template-columns: 1fr; }
}

.stat-card {
   flex: 1;
   background: var(--surface);
   border-radius: var(--radius-md);
   padding: 16px;
   box-shadow: var(--shadow-sm);
   display: flex;
   flex-direction: column;
   align-items: center;
}
.stat-value { font-size: 24px; font-weight: 800; color: var(--accent); }
.stat-label { font-size: 13px; color: var(--muted); font-weight: 500; }

.action-btn {
   flex: 1;
   min-width: 120px;
   background: var(--bg);
   padding: 16px;
   border-radius: var(--radius-md);
   text-decoration: none;
   color: var(--text-1);
   display: flex;
   flex-direction: column;
   align-items: center;
   gap: 8px;
   transition: all 0.2s;
}
.action-btn:hover {
   background: var(--surface-2);
   transform: translateY(-2px);
}
.action-icon { font-size: 24px; }
.action-text { font-weight: 600; font-size: 14px; }

.side-title { font-weight: 700; margin-bottom: 12px; font-size: 14px; text-transform: uppercase; color: var(--muted); letter-spacing: 0.5px; }

.status-item {
   display: flex;
   align-items: center;
   gap: 8px;
   font-size: 14px;
   font-weight: 500;
}
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-dot.green { background: var(--green); box-shadow: 0 0 0 4px rgba(52, 199, 89, 0.1); }
</style>
