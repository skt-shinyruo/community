# Community Frontend UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved frontend redesign so the public product feels like a reading-first discussion community and the admin product feels like a calm operations desk, while preserving existing routes and business flows.

**Architecture:** Execute the redesign in two phases. First, rewrite the shared token system, shell, and UI primitives so the app has explicit `public workspace`, `reading page`, `admin desk`, and `auth minimal` presentation modes. Then, once the shared foundation is stable, rewrite page families in parallel using disjoint write scopes: public core, supporting personal/social surfaces, admin surfaces, and auth/system polish. Finish with an integration pass that removes remaining placeholder UI, aligns route metadata, and verifies desktop/mobile behavior.

**Tech Stack:** Vue 3 SFCs, Vue Router, Pinia, Vite, Vitest, CSS variables, scoped CSS

---

## File Structure Map

### Shared foundation and shell

- `frontend/src/App.vue`
  Role: top-level route shell split between auth and app workspace; global toast wiring.
- `frontend/src/styles/variables.css`
  Role: global tokens for color, spacing, typography, layout, density.
- `frontend/src/styles/base.css`
  Role: reset, body typography, focus policy, base element defaults.
- `frontend/src/styles/utils.css`
  Role: utility helpers that currently encourage inline style escape hatches.
- `frontend/src/styles/components.css`
  Role: shared component visuals for controls, cards, chips, modals, page header, tags.
- `frontend/src/styles/layout.css`
  Role: shell structure, topbar/sidebar/right panel/mobile behavior, auth shell.
- `frontend/src/styles/pages.css`
  Role: page-level shared patterns, currently centered on table-like topic list.
- `frontend/src/components/layout/AppShell.vue`
  Role: desktop/mobile app workspace container.
- `frontend/src/components/layout/AuthShell.vue`
  Role: auth-only minimal shell.
- `frontend/src/components/layout/Topbar.vue`
  Role: page title, global search, theme/density/user actions.
- `frontend/src/components/layout/SidebarNav.vue`
  Role: main navigation and signed-in identity block.
- `frontend/src/components/layout/RightPanel.vue`
  Role: contextual support column; currently mixes real data with fake content.
- `frontend/src/components/layout/MobileNav.vue`
  Role: mobile bottom navigation.
- `frontend/src/components/ui/UiButton.vue`
- `frontend/src/components/ui/UiCard.vue`
- `frontend/src/components/ui/UiAvatar.vue`
- `frontend/src/components/ui/UiBreadcrumb.vue`
- `frontend/src/components/ui/UiChips.vue`
- `frontend/src/components/ui/UiInput.vue`
- `frontend/src/components/ui/UiTextarea.vue`
- `frontend/src/components/ui/UiPageHeader.vue`
- `frontend/src/components/ui/UiEmpty.vue`
- `frontend/src/components/ui/UiModalConfirm.vue`
- `frontend/src/components/ui/UiPagination.vue`
- `frontend/src/components/ui/UiRoleBadge.vue`
- `frontend/src/components/ui/UiBadge.vue`
- `frontend/src/components/ui/UiTag.vue`
- `frontend/src/components/ui/UiToast.vue`
  Role: shared primitives that must become the default styling path.

### Public core write scope

- `frontend/src/components/posts/FeedToolbar.vue`
- `frontend/src/views/PostsView.vue`
- `frontend/src/views/PostDetailView.vue`
- `frontend/src/views/SearchView.vue`
- `frontend/src/views/UserProfileView.vue`
  Role: discussion-defining public path; these views establish the new content-first grammar.

### Supporting personal/social write scope

- `frontend/src/views/ConversationsView.vue`
- `frontend/src/views/ConversationDetailView.vue`
- `frontend/src/views/NoticesView.vue`
- `frontend/src/views/NoticeDetailView.vue`
- `frontend/src/views/SettingsView.vue`
- `frontend/src/views/BookmarksView.vue`
- `frontend/src/views/LeaderboardView.vue`
- `frontend/src/views/FolloweesView.vue`
- `frontend/src/views/FollowersView.vue`
  Role: authenticated personal/social surfaces that should inherit the new public language without inventing a separate product.

### Admin desk write scope

- `frontend/src/views/AnalyticsView.vue`
- `frontend/src/views/ModerationView.vue`
- `frontend/src/views/OpsConsoleView.vue`
- `frontend/src/views/UserManagementView.vue`
  Role: admin workflow pages that should converge on the same quiet operational model.

### Auth/system polish write scope

