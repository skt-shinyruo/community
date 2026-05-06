# Community Frontend Product Redesign Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the product redesign foundation: workspace navigation, product shell, mobile navigation fix, core UI state components, restrained design tokens, and frontend handbook updates.

**Architecture:** Keep the existing Vue 3/Vite/Pinia stack and evolve the current shell instead of introducing a new UI framework. `router/navigation.js` remains the navigation SSOT; `components/layout/*` own shell behavior; `components/ui/*` expose reusable product primitives; `styles/*` provide tokens and layout rules; later workspace plans migrate views onto these primitives.

**Tech Stack:** Vue 3, Vue Router 4, Pinia, CSS custom properties, Vitest, Vue Test Utils, Vite.

---

## Scope

This is the first execution plan for the accepted full-platform redesign spec:

- Spec: `docs/superpowers/specs/2026-05-06-community-frontend-product-redesign-design.md`
- This plan covers foundation only.
- This plan intentionally does not redesign every page body. Community, Trading, Personal, and Admin page migrations should each get a follow-up plan after this foundation lands.

## Current Files And Responsibilities

Modify:

- `frontend/src/router/navigation.js`: regroup sidebar navigation into Community, Trading, Personal, Admin, and Account/System support entries.
- `frontend/src/router/navigation.test.js`: assert workspace groups, permission filtering, mobile nav behavior, active route families, and route inventory expectations.
- `frontend/src/components/layout/AppShell.vue`: ensure desktop sidebar and mobile bottom nav do not conflict; pass shell mode/workspace context to layout children.
- `frontend/src/components/layout/SidebarNav.vue`: render workspace groups with concise labels and no editorial copy.
- `frontend/src/components/layout/Topbar.vue`: render product-grade title/scope/search/account controls; only render shell search on useful routes.
- `frontend/src/components/layout/MobileNav.vue`: render high-frequency bottom entries and make secondary navigation explicit through the sidebar drawer.
- `frontend/src/components/layout/AuthShell.vue`: align login/register/reset pages with the new product shell family.
- `frontend/src/stores/ui.js`: separate persisted desktop collapsed preference from mobile drawer state.
- `frontend/src/stores/ui.test.js`: cover UI store behavior that prevents mobile sidebar conflicts.
- `frontend/src/components/ui/UiPageHeader.vue`: expand page header to support breadcrumbs, actions, and compact product copy.
- `frontend/src/components/ui/UiEmpty.vue`: normalize empty/error/unavailable/forbidden/pending/development-only presentation.
- `frontend/src/components/ui/UiBadge.vue`: normalize semantic status variants.
- `frontend/src/components/ui/UiCard.vue`: keep object-card semantics and make generic wrapper use less attractive.
- `frontend/src/styles/variables.css`: replace editorial token defaults with restrained product tokens.
- `frontend/src/styles/layout.css`: replace editorial shell rules with product workspace shell rules; fix mobile sidebar/bottom-nav conflict.
- `frontend/src/styles/components.css`: normalize card, control, badge, page header, toolbar, state, drawer/menu, and focus styles.
- `frontend/src/styles/pages.css`: remove or neutralize global market/editorial hero defaults that fight the new foundation.
- `frontend/src/styles/productTokens.test.js`: guard product token and routine component styling decisions.
- `docs/handbook/frontend.md`: update implemented navigation/shell semantics after code changes.

Create:

- `frontend/src/components/ui/UiState.vue`: shared state block for empty/loading/error/forbidden/unavailable/pending/development-only.
- `frontend/src/components/ui/UiState.test.js`: component tests for state variants, trace id, action slot, and development-only marking.
- `frontend/src/components/ui/UiToolbar.vue`: shared toolbar layout for filters/search/sort/actions.
- `frontend/src/components/ui/UiToolbar.test.js`: slot and layout-class tests.

Do not modify:

- Backend code.
- API services for behavior changes.
- Business state helpers except tests that prove unchanged route/navigation behavior.
- Existing uncommitted profile edits unless the user explicitly asks to include or resolve them.

## Task 1: Navigation Workspace Model

**Files:**

- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/navigation.test.js`

- [ ] **Step 1: Write failing tests for workspace navigation**

In `frontend/src/router/navigation.test.js`, add these tests to the existing `describe('router/navigation', ...)` block. Keep the existing normalization tests intact.

```js
it('getSidebarNavigation should group routes by product workspaces', () => {
  const anon = getSidebarNavigation({ authed: false })
  expect(anon.map((g) => g.key)).toEqual(['community', 'trading', 'account'])
  expect(anon.find((g) => g.key === 'community')?.items.map((it) => it.key)).toEqual(['posts', 'search'])
  expect(anon.find((g) => g.key === 'trading')?.items.map((it) => it.key)).toEqual(['market'])
  expect(anon.find((g) => g.key === 'account')?.items.map((it) => it.key)).toEqual(['login'])

  const authed = getSidebarNavigation({ authed: true, userId: '8', roles: ['ROLE_USER'] })
  expect(authed.map((g) => g.key)).toEqual(['community', 'trading', 'personal'])
  expect(authed.find((g) => g.key === 'community')?.items.map((it) => it.key)).toEqual([
    'posts',
    'search',
    'bookmarks',
    'profile'
  ])
  expect(authed.find((g) => g.key === 'trading')?.items.map((it) => it.key)).toEqual([
    'market',
    'marketPublish',
    'marketMyListings',
    'marketBuying',
    'marketSelling',
    'marketAddresses'
  ])
  expect(authed.find((g) => g.key === 'personal')?.items.map((it) => it.key)).toEqual([
    'wallet',
    'notices',
    'messages',
    'settings'
  ])
})

