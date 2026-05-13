# Community UI Positioning Redesign Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the current frontend with the May 13 Community positioning spec by fixing navigation priority, shell scope language, engineering copy, and the first-pass product expression of posts, profile, inbox, market, wallet, and drive.

## Execution Status - 2026-05-13

Phase 1 implementation and verification are complete in the shared working tree.

- Tasks 1-6 source state was verified against the plan: navigation priority, route-aware shell scope, posts positioning, profile/inbox copy, market/wallet/drive product labels, product token checks, and copy cleanup are present.
- Task 2 received additional regression assertions in `walletState.test.js`, `marketState.test.js`, `driveState.test.js`, and `userProfileSurface.test.js`.
- Task 7 verification passed for focused Phase 1 tests, full frontend tests, production builds, product-facing copy scan, and representative desktop/mobile browser checks.
- Browser regression evidence is recorded at `target/community-playwright-regression/reports/pw-20260513204836.md`.
- Commit steps in this plan were not executed because the user requested direct modification in the existing workspace and the workspace already contained unrelated dirty changes.
- Fresh Docker redeploy was retried successfully at 2026-05-13T13:23Z; gateway health returned UP and the frontend shell returned 200.

**Architecture:** Reuse the foundation already present in the app: `router/navigation.js` is the navigation SSOT, `components/layout/*` own the shell, `components/ui/*` provide shared primitives, page-level state helpers keep data shaping testable, and `styles/*` own product tokens/layout. This phase is a focused positioning pass, not a full rewrite of every trading/admin workflow.

**Tech Stack:** Vue 3, Vue Router 4, Pinia, CSS custom properties, Vitest, Vue Test Utils, Vite, Chrome DevTools browser checks.

---

## Scope

Spec:

- `docs/superpowers/specs/2026-05-13-community-ui-positioning-redesign-design.md`

This is Phase 1 of that spec. It covers:

1. Navigation and shell positioning.
2. Mobile bottom nav priority.
3. Global engineering-copy cleanup on the reviewed pages.
4. Posts homepage first-viewport positioning.
5. Member profile identity/trust copy.
6. Notices and messages as community inbox surfaces.
7. First-pass market trust expression.
8. First-pass wallet ledger/trust expression.
9. First-pass drive product labeling.
10. Browser verification on the routes reviewed with user `aaa`.

This phase does not fully redesign every order detail, publish workflow, admin console, moderation queue, analytics page, wallet admin page, or dispute adjudication page. Those should be covered by a follow-up Phase 2 plan after this positioning pass lands.

## Existing Foundation To Reuse

The May 6 foundation work is partially present and should not be duplicated:

- `frontend/src/router/navigation.js` already groups desktop nav as `community`, `trading`, `personal`, `admin`, and `account`.
- `frontend/src/components/ui/UiState.vue` exists.
- `frontend/src/components/ui/UiToolbar.vue` exists.
- `frontend/src/components/ui/UiPageHeader.vue`, `UiBadge.vue`, and `UiCard.vue` exist.
- Existing tests already cover core navigation, wallet, market, drive, notices, conversations, and profile contracts.

This plan modifies existing structures instead of creating new foundation components.

## Files And Responsibilities

Modify:

- `frontend/src/router/navigation.js`: move mobile bottom nav priority to discussion/search/notices/messages/me; keep desktop group changes coherent with route families.
- `frontend/src/router/navigation.test.js`: assert mobile nav priority, inbox route active states, and route grouping expectations.
- `frontend/src/components/layout/MobileNav.vue`: support five mobile entries and render bell/message icons consistently.
- `frontend/src/components/layout/Topbar.vue`: replace hardcoded `Discussion Workspace` with route-aware scope labels.
- `frontend/src/components/layout/SidebarNav.vue`: adjust brand/subcopy and labels so the shell reads as a community platform instead of only a discussion workbench.
- `frontend/src/styles/layout.css`: support five-item mobile nav, closed mobile sidebar semantics, and reduced top chrome.
- `frontend/src/styles/pages.css`: add `.market-trust-strip`, `.posts-workspace`, `.posts-main-feed`, `.posts-context-panel`, and any view-specific compact list/detail utilities used by this phase.
- `frontend/src/styles/productTokens.test.js`: guard radius, shadow, and mobile nav token decisions touched by this phase.
- `frontend/src/views/PostsView.vue`: make feed the primary surface; keep composer lightweight; use product copy in empty state.
- `frontend/src/views/posts/PostsView.css`: reduce card feel, make the first viewport list-oriented, and add optional context/empty-state layout.
- `frontend/src/views/PostsView.test.js`: assert positioning copy and structure.
- `frontend/src/views/UserProfileView.vue`: hide raw UUID from primary visual hierarchy and remove wallet caveat language.
- `frontend/src/views/userProfileSurface.js`: replace wallet/public profile caveat copy with private/unavailable state copy.
- `frontend/src/views/userProfileSurface.test.js`: assert new copy and no engineering caveats.
- `frontend/src/views/UserProfileView.test.js`: assert profile no longer contains product-facing wallet caveats.
- `frontend/src/views/NoticesView.vue`: make topic summary rows read as inbox actions with latest context-ready layout.
- `frontend/src/views/NoticesView.test.js`: assert actionable inbox wording and unread summary.
- `frontend/src/views/ConversationsView.vue`: use inbox wording and avoid `成员 #UUID` as primary participant label.
- `frontend/src/views/ConversationsView.test.js`: assert conversation row labels and no primary UUID label.
- `frontend/src/views/MarketListView.vue`: add first-pass trust strip, buyer/seller follow-up actions, and escrow/fulfillment copy.
- `frontend/src/views/marketState.js`: expose trust labels for listing rows without changing API semantics.
- `frontend/src/views/marketState.test.js`: assert listing trust/status labels.
- `frontend/src/views/MarketViews.test.js`: assert market trust expression and empty-state copy.
- `frontend/src/views/WalletView.vue`: replace form-demo language with asset/ledger language.
- `frontend/src/views/walletState.js`: make unknown summary state unavailable rather than future-work copy; expose status labels.
- `frontend/src/views/walletState.test.js`: assert wallet state text.
- `frontend/src/views/WalletView.test.js`: assert no future-work/session-demo copy.
- `frontend/src/views/DriveView.vue`: replace technical labels in the UI and add share/community context copy.
- `frontend/src/views/driveState.js`: expose product status labels for drive entries.
- `frontend/src/views/driveState.test.js`: assert `ACTIVE -> 可用`, `TRASHED -> 回收站`, private/share labels.
- `frontend/src/views/DriveView.test.js`: assert product labels and no raw `ACTIVE`.
- `docs/handbook/frontend.md`: update navigation/shell wording after implementation if route grouping or mobile nav semantics change.

