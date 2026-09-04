import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useUiStore } from './ui'

function installWindow(width = 1200, stored = null, { systemDark = false } = {}) {
  const storage = new Map()
  if (stored) storage.set('community.ui', JSON.stringify(stored))

  const mediaListeners = new Set()
  const darkMedia = {
    matches: systemDark,
    addEventListener: (type, cb) => {
      if (type === 'change') mediaListeners.add(cb)
    },
    removeEventListener: (type, cb) => {
      mediaListeners.delete(cb)
    }
  }

  vi.stubGlobal('window', {
    innerWidth: width,
    localStorage: {
      getItem: (key) => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, String(value))
    },
    matchMedia: (query) => (query === '(prefers-color-scheme: dark)' ? darkMedia : { matches: false })
  })

  const dataset = {}
  vi.stubGlobal('document', {
    documentElement: { dataset }
  })

  const setSystemDark = (dark) => {
    darkMedia.matches = dark
    for (const cb of [...mediaListeners]) cb({ matches: dark })
  }

  return { storage, dataset, setSystemDark }
}

describe('stores/ui', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
    setActivePinia(createPinia())
  })

  it('keeps desktop collapsed preference separate from mobile drawer state', () => {
    installWindow(390, { sidebarCollapsed: false, theme: 'light', density: 'compact' })
    const store = useUiStore()

    store.init()

    expect(store.sidebarCollapsed).toBe(false)
    expect(store.mobileSidebarOpen).toBe(false)

    store.toggleMobileSidebar()
    expect(store.mobileSidebarOpen).toBe(true)
    expect(store.sidebarCollapsed).toBe(false)

    store.closeMobileSidebar()
    expect(store.mobileSidebarOpen).toBe(false)
    expect(store.sidebarCollapsed).toBe(false)
  })

  it('persists theme density and desktop collapsed state but not mobile drawer state', () => {
    const { storage } = installWindow(1200)
    const store = useUiStore()

    store.toggleSidebar()
    store.toggleMobileSidebar()
    store.setTheme('dark')
    store.setDensity('comfortable')

    const persisted = JSON.parse(storage.get('community.ui'))
    expect(persisted).toEqual({
      theme: 'dark',
      density: 'comfortable',
      sidebarCollapsed: true
    })
  })

  it('keeps UI initialization usable when browser storage is unavailable', () => {
    installWindow()
    window.localStorage.getItem = () => { throw new Error('storage blocked') }
    window.localStorage.setItem = () => { throw new Error('storage blocked') }
    const store = useUiStore()

    expect(() => store.init()).not.toThrow()
    expect(() => store.setTheme('dark')).not.toThrow()
    expect(store.theme).toBe('dark')
  })

  it('should keep the retired right-panel state contract absent', () => {
    installWindow()
    const store = useUiStore()

    expect('rightPanelOpen' in store.$state).toBe(false)
    expect('toggleRightPanel' in store).toBe(false)
  })

  it('defaults to the system theme and resolves the effective theme from the OS preference', () => {
    const dark = installWindow(1200, null, { systemDark: true })
    const darkStore = useUiStore()
    darkStore.init()

    expect(darkStore.theme).toBe('system')
    expect(darkStore.effectiveTheme).toBe('dark')
    expect(dark.dataset.theme).toBe('dark')
    expect(dark.dataset.density).toBe('compact')
  })

  it('keeps compact as the default density', () => {
    installWindow()
    const store = useUiStore()
    store.init()

    expect(store.density).toBe('compact')
  })

  it('follows OS theme changes while the preference is system', () => {
    const { dataset, setSystemDark } = installWindow(1200, null, { systemDark: false })
    const store = useUiStore()
    store.init()

    expect(store.effectiveTheme).toBe('light')
    expect(dataset.theme).toBe('light')

    setSystemDark(true)
    expect(store.theme).toBe('system')
    expect(store.effectiveTheme).toBe('dark')
    expect(dataset.theme).toBe('dark')

    setSystemDark(false)
    expect(store.effectiveTheme).toBe('light')
    expect(dataset.theme).toBe('light')
  })

  it('ignores OS theme changes while an explicit preference is set', () => {
    const { dataset, setSystemDark } = installWindow(1200, { theme: 'light', density: 'compact' }, { systemDark: true })
    const store = useUiStore()
    store.init()

    expect(store.theme).toBe('light')
    expect(store.effectiveTheme).toBe('light')
    expect(dataset.theme).toBe('light')

    setSystemDark(false)
    expect(store.effectiveTheme).toBe('light')
    expect(dataset.theme).toBe('light')
  })

  it('toggleTheme switches away from the effective theme and saves an explicit preference', () => {
    const { storage } = installWindow(1200, null, { systemDark: true })
    const store = useUiStore()
    store.init()

    store.toggleTheme()
    expect(store.theme).toBe('light')
    expect(store.effectiveTheme).toBe('light')
    expect(JSON.parse(storage.get('community.ui')).theme).toBe('light')

    store.toggleTheme()
    expect(store.theme).toBe('dark')
    expect(JSON.parse(storage.get('community.ui')).theme).toBe('dark')
  })

  it('restores explicit and system preferences after a reload', () => {
    installWindow(1200, { theme: 'system', density: 'compact' }, { systemDark: true })
    const systemStore = useUiStore()
    systemStore.init()
    expect(systemStore.theme).toBe('system')
    expect(systemStore.effectiveTheme).toBe('dark')

    setActivePinia(createPinia())
    installWindow(1200, { theme: 'dark', density: 'compact' }, { systemDark: false })
    const explicitStore = useUiStore()
    explicitStore.init()
    expect(explicitStore.theme).toBe('dark')
    expect(explicitStore.effectiveTheme).toBe('dark')
  })

  it('setTheme accepts system and resumes following the OS preference', () => {
    const { dataset, setSystemDark } = installWindow(1200, { theme: 'dark', density: 'compact' }, { systemDark: false })
    const store = useUiStore()
    store.init()
    expect(store.effectiveTheme).toBe('dark')

    store.setTheme('system')
    expect(store.theme).toBe('system')
    expect(store.effectiveTheme).toBe('light')
    expect(dataset.theme).toBe('light')

    setSystemDark(true)
    expect(store.effectiveTheme).toBe('dark')
    expect(dataset.theme).toBe('dark')
  })

  it('falls back to the system theme for unknown stored or requested values', () => {
    installWindow(1200, { theme: 'solarized', density: 'compact' }, { systemDark: true })
    const store = useUiStore()
    store.init()

    expect(store.theme).toBe('system')
    expect(store.effectiveTheme).toBe('dark')

    store.setTheme('solarized')
    expect(store.theme).toBe('system')
  })
})
