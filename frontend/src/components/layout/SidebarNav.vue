<template>
  <div class="sidebar" :class="{ collapsed: ui.sidebarCollapsed, 'mobile-open': !ui.sidebarCollapsed }">
    <div class="sidebar-header" style="justify-content: space-between; padding: 16px">
     <!-- Mobile toggle inside sidebar to close? Or just clicking overlay. -->
      <RouterLink to="/posts" class="sidebar-brand row" style="gap: 10px; text-decoration: none" @click="ui.setSidebarCollapsed(true)">
        <div style="width: 32px; height: 32px; background: var(--accent); border-radius: 8px; color: white; display: flex; align-items: center; justify-content: center; font-weight: 900">C</div>
        <span v-if="!ui.sidebarCollapsed" style="font-weight: 700; font-size: 18px; color: var(--text-1)">Community</span>
      </RouterLink>
    </div>

    <div class="sidebar-scroll" style="padding: 0 12px">
      <div class="nav-group">
        <div class="nav-group-title" v-if="!ui.sidebarCollapsed">Explore</div>
        <RouterLink class="nav-item" to="/posts" title="全站">
          <span class="nav-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"></path><polyline points="9 22 9 12 15 12 15 22"></polyline></svg></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">All Posts</span>
        </RouterLink>
         <RouterLink class="nav-item" to="/search" title="搜索">
           <span class="nav-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Search</span>
        </RouterLink>
      </div>

       <div class="nav-group" style="margin-top: 24px">
        <div class="nav-group-title" v-if="!ui.sidebarCollapsed">Boards</div>
        <RouterLink class="nav-item" to="/posts?type=tech" title="技术">
          <span class="nav-icon"><span style="font-size: 14px">💻</span></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Tech</span>
        </RouterLink>
        <RouterLink class="nav-item" to="/posts?type=life" title="生活">
          <span class="nav-icon"><span style="font-size: 14px">☕</span></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Life</span>
        </RouterLink>
        <RouterLink class="nav-item" to="/posts?type=feedback" title="反馈">
          <span class="nav-icon"><span style="font-size: 14px">📢</span></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Feedback</span>
        </RouterLink>
      </div>
      
       <div class="nav-group" style="margin-top: 24px">
        <div class="nav-group-title" v-if="!ui.sidebarCollapsed">Me</div>
        <RouterLink class="nav-item" to="/notices" title="通知" @click="isMobile ? ui.setSidebarCollapsed(true) : null">
          <span class="nav-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Inbox</span>
        </RouterLink>
        <RouterLink class="nav-item" to="/messages" title="私信" @click="isMobile ? ui.setSidebarCollapsed(true) : null">
          <span class="nav-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Messages</span>
        </RouterLink>
        <RouterLink class="nav-item" :to="`/users/${meId || 0}`" title="我的" @click="isMobile ? ui.setSidebarCollapsed(true) : null">
          <span class="nav-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Profile</span>
        </RouterLink>
         <RouterLink class="nav-item" to="/settings" title="设置" @click="isMobile ? ui.setSidebarCollapsed(true) : null">
          <span class="nav-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Settings</span>
        </RouterLink>
      </div>

       <div class="nav-group" v-if="!auth.authed">
         <RouterLink class="nav-item" to="/auth/login" title="登录">
          <span class="nav-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"></path><polyline points="10 17 15 12 10 7"></polyline><line x1="15" y1="12" x2="3" y2="12"></line></svg></span>
          <span class="nav-text" v-if="!ui.sidebarCollapsed">Log In</span>
        </RouterLink>
       </div>

    </div>

    <!-- Bottom User Area -->
    <div class="sidebar-footer row" style="padding: 16px; border-top: 1px solid var(--border); justify-content: space-between">
       <button class="btn-icon" @click="toggleTheme" title="切换主题">
          <span v-if="theme === 'dark'">☀️</span>
          <span v-else>🌙</span>
       </button>

       <button class="btn-icon" @click="ui.toggleSidebar">
         <svg v-if="ui.sidebarCollapsed" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 17l5-5-5-5M6 17l5-5-5-5"/></svg>
         <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 17l-5-5 5-5M18 17l-5-5 5-5"/></svg>
       </button>
    </div>
  </div>
  <!-- Mobile Overlay -->
  <div class="sidebar-overlay" :class="{ open: !ui.sidebarCollapsed }" @click="ui.setSidebarCollapsed(true)"></div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'
import UiAvatar from '../ui/UiAvatar.vue'

const auth = useAuthStore()
const ui = useUiStore()
</script>

