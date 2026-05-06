# Community Frontend Product Redesign Remaining Workspaces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the remaining Community, Trading, Personal, and Admin workspace migrations, then polish the product so every in-scope route reads like one mature application.

**Architecture:** Reuse the shell, navigation, tokens, and shared UI primitives already shipped in the foundation. Keep page-specific data shaping in the existing `views/*State.js` helpers and use `UiPageHeader`, `UiToolbar`, `UiState`, `UiEmpty`, `UiBadge`, `UiCard`, and the existing layout shell to express the same page grammar everywhere. Avoid adding new shell layers or broad API changes; this phase is mostly template/CSS migration plus focused test updates.

**Tech Stack:** Vue 3, Vue Router 4, Pinia, CSS custom properties, Vitest, Vue Test Utils, Vite, existing API services.

---

## Scope

The foundation plan is already complete. This follow-up plan covers the remaining stages in the accepted redesign spec:

1. Community workspace pages.
2. Trading workspace pages.
3. Personal workspace pages.
4. Admin workspace pages.
5. Polish, guardrails, and documentation follow-up.

## Current Files And Responsibilities

Shared product primitives already exist and should be reused rather than replaced:

- `frontend/src/components/ui/UiPageHeader.vue`
- `frontend/src/components/ui/UiToolbar.vue`
- `frontend/src/components/ui/UiState.vue`
- `frontend/src/components/ui/UiEmpty.vue`
- `frontend/src/components/ui/UiBadge.vue`
- `frontend/src/components/ui/UiCard.vue`

Workspace touchpoints that still need product-grade migration:

- Community: `frontend/src/views/PostsView.vue`, `PostDetailView.vue`, `SearchView.vue`, `UserProfileView.vue`, `BookmarksView.vue`, `FollowersView.vue`, `FolloweesView.vue`, `posts/PostsView.css`, `post-detail/PostDetailView.css`, `posts/usePostsFeed.js`, `posts/usePostComposer.js`, `postDetailState.js`, `postsViewState.js`, `searchResultSurface.js`, `userProfileSurface.js`, `userProfileTimeline.js`, and the existing community tests.
- Trading: `frontend/src/views/MarketListView.vue`, `MarketDetailView.vue`, `MarketPublishView.vue`, `MarketMyListingsView.vue`, `MarketInventoryView.vue`, `MarketBuyingOrdersView.vue`, `MarketSellingOrdersView.vue`, `MarketOrderDetailView.vue`, `MarketAddressesView.vue`, `marketState.js`, and the existing market tests.
- Personal: `frontend/src/views/WalletView.vue`, `ConversationsView.vue`, `ConversationDetailView.vue`, `NoticesView.vue`, `NoticeDetailView.vue`, `SettingsView.vue`, `walletState.js`, `conversationDetailState.js`, and the existing personal tests.
- Admin: `frontend/src/views/AnalyticsView.vue`, `ModerationView.vue`, `UserManagementView.vue`, `WalletAdminView.vue`, `AdminMarketDisputesView.vue`, `OpsConsoleView.vue`, and the existing admin tests.
- Global polish: `frontend/src/styles/pages.css`, `frontend/src/styles/layout.css`, `frontend/src/styles/components.css`, `frontend/src/styles/variables.css`, `frontend/src/views/viewComplexity.test.js`, and `docs/handbook/frontend.md`.

---

## Task 1: Community Workspace Migration

