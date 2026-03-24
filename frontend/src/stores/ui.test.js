import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { useUiStore } from './ui'

describe('stores/ui', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should remove the legacy right-panel state contract', () => {
    const store = useUiStore()

    expect('rightPanelOpen' in store.$state).toBe(false)
    expect('toggleRightPanel' in store).toBe(false)
  })
})
