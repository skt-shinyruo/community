<!-- AuthShell：认证相关页面的简洁骨架（保留主题/密度切换）。 -->
<template>
  <div class="auth-shell">
    <div class="auth-topbar">
      <RouterLink to="/posts" class="auth-brand">
        <span class="auth-brand-mark" aria-hidden="true">C</span>
        <span class="auth-brand-copy">
          <span class="auth-brand-title">Community</span>
          <span class="auth-brand-subtitle">社区、交易与治理工作台</span>
        </span>
      </RouterLink>
      <div class="auth-shell-actions">
        <UiButton variant="secondary" class="auth-shell-btn" @click="ui.toggleDensity">
          {{ ui.density === 'compact' ? '舒适' : '紧凑' }}
        </UiButton>
        <UiButton variant="secondary" class="auth-shell-btn" @click="ui.toggleTheme">
          {{ ui.theme === 'dark' ? '浅色' : '深色' }}
        </UiButton>
      </div>
    </div>

    <div class="auth-content">
      <div class="auth-card auth-card-frame">
        <slot />
      </div>
    </div>
  </div>
</template>

<script setup>
import UiButton from '../ui/UiButton.vue'
import { useUiStore } from '../../stores/ui'

const ui = useUiStore()
</script>