Do not modify:

- Backend code.
- API service contracts.
- DDD architecture docs.
- Deployment files.
- Existing unrelated generated or local files.

---

## Task 1: Navigation And Shell Positioning

**Files:**

- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/navigation.test.js`
- Modify: `frontend/src/components/layout/MobileNav.vue`
- Modify: `frontend/src/components/layout/Topbar.vue`
- Modify: `frontend/src/components/layout/SidebarNav.vue`
- Modify: `frontend/src/styles/layout.css`
- Modify: `docs/handbook/frontend.md`

- [x] **Step 1: Write failing navigation tests**

In `frontend/src/router/navigation.test.js`, update the mobile nav test to match the spec:

```js
it('getMobileNavigation should prioritize community attention loops', () => {
  const anon = getMobileNavigation({ authed: false })
  expect(anon.map((it) => it.key)).toEqual(['posts', 'search', 'notices', 'messages', 'me'])
  expect(anon.find((it) => it.key === 'notices')?.to).toEqual({ name: 'login' })
  expect(anon.find((it) => it.key === 'messages')?.to).toEqual({ name: 'login' })
  expect(anon.find((it) => it.key === 'me')?.to).toEqual({ name: 'login' })

  const authed = getMobileNavigation({ authed: true, userId: 8, roles: ['ROLE_USER'] })
  expect(authed.map((it) => it.key)).toEqual(['posts', 'search', 'notices', 'messages', 'me'])
  expect(authed.find((it) => it.key === 'notices')?.to).toEqual({ name: 'notices' })
  expect(authed.find((it) => it.key === 'messages')?.to).toEqual({ name: 'messages' })
  expect(authed.find((it) => it.key === 'me')?.to).toEqual({ name: 'userProfile', params: { userId: '8' } })
})
```

Add a route-support test for topbar scope helper after adding it in Step 3:

```js
it('getRouteWorkspaceLabel should describe route scope for the topbar', () => {
  expect(getRouteWorkspaceLabel('posts')).toBe('Community')
  expect(getRouteWorkspaceLabel('search')).toBe('Community')
  expect(getRouteWorkspaceLabel('userProfile')).toBe('Community')
  expect(getRouteWorkspaceLabel('notices')).toBe('Inbox')
  expect(getRouteWorkspaceLabel('messageDetail')).toBe('Inbox')
  expect(getRouteWorkspaceLabel('market')).toBe('Trade & Assets')
  expect(getRouteWorkspaceLabel('wallet')).toBe('Trade & Assets')
  expect(getRouteWorkspaceLabel('drive')).toBe('Files')
  expect(getRouteWorkspaceLabel('settings')).toBe('Account')
  expect(getRouteWorkspaceLabel('moderation')).toBe('Operations')
})
```

Update imports:

```js
import {
  POSTS_FILTER,
  POSTS_ORDER,
  canAccessNavItem,
  getRouteWorkspaceLabel,
  getShellSearchRouteNames,
  getMobileNavigation,
  getSidebarNavigation,
  isNavItemActive,
  normalizePostsCategoryId,
  normalizePostsFilter,
  normalizePostsOrder
} from './navigation'
```

- [x] **Step 2: Run navigation tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js
```

Expected: FAIL because current mobile nav is `posts/search/market/me` and `getRouteWorkspaceLabel` does not exist.

- [x] **Step 3: Implement route workspace labels and mobile nav priority**

In `frontend/src/router/navigation.js`, add:

```js
const ROUTE_WORKSPACE_LABELS = Object.freeze({
  posts: 'Community',
  postDetail: 'Community',
  search: 'Community',
  bookmarks: 'Community',
  userProfile: 'Community',
  followees: 'Community',
  followers: 'Community',
  notices: 'Inbox',
  noticeDetail: 'Inbox',
  messages: 'Inbox',
  messageDetail: 'Inbox',
  market: 'Trade & Assets',
  marketDetail: 'Trade & Assets',
  marketPublish: 'Trade & Assets',
  marketMyListings: 'Trade & Assets',
  marketInventory: 'Trade & Assets',
  marketBuyingOrders: 'Trade & Assets',
  marketSellingOrders: 'Trade & Assets',
  marketOrderDetail: 'Trade & Assets',
  marketAddresses: 'Trade & Assets',
  wallet: 'Trade & Assets',
  drive: 'Files',
  driveShare: 'Files',
  settings: 'Account',
  analytics: 'Operations',
  moderation: 'Operations',
  userManagement: 'Operations',
  walletAdmin: 'Operations',
  adminMarketDisputes: 'Operations',
  opsConsole: 'Operations',
  forbidden: 'System',
  notFound: 'System'
})

export function getRouteWorkspaceLabel(routeName) {
  return ROUTE_WORKSPACE_LABELS[String(routeName || '')] || 'Community'
}
```

Rewrite `getMobileNavigation(ctx)` so it returns:

```js
const notices = findNavItem(groups, 'notices') || {
  key: 'notices',
  label: '通知',
  icon: 'bell',
  to: login?.to || { name: 'login' },
  activeNames: ['notices', 'noticeDetail']
}
const messages = findNavItem(groups, 'messages') || {
  key: 'messages',
  label: '私信',
  icon: 'messages',
  to: login?.to || { name: 'login' },
  activeNames: ['messages', 'messageDetail']
}

return [posts, search, notices, messages, me]
```

Keep `posts`, `search`, and `me` fallback behavior.

- [x] **Step 4: Update mobile nav rendering**

In `frontend/src/components/layout/MobileNav.vue`:

1. Update comment to mention `Posts / Search / Notices / Messages / Me`.
2. Add bell icon branch:

```vue
<svg
  v-else-if="item.icon === 'bell'"
  width="20"
  height="20"
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="2"
>
  <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
  <path d="M13.73 21a2 2 0 0 1-3.46 0" />
</svg>
```

3. Change mobile grid CSS:

```css
grid-template-columns: repeat(5, minmax(0, 1fr));
```

- [x] **Step 5: Update topbar scope label**

In `frontend/src/components/layout/Topbar.vue`, import `getRouteWorkspaceLabel`:

```js
import { getRouteWorkspaceLabel, routeSupportsShellSearch } from '../../router/navigation'
```

Replace `modeEyebrow`:

```js
const modeEyebrow = computed(() => getRouteWorkspaceLabel(route.name))
```

Keep admin badge behavior unchanged.

- [x] **Step 6: Update sidebar brand copy**

In `frontend/src/components/layout/SidebarNav.vue`, change public brand subcopy:

```vue
<span class="sidebar-brand-sub">{{ props.mode === 'admin' ? '运营工作台' : '社区工作台' }}</span>
```

This keeps the product from saying every route is only a discussion desk.

- [x] **Step 7: Update frontend handbook navigation section**

In `docs/handbook/frontend.md`, update the mobile navigation sentence to:

```markdown
`frontend/src/components/layout/AppShell.vue` 负责桌面 workspace shell，`SidebarNav.vue` 渲染工作区分组，`Topbar.vue` 渲染 route-aware scope、页面标题、账户控制和 shell search，`MobileNav.vue` 只承载高频移动入口：讨论、搜索、通知、私信和个人入口。移动端 sidebar drawer 状态与桌面 collapsed 偏好分离，避免 sidebar 和 bottom nav 同时作为持久导航出现。
```

