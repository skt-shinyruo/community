<!-- SidebarNav：左侧导航栏（三个一级域 + 底部账户区）。 -->
<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <RouterLink :to="{ name: 'posts' }" class="sidebar-brand" aria-label="返回帖子列表" @click="onNavClick">
        <div class="sidebar-brand-mark" aria-hidden="true">{{ props.mode === 'admin' ? 'M' : 'C' }}</div>
        <span v-if="!ui.sidebarCollapsed" class="sidebar-brand-copy">
          <span class="sidebar-brand-text">{{ props.mode === 'admin' ? 'Moderation Desk' : 'Community' }}</span>
          <span class="sidebar-brand-sub">{{ props.mode === 'admin' ? '运营工作台' : '社区工作台' }}</span>
        </span>
      </RouterLink>

      <UiIconButton
        :aria-label="ui.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        :title="ui.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="ui.toggleSidebar"
      >
        <PanelLeftOpen v-if="ui.sidebarCollapsed" :size="20" aria-hidden="true" />
        <PanelLeftClose v-else :size="20" aria-hidden="true" />
      </UiIconButton>
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
          :aria-label="navItemAriaLabel(item)"
          @click="onNavClick"
        >
          <span class="nav-icon" aria-hidden="true">
            <component :is="resolveNavIcon(item.icon)" v-if="resolveNavIcon(item.icon)" :size="18" />
          </span>
          <span v-if="!ui.sidebarCollapsed" class="nav-text">{{ item.label }}</span>
          <span
            v-if="navBadgeCount(item) > 0"
            class="nav-badge"
            :class="{ 'nav-badge--dot': ui.sidebarCollapsed }"
            aria-hidden="true"
          >{{ formatUnreadCount(navBadgeCount(item)) }}</span>
        </RouterLink>
      </div>
    </div>

    <div v-if="auth.authed && auth.userId" class="sidebar-footer">
      <RouterLink
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

      <div class="sidebar-account-actions">
        <template v-if="!ui.sidebarCollapsed">
          <UiButton
            variant="ghost"
            class="sidebar-account-action"
            :to="{ name: 'settings' }"
            title="设置"
            @click="onNavClick"
          >
            <Settings :size="16" aria-hidden="true" />
            设置
          </UiButton>
          <UiButton
            variant="ghost"
            class="sidebar-account-action"
            title="登出"
            @click="onLogout"
          >
            <LogOut :size="16" aria-hidden="true" />
            登出
          </UiButton>
        </template>
        <template v-else>
          <UiIconButton aria-label="设置" title="设置" @click="onSettingsClick">
            <Settings :size="18" aria-hidden="true" />
          </UiIconButton>
          <UiIconButton aria-label="登出" title="登出" @click="onLogout">
            <LogOut :size="18" aria-hidden="true" />
          </UiIconButton>
        </template>
      </div>
    </div>
  </div>

  <!-- Mobile Overlay -->
  <div class="sidebar-overlay" :class="{ open: ui.mobileSidebarOpen }" @click="ui.closeMobileSidebar"></div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LogOut, PanelLeftClose, PanelLeftOpen, Settings } from 'lucide-vue-next'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import { useInboxUnreadStore, formatUnreadCount } from '../../stores/inboxUnread'
import http from '../../api/http'
import UiAvatar from '../ui/UiAvatar.vue'
import UiButton from '../ui/UiButton.vue'
import UiIconButton from '../ui/UiIconButton.vue'
import UiRoleBadge from '../ui/UiRoleBadge.vue'
import { getSidebarNavigation, isNavItemActive } from '../../router/navigation'
import { resolveNavIcon } from './navIcons'

const props = defineProps({
  mode: { type: String, default: 'public' }
})

const auth = useAuthStore()
const ui = useUiStore()
const inboxUnread = useInboxUnreadStore()
const route = useRoute()
const router = useRouter()

const navGroups = computed(() =>
  getSidebarNavigation({
    authed: auth.authed,
    userId: auth.userId,
    roles: auth.authorities
  })
)

function navBadgeCount(item) {
  if (item?.badge === 'notices') return inboxUnread.noticeUnread
  if (item?.badge === 'messages') return inboxUnread.messageUnread
  return 0
}

function navItemAriaLabel(item) {
  const count = navBadgeCount(item)
  if (count > 0) return `${item.label}，${formatUnreadCount(count)} 条未读`
  return item.label
}

async function onLogout() {
  try {
    await http.post('/api/auth/logout')
  } finally {
    auth.clear()
    inboxUnread.reset()
    router.replace({ name: 'login' })
  }
}

function onSettingsClick() {
  onNavClick()
  router.push({ name: 'settings' })
}

function isMobileViewport() {
  if (typeof window === 'undefined') return false
  return !!window.matchMedia?.('(max-width: 768px)')?.matches
}

function onNavClick() {
  if (!isMobileViewport()) return
  ui.closeMobileSidebar()
}
</script>