**Files:**
- Modify: `frontend/src/views/PostsView.vue`
- Modify: `frontend/src/views/posts/PostsView.css`
- Modify: `frontend/src/views/posts/PostsFeedList.vue`
- Modify: `frontend/src/views/posts/PostComposerPanel.vue`
- Modify: `frontend/src/views/posts/usePostsFeed.js`
- Modify: `frontend/src/views/posts/usePostComposer.js`
- Modify: `frontend/src/views/postsViewState.js`
- Modify: `frontend/src/views/PostDetailView.vue`
- Modify: `frontend/src/views/post-detail/PostDetailView.css`
- Modify: `frontend/src/views/post-detail/PostDetailActions.vue`
- Modify: `frontend/src/views/post-detail/PostDetailComments.vue`
- Modify: `frontend/src/views/postDetailState.js`
- Modify: `frontend/src/views/SearchView.vue`
- Modify: `frontend/src/views/searchResultSurface.js`
- Modify: `frontend/src/views/UserProfileView.vue`
- Modify: `frontend/src/views/userProfileSurface.js`
- Modify: `frontend/src/views/userProfileTimeline.js`
- Modify: `frontend/src/views/BookmarksView.vue`
- Modify: `frontend/src/views/FollowersView.vue`
- Modify: `frontend/src/views/FolloweesView.vue`
- Modify: `frontend/src/views/PostsView.test.js`
- Modify: `frontend/src/views/SearchView.test.js`
- Modify: `frontend/src/views/UserProfileView.test.js`
- Modify: `frontend/src/views/postsViewState.test.js`
- Modify: `frontend/src/views/postDetailState.test.js`
- Modify: `frontend/src/views/searchResultSurface.test.js`
- Modify: `frontend/src/views/userProfileSurface.test.js`
- Modify: `frontend/src/views/userProfileTimeline.test.js`
- Create: `frontend/src/views/PostDetailView.test.js`
- Create: `frontend/src/views/BookmarksView.test.js`
- Create: `frontend/src/views/FollowersView.test.js`
- Create: `frontend/src/views/FolloweesView.test.js`

- [ ] **Step 1: Write the failing community tests**

Add assertions that the community pages now read as product surfaces:

- `PostsView.test.js`: header, toolbar, unread/new hint, and compact object rows instead of the old editorial feed framing.
- `PostDetailView.test.js`: breadcrumb + reading column + reply composer + context rail.
- `SearchView.test.js`: utility workbench layout, query summary, and result state blocks.
- `UserProfileView.test.js`: identity, relationship, trust, and recent activity sections without a cover-sheet hero.
- `BookmarksView.test.js`, `FollowersView.test.js`, `FolloweesView.test.js`: reuse the same object-list grammar and empty states.
- `postsViewState.test.js`, `postDetailState.test.js`, `searchResultSurface.test.js`, `userProfileSurface.test.js`, `userProfileTimeline.test.js`: keep the data-shaping helpers stable while the templates change.

- [ ] **Step 2: Run the focused community suite and verify it fails**

Run:

```bash
cd frontend
npm test -- src/views/PostsView.test.js src/views/PostDetailView.test.js src/views/SearchView.test.js src/views/UserProfileView.test.js src/views/BookmarksView.test.js src/views/FollowersView.test.js src/views/FolloweesView.test.js src/views/postsViewState.test.js src/views/postDetailState.test.js src/views/searchResultSurface.test.js src/views/userProfileSurface.test.js src/views/userProfileTimeline.test.js
```

Expected: FAIL because the current templates still have a mix of hero copy, cover-sheet patterns, and page-specific list chrome.

- [ ] **Step 3: Refactor the community pages**

Implement the community workspace as one product grammar:

- `PostsView.vue` and `PostsView.css`: keep composer entry points, but move the page into `UiPageHeader` + `UiToolbar` + `UiState` + compact discussion rows.
- `PostDetailView.vue` and `post-detail/PostDetailView.css`: replace the old full-card reading shell with a reading column, thread context, and reply composer that matches the new product density.
- `SearchView.vue`: remove workbench-like hero language, keep query and filter controls in a toolbar, and show honest loading/empty/error states.
- `UserProfileView.vue`: remove the large snapshot/cover composition, keep a concise identity rail, relationship stats, public data, and timeline in structured sections.
- `BookmarksView.vue`, `FollowersView.vue`, `FolloweesView.vue`: use the same object-list pattern as the main feed and keep the page titles and subtitles short.
- Keep the data truth in `postsViewState.js`, `postDetailState.js`, `searchResultSurface.js`, `userProfileSurface.js`, and `userProfileTimeline.js`; do not move business semantics into the templates.