- [x] **Step 8: Run navigation tests**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js
```

Expected: PASS.

- [x] **Step 9: Commit navigation and shell positioning** (skipped: direct workspace, existing unrelated dirty changes)

Run:

```bash
git add frontend/src/router/navigation.js frontend/src/router/navigation.test.js frontend/src/components/layout/MobileNav.vue frontend/src/components/layout/Topbar.vue frontend/src/components/layout/SidebarNav.vue frontend/src/styles/layout.css docs/handbook/frontend.md
git commit -m "feat: align community navigation priority"
```

---

## Task 2: Product State Label Helpers

**Files:**

- Modify: `frontend/src/views/walletState.js`
- Modify: `frontend/src/views/walletState.test.js`
- Modify: `frontend/src/views/marketState.js`
- Modify: `frontend/src/views/marketState.test.js`
- Modify: `frontend/src/views/driveState.js`
- Modify: `frontend/src/views/driveState.test.js`
- Modify: `frontend/src/views/userProfileSurface.js`
- Modify: `frontend/src/views/userProfileSurface.test.js`

- [x] **Step 1: Write failing state-helper tests**

In `frontend/src/views/walletState.test.js`, add:

```js
it('describes unknown wallet summary as unavailable without future-work copy', () => {
  const state = buildWalletState({ summary: { balance: 0 }, txns: [] })

  expect(state.hero.status).toBe('UNKNOWN')
  expect(state.hero.statusText).toBe('钱包状态暂不可用，余额以当前可见数据为准。')
  expect(state.hero.statusText).not.toContain('后续')
  expect(state.hero.statusText).not.toContain('待同步')
})
```

In `frontend/src/views/marketState.test.js`, add:

```js
it('adds trust labels to active listings', () => {
  const state = buildMarketState({
    listings: [
      { listingId: 1, goodsType: 'VIRTUAL', deliveryMode: 'PRELOADED', status: 'ACTIVE', unitPrice: 10, stockAvailable: 2 },
      { listingId: 2, goodsType: 'PHYSICAL', status: 'ACTIVE', unitPrice: 20, stockAvailable: 1 }
    ]
  })

  expect(state.listings[0]).toMatchObject({
    trustLabel: '钱包托管',
    fulfillmentLabel: '自动交付',
    statusLabel: '在售'
  })
  expect(state.listings[1]).toMatchObject({
    trustLabel: '钱包托管',
    fulfillmentLabel: '实物配送',
    statusLabel: '在售'
  })
})
```

In `frontend/src/views/driveState.test.js`, add:

```js
it('normalizes drive entry status and visibility labels for product UI', () => {
  expect(normalizeDriveEntry({ status: 'ACTIVE', type: 'FILE', canShare: true })).toMatchObject({
    statusLabel: '可用',
    visibilityLabel: '可分享'
  })
  expect(normalizeDriveEntry({ status: 'TRASHED', type: 'FILE', canShare: false })).toMatchObject({
    statusLabel: '回收站',
    visibilityLabel: '私有'
  })
})
```

In `frontend/src/views/userProfileSurface.test.js`, add:

```js
it('does not expose wallet implementation caveats in profile signals', () => {
  const asset = buildProfileWalletAsset({ authed: true, isSelf: true })

  expect(asset.valueText).toBe('仅自己可见')
  expect(asset.chipText).toBe('仅自己可见')
  expect(asset.description).toBe('资产明细只在钱包页向本人展示。')
  expect(asset.description).not.toContain('未接入')
  expect(asset.description).not.toContain('钱包页为准')
})
```

- [x] **Step 2: Run state-helper tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/views/walletState.test.js src/views/marketState.test.js src/views/driveState.test.js src/views/userProfileSurface.test.js
```

Expected: FAIL because the new labels are not present and wallet/profile still contain implementation caveats.

- [x] **Step 3: Implement wallet state labels**

In `frontend/src/views/walletState.js`, change `statusText(status)`:

```js
function statusText(status) {
  if (status === 'FROZEN') return '钱包已冻结，当前仅保留查询能力。'
  if (status === 'CLOSED') return '钱包已关闭，如需恢复请联系管理员。'
  if (status === 'ACTIVE') return '钱包状态正常，可继续消费、转账与提现。'
  if (status === 'UNKNOWN') return '钱包状态暂不可用，余额以当前可见数据为准。'
  return '钱包状态正常，可继续消费、转账与提现。'
}
```

Keep transaction mapping unchanged.

- [x] **Step 4: Implement market trust labels**

In `frontend/src/views/marketState.js`, add:

```js
function fulfillmentLabel(item) {
  const goodsType = String(item?.goodsType || '').trim().toUpperCase()
  if (goodsType === 'VIRTUAL') return deliveryLabel(item?.deliveryMode)
  if (goodsType === 'PHYSICAL') return '实物配送'
  return '履约待确认'
}

function trustLabel(status) {
  const normalized = normalizeStatus(status)
  if (normalized === 'ACTIVE') return '钱包托管'
  if (normalized === 'SOLD_OUT') return '交易已结束'
  if (normalized === 'PAUSED') return '暂不可购买'
  if (normalized === 'CLOSED') return '已关闭'
  return '状态待确认'
}
```

Add these fields to each listing:

```js
fulfillmentLabel: fulfillmentLabel(item),
trustLabel: trustLabel(item?.status),
```

- [x] **Step 5: Implement drive product labels**

In `frontend/src/views/driveState.js`, add:

```js
function driveStatusLabel(status) {
  const normalized = String(status || '').trim().toUpperCase()
  if (normalized === 'ACTIVE') return '可用'
  if (normalized === 'TRASHED') return '回收站'
  if (normalized === 'DELETED') return '已删除'
  return '状态待确认'
}

function driveVisibilityLabel(raw, active) {
  if (!active) return '私有'
  if (raw?.canShare === false) return '私有'
  return '可分享'
}
```

In `normalizeDriveEntry`, return:

```js
statusLabel: driveStatusLabel(status),
visibilityLabel: driveVisibilityLabel(raw, active),
```

- [x] **Step 6: Implement profile wallet privacy copy**

In `frontend/src/views/userProfileSurface.js`, replace `buildProfileWalletAsset` with:

```js
export function buildProfileWalletAsset({ profile, authed, isSelf } = {}) {
  if (authed && isSelf) {
    return {
      valueText: '仅自己可见',
      chipText: '仅自己可见',
      description: '资产明细只在钱包页向本人展示。'
    }
  }

  return {
    valueText: '未公开',
    chipText: '未公开',
    description: '该成员未公开资产信息。'
  }
}
```

- [x] **Step 7: Run state-helper tests**

Run:

```bash
cd frontend
npm test -- src/views/walletState.test.js src/views/marketState.test.js src/views/driveState.test.js src/views/userProfileSurface.test.js
```

Expected: PASS.

