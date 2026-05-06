// UI 偏好状态：主题（light/dark）、密度（compact/comfortable）、侧边栏展开等。

import { defineStore } from 'pinia'

const STORAGE_KEY = 'community.ui'

function safeParse(json) {
  try {
    return JSON.parse(json)
  } catch {
    return null
  }
}

function clampEnum(value, allowed, fallback) {
  return allowed.includes(value) ? value : fallback
}

export const useUiStore = defineStore('ui', {
  state: () => ({
    theme: 'light', // light | dark
    density: 'compact', // comfortable | compact
    sidebarCollapsed: false,
    mobileSidebarOpen: false
  }),
  actions: {
    init() {
      if (typeof window === 'undefined') return

      const raw = window.localStorage.getItem(STORAGE_KEY) || ''
      const parsed = raw ? safeParse(raw) : null

      const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)')?.matches
      const theme = clampEnum(parsed?.theme, ['light', 'dark'], prefersDark ? 'dark' : 'light')
      // 技术社区默认更偏紧凑（PC 主场景信息密度更高）；老用户以 localStorage 为准不受影响。
      const density = clampEnum(parsed?.density, ['comfortable', 'compact'], 'compact')
      const sidebarCollapsed = typeof parsed?.sidebarCollapsed === 'boolean' ? parsed.sidebarCollapsed : false

      this.theme = theme
      this.density = density
      this.sidebarCollapsed = sidebarCollapsed
      this.mobileSidebarOpen = false

      this.applyToDocument()
      this.persist()
    },

    applyToDocument() {
      if (typeof document === 'undefined') return
      document.documentElement.dataset.theme = this.theme
      document.documentElement.dataset.density = this.density
    },

    persist() {
      if (typeof window === 'undefined') return
      window.localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          theme: this.theme,
          density: this.density,
          sidebarCollapsed: this.sidebarCollapsed
        })
      )
    },

    setTheme(theme) {
      this.theme = theme === 'dark' ? 'dark' : 'light'
      this.applyToDocument()
      this.persist()
    },

    toggleTheme() {
      this.setTheme(this.theme === 'dark' ? 'light' : 'dark')
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

    setSidebarCollapsed(v) {
      this.sidebarCollapsed = !!v
      this.persist()
    },

    openMobileSidebar() {
      this.mobileSidebarOpen = true
    },

    closeMobileSidebar() {
      this.mobileSidebarOpen = false
    },

    toggleMobileSidebar() {
      this.mobileSidebarOpen = !this.mobileSidebarOpen
    }
  }
})
