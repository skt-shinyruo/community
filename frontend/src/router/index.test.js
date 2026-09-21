import { afterEach, describe, expect, it, vi } from 'vitest'
import { ROUTES } from './routeCatalog'

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
  const windowStub = {
    location: locationStub,
    history: historyStub,
    scrollTo: vi.fn(),
    addEventListener() {},
    removeEventListener() {}
  }
  const documentStub = {
    title: '',
    documentElement: { style: { scrollBehavior: '' } },
    querySelector: () => null,
    getElementById: () => null,
    addEventListener() {},
    removeEventListener() {},
    createElement: () => ({ relList: { supports: () => false } })
  }
  vi.stubGlobal('location', locationStub)
  vi.stubGlobal('history', historyStub)
  vi.stubGlobal('window', windowStub)
  vi.stubGlobal('document', documentStub)
  return { historyStub, locationStub, windowStub, documentStub }
}

function flushAsyncWork() {
  return new Promise((resolve) => setTimeout(resolve, 0))
}

describe('router/index', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.resetModules()
    vi.doUnmock('./authGuard')
    vi.doUnmock('../ui/toastService')
  })

  it('should keep the product entry on posts without preview or development routes', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    stubRouterGlobals()

    const { default: router } = await import('./index')
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('posts')

    expect(router.resolve('/preview/editorial').name).toBe('notFound')
    expect(router.resolve('/preview/editorial/a').name).toBe('notFound')
    expect(router.resolve('/preview/editorial/b').name).toBe('notFound')
    expect(router.resolve('/preview/editorial/c').name).toBe('notFound')
    expect(router.resolve('/dev').name).toBe('notFound')
    expect(router.getRoutes().some((route) => route.name === 'activation')).toBe(false)
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
    expect(routesByName.has('opsConsole')).toBe(false)
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

  it('redirects the legacy market addresses entry into the settings addresses section', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    stubRouterGlobals()

    const { default: router } = await import('./index')

    await router.push('/market/addresses')
    expect(router.currentRoute.value.name).toBe('settings')
    expect(router.currentRoute.value.fullPath).toBe('/settings?section=addresses')

    await router.push({ name: 'marketAddresses' })
    expect(router.currentRoute.value.name).toBe('settings')
    expect(router.currentRoute.value.fullPath).toBe('/settings?section=addresses')
  })

  it('passes list variants through route props', async () => {
    vi.doMock('./authGuard', () => ({ authGuard: () => true }))
    stubRouterGlobals()

    const { default: router } = await import('./index')
    const routes = new Map(router.getRoutes().map((route) => [route.name, route]))

    // 路由表必含这四条：用类型断言收窄 Map 查找，避免逐条空检查。
    const routeOf = (name) => /** @type {import('vue-router').RouteRecord} */ (routes.get(name))
    expect(routeOf('marketBuyingOrders').props.default).toEqual({ side: 'buying' })
    expect(routeOf('marketSellingOrders').props.default).toEqual({ side: 'selling' })
    expect((/** @type {(args: { params: Record<string, string> }) => object} */ (routeOf('followees').props.default))({ params: { userId: 'user-1' } })).toEqual({
      relationKind: 'followees',
      userId: 'user-1'
    })
    expect((/** @type {(args: { params: Record<string, string> }) => object} */ (routeOf('followers').props.default))({ params: { userId: 'user-1' } })).toEqual({
      relationKind: 'followers',
      userId: 'user-1'
    })
  })

  it('should register every catalog route exactly once', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    stubRouterGlobals()

    const { default: router } = await import('./index')
    const registeredNames = router.getRoutes()
      .map((route) => route.name)
      .filter(Boolean)
      .map(String)
      .sort()

    expect(registeredNames).toEqual([...Object.keys(ROUTES)].sort())
  })

  it('should register authenticated drive route and public share route', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    stubRouterGlobals()

    const { default: router } = await import('./index')
    const drive = router.getRoutes().find((r) => r.name === 'drive')
    const share = router.getRoutes().find((r) => r.name === 'driveShare')

    expect(drive?.path).toBe('/drive')
    expect(drive?.meta?.requiresAuth).toBe(true)
    expect(share?.path).toBe('/drive/s/:shareToken')
    expect(share?.meta?.requiresAuth).toBeFalsy()
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

  it('updates document.title from the route meta on navigation', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    const { documentStub } = stubRouterGlobals()

    const { default: router } = await import('./index')

    await router.push('/posts')
    expect(documentStub.title).toBe('讨论首页 - Community')

    await router.push('/search')
    expect(documentStub.title).toBe('搜索 - Community')
  })

  it('scrolls to top when navigating across routes', async () => {
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))

    const { windowStub } = stubRouterGlobals()

    const { default: router } = await import('./index')
    await router.push('/posts')
    await flushAsyncWork()
    windowStub.scrollTo.mockClear()

    await router.push('/search')
    await flushAsyncWork()

    expect(windowStub.scrollTo).toHaveBeenCalledWith({ top: 0 })
  })

  it('shows a user-visible refresh prompt when a lazy route chunk fails to load', async () => {
    const showToast = vi.fn()
    vi.doMock('./authGuard', () => ({
      authGuard: () => true
    }))
    vi.doMock('../ui/toastService', () => ({ showToast }))

    stubRouterGlobals()

    const { default: router } = await import('./index')
    // 发版后驻留标签页的懒加载 chunk 已失效：import() 以 TypeError 拒绝，这里用等价 loader 模拟。
    router.addRoute({
      path: '/__stale-chunk',
      name: '__staleChunk',
      component: () =>
        Promise.reject(new TypeError('Failed to fetch dynamically imported module: http://localhost:4173/assets/Stale-deadbeef.js'))
    })

    await router.push('/__stale-chunk').catch(() => {})

    expect(showToast).toHaveBeenCalledTimes(1)
    const payload = showToast.mock.calls[0][0]
    expect(payload.type).toBe('warning')
    expect(payload.duration).toBe(0)
    expect(payload.actionText).toBe('刷新页面')
    expect(typeof payload.onAction).toBe('function')
  })
})