- [x] **Step 8: Commit product state labels** (skipped: direct workspace, existing unrelated dirty changes)

Run:

```bash
git add frontend/src/views/walletState.js frontend/src/views/walletState.test.js frontend/src/views/marketState.js frontend/src/views/marketState.test.js frontend/src/views/driveState.js frontend/src/views/driveState.test.js frontend/src/views/userProfileSurface.js frontend/src/views/userProfileSurface.test.js
git commit -m "feat: normalize product state labels"
```

---

## Task 3: Posts Homepage Positioning

**Files:**

- Modify: `frontend/src/views/PostsView.vue`
- Modify: `frontend/src/views/posts/PostsView.css`
- Modify: `frontend/src/views/PostsView.test.js`

- [x] **Step 1: Write failing posts positioning test**

In `frontend/src/views/PostsView.test.js`, add:

```js
it('positions the discussion feed before secondary explanation copy', async () => {
  const wrapper = mountView()
  await flushPromises()

  expect(wrapper.find('.posts-workspace').exists()).toBe(true)
  expect(wrapper.find('.posts-main-feed').exists()).toBe(true)
  expect(wrapper.text()).toContain('社区讨论')
  expect(wrapper.text()).toContain('开始一个讨论')
  expect(wrapper.text()).not.toContain('发帖入口保留在顶部，不把整个首屏变成编辑器')
})
```

- [x] **Step 2: Run posts test and verify failure**

Run:

```bash
cd frontend
npm test -- src/views/PostsView.test.js
```

Expected: FAIL because `.posts-workspace` and `.posts-main-feed` do not exist and the old explanatory subtitle is still present.

- [x] **Step 3: Update `PostsView.vue` structure and copy**

Change the page header subtitle:

```vue
<template #title>社区讨论</template>
<template #subtitle>查看最新问题、未读回复和成员正在推进的话题。</template>
```

Wrap the feed body:

```vue
<div class="posts-workspace">
  <main class="posts-main-feed">
    <!-- existing toolbar, context strip, composer, states, discussion feed, load more -->
  </main>
  <aside class="posts-context-panel" aria-label="社区上下文">
    <div class="posts-context-block">
      <strong>当前视图</strong>
      <span>{{ items.length || 0 }} 条讨论 · {{ categories.length }} 个分类</span>
    </div>
    <div class="posts-context-block">
      <strong>快速入口</strong>
      <span>使用未读、热门和订阅筛选快速回到关注的话题。</span>
    </div>
  </aside>
</div>
```

Keep the existing toolbar, composer, empty state, and load-more logic inside `posts-main-feed`.

Change composer strip text:

```vue
<span class="posts-feed-compose-title">开始一个讨论</span>
<span class="posts-feed-compose-sub">把问题、经验或交易提醒发到社区时间线。</span>
```

Change empty state copy:

```vue
<UiState v-if="!loading && items.length === 0 && !error" class="posts-empty-inline">
  当前视图暂无讨论
  <template #description>
    可以重置筛选、查看热门，或者直接开始一个讨论。
  </template>
</UiState>
```

- [x] **Step 4: Update posts CSS**

In `frontend/src/views/posts/PostsView.css`, add:

```css
.posts-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: 18px;
  align-items: start;
}

.posts-main-feed {
  min-width: 0;
  display: grid;
  gap: 12px;
}

.posts-context-panel {
  position: sticky;
  top: calc(var(--topbar-height) + 18px);
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
}

.posts-context-block {
  display: grid;
  gap: 4px;
  font-size: 13px;
  color: var(--text-2);
}

.posts-context-block strong {
  color: var(--text-1);
}

@media (max-width: 1080px) {
  .posts-workspace {
    grid-template-columns: 1fr;
  }

  .posts-context-panel {
    position: static;
    order: -1;
  }
}
```

If this duplicates existing `.posts-context-*` names, rename to `.posts-side-*` consistently in template and CSS.

- [x] **Step 5: Run posts test**

Run:

```bash
cd frontend
npm test -- src/views/PostsView.test.js
```

Expected: PASS.

- [x] **Step 6: Commit posts positioning** (skipped: direct workspace, existing unrelated dirty changes)

Run:

```bash
git add frontend/src/views/PostsView.vue frontend/src/views/posts/PostsView.css frontend/src/views/PostsView.test.js
git commit -m "feat: reposition discussion homepage"
```

---

## Task 4: Profile, Notices, And Messages Copy

**Files:**

- Modify: `frontend/src/views/UserProfileView.vue`
- Modify: `frontend/src/views/UserProfileView.test.js`
- Modify: `frontend/src/views/NoticesView.vue`
- Modify: `frontend/src/views/NoticesView.test.js`
- Modify: `frontend/src/views/ConversationsView.vue`
- Modify: `frontend/src/views/ConversationsView.test.js`

- [x] **Step 1: Write failing profile/inbox tests**

In `frontend/src/views/UserProfileView.test.js`, update assertions that currently expect full UUID primary display:

```js
expect(wrapper.text()).not.toContain('钱包页为准')
expect(wrapper.text()).not.toContain('当前主页还未接入真实钱包余额')
expect(wrapper.find('.profile-id-value').text()).not.toBe(userId)
expect(wrapper.find('.profile-id-value').attributes('title')).toBe(userId)
```