- [ ] **Step 4: Re-run the community tests and verify they pass**

Run:

```bash
cd frontend
npm test -- src/views/PostsView.test.js src/views/PostDetailView.test.js src/views/SearchView.test.js src/views/UserProfileView.test.js src/views/BookmarksView.test.js src/views/FollowersView.test.js src/views/FolloweesView.test.js src/views/postsViewState.test.js src/views/postDetailState.test.js src/views/searchResultSurface.test.js src/views/userProfileSurface.test.js src/views/userProfileTimeline.test.js
```

Expected: PASS.

- [ ] **Step 5: Browser check representative community routes**

Check desktop and 390px for:

- `/#/posts`
- `/#/posts/:postId`
- `/#/search`
- `/#/users/:userId`
- `/#/bookmarks`
- `/#/followers/:userId`
- `/#/followees/:userId`

Expected: no overlap, no hero cards, no clipped text, and no inert controls.

---

## Task 2: Trading Workspace Migration

**Files:**
- Modify: `frontend/src/views/MarketListView.vue`
- Modify: `frontend/src/views/MarketDetailView.vue`
- Modify: `frontend/src/views/MarketPublishView.vue`
- Modify: `frontend/src/views/MarketMyListingsView.vue`
- Modify: `frontend/src/views/MarketInventoryView.vue`
- Modify: `frontend/src/views/MarketBuyingOrdersView.vue`
- Modify: `frontend/src/views/MarketSellingOrdersView.vue`
- Modify: `frontend/src/views/MarketOrderDetailView.vue`
- Modify: `frontend/src/views/MarketAddressesView.vue`
- Modify: `frontend/src/views/marketState.js`
- Modify: `frontend/src/views/MarketViews.test.js`
- Modify: `frontend/src/views/MarketOrderViews.test.js`
- Modify: `frontend/src/views/MarketSellerViews.test.js`
- Modify: `frontend/src/views/MarketAddressesView.test.js`
- Modify: `frontend/src/views/marketState.test.js`
- Create: `frontend/src/views/MarketDetailView.test.js`
- Create: `frontend/src/views/MarketPublishView.test.js`
- Create: `frontend/src/views/MarketMyListingsView.test.js`
- Create: `frontend/src/views/MarketInventoryView.test.js`
- Create: `frontend/src/views/MarketBuyingOrdersView.test.js`
- Create: `frontend/src/views/MarketSellingOrdersView.test.js`
- Create: `frontend/src/views/MarketOrderDetailView.test.js`

- [ ] **Step 1: Write the failing trading tests**

Add assertions for:

- market list filters, status labels, and trust-oriented summary rows.
- listing detail layout with price, stock, delivery mode, and the primary action.
- publish/inventory views as workflow forms rather than hero pages.
- buying/selling order lists as status-first operational surfaces.
- order detail showing lifecycle, payment/escrow, dispute, and audit context.
- addresses as compact management rows with default/edit/delete actions.
- `marketState.test.js` staying the source of truth for status text and list item shaping.

- [ ] **Step 2: Run the focused trading suite and verify it fails**

Run:

```bash
cd frontend
npm test -- src/views/MarketViews.test.js src/views/MarketDetailView.test.js src/views/MarketPublishView.test.js src/views/MarketMyListingsView.test.js src/views/MarketInventoryView.test.js src/views/MarketBuyingOrdersView.test.js src/views/MarketSellingOrdersView.test.js src/views/MarketOrderDetailView.test.js src/views/MarketAddressesView.test.js src/views/MarketOrderViews.test.js src/views/MarketSellerViews.test.js src/views/marketState.test.js
```

Expected: FAIL because the market pages still carry hero-style sections and page-specific chrome.

