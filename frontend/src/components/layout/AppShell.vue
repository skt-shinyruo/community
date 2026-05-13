<!-- AppShell：桌面优先两栏工作区骨架（Sidebar + Main）。 -->
<template>
  <div class="app-shell" :class="shellClass">
    <aside
      class="app-sidebar"
      :class="{
        'is-collapsed': ui.sidebarCollapsed,
        'is-mobile-open': ui.mobileSidebarOpen,
        'is-mobile-hidden': isMobileSidebarHidden
      }"
      :aria-hidden="isMobileSidebarHidden ? 'true' : null"
      :inert="isMobileSidebarHidden ? true : null"
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
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useUiStore } from '../../stores/ui'
import SidebarNav from './SidebarNav.vue'
import Topbar from './Topbar.vue'
import MobileNav from './MobileNav.vue'

const props = defineProps({
  mode: { type: String, default: 'public' }
})

const ui = useUiStore()
const isMobileViewport = ref(false)

function syncViewportState() {
  if (typeof window === 'undefined') return
  isMobileViewport.value = !!window.matchMedia?.('(max-width: 768px)')?.matches
}

const shellClass = computed(() => ({
  'sidebar-collapsed': ui.sidebarCollapsed,
  'app-shell--public': props.mode === 'public',
  'app-shell--admin': props.mode === 'admin'
}))

const isMobileSidebarHidden = computed(() => isMobileViewport.value && !ui.mobileSidebarOpen)

onMounted(() => {
  syncViewportState()
  window.addEventListener('resize', syncViewportState)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportState)
})
</script>
