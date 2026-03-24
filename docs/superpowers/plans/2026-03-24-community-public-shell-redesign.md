# Community Public Shell Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved public-shell redesign so the community public product becomes a two-column discussion workspace with no public right panel, content-first posts/search/detail pages, and fixed mobile primary navigation.

**Architecture:** First lock the routing and navigation contract so desktop and mobile IA match the approved public-shell spec and public right-panel assumptions are removed at the application boundary. Then rebuild the shared public shell and core routes (`/posts`, `/search`, `/posts/:postId`) around the new hierarchy, followed by a lighter inheritance pass on secondary public routes so they read as the same product without reintroducing a third column or explanation-heavy page openers.

**Tech Stack:** Vue 3 SFCs, Vue Router, Pinia, Vite, Vitest, CSS variables, scoped CSS

---

## Relationship To Existing Docs

- This plan implements [2026-03-23-community-public-shell-design.md](/home/feng/code/project/community/docs/superpowers/specs/2026-03-23-community-public-shell-design.md).
- For public shell, public navigation, and public core page opening, this plan supersedes the corresponding shell/public-core portions of [2026-03-20-community-ui-redesign.md](/home/feng/code/project/community/docs/superpowers/plans/2026-03-20-community-ui-redesign.md).
- Do not execute both plans for the same public-shell files in parallel.

---

## File Structure Map

### Shared public shell and navigation

- `frontend/src/App.vue`
  Role: top-level auth/public/admin shell split; currently mounts the public right-panel slot via route-name allowlist.
- `frontend/src/components/layout/AppShell.vue`
  Role: desktop/mobile shell container; currently still reserves a third column when the public right panel is open.
- `frontend/src/components/layout/Topbar.vue`
  Role: page title, global search, theme/density controls, account actions; needs to become a smaller title-first topbar with overflow handling.
- `frontend/src/components/layout/SidebarNav.vue`
  Role: desktop sidebar navigation and signed-in identity block; must reflect the final public `Explore / Me` IA.
- `frontend/src/components/layout/MobileNav.vue`
  Role: current mobile quick-nav; must become fixed `Posts / Search / Me / More`.
- `frontend/src/components/layout/RightPanel.vue`
  Role: legacy public right panel; removal target.
- `frontend/src/router/index.js`
  Role: route definitions, `meta.title`, `meta.subtitle`, and `meta.navGroup`; the source of truth for `explore` vs `me` public route families.
- `frontend/src/router/navigation.js`
  Role: navigation SSOT for desktop groups and mobile quick-entry behavior; must encode the approved public IA.
- `frontend/src/router/navigation.test.js`
  Role: test lock for sidebar/mobile navigation behavior.
- `frontend/src/router/index.test.js`
  Role: route-level contract tests; can be extended if route/meta assumptions need explicit locking.
- `frontend/src/styles/layout.css`
  Role: shell structure, sidebar/topbar/public layout, mobile shell behavior; main CSS surface for removing the third column.
- `frontend/src/styles/components.css`
  Role: shared control, button, and input styling; used to soften topbar/toolbars and mobile tab chrome.
- `frontend/src/styles/pages.css`
  Role: shared page-level patterns; may absorb lighter public content-first list/search scaffolds.

### Public core path

- `frontend/src/components/posts/FeedToolbar.vue`
  Role: posts list toolbar; must become a lighter first-operation layer.
- `frontend/src/views/PostsView.vue`
  Role: posts index; currently includes a strong intro/overview composition that delays entry into the feed.
- `frontend/src/views/postsFeedState.js`
  Role: posts feed state helpers for last-seen behavior and latest-feed rules; should stay stable while the view layout changes.
- `frontend/src/views/postsFeedState.test.js`
  Role: regression lock for posts feed helper behavior.
- `frontend/src/views/SearchView.vue`
  Role: search page; must become a workbench-style search surface instead of a split explanation/form/result layout.
- `frontend/src/views/searchResultSurface.js`
  Role: search result enrichment helpers; should remain compatible with the new result card presentation.