In `frontend/src/views/NoticesView.test.js`, add:

```js
expect(wrapper.text()).toContain('需要处理')
expect(wrapper.text()).toContain('打开通知')
expect(wrapper.text()).not.toContain('可快速处理的收件箱')
```

In `frontend/src/views/ConversationsView.test.js`, add:

```js
expect(wrapper.text()).toContain('待回复')
expect(wrapper.text()).not.toContain('成员 #11111111-1111-7111-8111-111111111111')
```

- [x] **Step 2: Run profile/inbox tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/views/UserProfileView.test.js src/views/NoticesView.test.js src/views/ConversationsView.test.js
```

Expected: FAIL because old copy and full UUID labels remain.

- [x] **Step 3: Shorten primary profile ID display**

In `frontend/src/views/UserProfileView.vue`, add a helper in `<script setup>`:

```js
function shortUserId(value) {
  const raw = String(value || '')
  if (raw.length <= 12) return raw || '—'
  return `${raw.slice(0, 8)}...${raw.slice(-4)}`
}
```

Where `.profile-id-value` renders the full user id, change visible text to:

```vue
<span class="profile-id-value profile-text-wrap" :title="String(profile?.id || normalizedUserId)">
  {{ shortUserId(profile?.id || normalizedUserId) }}
</span>
```

Keep the full ID in `title`.

- [x] **Step 4: Update notices copy**

In `frontend/src/views/NoticesView.vue`:

1. Change subtitle:

```vue
<template #subtitle>查看评论、点赞、关注和治理提醒。</template>
```

2. Change unread badge text from `有新内容` to `需要处理`.

3. Add a visible open action text in `.inbox-tail` or row tail:

```vue
<span class="inbox-open-copy">打开通知</span>
```

Keep the route link behavior unchanged.

- [x] **Step 5: Update conversations copy**

In `frontend/src/views/ConversationsView.vue`:

1. Change subtitle:

```vue
<template #subtitle>查看私信、未读消息和需要跟进的成员对话。</template>
```

2. Replace primary name logic:

```vue
<span class="conv-name">{{ c.unreadCount > 0 ? '待回复' : '继续对话' }}</span>
<span class="conv-context">{{ shortParticipant(c?.otherUserId) }}</span>
```

3. Add helper:

```js
function shortParticipant(value) {
  const raw = String(value || '').trim()
  if (!raw) return '社区成员'
  return `社区成员 ${raw.slice(0, 8)}`
}
```

This avoids full UUID as the primary label while remaining honest if no username is available.

- [x] **Step 6: Run profile/inbox tests**

Run:

```bash
cd frontend
npm test -- src/views/UserProfileView.test.js src/views/NoticesView.test.js src/views/ConversationsView.test.js
```

Expected: PASS.

- [x] **Step 7: Commit profile and inbox copy** (skipped: direct workspace, existing unrelated dirty changes)

Run:

```bash
git add frontend/src/views/UserProfileView.vue frontend/src/views/UserProfileView.test.js frontend/src/views/NoticesView.vue frontend/src/views/NoticesView.test.js frontend/src/views/ConversationsView.vue frontend/src/views/ConversationsView.test.js
git commit -m "feat: refine profile and inbox surfaces"
```

---

## Task 5: Market, Wallet, And Drive First-Pass Product Surfaces

**Files:**

- Modify: `frontend/src/views/MarketListView.vue`
- Modify: `frontend/src/views/MarketViews.test.js`
- Modify: `frontend/src/views/WalletView.vue`
- Modify: `frontend/src/views/WalletView.test.js`
- Modify: `frontend/src/views/DriveView.vue`
- Modify: `frontend/src/views/DriveView.test.js`

- [x] **Step 1: Write failing surface tests**

In `frontend/src/views/MarketViews.test.js`, add:

```js
it('renders trust-oriented empty market copy', async () => {
  listMarketListings.mockResolvedValue({ data: [], traceId: 'trace-market-list' })

  const wrapper = mount(MarketListView, mountOptions())
  await flushPromises()

  expect(wrapper.text()).toContain('钱包托管')
  expect(wrapper.text()).toContain('履约方式')
  expect(wrapper.text()).toContain('争议可裁定')
  expect(wrapper.text()).not.toContain('前台只按商品类型展示不同的履约语义')
})
```

In `frontend/src/views/WalletView.test.js`, add:

```js
it('renders wallet as an asset and ledger surface without demo copy', async () => {
  const wrapper = mountWalletView()
  await flushPromises()

  expect(wrapper.text()).toContain('可用余额')
  expect(wrapper.text()).toContain('最近流水')
  expect(wrapper.text()).not.toContain('当前会话')
  expect(wrapper.text()).not.toContain('后续')
})
```

In `frontend/src/views/DriveView.test.js`, add a mocked active file and assert:

```js
expect(wrapper.text()).toContain('可用')
expect(wrapper.text()).toContain('可分享')
expect(wrapper.text()).not.toContain('ACTIVE')
```

- [x] **Step 2: Run surface tests and verify failure**

Run:

```bash
cd frontend
npm test -- src/views/MarketViews.test.js src/views/WalletView.test.js src/views/DriveView.test.js
```

Expected: FAIL because current product copy still contains old wording or raw labels.

- [x] **Step 3: Update market list copy and trust strip**

In `frontend/src/views/MarketListView.vue`:

1. Change subtitle:

```vue
<template #subtitle>通过钱包托管购买虚拟商品和实物商品，按履约方式跟进订单。</template>
```

2. Add a trust strip after `UiPageHeader`:

```vue
<section class="market-trust-strip" aria-label="交易保障">
  <span>钱包托管</span>
  <span>履约方式清晰</span>
  <span>争议可裁定</span>
