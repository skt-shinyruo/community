<!-- SidebarNav：左侧导航（Notion 风格分组 + 折叠）。 -->
<template>
  <div class="sidebar">
    <div class="sidebar-header">
      <RouterLink to="/posts" class="sidebar-brand">
        <span class="sidebar-brand-mark">C</span>
        <span class="sidebar-brand-text">Community</span>
      </RouterLink>
      <button class="btn ghost" @click="ui.toggleSidebar" :title="ui.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'">
        <span v-if="ui.sidebarCollapsed">»</span>
        <span v-else>«</span>
      </button>
    </div>

    <div class="sidebar-scroll">
      <div class="nav-group">
        <div class="nav-group-title">社区</div>
        <RouterLink class="nav-item" to="/posts" title="帖子">
          <span class="nav-icon">帖</span>
          <span class="nav-text">帖子</span>
        </RouterLink>
        <RouterLink class="nav-item" to="/search" title="搜索">
          <span class="nav-icon">搜</span>
          <span class="nav-text">搜索</span>
        </RouterLink>
      </div>

      <div class="nav-group" v-if="auth.authed">
        <div class="nav-group-title">消息</div>
        <RouterLink class="nav-item" to="/messages" title="私信">
          <span class="nav-icon">信</span>
          <span class="nav-text">私信</span>
        </RouterLink>
        <RouterLink class="nav-item" to="/notices" title="通知">
          <span class="nav-icon">通</span>
          <span class="nav-text">通知</span>
        </RouterLink>
      </div>

      <div class="nav-group" v-if="auth.authed && auth.isAdminOrModerator">
        <div class="nav-group-title">管理</div>
        <RouterLink class="nav-item" to="/analytics" title="统计">
          <span class="nav-icon">数</span>
          <span class="nav-text">统计</span>
        </RouterLink>
      </div>

      <div class="nav-group">
        <div class="nav-group-title">个人</div>

        <RouterLink v-if="auth.authed && auth.userId" class="nav-item" :to="`/users/${auth.userId}`" title="我的主页">
          <span class="nav-icon">我</span>
          <span class="nav-text">我的主页</span>
        </RouterLink>

        <RouterLink v-if="auth.authed" class="nav-item" to="/settings" title="设置">
          <span class="nav-icon">设</span>
          <span class="nav-text">设置</span>
        </RouterLink>

        <RouterLink v-if="auth.authed" class="nav-item" to="/dev" title="联调">
          <span class="nav-icon">联</span>
          <span class="nav-text">联调</span>
        </RouterLink>

        <RouterLink v-if="!auth.authed" class="nav-item" to="/auth/login" title="登录">
          <span class="nav-icon">登</span>
          <span class="nav-text">登录</span>
        </RouterLink>

        <RouterLink v-if="!auth.authed" class="nav-item" to="/auth/register" title="注册">
          <span class="nav-icon">注</span>
          <span class="nav-text">注册</span>
        </RouterLink>
      </div>
    </div>

    <div class="sidebar-footer">
      <div v-if="auth.authed" class="sidebar-user">
        <div class="sidebar-user-name">{{ auth.username || `user#${auth.userId}` }}</div>
        <div class="sidebar-user-meta muted">userId={{ auth.userId }}</div>
      </div>
      <div v-else class="sidebar-user muted">未登录</div>
    </div>
  </div>
</template>

<script setup>
import { useAuthStore } from '../../stores/auth'
import { useUiStore } from '../../stores/ui'

const auth = useAuthStore()
const ui = useUiStore()
</script>