it('getSidebarNavigation should expose admin workspace by role', () => {
  const moderator = getSidebarNavigation({ authed: true, userId: '8', roles: ['ROLE_MODERATOR'] })
  expect(moderator.find((g) => g.key === 'admin')?.items.map((it) => it.key)).toEqual([
    'moderation',
    'analytics'
  ])

  const admin = getSidebarNavigation({ authed: true, userId: '8', roles: ['ROLE_ADMIN'] })
  expect(admin.find((g) => g.key === 'admin')?.items.map((it) => it.key)).toEqual([
    'moderation',
    'analytics',
    'userManagement',
    'walletAdmin',
    'adminMarketDisputes',
    'opsConsole'
  ])
})

it('getMobileNavigation should keep only high-frequency entries', () => {
  const anon = getMobileNavigation({ authed: false })
  expect(anon.map((it) => it.key)).toEqual(['posts', 'search', 'market', 'me'])
  expect(anon.find((it) => it.key === 'me')?.to).toEqual({ name: 'login' })

  const authed = getMobileNavigation({ authed: true, userId: '8', roles: ['ROLE_USER'] })
  expect(authed.map((it) => it.key)).toEqual(['posts', 'search', 'market', 'me'])
  expect(authed.find((it) => it.key === 'me')?.to).toEqual({ name: 'userProfile', params: { userId: '8' } })
})

it('isNavItemActive should keep workspace entries active across nested route families', () => {
  const nav = getSidebarNavigation({ authed: true, userId: '8', roles: ['ROLE_ADMIN'] })
  const allItems = nav.flatMap((g) => g.items)

  expect(isNavItemActive({ name: 'postDetail' }, allItems.find((it) => it.key === 'posts'))).toBe(true)
  expect(isNavItemActive({ name: 'followees' }, allItems.find((it) => it.key === 'profile'))).toBe(true)
  expect(isNavItemActive({ name: 'followers' }, allItems.find((it) => it.key === 'profile'))).toBe(true)
  expect(isNavItemActive({ name: 'marketDetail' }, allItems.find((it) => it.key === 'market'))).toBe(true)
  expect(isNavItemActive({ name: 'marketInventory' }, allItems.find((it) => it.key === 'marketMyListings'))).toBe(true)
  expect(isNavItemActive({ name: 'marketOrderDetail' }, allItems.find((it) => it.key === 'marketBuying'))).toBe(true)
  expect(isNavItemActive({ name: 'messageDetail' }, allItems.find((it) => it.key === 'messages'))).toBe(true)
  expect(isNavItemActive({ name: 'noticeDetail' }, allItems.find((it) => it.key === 'notices'))).toBe(true)
})
```

- [ ] **Step 2: Run the navigation tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js
```

Expected: FAIL because current groups are `explore`, `me`, `admin`, and `auth`, and new keys like `marketPublish`, `userManagement`, and `opsConsole` are not all present in navigation definitions.

- [ ] **Step 3: Implement workspace navigation definitions**

In `frontend/src/router/navigation.js`, replace the `NAV_DEFS` group definitions with workspace groups. Keep `POSTS_ORDER`, `POSTS_FILTER`, normalization helpers, `canAccessNavItem`, `getSidebarNavigation`, `getMobileNavigation`, `isNavItemActive`, and breadcrumb helpers as exported APIs.

Use this group shape:

```js
const NAV_DEFS = Object.freeze([
  {
    key: 'community',
    title: '社区',
    items: [
      { key: 'posts', label: '讨论', icon: 'posts', to: () => ({ name: 'posts' }), activeNames: ['posts', 'postDetail'] },
      { key: 'search', label: '搜索', icon: 'search', to: () => ({ name: 'search' }), activeNames: ['search'] },
      { key: 'bookmarks', label: '收藏', icon: 'bookmark', requiresAuth: true, to: () => ({ name: 'bookmarks' }), activeNames: ['bookmarks'] },
      {
        key: 'profile',
        label: '成员主页',
        icon: 'user',
        requiresAuth: true,
        requiresUserId: true,
        to: (ctx) => ({ name: 'userProfile', params: { userId: String(ctx?.userId || '') } }),
        activeNames: ['userProfile', 'followees', 'followers']
      }
    ]
  },
  {
    key: 'trading',
    title: '交易',
    items: [
      { key: 'market', label: '市场', icon: 'sparkle', to: () => ({ name: 'market' }), activeNames: ['market', 'marketDetail'] },
      { key: 'marketPublish', label: '发布商品', icon: 'posts', requiresAuth: true, to: () => ({ name: 'marketPublish' }), activeNames: ['marketPublish'] },
      {
        key: 'marketMyListings',
        label: '我的出售',
        icon: 'analytics',
        requiresAuth: true,
        to: () => ({ name: 'marketMyListings' }),
        activeNames: ['marketMyListings', 'marketInventory']
      },
      {
        key: 'marketBuying',
        label: '我的购买',
        icon: 'bookmark',
        requiresAuth: true,
        to: () => ({ name: 'marketBuyingOrders' }),
        activeNames: ['marketBuyingOrders', 'marketOrderDetail']
      },
      {
        key: 'marketSelling',
        label: '出售订单',
        icon: 'analytics',
        requiresAuth: true,
        to: () => ({ name: 'marketSellingOrders' }),
        activeNames: ['marketSellingOrders', 'marketOrderDetail']
      },
      { key: 'marketAddresses', label: '收货地址', icon: 'bookmark', requiresAuth: true, to: () => ({ name: 'marketAddresses' }), activeNames: ['marketAddresses'] }
    ]
  },
  {
    key: 'personal',
    title: '个人',
    items: [
      { key: 'wallet', label: '钱包', icon: 'sparkle', requiresAuth: true, to: () => ({ name: 'wallet' }), activeNames: ['wallet'] },
      { key: 'notices', label: '通知', icon: 'bell', requiresAuth: true, to: () => ({ name: 'notices' }), activeNames: ['notices', 'noticeDetail'] },
      { key: 'messages', label: '私信', icon: 'messages', requiresAuth: true, to: () => ({ name: 'messages' }), activeNames: ['messages', 'messageDetail'] },
      { key: 'settings', label: '设置', icon: 'settings', requiresAuth: true, to: () => ({ name: 'settings' }), activeNames: ['settings'] }
    ]
  },
  {
    key: 'admin',
    title: '管理',
    items: [
      { key: 'moderation', label: '治理', icon: 'shield', requiresAuth: true, roles: ['ROLE_ADMIN', 'ROLE_MODERATOR'], to: () => ({ name: 'moderation' }), activeNames: ['moderation'] },
      { key: 'analytics', label: '统计', icon: 'analytics', requiresAuth: true, roles: ['ROLE_ADMIN', 'ROLE_MODERATOR'], to: () => ({ name: 'analytics' }), activeNames: ['analytics'] },
      { key: 'userManagement', label: '用户', icon: 'user', requiresAuth: true, roles: ['ROLE_ADMIN'], to: () => ({ name: 'userManagement' }), activeNames: ['userManagement'] },
      { key: 'walletAdmin', label: '钱包后台', icon: 'analytics', requiresAuth: true, roles: ['ROLE_ADMIN'], to: () => ({ name: 'walletAdmin' }), activeNames: ['walletAdmin'] },
      { key: 'adminMarketDisputes', label: '争议', icon: 'shield', requiresAuth: true, roles: ['ROLE_ADMIN'], to: () => ({ name: 'adminMarketDisputes' }), activeNames: ['adminMarketDisputes'] },
      { key: 'opsConsole', label: '运维', icon: 'settings', requiresAuth: true, roles: ['ROLE_ADMIN'], to: () => ({ name: 'opsConsole' }), activeNames: ['opsConsole'] }
    ]
  },
  {
    key: 'account',
    title: '账户',
    items: [
      { key: 'login', label: '登录', icon: 'login', hideWhenAuthed: true, to: () => ({ name: 'login' }), activeNames: ['login', 'register', 'passwordReset'] }
    ]
  }
])
```

Update `getMobileNavigation(ctx)` so it returns `[posts, search, market, me]`. `me` should route to profile when authenticated with user id, to the first available personal item when authenticated without user id, and to login when anonymous. `me.activeNames` must include account, profile, personal, and admin-adjacent user routes so the bottom item stays active for personal pages.

- [ ] **Step 4: Run the navigation tests and verify they pass**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit navigation workspace model**

Run:

```bash
git add frontend/src/router/navigation.js frontend/src/router/navigation.test.js
git commit -m "feat(frontend): group navigation by product workspace"
```

Expected: commit includes only the two navigation files.

## Task 2: UI Store Mobile Drawer State

**Files:**

- Modify: `frontend/src/stores/ui.js`
- Modify: `frontend/src/stores/ui.test.js`

- [ ] **Step 1: Write failing UI store tests**

Replace `frontend/src/stores/ui.test.js` with:

```js
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

  it('should remove the legacy right-panel state contract', () => {
    installWindow()
    const store = useUiStore()

    expect('rightPanelOpen' in store.$state).toBe(false)
    expect('toggleRightPanel' in store).toBe(false)
  })
})
```

- [ ] **Step 2: Run UI store tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/stores/ui.test.js
```

Expected: FAIL because `mobileSidebarOpen`, `openMobileSidebar`, and `closeMobileSidebar` do not exist.

- [ ] **Step 3: Implement separate mobile drawer state**

In `frontend/src/stores/ui.js`, update state and actions:

```js
state: () => ({
  theme: 'light',
  density: 'compact',
  sidebarCollapsed: false,
  mobileSidebarOpen: false
}),
```

Ensure `init()` no longer sets `sidebarCollapsed` based on `window.innerWidth < 980`. It should read persisted `sidebarCollapsed` or default to `false`, then force `mobileSidebarOpen = false`.

Add actions:

```js
openMobileSidebar() {
  this.mobileSidebarOpen = true
},

closeMobileSidebar() {
  this.mobileSidebarOpen = false
},

toggleMobileSidebar() {
  this.mobileSidebarOpen = !this.mobileSidebarOpen
}
```

Keep `persist()` writing only:

```js
{
  theme: this.theme,
  density: this.density,
  sidebarCollapsed: this.sidebarCollapsed
}
```

- [ ] **Step 4: Run UI store tests and verify they pass**

Run:

```bash
cd frontend
npm test -- src/stores/ui.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit UI store mobile drawer state**