- `frontend/src/views/searchResultSurface.test.js`
  Role: regression lock for search result hydration/activity helper behavior.
- `frontend/src/views/PostDetailView.vue`
  Role: post detail page; should retain strong reading flow without reintroducing page hero duplication.

### Secondary public inheritance pass

- `frontend/src/views/BookmarksView.vue`
- `frontend/src/views/LeaderboardView.vue`
- `frontend/src/views/UserProfileView.vue`
- `frontend/src/views/FolloweesView.vue`
- `frontend/src/views/FollowersView.vue`
  Role: profile/list surfaces that should inherit the public grammar rather than invent new shells.

- `frontend/src/views/SettingsView.vue`
- `frontend/src/views/ConversationsView.vue`
- `frontend/src/views/ConversationDetailView.vue`
- `frontend/src/views/NoticesView.vue`
- `frontend/src/views/NoticeDetailView.vue`
- `frontend/src/views/GrowthCenterView.vue`
- `frontend/src/views/RewardShopView.vue`
- `frontend/src/views/RewardOrderHistoryView.vue`
  Role: public `Me` surfaces that must sit under the new shell and mobile IA, even if they are not as aggressively redesigned as the core path.

---

### Task 1: Lock The Public Navigation Contract Before Touching Shell UI

**Files:**
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`
- Modify: `frontend/src/router/navigation.test.js`
- Modify: `frontend/src/router/index.test.js`

- [ ] **Step 1: Update route metadata classification to match the approved public IA**

  Align `meta.navGroup` usage in `frontend/src/router/index.js` with the approved public split:
  - `explore`: `/posts`, `/posts/:postId`, `/search`, `/leaderboard`
  - `me`: `/growth`, `/rewards/shop`, `/rewards/orders`, `/messages`, `/messages/:conversationId`, `/notices`, `/notices/:topic`, `/bookmarks`, `/settings`, `/users/:userId`, `/users/:userId/followees`, `/users/:userId/followers`

- [ ] **Step 2: Rewrite the navigation tests first so the new IA is explicit**

  Update `frontend/src/router/navigation.test.js` to assert:
  - desktop Explore contains `posts`, `search`, `leaderboard`
  - desktop Me contains `growth`, `rewardShop`, `bookmarks`, `notices`, `messages`, `profile`, `settings`
  - mobile primary nav is no longer the current `posts/search/growth/messages/profile` set
  - mobile primary nav is fixed by the new contract rather than inherited mechanically from sidebar groups

  Extend `frontend/src/router/index.test.js` only if needed to lock route/meta assumptions that would otherwise remain implicit.

- [ ] **Step 3: Run the targeted router tests and confirm RED**

  Run:
  - `cd frontend && npm test -- src/router/navigation.test.js src/router/index.test.js`

  Expected:
  - FAIL because current navigation.js and mobile allowlist still reflect the old public shell contract

- [ ] **Step 4: Rewrite navigation.js around the new desktop and mobile model**

  Implement:
  - desktop sidebar groups that match the approved `Explore / Me / Admin / Auth` structure
  - mobile primary navigation contract that resolves to `Posts / Search / Me / More`
  - a stable way to derive `Me`-hub and `More`-menu items without treating `growth/messages/profile` as bottom-tab peers
  - route/item semantics that keep `/rewards/orders`, detail routes, and follow/follower routes inside the right parent family

- [ ] **Step 5: Re-run the targeted router tests and verify GREEN**

  Run:
  - `cd frontend && npm test -- src/router/navigation.test.js src/router/index.test.js`

- [ ] **Step 6: Checkpoint the diff for the navigation contract task**

  Note:
  - do not create a git commit unless the user explicitly asks for one

---

### Task 2: Remove Public Right-Panel Assumptions And Rebuild The Shared Shell

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/layout/AppShell.vue`
- Modify: `frontend/src/components/layout/Topbar.vue`
- Modify: `frontend/src/components/layout/SidebarNav.vue`
- Modify: `frontend/src/components/layout/MobileNav.vue`
- Delete: `frontend/src/components/layout/RightPanel.vue`
- Modify: `frontend/src/styles/layout.css`
- Modify: `frontend/src/styles/components.css`

