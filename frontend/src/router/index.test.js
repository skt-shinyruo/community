import { afterEach, describe, expect, it, vi } from 'vitest'

function stubRouterGlobals() {
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
}

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

    stubRouterGlobals()

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

  it('should keep the approved public route navGroup split', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    stubRouterGlobals()

    const { default: router } = await import('./index')
    const routesByName = new Map(router.getRoutes().map((route) => [route.name, route]))

    expect(routesByName.get('posts')?.meta?.navGroup).toBe('explore')
    expect(routesByName.get('postDetail')?.meta?.navGroup).toBe('explore')
    expect(routesByName.get('search')?.meta?.navGroup).toBe('explore')
    expect(routesByName.get('market')?.meta?.navGroup).toBe('explore')
    expect(routesByName.get('wallet')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('marketPublish')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('marketBuyingOrders')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('marketAddresses')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('messages')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('messageDetail')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('notices')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('noticeDetail')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('bookmarks')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('settings')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('userProfile')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('followees')?.meta?.navGroup).toBe('me')
    expect(routesByName.get('followers')?.meta?.navGroup).toBe('me')

    expect(routesByName.get('walletAdmin')?.meta?.navGroup).toBe('admin')
    expect(routesByName.get('adminMarketDisputes')?.meta?.navGroup).toBe('admin')
    expect(routesByName.has('growthCenter')).toBe(false)
    expect(routesByName.has('rewardShop')).toBe(false)
    expect(routesByName.has('rewardOrders')).toBe(false)
    expect(routesByName.has('growthAdmin')).toBe(false)
    expect(routesByName.has('rewardOps')).toBe(false)
    expect(routesByName.has('leaderboard')).toBe(false)
  })

  it('should expose unified market routes', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    stubRouterGlobals()

    const { default: router } = await import('./index')
    const routeNames = router.getRoutes().map((route) => route.name)

    expect(routeNames).toContain('market')
    expect(routeNames).toContain('marketAddresses')
    expect(routeNames).toContain('marketPublish')
    expect(routeNames).toContain('adminMarketDisputes')
    expect(routeNames).not.toContain('growthCenter')
    expect(routeNames).not.toContain('rewardShop')
    expect(routeNames).not.toContain('rewardOrders')
    expect(routeNames).not.toContain('growthAdmin')
    expect(routeNames).not.toContain('rewardOps')
    expect(routeNames).not.toContain('leaderboard')
  })

  it('should lazy-load non-trivial route views to keep them out of the entry bundle', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    stubRouterGlobals()

    const { default: router } = await import('./index')
    const routesByName = new Map(router.getRoutes().map((route) => [route.name, route]))

    expect(typeof routesByName.get('posts')?.components?.default).toBe('function')
    expect(typeof routesByName.get('postDetail')?.components?.default).toBe('function')
    expect(typeof routesByName.get('market')?.components?.default).toBe('function')
    expect(typeof routesByName.get('messages')?.components?.default).toBe('function')
    expect(typeof routesByName.get('moderation')?.components?.default).toBe('function')
  })
})