Run:

```bash
git add frontend/src/stores/ui.js frontend/src/stores/ui.test.js
git commit -m "feat(frontend): separate mobile sidebar drawer state"
```

Expected: commit includes only UI store files.

## Task 3: Product Shell And Mobile Navigation Fix

**Files:**

- Modify: `frontend/src/components/layout/AppShell.vue`
- Modify: `frontend/src/components/layout/SidebarNav.vue`
- Modify: `frontend/src/components/layout/Topbar.vue`
- Modify: `frontend/src/components/layout/MobileNav.vue`
- Modify: `frontend/src/components/layout/AuthShell.vue`
- Modify: `frontend/src/styles/layout.css`

- [ ] **Step 1: Add shell behavior tests to existing navigation/UI tests**

Add this test to `frontend/src/router/navigation.test.js` to lock search behavior decisions in data rather than component conditionals:

```js
it('navigation metadata should identify routes that support shell search', () => {
  const searchable = getShellSearchRouteNames()
  expect(searchable).toEqual(['posts', 'search', 'market'])
})
```

Update imports in the same file:

```js
  getShellSearchRouteNames,
```

- [ ] **Step 2: Run the test and verify it fails**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js
```

Expected: FAIL because `getShellSearchRouteNames` does not exist.

- [ ] **Step 3: Add shell search metadata**

In `frontend/src/router/navigation.js`, add:

```js
const SHELL_SEARCH_ROUTE_NAMES = Object.freeze(['posts', 'search', 'market'])

export function getShellSearchRouteNames() {
  return [...SHELL_SEARCH_ROUTE_NAMES]
}

export function routeSupportsShellSearch(routeName) {
  return SHELL_SEARCH_ROUTE_NAMES.includes(String(routeName || ''))
}
```

Update `Topbar.vue` to import `routeSupportsShellSearch` and compute:

```js
const showShellSearch = computed(() =>
  props.mode !== 'admin' &&
  desktopSearchVisible.value &&
  routeSupportsShellSearch(route.name)
)
```

- [ ] **Step 4: Update shell components to use desktop collapsed and mobile drawer separately**

In `AppShell.vue`, change the aside class binding:

```vue
<aside
  class="app-sidebar"
  :class="{
    'is-collapsed': ui.sidebarCollapsed,
    'is-mobile-open': ui.mobileSidebarOpen
  }"
>
```

Keep `MobileNav` rendered for non-admin workspace routes, but ensure CSS hides it on desktop.

In `Topbar.vue`, update menu button behavior:

```js
function onMenuClick() {
  if (typeof window !== 'undefined' && window.matchMedia?.('(max-width: 768px)')?.matches) {
    ui.toggleMobileSidebar()
    return
  }
  ui.toggleSidebar()
}
```

Then change the menu button click from `@click="ui.toggleSidebar"` to `@click="onMenuClick"`.

In `SidebarNav.vue`, change mobile close behavior to call `ui.closeMobileSidebar()` instead of `ui.setSidebarCollapsed(true)`. The overlay class should use `ui.mobileSidebarOpen`.

- [ ] **Step 5: Replace mobile shell CSS rules**

In `frontend/src/styles/layout.css`, update mobile rules so:

```css
@media (max-width: 768px) {
  .app-shell,
  .app-shell.sidebar-collapsed {
    display: block;
  }

  .app-sidebar {
    position: fixed;
    inset: 0 auto 0 0;
    z-index: 120;
    width: min(82vw, var(--sidebar-width));
    max-width: 320px;
    transform: translateX(-100%);
    transition: transform 0.2s ease;
  }

  .app-sidebar.is-mobile-open {
    transform: translateX(0);
  }

  .app-sidebar.is-collapsed {
    width: min(82vw, var(--sidebar-width));
  }

  .sidebar-overlay.open {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 110;
    background: rgba(15, 23, 42, 0.36);
  }

  .app-content {
    padding-bottom: 88px;
  }
}
```

Remove or override any rule that makes `.app-sidebar.mobile-open` visible by default on mobile.

- [ ] **Step 6: Align AuthShell copy and density controls**

In `AuthShell.vue`, keep the brand link but change the subtitle to product copy:

```vue
<span class="auth-brand-subtitle">社区、交易与治理工作台</span>
```

Keep theme/density controls but avoid long button text. Use:

```vue
{{ ui.density === 'compact' ? '舒适' : '紧凑' }}
{{ ui.theme === 'dark' ? '浅色' : '深色' }}
```

- [ ] **Step 7: Run focused tests**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js src/stores/ui.test.js
```

Expected: PASS.

- [ ] **Step 8: Manual browser check for mobile shell**

Start or reuse the Vite dev server:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Open `http://127.0.0.1:5173/#/posts` or the actual Vite port shown in output. At 390px width, verify:

- Sidebar is hidden initially.
- Bottom nav is visible.
- Tapping the topbar menu opens the sidebar drawer.
- Drawer overlay closes the sidebar.
- Sidebar and bottom nav are not both acting as persistent navigation.

- [ ] **Step 9: Commit shell foundation**

Run:

```bash
git add frontend/src/router/navigation.js frontend/src/router/navigation.test.js frontend/src/components/layout/AppShell.vue frontend/src/components/layout/SidebarNav.vue frontend/src/components/layout/Topbar.vue frontend/src/components/layout/MobileNav.vue frontend/src/components/layout/AuthShell.vue frontend/src/styles/layout.css
git commit -m "feat(frontend): add product shell navigation behavior"
```

Expected: commit includes shell, navigation metadata, and layout CSS.

## Task 4: Product Tokens And Core Component Styling

**Files:**

- Modify: `frontend/src/styles/variables.css`
- Modify: `frontend/src/styles/components.css`
- Modify: `frontend/src/styles/layout.css`
- Modify: `frontend/src/components/ui/UiCard.vue`
- Modify: `frontend/src/components/ui/UiBadge.vue`
- Modify: `frontend/src/components/ui/UiButton.vue`
- Create: `frontend/src/styles/productTokens.test.js`

- [ ] **Step 1: Add product token guardrail tests**

Create `frontend/src/styles/productTokens.test.js`:

```js
import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

function read(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

function cssBlock(css, selector) {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = css.match(new RegExp(`${escaped}\\s*\\{([^}]*)\\}`, 's'))
  return match?.[1] || ''
}

describe('product design token guardrails', () => {
  it('uses restrained radius and shadow tokens', () => {
    const variables = read('src/styles/variables.css')

    expect(variables).toContain('--radius-lg: 12px;')
    expect(variables).toContain('--radius-xl: 16px;')
    expect(variables).toContain('--pending:')
    expect(variables).toContain('--unread:')
    expect(variables).not.toContain('warm editorial by default')
    expect(variables).not.toContain('public editorial shell')
  })

  it('keeps routine cards and buttons out of editorial styling', () => {
    const components = read('src/styles/components.css')
    const card = cssBlock(components, '.card')
    const button = cssBlock(components, '.btn')

    expect(card).toContain('border-radius: var(--radius-md)')
    expect(card).toContain('box-shadow: none')
    expect(card).not.toContain('transform')
    expect(button).toContain('border-radius: var(--radius-md)')
  })

  it('defines semantic pending and unread badge styling', () => {
    const components = read('src/styles/components.css')

    expect(components).toContain('.badge-pending')
    expect(components).toContain('.badge-unread')
  })
})
```

- [ ] **Step 2: Run product token guardrail tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/styles/productTokens.test.js
```

Expected: FAIL because current tokens still use editorial defaults, current cards use `var(--radius-lg)` and shadows, and pending/unread badge styles are absent.

- [ ] **Step 3: Normalize tokens**

In `frontend/src/styles/variables.css`, update comments and values toward product defaults:

- Keep `--font-sans`.
- Keep `--font-display`, but comment that it is reserved for long-form content.
- Set `--radius-lg: 12px`, `--radius-xl: 16px`.
- Reduce shadows so `--shadow-sm` is near `0 1px 2px rgba(15, 23, 42, 0.06)`.
- Replace the `/* public editorial shell */` section with `/* product workspace shell */` aliases or remove public-only editorial tokens after layout CSS no longer needs them.
- Keep semantic color variables and add:

```css
--pending: #6366f1;
--pending-weak: color-mix(in srgb, var(--pending) 12%, transparent 88%);
--unread: #2563eb;
--unread-weak: color-mix(in srgb, var(--unread) 12%, transparent 88%);
```

Mirror dark theme values under `html[data-theme='dark']`.

- [ ] **Step 4: Normalize components CSS**

In `frontend/src/styles/components.css`:

- Make `.card` use `border-radius: var(--radius-md)`, `box-shadow: none`, and no hover lift by default.
- Add `.object-card` for object cards that need hover emphasis.
- Make `.btn` radius `var(--radius-md)` instead of pill by default.
- Preserve `.btn-icon` as round icon button.
- Add badge variants:

```css
.badge-pending {
  color: var(--pending);
  background: var(--pending-weak);
  border-color: color-mix(in srgb, var(--pending) 30%, var(--border) 70%);
}

.badge-unread {
  color: var(--unread);
  background: var(--unread-weak);
  border-color: color-mix(in srgb, var(--unread) 30%, var(--border) 70%);
}
```

- Ensure `:focus-visible` remains visible for buttons, links, inputs, select triggers, and icon buttons.

- [ ] **Step 5: Update UiBadge variant comment and class mapping**

In `UiBadge.vue`, update the prop comment to:

```js
variant: { type: String, default: 'default' } // default | accent | danger | success | warning | pending | unread
```

The current computed class pattern can remain because it already maps any non-default variant to `badge-${v}`.

- [ ] **Step 6: Run product token guardrail tests**

Run:

```bash
cd frontend
npm test -- src/styles/productTokens.test.js
```

Expected: PASS.

- [ ] **Step 7: Commit tokens and component styling**

Run:

```bash
git add frontend/src/styles/variables.css frontend/src/styles/components.css frontend/src/styles/layout.css frontend/src/styles/productTokens.test.js frontend/src/components/ui/UiCard.vue frontend/src/components/ui/UiBadge.vue frontend/src/components/ui/UiButton.vue
git commit -m "feat(frontend): normalize product design tokens"
```

Expected: commit includes product token and core component style changes.

## Task 5: Shared State And Toolbar Components

**Files:**

- Create: `frontend/src/components/ui/UiState.vue`
- Create: `frontend/src/components/ui/UiState.test.js`
- Create: `frontend/src/components/ui/UiToolbar.vue`
- Create: `frontend/src/components/ui/UiToolbar.test.js`
- Modify: `frontend/src/components/ui/UiEmpty.vue`
- Modify: `frontend/src/styles/components.css`

- [ ] **Step 1: Create failing tests for UiState**

Create `frontend/src/components/ui/UiState.test.js`:

```js
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiState from './UiState.vue'