- [ ] **Step 3: Refactor the trading pages**

Move the market workspace to the product grammar:

- `MarketListView.vue`: turn the top of the page into a filter/action bar and keep listings in compact rows or object cards with price, stock, and fulfillment state.
- `MarketDetailView.vue`: use one summary header, one action panel, and one details panel; avoid stacked hero blocks.
- `MarketPublishView.vue` and `MarketInventoryView.vue`: behave like forms/workflows with explicit field groups and clear status semantics.
- `MarketBuyingOrdersView.vue`, `MarketSellingOrdersView.vue`, and `MarketOrderDetailView.vue`: make status, next action, and audit context the visual anchor.
- `MarketAddressesView.vue`: keep the default address and inline editing affordances in a compact management surface.
- `marketState.js`: continue to normalize labels and status text so the views do not duplicate business rules.

- [ ] **Step 4: Re-run the trading tests and verify they pass**

Run:

```bash
cd frontend
npm test -- src/views/MarketViews.test.js src/views/MarketDetailView.test.js src/views/MarketPublishView.test.js src/views/MarketMyListingsView.test.js src/views/MarketInventoryView.test.js src/views/MarketBuyingOrdersView.test.js src/views/MarketSellingOrdersView.test.js src/views/MarketOrderDetailView.test.js src/views/MarketAddressesView.test.js src/views/MarketOrderViews.test.js src/views/MarketSellerViews.test.js src/views/marketState.test.js
```

Expected: PASS.

- [ ] **Step 5: Browser check representative trading routes**

Check desktop and 390px for:

- `/#/market`
- `/#/market/listings/:listingId`
- `/#/market/publish`
- `/#/market/my-listings`
- `/#/market/inventory`
- `/#/market/buying-orders`
- `/#/market/selling-orders`
- `/#/market/orders/:orderId`
- `/#/market/addresses`

Expected: pricing, status, and stock remain readable and no form field overflows on mobile.

---

## Task 3: Personal Workspace Migration

**Files:**
- Modify: `frontend/src/views/WalletView.vue`
- Modify: `frontend/src/views/walletState.js`
- Modify: `frontend/src/views/ConversationsView.vue`
- Modify: `frontend/src/views/ConversationDetailView.vue`
- Modify: `frontend/src/views/conversationDetailState.js`
- Modify: `frontend/src/views/NoticesView.vue`
- Modify: `frontend/src/views/NoticeDetailView.vue`
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/WalletView.test.js`
- Modify: `frontend/src/views/NoticeDetailView.test.js`
- Modify: `frontend/src/views/SettingsView.test.js`
- Modify: `frontend/src/views/walletState.test.js`
- Modify: `frontend/src/views/conversationDetailState.test.js`
- Create: `frontend/src/views/ConversationsView.test.js`
- Create: `frontend/src/views/NoticesView.test.js`
- Create: `frontend/src/views/ConversationDetailView.test.js`

- [ ] **Step 1: Write the failing personal workspace tests**

Add assertions for:

- wallet balance / pending / available status blocks and ledger history.
- inbox and thread detail layout for messages.
- notice inbox + notice topic detail with unread handling.
- settings page identity and upload sections without filler hero text.

- [ ] **Step 2: Run the focused personal suite and verify it fails**

Run:

```bash
cd frontend
npm test -- src/views/WalletView.test.js src/views/ConversationsView.test.js src/views/ConversationDetailView.test.js src/views/NoticesView.test.js src/views/NoticeDetailView.test.js src/views/SettingsView.test.js src/views/walletState.test.js src/views/conversationDetailState.test.js
```

Expected: FAIL because the current pages still mix hero-like sections with older card stacks.

- [ ] **Step 3: Refactor the personal pages**

Bring the personal workspace onto the product grammar:

- `WalletView.vue`: convert the hero copy into a concise balance/status panel and a ledger list that distinguishes pending from completed transactions.
- `ConversationsView.vue` and `ConversationDetailView.vue`: keep the inbox + thread pattern, but make the list and message composer denser and more operational.
- `NoticesView.vue` and `NoticeDetailView.vue`: use grouped notice summaries and detail views that emphasize unread state and freshness.
- `SettingsView.vue`: keep the existing upload flow but make the public identity and avatar workflow read like a settings surface, not a profile article.
- `walletState.js` and `conversationDetailState.js`: preserve the user-visible state semantics and keep any non-trivial transformations in pure helpers.

- [ ] **Step 4: Re-run the personal tests and verify they pass**

Run:

```bash
cd frontend
npm test -- src/views/WalletView.test.js src/views/ConversationsView.test.js src/views/ConversationDetailView.test.js src/views/NoticesView.test.js src/views/NoticeDetailView.test.js src/views/SettingsView.test.js src/views/walletState.test.js src/views/conversationDetailState.test.js
```

Expected: PASS.

- [ ] **Step 5: Browser check representative personal routes**

Check desktop and 390px for:

- `/#/wallet`
- `/#/messages`
- `/#/messages/:conversationId`
- `/#/notices`
- `/#/notices/:topic`
- `/#/settings`

