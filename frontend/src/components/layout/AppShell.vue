<!-- AppShell：桌面优先三栏工作区骨架（Sidebar + Topbar + Content）。 -->
<template>
  <div class="app-shell" :class="shellClass">
    <aside class="app-sidebar" :class="{ 'mobile-open': !ui.sidebarCollapsed }">
      <SidebarNav :mode="props.mode" />
    </aside>

    <div class="app-main">
      <Topbar :mode="props.mode" />
      <div class="app-content">
        <slot />
      </div>
      <MobileNav v-if="props.mode !== 'admin'" :mode="props.mode" />
    </div>

    <aside v-if="hasRight" class="app-right">
      <slot name="right" />
    </aside>
  </div>
</template>

<script setup>
import { computed, useSlots } from 'vue'
import { useUiStore } from '../../stores/ui'
import SidebarNav from './SidebarNav.vue'
import Topbar from './Topbar.vue'
import MobileNav from './MobileNav.vue'

const props = defineProps({
  mode: { type: String, default: 'public' }
})

const ui = useUiStore()
const slots = useSlots()
const hasRight = computed(() => props.mode === 'public' && !!slots.right && ui.rightPanelOpen)

const shellClass = computed(() => ({
  'sidebar-collapsed': ui.sidebarCollapsed,
  'has-right': hasRight.value,
  'app-shell--public': props.mode === 'public',
  'app-shell--admin': props.mode === 'admin'
}))
</script>