describe('UiState', () => {
  it('renders a compact empty state with title and description', () => {
    const wrapper = mount(UiState, {
      props: {
        variant: 'empty',
        title: '暂无订单',
        description: '当前筛选条件下没有订单。'
      }
    })

    expect(wrapper.classes()).toContain('ui-state')
    expect(wrapper.classes()).toContain('ui-state--empty')
    expect(wrapper.text()).toContain('暂无订单')
    expect(wrapper.text()).toContain('当前筛选条件下没有订单。')
  })

  it('renders error trace id and action slot', () => {
    const wrapper = mount(UiState, {
      props: {
        variant: 'error',
        title: '加载失败',
        traceId: 'trace-123'
      },
      slots: {
        actions: '<button>重试</button>'
      }
    })

    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).toContain('trace-123')
    expect(wrapper.find('button').text()).toBe('重试')
  })

  it('marks development-only state explicitly', () => {
    const wrapper = mount(UiState, {
      props: {
        variant: 'development',
        title: '开发辅助信息'
      }
    })

    expect(wrapper.attributes('data-development-only')).toBe('true')
    expect(wrapper.text()).toContain('Development only')
  })
})
```

- [ ] **Step 2: Create failing tests for UiToolbar and UiEmpty compatibility**

Create `frontend/src/components/ui/UiToolbar.test.js`:

```js
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiToolbar from './UiToolbar.vue'

describe('UiToolbar', () => {
  it('renders leading, filters, and actions slots', () => {
    const wrapper = mount(UiToolbar, {
      slots: {
        leading: '<span data-test=\"leading\">订单</span>',
        filters: '<input data-test=\"filter\" />',
        actions: '<button data-test=\"action\">导出</button>'
      }
    })

    expect(wrapper.classes()).toContain('ui-toolbar')
    expect(wrapper.find('[data-test=\"leading\"]').exists()).toBe(true)
    expect(wrapper.find('[data-test=\"filter\"]').exists()).toBe(true)
    expect(wrapper.find('[data-test=\"action\"]').exists()).toBe(true)
  })
})
```

Create `frontend/src/components/ui/UiEmpty.test.js`:

```js
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UiEmpty from './UiEmpty.vue'

describe('UiEmpty', () => {
  it('keeps existing title description and action slots visible through UiState', () => {
    const wrapper = mount(UiEmpty, {
      slots: {
        default: '暂无通知',
        description: '当前没有新的通知。',
        actions: '<button>刷新</button>'
      }
    })

    expect(wrapper.findComponent({ name: 'UiState' }).exists()).toBe(true)
    expect(wrapper.text()).toContain('暂无通知')
    expect(wrapper.text()).toContain('当前没有新的通知。')
    expect(wrapper.find('button').text()).toBe('刷新')
  })

  it('maps legacy error type to error state variant', () => {
    const wrapper = mount(UiEmpty, {
      props: { type: 'error' },
      slots: { default: '加载失败' }
    })

    expect(wrapper.find('.ui-state--error').exists()).toBe(true)
  })
})
```

- [ ] **Step 3: Run tests and verify they fail**

Run:

```bash
cd frontend
npm test -- src/components/ui/UiState.test.js src/components/ui/UiToolbar.test.js src/components/ui/UiEmpty.test.js
```

Expected: FAIL because `UiState.vue`, `UiToolbar.vue`, and `UiEmpty.test.js` do not exist yet, and the current `UiEmpty.vue` does not delegate to `UiState`.

- [ ] **Step 4: Implement UiState**

Create `frontend/src/components/ui/UiState.vue`:

```vue
<template>
  <section
    class="ui-state"
    :class="`ui-state--${safeVariant}`"
    :data-development-only="safeVariant === 'development' ? 'true' : undefined"
    role="status"
  >
    <div class="ui-state-icon" aria-hidden="true">{{ iconText }}</div>
    <div class="ui-state-body">
      <div v-if="safeVariant === 'development'" class="ui-state-kicker">Development only</div>
      <h2 class="ui-state-title">{{ title }}</h2>
      <p v-if="description" class="ui-state-description">{{ description }}</p>
      <p v-if="traceId" class="ui-state-trace">Trace ID: {{ traceId }}</p>
      <div v-if="$slots.actions" class="ui-state-actions">
        <slot name="actions" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'empty' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  traceId: { type: String, default: '' }
})

const variants = ['empty', 'loading', 'error', 'forbidden', 'unavailable', 'pending', 'development']

const safeVariant = computed(() => {
  const v = String(props.variant || '').trim()
  return variants.includes(v) ? v : 'empty'
})