- [ ] **Step 1: Remove the application-level right-panel route contract**

  In `frontend/src/App.vue`:
  - remove the public `RIGHT_PANEL_ROUTE_NAMES` allowlist
  - remove `showRightPanel`
  - remove the `#right` slot mount
  - remove the `RightPanel` import

- [ ] **Step 2: Collapse AppShell from public three-column branching to a stable two-column public shell**

  In `frontend/src/components/layout/AppShell.vue`:
  - remove `useSlots()` dependence for public right-slot rendering
  - remove `hasRight`
  - remove public-shell class branches that reserve width for a third column

- [ ] **Step 3: Rewrite the public topbar as a smaller title-first shell control bar**

  In `frontend/src/components/layout/Topbar.vue`:
  - keep title and one light subtitle line
  - keep global search on desktop
  - move theme/density/other low-frequency global actions into overflow behavior instead of leaving a flat control row
  - remove any public-right-panel toggle control

- [ ] **Step 4: Rebuild SidebarNav and MobileNav around the approved IA**

  In `frontend/src/components/layout/SidebarNav.vue`:
  - align the public sidebar group ordering and labeling to the final IA
  - keep desktop sidebar readable at normal width

  In `frontend/src/components/layout/MobileNav.vue`:
  - make the primary bottom bar fixed to `Posts / Search / Me / More`
  - treat `Search` as the primary search entry
  - ensure `More` is not implemented as a disguised legacy sidebar clone

- [ ] **Step 5: Rewrite shared shell CSS and remove right-panel-specific layout styling**

  In `frontend/src/styles/layout.css` and, where needed, `frontend/src/styles/components.css`:
  - remove `.app-right`-driven public layout behavior
  - retune topbar height, sidebar/public spacing, and mobile bottom-nav presentation
  - keep the public shell visually lighter without turning it into a minimal content site

- [ ] **Step 6: Run the targeted router tests and a frontend build after shell rewiring**

  Run:
  - `cd frontend && npm test -- src/router/navigation.test.js src/router/index.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 7: Manually verify shell behavior**

  Manually verify:
  - `/posts`
  - `/search`
  - `/posts/:postId`
  - `/leaderboard`
  - `/bookmarks`
  - `/users/:userId`
  - `/users/:userId/followees`
  - `/users/:userId/followers`

  Check:
  - no public right-panel remains
  - no dead topbar control references the removed panel
  - auth/public/admin shell switching still works
  - mobile shell keeps first-screen content visible

- [ ] **Step 8: Checkpoint the diff for the shell task**

  Note:
  - do not create a git commit unless the user explicitly asks for one

---

### Task 3: Rebuild The Posts Index Around Operation-Then-Content Flow

**Files:**
- Modify: `frontend/src/components/posts/FeedToolbar.vue`
- Modify: `frontend/src/views/PostsView.vue`
- Modify: `frontend/src/styles/pages.css`
- Test: `frontend/src/views/postsFeedState.test.js`

- [ ] **Step 1: Keep the posts feed helper tests as a regression anchor**

  Run:
  - `cd frontend && npm test -- src/views/postsFeedState.test.js`

  Expected:
  - PASS before layout work; this establishes the latest-feed and last-seen behavior baseline

- [ ] **Step 2: Rewrite FeedToolbar into a lighter first-operation layer**

  In `frontend/src/components/posts/FeedToolbar.vue`:
  - keep URL-query-driven sorting/filtering behavior
  - reduce decorative weight so the toolbar supports the feed rather than reading as a standalone hero block
  - keep desktop and mobile layout compact enough to avoid becoming a second page header

- [ ] **Step 3: Remove hero/overview-first composition from PostsView**

  In `frontend/src/views/PostsView.vue`:
  - remove the large introductory hero/overview region as the default posts entry
  - remove or compress any quick-entry/promotional stage that delays access to the discussion stream
  - keep the composer, but reduce it to a light trigger/inline action rather than a second dominant region
  - keep empty states inside the feed area rather than as a separate poster block

- [ ] **Step 4: Adjust shared page patterns only where needed to support the new feed grammar**

  In `frontend/src/styles/pages.css`:
  - keep reusable feed/list structure that helps PostsView without reintroducing dashboard-card weight
  - avoid re-adding right-rail or intro-card scaffolding through shared page utilities

- [ ] **Step 5: Re-run the posts feed helper tests and frontend build**

  Run:
  - `cd frontend && npm test -- src/views/postsFeedState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 6: Manually verify the posts list**

  Manually verify:
  - `/posts`

  Check:
  - first viewport shows topbar + toolbar + real thread cards
  - no large public intro/overview block returns
  - composer reads as part of the discussion flow, not a dashboard form
  - empty state is embedded and light