</section>
```

3. In listing rows, use `item.fulfillmentLabel` and `item.trustLabel`:

```vue
<span>{{ item.fulfillmentLabel }}</span>
<span>{{ item.trustLabel }}</span>
```

4. Change empty-state description:

```vue
<template #description>发布商品后，买家通过钱包托管下单；虚拟商品按交付方式处理，实物商品按配送状态跟进。</template>
```

- [x] **Step 4: Update wallet copy**

In `frontend/src/views/WalletView.vue`:

1. Change summary label `积分余额` to `可用余额`.
2. Change summary description:

```vue
<p>当前可用于消费、转账和提现的站内积分。</p>
```

3. Change `最近动作` to `最近流水`.
4. Change side metric description:

```vue
<p>充值、提现、转账和交易相关流水会显示在这里。</p>
```

5. Change wallet action subtitle:

```vue
<template #subtitle>充值、提现和转账会进入钱包账务流程，请确认金额和对象后提交。</template>
```

6. Change recent transactions subtitle:

```vue
<template #subtitle>按时间查看钱包流水、状态和对方信息。</template>
```

7. Change empty transaction description:

```vue
<template #description>产生充值、提现、转账或交易托管后，这里会显示流水摘要。</template>
```

- [x] **Step 5: Update drive labels**

In `frontend/src/views/DriveView.vue`:

1. Use `entry.statusLabel` instead of `entry.status` wherever shown.
2. Use `entry.visibilityLabel` instead of `entry.canShare ? '可分享' : '仅内部'`.
3. Change subtitle to:

```vue
<template #subtitle>
  <span>{{ quota.label }}</span>
  <span class="drive-header-dot" aria-hidden="true">·</span>
  <span>{{ quota.usedPercent }}% 已用</span>
  <span class="drive-header-dot" aria-hidden="true">·</span>
  <span>私有文件、分享链接和社区附件</span>
</template>
```

4. Change share panel subtitle:

```vue
<template #subtitle>默认私有；生成链接后可用于帖子附件、成员分享或虚拟商品交付。</template>
```

- [x] **Step 6: Run surface tests**

Run:

```bash
cd frontend
npm test -- src/views/MarketViews.test.js src/views/WalletView.test.js src/views/DriveView.test.js
```

Expected: PASS.

- [x] **Step 7: Commit trust and asset surfaces** (skipped: direct workspace, existing unrelated dirty changes)

Run:

```bash
git add frontend/src/views/MarketListView.vue frontend/src/views/MarketViews.test.js frontend/src/views/WalletView.vue frontend/src/views/WalletView.test.js frontend/src/views/DriveView.vue frontend/src/views/DriveView.test.js
git commit -m "feat: refine trust and asset surfaces"
```

---

## Task 6: Global Copy And Styling Sweep

**Files:**

- Modify: `frontend/src/styles/layout.css`
- Modify: `frontend/src/styles/pages.css`
- Modify: `frontend/src/styles/components.css`
- Modify: `frontend/src/styles/productTokens.test.js`
- Modify, only if the scan in Step 2 reports a product-facing match: touched files under `frontend/src/views` and `frontend/src/components/layout`.

- [x] **Step 1: Update token tests**

In `frontend/src/styles/productTokens.test.js`, update the first test to include these assertions inside `it('uses restrained radius and shadow tokens', ...)` after `const variables = read('src/styles/variables.css')`:

```js
expect(variables).toContain('--radius-md: 12px;')
expect(variables).toContain('--radius-lg: 12px;')
expect(variables).toContain('--radius-xl: 16px;')
expect(variables).not.toContain('--radius-lg: 24px')
expect(variables).not.toContain('--radius-xl: 28px')
```

In the same file, add this test after the routine card/button test:

```js
it('keeps mobile navigation compact for five high-frequency entries', () => {
  const mobileNav = read('src/components/layout/MobileNav.vue')

  expect(mobileNav).toContain('repeat(5, minmax(0, 1fr))')
  expect(mobileNav).toContain('item.icon === \\'bell\\'')
  expect(mobileNav).toContain('item.icon === \\'messages\\'')
})
```

- [x] **Step 2: Run copy scan and record failures**

Run:

```bash
rg -n "第一版|后续|暂未返回|待同步|钱包页为准|当前主页还未接入|前台只按|ACTIVE|成员 #" frontend/src/views frontend/src/components/layout
```

Expected before cleanup: only test fixtures or intentionally hidden technical values should remain. Any product-facing hit in templates or state helpers must be removed or replaced.

- [x] **Step 3: Replace remaining product-facing engineering copy**

Use these exact replacements for product-facing template/state-helper matches:

- `后续` -> remove sentence or say `当前不可用`.
- `暂未返回` -> `暂不可用`.
- `待同步` -> `同步中` only if actively loading, otherwise `暂不可用`.
- `钱包页为准` -> `仅自己可见` or hide.
- `ACTIVE` -> `可用`.
- `成员 #<uuid>` -> `社区成员 <short-id>` or resolved username.