const iconText = computed(() => {
  if (safeVariant.value === 'loading') return '...'
  if (safeVariant.value === 'error') return '!'
  if (safeVariant.value === 'forbidden') return '403'
  if (safeVariant.value === 'pending') return '~'
  if (safeVariant.value === 'development') return 'DEV'
  return '-'
})
</script>
```

- [ ] **Step 5: Implement UiToolbar**

Create `frontend/src/components/ui/UiToolbar.vue`:

```vue
<template>
  <section class="ui-toolbar" aria-label="页面工具栏">
    <div v-if="$slots.leading" class="ui-toolbar-leading">
      <slot name="leading" />
    </div>
    <div v-if="$slots.filters" class="ui-toolbar-filters">
      <slot name="filters" />
    </div>
    <div v-if="$slots.actions" class="ui-toolbar-actions">
      <slot name="actions" />
    </div>
  </section>
</template>
```

- [ ] **Step 6: Add CSS for state and toolbar components**

In `frontend/src/styles/components.css`, add:

```css
.ui-state {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
  padding: 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.ui-state-icon {
  flex: none;
  min-width: 36px;
  height: 36px;
  padding: 0 8px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-md);
  background: var(--surface-2);
  color: var(--text-2);
  font-size: 12px;
  font-weight: 800;
}

.ui-state-body {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.ui-state-kicker,
.ui-state-trace {
  color: var(--text-3);
  font-size: var(--text-xs);
  font-family: var(--font-mono);
}

.ui-state-title {
  margin: 0;
  color: var(--text-1);
  font-size: var(--text-md);
  line-height: var(--line-tight);
}

.ui-state-description {
  margin: 0;
  color: var(--text-2);
  font-size: var(--text-sm);
  line-height: var(--line-normal);
}

.ui-state-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.ui-state--error {
  border-color: color-mix(in srgb, var(--danger) 28%, var(--border) 72%);
}

.ui-state--pending {
  border-color: color-mix(in srgb, var(--pending) 28%, var(--border) 72%);
}

.ui-state--development {
  border-style: dashed;
}

.ui-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
}

.ui-toolbar-leading,
.ui-toolbar-filters,
.ui-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ui-toolbar-filters {
  flex: 1;
  flex-wrap: wrap;
}

@media (max-width: 768px) {
  .ui-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .ui-toolbar-actions {
    justify-content: flex-start;
  }
}
```

- [ ] **Step 7: Make UiEmpty delegate visually to the new state style**

Replace `UiEmpty.vue` with a compatibility wrapper around `UiState`:

```vue
<template>
  <div class="empty-state empty-state--compat">
    <UiState :variant="stateVariant" :title="title">
      <template v-if="$slots.description" #description>
        <slot name="description" />
      </template>
      <template v-if="$slots.actions" #actions>
        <slot name="actions" />
      </template>
    </UiState>
  </div>
</template>

<script setup>
import { computed, useSlots } from 'vue'
import UiState from './UiState.vue'

defineOptions({ name: 'UiEmpty' })

const props = defineProps({
  type: { type: String, default: 'data' }
})

const slots = useSlots()

const stateVariant = computed(() => {
  if (props.type === 'error') return 'error'
  if (props.type === 'search') return 'empty'
  return 'empty'
})

const title = computed(() => {
  const nodes = slots.default?.() || []
  const text = nodes.map((node) => String(node.children || '')).join('').trim()
  return text || '暂无数据'
})
</script>
```

Then update `UiState.vue` so its description supports either prop text or a description slot:

```vue
<p v-if="description || $slots.description" class="ui-state-description">
  <slot name="description">{{ description }}</slot>
</p>
```

- [ ] **Step 8: Run focused UI component tests**

Run:

```bash
cd frontend
npm test -- src/components/ui/UiState.test.js src/components/ui/UiToolbar.test.js src/components/ui/UiEmpty.test.js src/components/ui/UiIconButton.test.js
```

Expected: PASS.

- [ ] **Step 9: Commit shared state and toolbar components**

Run:

```bash
git add frontend/src/components/ui/UiState.vue frontend/src/components/ui/UiState.test.js frontend/src/components/ui/UiToolbar.vue frontend/src/components/ui/UiToolbar.test.js frontend/src/components/ui/UiEmpty.vue frontend/src/components/ui/UiEmpty.test.js frontend/src/styles/components.css
git commit -m "feat(frontend): add shared product state primitives"
```

Expected: commit includes only shared UI primitive files and component CSS.

## Task 6: Neutralize Editorial Global Page Defaults

**Files:**

- Modify: `frontend/src/styles/pages.css`
- Modify: `frontend/src/styles/layout.css`
- Modify: `frontend/src/styles/components.css`
- Modify: `frontend/src/views/viewComplexity.test.js`

- [ ] **Step 1: Add CSS guardrail tests**

Extend `frontend/src/views/viewComplexity.test.js`:

```js
function read(path) {
  return readFileSync(resolve(process.cwd(), path), 'utf8')
}

