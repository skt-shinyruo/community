<!-- AuthShell：认证相关页面的简洁骨架（保留主题/密度切换）。 -->
<template>
  <div class="auth-shell">
    <div class="auth-topbar">
      <RouterLink to="/posts" class="auth-brand">Community</RouterLink>
      <div class="row" style="gap: 8px">
        <button class="btn secondary" @click="ui.toggleDensity">{{ ui.density === 'compact' ? '紧凑' : '舒适' }}</button>
        <button class="btn secondary" @click="ui.toggleTheme">{{ ui.theme === 'dark' ? '深色' : '浅色' }}</button>
      </div>
    </div>

    <div class="auth-content">
      <div class="auth-card">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useUiStore } from '../../stores/ui'

const ui = useUiStore()
</script>

