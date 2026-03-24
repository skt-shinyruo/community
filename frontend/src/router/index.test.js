import { afterEach, describe, expect, it, vi } from 'vitest'

describe('router/index', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
    vi.doUnmock('./authGuard')
  })

  it('should keep the real product entry on posts and expose preview B as the default preview route', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    const historyStub = {
      state: null,
      replaceState(state) {
        this.state = state
      },
      pushState(state) {
        this.state = state
      }
    }
    const locationStub = {
      protocol: 'http:',
      host: 'localhost:4173',
      hostname: 'localhost',
      port: '4173',
      pathname: '/',
      search: '',
      hash: ''
    }
    vi.stubGlobal('location', locationStub)
    vi.stubGlobal('history', historyStub)
    vi.stubGlobal('window', { location: locationStub, history: historyStub, addEventListener() {}, removeEventListener() {} })
    vi.stubGlobal('document', {
      querySelector: () => null,
      addEventListener() {},
      removeEventListener() {},
      createElement: () => ({ relList: { supports: () => false } })
    })

    const { default: router } = await import('./index')
    const resolvedA = router.resolve('/preview/editorial/a')
    const resolvedB = router.resolve('/preview/editorial/b')
    const resolvedC = router.resolve('/preview/editorial/c')
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('posts')
    await router.push('/preview/editorial')

    expect(router.currentRoute.value.name).toBe('editorialPreviewB')
    expect(resolvedA.name).toBe('editorialPreviewA')
    expect(resolvedB.name).toBe('editorialPreviewB')
    expect(resolvedC.name).toBe('editorialPreviewC')
    expect(router.getRoutes().some((route) => route.name === 'activation')).toBe(false)
    expect(resolvedA.meta?.requiresAuth).not.toBe(true)
    expect(resolvedA.meta?.navGroup).toBe('system')
  })
})