describe('product redesign CSS guardrails', () => {
  it('does not keep public card styling as the global product default', () => {
    const layout = read('src/styles/layout.css')
    expect(layout).not.toContain('.app-shell--public .card')
    expect(layout).not.toContain('--editorial-shadow')
  })

  it('keeps routine card radii below large editorial treatment', () => {
    const components = read('src/styles/components.css')
    expect(components).not.toMatch(/\\.card\\s*\\{[^}]*border-radius:\\s*30px/s)
    expect(components).not.toMatch(/\\.card\\s*\\{[^}]*border-radius:\\s*var\\(--radius-xl\\)/s)
  })
})
```

- [ ] **Step 2: Run guardrail test and verify it fails**

Run:

```bash
cd frontend
npm test -- src/views/viewComplexity.test.js
```

Expected: FAIL while editorial global card or editorial shadow tokens remain in active CSS.

- [ ] **Step 3: Remove public editorial global overrides**

In `frontend/src/styles/layout.css`:

- Remove `.app-shell--public .card` global overrides.
- Remove dark-sidebar public background rules that create editorial split backgrounds.
- Keep admin-specific safety only if required.
- Keep `.app-shell--public` as a compatibility class, but make it visually equivalent to the product workspace shell.

In `frontend/src/styles/pages.css`:

- Leave page-specific classes in place for later workspace migrations.
- Remove global hero/card rules that apply broad decorative gradients to routine market/search/workspace surfaces.
- If removal risks large visual breakage before page migrations, scope old rules behind explicit page classes such as `.legacy-editorial-preview`.

- [ ] **Step 4: Run guardrail test and focused UI tests**

Run:

```bash
cd frontend
npm test -- src/views/viewComplexity.test.js src/router/navigation.test.js src/stores/ui.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit global CSS neutralization**

Run:

```bash
git add frontend/src/styles/pages.css frontend/src/styles/layout.css frontend/src/styles/components.css frontend/src/views/viewComplexity.test.js
git commit -m "style(frontend): remove editorial global page defaults"
```

Expected: commit includes CSS guardrails and CSS cleanup.

## Task 7: Frontend Handbook Update

**Files:**

- Modify: `docs/handbook/frontend.md`

- [ ] **Step 1: Update navigation and shell documentation**

In `docs/handbook/frontend.md`, update the `router/navigation.js` section to document:

```markdown
`frontend/src/router/navigation.js` 是导航 SSOT，包含：

- Community / Trading / Personal / Admin / Account 工作区导航分组。
- 侧边栏、移动端底栏和 shell search 的 route 级可见性。
- 角色、登录态、用户 id 的前端可见性判断。
- posts 列表的 `order`、`type`、`categoryId`、`tag`、`subscribed` query 规范化和构造。
```

Add a short shell paragraph after the route/nav section:

```markdown
## 产品壳层和移动导航

`frontend/src/components/layout/AppShell.vue` 负责桌面 workspace shell，`SidebarNav.vue` 渲染工作区分组，`Topbar.vue` 渲染页面标题、账户控制和 route-aware shell search，`MobileNav.vue` 只承载高频移动入口。移动端 sidebar drawer 状态与桌面 collapsed 偏好分离，避免 sidebar 和 bottom nav 同时作为持久导航出现。
```

- [ ] **Step 2: Run docs diff check**

Run:

```bash
git diff --check -- docs/handbook/frontend.md docs/superpowers/plans/2026-05-06-community-frontend-product-redesign-foundation.md
```

Expected: PASS with no whitespace errors.

- [ ] **Step 3: Commit handbook update**

Run:

```bash
git add docs/handbook/frontend.md
git commit -m "docs: document frontend product shell navigation"
```

Expected: commit includes only handbook frontend documentation.

## Task 8: Foundation Verification

**Files:**

- No code changes expected.

- [ ] **Step 1: Run focused frontend tests**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js src/stores/ui.test.js src/components/ui/UiState.test.js src/components/ui/UiToolbar.test.js src/components/ui/UiEmpty.test.js src/components/ui/UiIconButton.test.js src/styles/productTokens.test.js src/views/viewComplexity.test.js
```

Expected: PASS.

- [ ] **Step 2: Run full frontend test suite**

Run:

```bash
cd frontend
npm test
```

Expected: PASS.

- [ ] **Step 3: Run production build**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS and Vite emits a production bundle.

- [ ] **Step 4: Browser smoke test representative routes**

Start Vite:

```bash
cd frontend
npm run dev -- --host 127.0.0.1
```

Check:

- Desktop `/#/posts`: sidebar workspace groups are Community, Trading, Personal if authenticated or Account if anonymous.
- Desktop `/#/search`: shell search is visible and routes search submissions to the search route.
- Desktop `/#/market`: shell search is visible.
- Desktop `/#/auth/login`: auth shell uses product copy and compact controls.
- Mobile 390px `/#/posts`: sidebar hidden initially, bottom nav visible, topbar menu opens drawer, overlay closes drawer.

- [ ] **Step 5: Capture final status**

Run:

```bash
git status --short
```

Expected: no uncommitted changes from this foundation work. Pre-existing unrelated changes may remain; identify them explicitly.

## Follow-Up Plans

After this foundation lands and verifies, write separate implementation plans in this order:

1. `community-frontend-product-redesign-community.md`: posts, post detail, search, profile, social lists, bookmarks.
2. `community-frontend-product-redesign-trading.md`: market list/detail, publish, listings, inventory, buying/selling orders, order detail, addresses.
3. `community-frontend-product-redesign-personal.md`: wallet, conversations, notices, settings.
4. `community-frontend-product-redesign-admin.md`: analytics, moderation, user management, wallet admin, market disputes, ops.
5. `community-frontend-product-redesign-polish.md`: dark theme, responsive audit, copy reduction, accessibility pass, browser screenshot review.

Each follow-up plan must include route-specific view tests or state-helper tests where behavior changes, plus browser checks for desktop and 390px mobile.