- [ ] **Step 7: Checkpoint the diff for the posts task**

  Note:
  - do not create a git commit unless the user explicitly asks for one

---

### Task 4: Turn Search And Post Detail Into Content-First Work Surfaces

**Files:**
- Modify: `frontend/src/views/SearchView.vue`
- Modify: `frontend/src/views/PostDetailView.vue`
- Test: `frontend/src/views/searchResultSurface.test.js`

- [ ] **Step 1: Keep the search helper tests as a regression anchor**

  Run:
  - `cd frontend && npm test -- src/views/searchResultSurface.test.js`

  Expected:
  - PASS before layout work; this establishes current result hydration/activity helper behavior

- [ ] **Step 2: Rewrite SearchView into a direct search workbench**

  In `frontend/src/views/SearchView.vue`:
  - remove the explanation-first masthead composition
  - make the search input, filter row, and results one continuous work surface
  - keep the admin reindex affordance available, but visually subordinate
  - avoid adding a second explanation band below the topbar

- [ ] **Step 3: Rewrite PostDetailView to avoid duplicated page-hero semantics**

  In `frontend/src/views/PostDetailView.vue`:
  - keep the page readable and information-rich
  - avoid reintroducing a large page hero above the actual post
  - keep post metadata and actions close to the main post
  - keep the comments area reading as part of the conversation, not a stack of unrelated utility blocks

- [ ] **Step 4: Re-run the search helper tests and frontend build**

  Run:
  - `cd frontend && npm test -- src/views/searchResultSurface.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 5: Manually verify search and post detail**

  Manually verify:
  - `/search`
  - `/posts/:postId`

  Check:
  - search starts with operation, not explanation
  - result cards use the same product grammar as the feed
  - post detail keeps reading flow without an oversized opening block
  - no shell-level right-panel assumptions remain

- [ ] **Step 6: Checkpoint the diff for the search/detail task**

  Note:
  - do not create a git commit unless the user explicitly asks for one

---

### Task 5: Align Secondary Public Pages To The New Shell Grammar

**Files:**
- Modify: `frontend/src/views/BookmarksView.vue`
- Modify: `frontend/src/views/LeaderboardView.vue`
- Modify: `frontend/src/views/UserProfileView.vue`
- Modify: `frontend/src/views/FolloweesView.vue`
- Modify: `frontend/src/views/FollowersView.vue`
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/ConversationsView.vue`
- Modify: `frontend/src/views/ConversationDetailView.vue`
- Modify: `frontend/src/views/NoticesView.vue`
- Modify: `frontend/src/views/NoticeDetailView.vue`
- Modify: `frontend/src/views/GrowthCenterView.vue`
- Modify: `frontend/src/views/RewardShopView.vue`
- Modify: `frontend/src/views/RewardOrderHistoryView.vue`
- Test: `frontend/src/views/userProfileSurface.test.js`
- Test: `frontend/src/views/userProfileTimeline.test.js`
- Test: `frontend/src/views/growthCenterState.test.js`
- Test: `frontend/src/views/rewardShopState.test.js`