- `frontend/src/views/LoginView.vue`
- `frontend/src/views/RegisterView.vue`
- `frontend/src/views/PasswordResetView.vue`
- `frontend/src/views/ActivationView.vue`
- `frontend/src/views/ForbiddenView.vue`
- `frontend/src/views/NotFoundView.vue`
  Role: compatibility surfaces that should align with the redesign but not define it.

### Final integration and route verification

- `frontend/src/router/index.js`
- `frontend/src/views/HomeView.vue`
  Role: route metadata and non-product `/dev` verification; keep functional while confirming scope boundaries.

---

### Task 1: Rewrite Tokens And Shared UI Primitives

**Files:**
- Modify: `frontend/src/styles/variables.css`
- Modify: `frontend/src/styles/base.css`
- Modify: `frontend/src/styles/utils.css`
- Modify: `frontend/src/styles/components.css`
- Modify: `frontend/src/styles/pages.css`
- Modify: `frontend/src/components/ui/UiButton.vue`
- Modify: `frontend/src/components/ui/UiCard.vue`
- Modify: `frontend/src/components/ui/UiAvatar.vue`
- Modify: `frontend/src/components/ui/UiBreadcrumb.vue`
- Modify: `frontend/src/components/ui/UiChips.vue`
- Modify: `frontend/src/components/ui/UiInput.vue`
- Modify: `frontend/src/components/ui/UiTextarea.vue`
- Modify: `frontend/src/components/ui/UiPageHeader.vue`
- Modify: `frontend/src/components/ui/UiEmpty.vue`
- Modify: `frontend/src/components/ui/UiModalConfirm.vue`
- Modify: `frontend/src/components/ui/UiPagination.vue`
- Modify: `frontend/src/components/ui/UiRoleBadge.vue`
- Modify: `frontend/src/components/ui/UiBadge.vue`
- Modify: `frontend/src/components/ui/UiTag.vue`
- Modify: `frontend/src/components/ui/UiToast.vue`

- [ ] **Step 1: Run the current frontend checks to capture a clean baseline**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

- [ ] **Step 2: Rewrite the token layer to support public, admin, reading, and auth surfaces**

  Implement:
  - replace the current single slate-first palette with explicit warm public tokens and cooler admin tokens
  - refine typography, spacing, radius, and page-width tokens for editorial and tool layouts
  - preserve density/theme mechanics, but make the token names expressive enough for the new shells

- [ ] **Step 3: Rework shared CSS contracts so pages stop escaping into inline styles**

  Implement:
  - stronger shared control styles in `components.css`
  - named utility/layout helpers in `utils.css` and `pages.css` for spacing, metadata rows, surface stacks, and state blocks
  - page-level shared patterns for discussion cards, reading sheets, admin stat strips, queue rows, and structured form groups

- [ ] **Step 4: Upgrade the shared Vue primitives to expose the variants the page rewrites will need**

  Add or refine:
  - button variants for editorial primary/secondary/ghost and admin danger/destructive flows
  - card/sheet variants for flat, reading, admin, and inset surfaces where appropriate
  - page header support for stronger hierarchy without inline overrides
  - modal/empty/toast/tag/badge primitives that match the new visual system

- [ ] **Step 5: Re-run shared frontend verification after the foundation rewrite**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

- [ ] **Step 6: Checkpoint the diff for the foundation task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Rebuild The Shell Into Explicit Public, Admin, And Auth Modes

