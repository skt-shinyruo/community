<!-- Topbar：折叠按钮 + 中文工作区 eyebrow + 壳搜索 + 主题快捷按钮。 -->
<template>
  <div class="app-topbar" :class="`app-topbar--${props.mode}`">
    <div class="topbar-leading">
      <UiIconButton
        class="topbar-menu-btn"
        aria-label="折叠或展开侧边栏"
        title="折叠/展开侧边栏"
        @click="onMenuClick"
      >
        <Menu :size="20" aria-hidden="true" />
      </UiIconButton>
      <div class="topbar-eyebrow">{{ modeEyebrow }}</div>
    </div>

    <div class="topbar-trailing">
      <TopbarSearchBox
        v-if="showShellSearch"
        v-model.trim="searchKeyword"
        :is-mac="isMac"
        :placeholder="searchPlaceholder"
        @submit="submitSearch"
      />

      <UiIconButton
        :aria-label="themeActionLabel"
        :title="themeActionLabel"
        @click="ui.toggleTheme"
      >
        <Sun v-if="ui.effectiveTheme === 'dark'" :size="20" aria-hidden="true" />
        <Moon v-else :size="20" aria-hidden="true" />
      </UiIconButton>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Menu, Moon, Sun } from 'lucide-vue-next'
import { useUiStore } from '../../stores/ui'
import { getRouteWorkspaceLabel } from '../../router/routeCatalog'
import TopbarSearchBox from '../scene/TopbarSearchBox.vue'
import UiIconButton from '../ui/UiIconButton.vue'

const props = defineProps({
  mode: { type: String, default: 'public' }
})

const route = useRoute()
const router = useRouter()
const ui = useUiStore()

const searchKeyword = ref('')
const desktopSearchVisible = ref(typeof window === 'undefined' ? true : window.innerWidth > 920)

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad|iPod/i.test(navigator.platform || '')

const modeEyebrow = computed(() => getRouteWorkspaceLabel(route.name))
// 壳搜索在公开页常显，跳转 /search 覆盖帖子、标签与用户；市场页内搜索随市场波次交付。
const searchPlaceholder = `搜索帖子、标签或用户（${isMac ? '⌘' : 'Ctrl'} K）`
const showShellSearch = computed(() => props.mode !== 'admin' && desktopSearchVisible.value)

const themeActionLabel = computed(() => (ui.effectiveTheme === 'dark' ? '切换到浅色主题' : '切换到深色主题'))

function submitSearch() {
  const q = searchKeyword.value || ''
  const query = q ? { ...route.query, q } : { ...route.query }
  if (q) {
    query.q = q
  } else {
    delete query.q
  }
  // 在同一路由下触发搜索时，replace 可减少历史记录噪音。
  if (String(route.name || '') === 'search') {
    router.replace({ name: 'search', query })
    return
  }
  router.push({ name: 'search', query })
}

function onMenuClick() {
  if (typeof window !== 'undefined' && window.matchMedia?.('(max-width: 768px)')?.matches) {
    ui.toggleMobileSidebar()
    return
  }
  ui.toggleSidebar()
}

function syncDesktopSearchVisible() {
  if (typeof window === 'undefined') return
  desktopSearchVisible.value = window.innerWidth > 920
}

onMounted(() => {
  syncDesktopSearchVisible()
  window.addEventListener('resize', syncDesktopSearchVisible)
  const q = route.query?.q
  if (typeof q === 'string') searchKeyword.value = q
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncDesktopSearchVisible)
})

watch(
  () => route.query?.q,
  (q) => {
    if (typeof q === 'string') {
      searchKeyword.value = q
      return
    }
    // 不强制清空：保留用户最近一次输入，便于复用。
  }
)
</script>
