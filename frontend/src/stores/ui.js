// UI 偏好状态：主题（light/dark/system 三态）、密度（compact/comfortable）、侧边栏展开等。

import { defineStore } from 'pinia'
import { safeJsonParse } from '../utils/safeJson'
import { safeStorageGet, safeStorageSet } from '../utils/safeStorage'

const STORAGE_KEY = 'community.ui'
const THEME_VALUES = ['light', 'dark', 'system']
const DARK_MEDIA_QUERY = '(prefers-color-scheme: dark)'

function clampEnum(value, allowed, fallback) {
  return allowed.includes(value) ? value : fallback
}

function readSystemDark() {
  if (typeof window === 'undefined') return false
  return Boolean(window.matchMedia?.(DARK_MEDIA_QUERY)?.matches)
}

export const useUiStore = defineStore('ui', {
  state: () => ({
    theme: 'system', // light | dark | system —— 用户偏好；system 表示跟随系统
    systemDark: false, // 系统偏好快照（不持久化），由 matchMedia 监听维护
    density: 'compact', // comfortable | compact
    sidebarCollapsed: false,
    mobileSidebarOpen: false
  }),
  getters: {
    // 实际生效的主题：跟随系统时取系统偏好，否则取显式偏好。
    effectiveTheme: (state) => {
      if (state.theme === 'system') return state.systemDark ? 'dark' : 'light'
      return state.theme
    }
  },
  actions: {
    init() {
      if (typeof window === 'undefined') return

      const parsed = safeJsonParse(safeStorageGet(STORAGE_KEY))

      const theme = clampEnum(parsed?.theme, THEME_VALUES, 'system')
      // 技术社区默认更偏紧凑（PC 主场景信息密度更高）；老用户以 localStorage 为准不受影响。
      const density = clampEnum(parsed?.density, ['comfortable', 'compact'], 'compact')
      const sidebarCollapsed = typeof parsed?.sidebarCollapsed === 'boolean' ? parsed.sidebarCollapsed : false

      this.theme = theme
      this.systemDark = readSystemDark()
      this.density = density
      this.sidebarCollapsed = sidebarCollapsed
      this.mobileSidebarOpen = false

      // 跟随系统时响应系统主题切换；显式偏好下系统变化不影响生效主题。
      const media = window.matchMedia?.(DARK_MEDIA_QUERY)
      const onSystemThemeChange = (event) => {
        this.systemDark = Boolean(event?.matches)
        this.applyToDocument()
      }
      if (typeof media?.addEventListener === 'function') {
        media.addEventListener('change', onSystemThemeChange)
      } else if (typeof media?.addListener === 'function') {
        media.addListener(onSystemThemeChange)
      }

      this.applyToDocument()
      this.persist()
    },

    applyToDocument() {
      if (typeof document === 'undefined') return
      document.documentElement.dataset.theme = this.effectiveTheme
      document.documentElement.dataset.density = this.density
    },

    persist() {
      if (typeof window === 'undefined') return
      safeStorageSet(
        STORAGE_KEY,
        JSON.stringify({
          theme: this.theme,
          density: this.density,
          sidebarCollapsed: this.sidebarCollapsed
        })
      )
    },

    setTheme(theme) {
      this.theme = clampEnum(theme, THEME_VALUES, 'system')
      this.applyToDocument()
      this.persist()
    },

    // Topbar 快捷按钮：按当前生效主题切到相反主题，并保存为显式偏好。
    toggleTheme() {
      this.setTheme(this.effectiveTheme === 'dark' ? 'light' : 'dark')
    },

    setDensity(density) {
      this.density = density === 'compact' ? 'compact' : 'comfortable'
      this.applyToDocument()
      this.persist()
    },

    toggleDensity() {
      this.setDensity(this.density === 'compact' ? 'comfortable' : 'compact')
    },

    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      this.persist()
    },

    closeMobileSidebar() {
      this.mobileSidebarOpen = false
    },

    toggleMobileSidebar() {
      this.mobileSidebarOpen = !this.mobileSidebarOpen
    }
  }
})
