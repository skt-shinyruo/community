<!-- SidebarNav：左侧导航栏（信息架构与导航入口）。 -->
<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <RouterLink :to="{ name: 'posts' }" class="sidebar-brand" aria-label="返回帖子列表" @click="onNavClick">
        <div class="sidebar-brand-mark" aria-hidden="true">{{ props.mode === 'admin' ? 'M' : 'C' }}</div>
        <span v-if="!ui.sidebarCollapsed" class="sidebar-brand-copy">
          <span class="sidebar-brand-text">{{ props.mode === 'admin' ? 'Moderation Desk' : 'Community' }}</span>
          <span class="sidebar-brand-sub">{{ props.mode === 'admin' ? '运营工作台' : '讨论版编辑部' }}</span>
        </span>
      </RouterLink>

      <button
        class="btn-icon"
        type="button"
        :aria-label="ui.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        :title="ui.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="ui.toggleSidebar"
      >
        <svg v-if="ui.sidebarCollapsed" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M9 18l6-6-6-6" />
        </svg>
        <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M15 18l-6-6 6-6" />
        </svg>
      </button>
    </div>

    <div class="sidebar-scroll">
      <div v-for="group in navGroups" :key="group.key" class="nav-group">
        <div class="nav-group-title" v-if="!ui.sidebarCollapsed">{{ group.title }}</div>

        <RouterLink
          v-for="item in group.items"
          :key="item.key"
          class="nav-item"
          :class="{ 'is-active': isNavItemActive(route, item) }"
          :to="item.to"
          :title="item.label"
          :aria-label="item.label"
          @click="onNavClick"
        >
          <span class="nav-icon" aria-hidden="true">
            <svg
              v-if="item.icon === 'posts'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>

            <svg
              v-else-if="item.icon === 'search'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>

            <svg
              v-else-if="item.icon === 'star'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <polygon points="12 2 15 9 22 9 16.5 13.5 18.5 21 12 16.8 5.5 21 7.5 13.5 2 9 9 9" />
            </svg>

            <svg
              v-else-if="item.icon === 'bookmark'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M6 3h12a1 1 0 0 1 1 1v18l-7-4-7 4V4a1 1 0 0 1 1-1z" />
            </svg>

            <svg
              v-else-if="item.icon === 'trophy'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M8 21h8" />
              <path d="M12 17v4" />
              <path d="M7 4h10v5a5 5 0 0 1-10 0V4z" />
              <path d="M5 5H3v2a5 5 0 0 0 5 5" />
              <path d="M19 5h2v2a5 5 0 0 1-5 5" />
            </svg>

            <svg
              v-else-if="item.icon === 'shield'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M12 2l7 4v6c0 5-3 9-7 10-4-1-7-5-7-10V6l7-4z" />
            </svg>

            <svg
              v-else-if="item.icon === 'pin'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z" />
              <circle cx="12" cy="10" r="3" />
            </svg>

            <svg
              v-else-if="item.icon === 'sparkle'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M12 2l2.2 6.8H21l-5.6 4.1 2.1 7-5.5-4-5.5 4 2.1-7L3 8.8h6.8z" />
            </svg>
            <span v-else-if="item.icon === 'dot'">●</span>

            <svg
              v-else-if="item.icon === 'bell'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>

            <svg
              v-else-if="item.icon === 'messages'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
            </svg>

            <svg
              v-else-if="item.icon === 'user'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>

            <svg
              v-else-if="item.icon === 'settings'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <circle cx="12" cy="12" r="3" />
              <path
                d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"
              />
            </svg>

            <svg
              v-else-if="item.icon === 'analytics'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M3 3v18h18" />
              <path d="M7 15l3-3 4 4 6-6" />
            </svg>

            <svg
              v-else-if="item.icon === 'login'"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4" />
              <polyline points="10 17 15 12 10 7" />
              <line x1="15" y1="12" x2="3" y2="12" />
            </svg>
          </span>
          <span v-if="!ui.sidebarCollapsed" class="nav-text">{{ item.label }}</span>
        </RouterLink>
      </div>
    </div>

    <div class="sidebar-footer">
      <div class="sidebar-footer-actions">
        <button
          class="btn-icon"
          type="button"
          :title="ui.theme === 'dark' ? '切换到浅色' : '切换到深色'"
          :aria-label="ui.theme === 'dark' ? '切换到浅色主题' : '切换到深色主题'"
          @click="ui.toggleTheme"
        >
          <span v-if="ui.theme === 'dark'" aria-hidden="true">☀️</span>
          <span v-else aria-hidden="true">🌙</span>
        </button>

        <button
          class="btn-icon"
          type="button"
          :title="ui.density === 'compact' ? '切换到舒适' : '切换到紧凑'"
          :aria-label="ui.density === 'compact' ? '切换到舒适密度' : '切换到紧凑密度'"
          @click="ui.toggleDensity"
        >
          <svg v-if="ui.density === 'compact'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <line x1="3" y1="8" x2="21" y2="8" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="16" x2="21" y2="16" />
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
            <rect x="3" y="4" width="18" height="16" rx="2" />
            <line x1="3" y1="10" x2="21" y2="10" />
            <line x1="3" y1="15" x2="21" y2="15" />
          </svg>
        </button>
      </div>

      <RouterLink
        v-if="auth.authed && auth.userId"
        :to="{ name: 'userProfile', params: { userId: String(auth.userId) } }"
        class="sidebar-user-link"
        @click="onNavClick"
      >
        <UiAvatar :src="auth.me?.headerUrl || ''" :name="auth.username || ''" :size="28" />
        <div v-if="!ui.sidebarCollapsed" class="sidebar-user">
          <div class="sidebar-user-row">
            <div class="sidebar-user-name truncate">{{ auth.username || `成员 ${auth.userId}` }}</div>
            <UiRoleBadge :user="auth.me" />
          </div>
          <div class="sidebar-user-meta muted truncate">{{ props.mode === 'admin' ? '治理视图已启用' : '继续你的讨论与阅读' }}</div>
        </div>
      </RouterLink>
    </div>
  </div>

  <!-- Mobile Overlay -->
  <div class="sidebar-overlay" :class="{ open: !ui.sidebarCollapsed }" @click="ui.setSidebarCollapsed(true)"></div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import UiAvatar from '../ui/UiAvatar.vue'
import UiRoleBadge from '../ui/UiRoleBadge.vue'
import { getSidebarNavigation, isNavItemActive } from '../../router/navigation'

const props = defineProps({
  mode: { type: String, default: 'public' }
})

const auth = useAuthStore()
const ui = useUiStore()
const route = useRoute()

const navGroups = computed(() =>
  getSidebarNavigation({
    authed: auth.authed,
    userId: auth.userId,
    roles: auth.authorities
  })
)

function isMobileViewport() {
  if (typeof window === 'undefined') return false
  return !!window.matchMedia?.('(max-width: 768px)')?.matches
}

function onNavClick() {
  if (!isMobileViewport()) return
  ui.setSidebarCollapsed(true)
}
</script>