Expected: no clipped balances, no unread badge overlap, and no upload controls collapsing into each other.

---

## Task 4: Admin Workspace Migration

**Files:**
- Modify: `frontend/src/views/AnalyticsView.vue`
- Modify: `frontend/src/views/ModerationView.vue`
- Modify: `frontend/src/views/UserManagementView.vue`
- Modify: `frontend/src/views/WalletAdminView.vue`
- Modify: `frontend/src/views/AdminMarketDisputesView.vue`
- Modify: `frontend/src/views/OpsConsoleView.vue`
- Modify: `frontend/src/views/ModerationView.test.js`
- Modify: `frontend/src/views/WalletAdminView.test.js`
- Create: `frontend/src/views/AnalyticsView.test.js`
- Create: `frontend/src/views/UserManagementView.test.js`
- Create: `frontend/src/views/AdminMarketDisputesView.test.js`
- Create: `frontend/src/views/OpsConsoleView.test.js`

- [ ] **Step 1: Write the failing admin tests**

Add assertions for:

- analytics filters, KPIs, and freshness/status copy.
- moderation queue, audit trail, and action modal emphasis.
- user management search + role change confirmation.
- wallet admin dangerous actions and local session log.
- market disputes showing buyer/seller claims, fund state, and final resolution actions.
- ops console keeping its high-risk controls visually distinct.

- [ ] **Step 2: Run the focused admin suite and verify it fails**

Run:

```bash
cd frontend
npm test -- src/views/AnalyticsView.test.js src/views/ModerationView.test.js src/views/UserManagementView.test.js src/views/WalletAdminView.test.js src/views/AdminMarketDisputesView.test.js src/views/OpsConsoleView.test.js
```

Expected: FAIL because several pages still rely on older hero/banner layouts and generic card stacks.

- [ ] **Step 3: Refactor the admin pages**

Bring the admin workspace into a console-style product layout:

- `AnalyticsView.vue`: keep the date filters and KPI modules, but make the page feel like an operational dashboard with explicit scope and data freshness.
- `ModerationView.vue`: keep the queue/action modal, but align spacing, table rows, badges, and audit context with the rest of the product.
- `UserManagementView.vue`: keep the search, role edit, and confirmation flow, but present them as an audit-friendly management surface.
- `WalletAdminView.vue`: separate dangerous actions from the session log and make risk copy and traceability explicit.
- `AdminMarketDisputesView.vue`: show the claims and resolution actions in a structured list/detail layout.
- `OpsConsoleView.vue`: keep the surface visually distinct, but use the same product tokens and shared state semantics as the rest of admin.

- [ ] **Step 4: Re-run the admin tests and verify they pass**

Run:

```bash
cd frontend
npm test -- src/views/AnalyticsView.test.js src/views/ModerationView.test.js src/views/UserManagementView.test.js src/views/WalletAdminView.test.js src/views/AdminMarketDisputesView.test.js src/views/OpsConsoleView.test.js
```

Expected: PASS.

- [ ] **Step 5: Browser check representative admin routes**

Check desktop and 390px for:

- `/#/analytics`
- `/#/moderation`
- `/#/admin/users`
- `/#/admin/wallet`
- `/#/admin/market-disputes`
- `/#/admin/ops`

Expected: no dashboard-card clutter, no modal overflow, and no dangerous control ambiguity.

---

## Task 5: Polish, Guardrails, And Documentation

**Files:**
- Modify: `frontend/src/styles/pages.css`
- Modify: `frontend/src/styles/layout.css`
- Modify: `frontend/src/styles/components.css`
- Modify: `frontend/src/styles/variables.css`
- Modify: `frontend/src/views/viewComplexity.test.js`
- Modify: `docs/handbook/frontend.md`

- [ ] **Step 1: Write the failing guardrail and polish tests**

Extend `frontend/src/views/viewComplexity.test.js` so it catches the reintroduction of:

- market hero selectors in `pages.css`.
- editorial cover-sheet style patterns in public/profile pages.
- oversized card radii or shadow-heavy public defaults.

Also update any affected page tests so they check the final copy/state language after the workspace migrations.

- [ ] **Step 2: Run the guardrail test and verify it fails**

Run:

```bash
cd frontend
npm test -- src/views/viewComplexity.test.js
```

Expected: FAIL while the old hero/layout selectors still exist.

- [ ] **Step 3: Remove the remaining legacy visual patterns**

Make the remaining style cleanup:

- `pages.css`: remove or neutralize leftover hero selectors and keep page-level patterns focused on lists, detail layouts, and workflow rows.
- `layout.css` and `components.css`: keep the public shell/product shell neutral, maintain modest radii, and avoid decorative shadows on routine surfaces.
- `variables.css`: keep the color system neutral and semantic instead of drifting back toward editorial styling.
- Sweep the touched views for copy that sounds like a demo or a placeholder and replace it with product copy that states scope, status, or next action.

- [ ] **Step 4: Re-run the guardrail test and verify it passes**

Run:

```bash
cd frontend
npm test -- src/views/viewComplexity.test.js
```

Expected: PASS.

- [ ] **Step 5: Update the frontend handbook**

Update `docs/handbook/frontend.md` if the final workspace migration changes route grouping semantics, page-state helper responsibilities, or the way the shell and shared UI primitives are used.

- [ ] **Step 6: Run the broad frontend verification**

Run:

```bash
cd frontend
npm test
npm run build
```

Expected: both commands pass.

- [ ] **Step 7: Re-run browser smoke checks**

Re-check representative desktop and 390px routes across all four workspaces plus account/system routes:

- Community: `/#/posts`, `/#/posts/:postId`, `/#/search`, `/#/users/:userId`
- Trading: `/#/market`, `/#/market/listings/:listingId`, `/#/market/publish`
- Personal: `/#/wallet`, `/#/messages`, `/#/notices`, `/#/settings`
- Admin: `/#/analytics`, `/#/moderation`, `/#/admin/users`
- System/account: `/#/auth/login`, `/#/forbidden`, `/#/not-found`

Expected: shell/nav behavior stays coherent, page bodies fit the viewport, and no route shows an inert search or a conflicting navigation model.

---

## Self-Review Checklist

- Community pages now use the same header / toolbar / list / state grammar.
- Trading pages communicate price, status, stock, and next action without hero blocks.
- Personal pages stay quiet, task-focused, and honest about pending or unavailable state.
- Admin pages read like an operations console instead of generic cards.
- Global styles no longer prefer editorial surfaces as the default product look.
- The handbook and guardrails still describe the implemented frontend behavior.