- [ ] **Step 1: Run the existing secondary-page helper tests before touching these views**

  Run:
  - `cd frontend && npm test -- src/views/userProfileSurface.test.js src/views/userProfileTimeline.test.js src/views/growthCenterState.test.js src/views/rewardShopState.test.js`

  Expected:
  - PASS before view inheritance changes

- [ ] **Step 2: Align list/profile-style pages to the new public grammar**

  In:
  - `BookmarksView.vue`
  - `LeaderboardView.vue`
  - `UserProfileView.vue`
  - `FolloweesView.vue`
  - `FollowersView.vue`

  Implement:
  - remove oversized intro regions or leftover shell assumptions
  - keep list/profile content as the first visual priority
  - make them read as part of the same public product as `/posts`

- [ ] **Step 3: Align Me/work-surface pages to the new shell without inventing new chrome**

  In:
  - `SettingsView.vue`
  - `ConversationsView.vue`
  - `ConversationDetailView.vue`
  - `NoticesView.vue`
  - `NoticeDetailView.vue`
  - `GrowthCenterView.vue`
  - `RewardShopView.vue`
  - `RewardOrderHistoryView.vue`

  Implement:
  - keep page openings compact
  - do not reintroduce explanation-heavy or right-rail-style modules
  - ensure the pages fit the new `Me` information architecture on both desktop and mobile

- [ ] **Step 4: Re-run the existing secondary-page helper tests and frontend build**

  Run:
  - `cd frontend && npm test -- src/views/userProfileSurface.test.js src/views/userProfileTimeline.test.js src/views/growthCenterState.test.js src/views/rewardShopState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 5: Manually verify secondary public routes**

  Manually verify:
  - `/leaderboard`
  - `/bookmarks`
  - `/messages`
  - `/messages/:conversationId`
  - `/notices`
  - `/notices/:topic`
  - `/growth`
  - `/rewards/shop`
  - `/rewards/orders`
  - `/settings`
  - `/users/:userId`
  - `/users/:userId/followees`
  - `/users/:userId/followers`

  Check:
  - they inherit the shell without recreating the old right-panel information load
  - desktop left navigation and mobile `Me / More` entry model still make sense

- [ ] **Step 6: Checkpoint the diff for the inheritance task**

  Note:
  - do not create a git commit unless the user explicitly asks for one

---

### Task 6: Final Verification And Cleanup

**Files:**
- Verify only; no specific write scope unless issues are discovered during verification

- [ ] **Step 1: Run the full frontend test suite**

  Run:
  - `cd frontend && npm test`

- [ ] **Step 2: Run the production build**

  Run:
  - `cd frontend && npm run build`

- [ ] **Step 3: Manually verify the major public-shell routes on desktop**

  Manually verify:
  - `/posts`
  - `/search`
  - `/posts/:postId`
  - `/leaderboard`
  - `/bookmarks`
  - `/messages`
  - `/notices`
  - `/growth`
  - `/rewards/shop`
  - `/settings`
  - `/users/:userId`

  Check:
  - no public right panel remains
  - topbar is smaller and title-first
  - page opening order is orientation -> operation -> content

- [ ] **Step 4: Manually verify mobile behavior**

  Manually verify on a mobile viewport:
  - `/posts`
  - `/search`
  - one representative `Me` route such as `/bookmarks` or `/users/:userId`

  Check:
  - bottom tabs are `Posts / Search / Me / More`
  - non-search pages do not surface a second full search input in the topbar
  - first viewport still reveals real content

- [ ] **Step 5: Remove any leftover dead references discovered during verification**

  Remove if still present:
  - dead imports of `RightPanel`
  - dead CSS for a public third column
  - dead UI state for public right-panel toggling
  - stale navigation keys or tests that reflect the old contract

- [ ] **Step 6: Checkpoint the final diff**

  Note:
  - do not create a git commit unless the user explicitly asks for one