**Files:**
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles/layout.css`
- Modify: `frontend/src/components/layout/AppShell.vue`
- Modify: `frontend/src/components/layout/AuthShell.vue`
- Modify: `frontend/src/components/layout/Topbar.vue`
- Modify: `frontend/src/components/layout/SidebarNav.vue`
- Modify: `frontend/src/components/layout/RightPanel.vue`
- Modify: `frontend/src/components/layout/MobileNav.vue`

- [ ] **Step 1: Split the shell behavior into deliberate presentation modes**

  Implement:
  - public workspace mode for community exploration
  - reading-centric content framing within the public product
  - admin desk framing for admin routes
  - minimal auth shell for login/register/reset/activation

- [ ] **Step 2: Simplify topbar, sidebar, and mobile navigation around the new information hierarchy**

  Implement:
  - less crowded topbar with stronger title/search/action hierarchy
  - quieter left navigation for public pages
  - right panel as contextual support rather than decorative filler
  - mobile navigation that complements the new shells instead of mirroring desktop mechanically

- [ ] **Step 3: Remove fake or credibility-damaging shell content**

  Replace:
  - hard-coded trending placeholders
  - dead footer/privacy/terms placeholders
  - shell-level debug-looking elements that do not belong in product UI

- [ ] **Step 4: Verify shell behavior on route transitions and auth/public switching**

  Check:
  - auth routes still bypass the main workspace shell
  - public routes render the redesigned workspace shell
  - admin routes render with the quieter admin desk framing
  - right panel and mobile navigation do not break route transitions

- [ ] **Step 5: Re-run frontend verification after shell changes**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

- [ ] **Step 6: Checkpoint the diff for the shell task**

  Note: do not create a git commit unless the user explicitly asks for one.

---

After Task 2, Tasks 3-6 are designed to run in parallel because they use disjoint write scopes and should consume the shared foundation rather than editing it.

Parallel execution rule:

- workers for Tasks 3-6 should stay within their listed files
- if a worker discovers a missing shared primitive or shared-style blocker, stop and surface the blocker instead of editing Task 1 or Task 2 files opportunistically
- page-family-specific styling should prefer scoped styles inside the task-local view/component files unless the shared foundation is clearly insufficient

### Task 3: Rewrite The Public Core Path Into A Reading-First Discussion Experience

**Files:**
- Modify: `frontend/src/components/posts/FeedToolbar.vue`
- Modify: `frontend/src/views/PostsView.vue`
- Modify: `frontend/src/views/PostDetailView.vue`
- Modify: `frontend/src/views/SearchView.vue`
- Modify: `frontend/src/views/UserProfileView.vue`

- [ ] **Step 1: Rebuild the posts list around discussion cards instead of a metadata table**

  Implement:
  - content-led topic cards/sheets
  - secondary treatment for replies/likes/activity counts
  - composer that feels integrated into the discussion flow
  - toolbar that stays powerful but visually subordinate

- [ ] **Step 2: Rewrite post detail into article-first main content and discussion-first replies**

  Implement:
  - article-like post header/body treatment
  - clearer author and metadata framing
  - reply hierarchy that reads like a conversation instead of nested utility blocks
  - comment composer integrated into the thread

- [ ] **Step 3: Convert search and profile into first-class product surfaces**

  Implement:
  - search as a research/discovery surface using the same public content language
  - profile as a real identity/activity page
  - removal of fake tabs and placeholder profile content

- [ ] **Step 4: Run public-core verification**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

  Manually verify:
  - `/posts`
  - `/posts/:postId`
  - `/search`
  - `/users/:userId`

- [ ] **Step 5: Checkpoint the diff for the public core task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Rewrite Supporting Personal And Social Surfaces To Match The New Product

**Files:**
- Modify: `frontend/src/views/ConversationsView.vue`
- Modify: `frontend/src/views/ConversationDetailView.vue`
- Modify: `frontend/src/views/NoticesView.vue`
- Modify: `frontend/src/views/NoticeDetailView.vue`
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/BookmarksView.vue`
- Modify: `frontend/src/views/LeaderboardView.vue`
- Modify: `frontend/src/views/FolloweesView.vue`
- Modify: `frontend/src/views/FollowersView.vue`

- [ ] **Step 1: Rebuild messaging around inbox + thread on desktop and focused thread behavior on mobile**

  Implement:
  - conversations list that feels like part of the product, not a demo utility
  - thread layout that inherits the new shell and typographic system
  - stronger message metadata and composition treatment

- [ ] **Step 2: Normalize notices, settings, and secondary list pages onto shared patterns**

  Implement:
  - notices as a message/feed pattern
  - settings as grouped account/preferences/sensitive-action sections
  - bookmarks, leaderboard, and follow/follower pages using inherited public list/profile grammar rather than bespoke styling

- [ ] **Step 3: Remove debug-looking identity presentation where real product framing should exist**

  Replace:
  - raw `用户 #id`-style presentation where richer context is available
  - inconsistent empty/loading blocks across personal surfaces

- [ ] **Step 4: Run personal/social verification**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

  Manually verify:
  - `/messages`
  - `/messages/:conversationId`
  - `/notices`
  - `/notices/:topic`
  - `/settings`
  - `/bookmarks`
  - `/leaderboard`
  - `/users/:userId/followees`
  - `/users/:userId/followers`

- [ ] **Step 5: Checkpoint the diff for the personal/social task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 5: Converge Admin Pages On The Quiet Operations Desk Model

**Files:**
- Modify: `frontend/src/views/AnalyticsView.vue`
- Modify: `frontend/src/views/ModerationView.vue`
- Modify: `frontend/src/views/OpsConsoleView.vue`
- Modify: `frontend/src/views/UserManagementView.vue`

