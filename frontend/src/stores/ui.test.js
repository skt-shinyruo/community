import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useUiStore } from './ui'

function installWindow(width = 1200, stored = null) {
  const storage = new Map()
  if (stored) storage.set('community.ui', JSON.stringify(stored))

  vi.stubGlobal('window', {
    innerWidth: width,
    localStorage: {
      getItem: (key) => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, String(value))
    },
    matchMedia: () => ({ matches: false })
  })

  vi.stubGlobal('document', {
    documentElement: { dataset: {} }
  })

  return storage
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

    store.openMobileSidebar()
    expect(store.mobileSidebarOpen).toBe(true)
    expect(store.sidebarCollapsed).toBe(false)

    store.closeMobileSidebar()
    expect(store.mobileSidebarOpen).toBe(false)
    expect(store.sidebarCollapsed).toBe(false)
  })

  it('persists theme density and desktop collapsed state but not mobile drawer state', () => {
    const storage = installWindow(1200)
    const store = useUiStore()

    store.setSidebarCollapsed(true)
    store.openMobileSidebar()
    store.setTheme('dark')
    store.setDensity('comfortable')

    const persisted = JSON.parse(storage.get('community.ui'))
    expect(persisted).toEqual({
      theme: 'dark',
      density: 'comfortable',
      sidebarCollapsed: true
    })
  })

  it('should keep the retired right-panel state contract absent', () => {
    installWindow()
    const store = useUiStore()

    expect('rightPanelOpen' in store.$state).toBe(false)
    expect('toggleRightPanel' in store).toBe(false)
  })
})