Do not rewrite test assertions that intentionally verify old copy is absent.

- [x] **Step 4: Reduce card/radius excess for touched pages**

In CSS touched by this phase:

- Routine panels should use `border-radius: 8px` to `12px`.
- Keep `border-radius: 999px` only for pills/avatars.
- Remove decorative `box-shadow: var(--shadow-lg)` from static content containers.
- Keep shadows for dropdowns, drawer, toast, and floating nav.

- [x] **Step 5: Run token tests and copy scan**

Run:

```bash
cd frontend
npm test -- src/styles/productTokens.test.js
cd ..
rg -n "第一版|后续|暂未返回|待同步|钱包页为准|当前主页还未接入|前台只按|ACTIVE|成员 #" frontend/src/views frontend/src/components/layout
```

Expected: token tests PASS. Copy scan has no product-facing matches; any remaining matches are in tests or comments that explicitly describe prohibited old copy.

- [x] **Step 6: Commit copy and styling sweep** (skipped: direct workspace, existing unrelated dirty changes)

Run:

```bash
git add frontend/src/styles/layout.css frontend/src/styles/pages.css frontend/src/styles/components.css frontend/src/styles/productTokens.test.js frontend/src/views frontend/src/components/layout
git commit -m "style: clean up community product copy"
```

---

## Task 7: Full Verification And Browser Review

**Files:**

- No planned source edits in the happy path.
- If verification fails, modify only the file named by the failing test, build error, copy scan, or browser defect.

- [x] **Step 1: Run focused frontend tests**

Run:

```bash
cd frontend
npm test -- src/router/navigation.test.js src/views/PostsView.test.js src/views/UserProfileView.test.js src/views/NoticesView.test.js src/views/ConversationsView.test.js src/views/MarketViews.test.js src/views/WalletView.test.js src/views/DriveView.test.js src/views/walletState.test.js src/views/marketState.test.js src/views/driveState.test.js src/views/userProfileSurface.test.js src/styles/productTokens.test.js
```

Expected: PASS.

- [x] **Step 2: Run full frontend tests**

Run:

```bash
cd frontend
npm test
```

Expected: PASS.

- [x] **Step 3: Run production build**

Run:

```bash
cd frontend
npm run build
```

Expected: PASS.

- [x] **Step 4: Browser review on running single topology**

With `./deploy/deployment.sh up --topology single` already running, use Chrome DevTools at `http://localhost:12881`.

Verify desktop `1440x900`:

- `/#/posts`: feed surface visible in first viewport; no old explanatory subtitle.
- `/#/market`: trust strip visible; no old fulfillment-semantics copy.
- `/#/wallet`: says `可用余额` and `最近流水`; no `当前会话` or `后续`.
- `/#/drive`: shows `可用`/`私有`/`可分享`; no raw `ACTIVE`.
- `/#/messages`: mobile/desktop copy uses inbox language and no full UUID primary label.
- `/#/notices`: topic rows read as actionable inbox rows.
- `/#/users/00000000-0000-7000-8000-000000000001`: no wallet caveat copy; user ID shortened in primary layout.
- `/#/403`: still routes correctly for `aaa` on admin pages.

Verify mobile `390x844`:

- Bottom nav has five entries: discussion, search, notices, messages, me.
- No horizontal overflow.
- Closed sidebar is not visible and does not fight bottom nav.
- Touched pages have no text overlap.

- [x] **Step 5: Final copy scan**

Run:

```bash
rg -n "第一版|后续|暂未返回|待同步|钱包页为准|当前主页还未接入|前台只按|ACTIVE|成员 #" frontend/src/views frontend/src/components/layout
```

Expected: no product-facing matches.

- [x] **Step 6: Commit verification fixes only when verification found a defect** (skipped: direct workspace, existing unrelated dirty changes)

If browser or full tests require fixes, make the smallest fix in the affected file and run the relevant failing command again. Commit fixes:

```bash
git add <changed-files>
git commit -m "fix: polish community positioning pass"
```

If no fixes are needed, do not create an empty commit.

---

## Phase 1 Completion Criteria

Phase 1 is complete when:

- Mobile nav prioritizes discussion, search, notices, messages, and profile/account.
- Topbar scope labels are route-aware and no longer always say `Discussion Workspace`.
- Posts homepage foregrounds the discussion feed.
- Profile removes wallet caveat copy and shortens full UUID from primary display.
- Notices and messages read as actionable inbox surfaces.
- Market communicates wallet escrow, fulfillment, and dispute trust in the list/empty state.
- Wallet reads as asset/ledger UI, not form-demo UI.
- Drive uses product labels instead of raw technical state.
- The focused tests, full frontend tests, and build pass.
- Browser checks pass on desktop and mobile for the representative routes.

## Follow-Up Phase 2 Scope

After Phase 1 lands, write a second plan for:

- Full market publish/detail/order lifecycle redesign.
- Buying/selling order list and order detail timeline.
- Address management polish.
- Admin analytics, moderation, user management, wallet admin, disputes, and ops console.
- Deeper accessibility pass for detail pages and modals.