- [ ] **Step 1: Define one reusable admin page structure across all admin routes**

  Implement:
  - KPI/stat strip
  - filter/action row
  - record list/table region
  - restrained destructive/high-risk actions

- [ ] **Step 2: Rewrite analytics, moderation, ops, and user management to use that structure**

  Implement:
  - intentional analytics layout even when chart depth is limited
  - moderation queue and action flow with clearer state/risk emphasis
  - compact high-risk ops console
  - user management aligned to the same control-desk language

- [ ] **Step 3: Remove placeholder and fake-admin treatments**

  Replace:
  - placeholder chart presentation
  - scattered inline modal/layout styling where shared admin patterns now exist

- [ ] **Step 4: Run admin verification**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

  Manually verify:
  - `/analytics`
  - `/moderation`
  - `/ops`
  - `/admin/users`

- [ ] **Step 5: Checkpoint the diff for the admin task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 6: Polish Auth And System Compatibility Routes

**Files:**
- Modify: `frontend/src/views/LoginView.vue`
- Modify: `frontend/src/views/RegisterView.vue`
- Modify: `frontend/src/views/PasswordResetView.vue`
- Modify: `frontend/src/views/ActivationView.vue`
- Modify: `frontend/src/views/ForbiddenView.vue`
- Modify: `frontend/src/views/NotFoundView.vue`
- Verify only: `frontend/src/views/HomeView.vue`

- [ ] **Step 1: Align auth pages with the new minimal auth shell**

  Implement:
  - new spacing, hierarchy, and state treatment
  - visual compatibility with the redesigned product without inventing a separate mini-brand
  - removal of outdated inline tweaks where the shared auth shell and shared primitives are sufficient

- [ ] **Step 2: Polish forbidden/not-found screens to match the new system**

  Implement:
  - honest, product-aligned system states
  - cleaner empty/error framing

- [ ] **Step 3: Verify `/dev` stays functional but intentionally out of redesign scope**

  Verify:
  - no product-grade redesign required
  - no regressions introduced by shell or token changes

- [ ] **Step 4: Run auth/system verification**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

  Manually verify:
  - `/auth/login`
  - `/auth/register`
  - `/auth/password/reset`
  - `/auth/activation/:userId/:code`
  - `/403`
  - `/:pathMatch(.*)*`
  - `/dev`

- [ ] **Step 5: Checkpoint the diff for the auth/system task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 7: Final Integration, Route Metadata Cleanup, And Whole-Frontend Verification

**Files:**
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/App.vue`
- Verify only: `frontend/src/styles/variables.css`
- Verify only: `frontend/src/styles/layout.css`
- Verify only: `frontend/src/styles/components.css`
- Verify only: `frontend/src/views`

- [ ] **Step 1: Reconcile route metadata and final shell behavior with the redesigned product**

  Implement:
  - route titles/subtitles that match the new product language where needed
  - final shell gating across public/admin/auth/system routes
  - cleanup of any route-level assumptions broken by the redesign

- [ ] **Step 2: Sweep for remaining placeholder or credibility-damaging UI**

  Remove or replace:
  - leftover fake widgets
  - dead decorative legal/footer placeholders
  - obvious raw-ID presentation that survived earlier tasks
  - newly introduced inline style escapes in redesigned routes

- [ ] **Step 3: Run full frontend verification**

  Run:
  - `cd frontend && npm test`
  - `cd frontend && npm run build`

- [ ] **Step 4: Execute the final manual verification matrix**

  Verify:
  - public core routes: `/posts`, `/posts/:postId`, `/search`, `/users/:userId`
  - personal/social routes: `/messages`, `/messages/:conversationId`, `/notices`, `/notices/:topic`, `/settings`, `/bookmarks`, `/leaderboard`, `/users/:userId/followees`, `/users/:userId/followers`
  - admin routes: `/analytics`, `/moderation`, `/ops`, `/admin/users`
  - auth/system routes: `/auth/login`, `/auth/register`, `/auth/password/reset`, `/auth/activation/:userId/:code`, `/403`, `/:pathMatch(.*)*`
  - non-product compatibility route: `/dev`
  - desktop and mobile breakpoints for public shell, admin desk, reading pages, auth shell, and messaging

- [ ] **Step 5: Summarize residual gaps before closing the implementation**

  Report:
  - any placeholders intentionally kept as honest intermediate states
  - any routes that are visually compatible but still candidates for future product work
  - any verification that could not be completed locally
