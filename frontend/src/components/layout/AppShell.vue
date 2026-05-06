<!-- AppShell：桌面优先两栏工作区骨架（Sidebar + Main）。 -->
<template>
  <div class="app-shell" :class="shellClass">
    <aside
      class="app-sidebar"
      :class="{
        'is-collapsed': ui.sidebarCollapsed,
        'is-mobile-open': ui.mobileSidebarOpen
      }"
    >
      <SidebarNav :mode="props.mode" />
    </aside>

    <div class="app-main">
      <Topbar :mode="props.mode" />
      <div class="app-content">
        <slot />
      </div>
      <MobileNav v-if="props.mode !== 'admin'" :mode="props.mode" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useUiStore } from '../../stores/ui'
import SidebarNav from './SidebarNav.vue'
import Topbar from './Topbar.vue'
import MobileNav from './MobileNav.vue'

const props = defineProps({
  mode: { type: String, default: 'public' }
})

const ui = useUiStore()

const shellClass = computed(() => ({
  'sidebar-collapsed': ui.sidebarCollapsed,
  'app-shell--public': props.mode === 'public',
  'app-shell--admin': props.mode === 'admin'
}))
</script>
